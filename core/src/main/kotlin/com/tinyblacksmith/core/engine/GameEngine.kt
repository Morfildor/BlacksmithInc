package com.tinyblacksmith.core.engine

import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.BlessingEffect
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.content.UpgradeEffect
import com.tinyblacksmith.core.crafting.Forge
import com.tinyblacksmith.core.gazette.Gazette
import com.tinyblacksmith.core.heroes.Heroes
import com.tinyblacksmith.core.legacy.Legacy
import com.tinyblacksmith.core.legacy.LegacyOutcome
import com.tinyblacksmith.core.legacy.RunEndResult
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.persistence.EventCompaction
import com.tinyblacksmith.core.rng.RngState
import com.tinyblacksmith.core.rng.RngStream

/**
 * Pure, deterministic command handler (GDD 13.2). No Android, no coroutines, no platform randomness.
 * Same content version + seed + command sequence => identical state and ordered events.
 */
class GameEngine(val content: ContentCatalog = com.tinyblacksmith.core.content.LaunchContent.catalog, val config: BalanceConfig = BalanceConfig.DEFAULT) {

    companion object {
        const val RULES_VERSION = 1
    }

    init {
        val problems = content.validate()
        require(problems.isEmpty()) { "Invalid content: $problems" }
    }

    fun newRun(legacy: LegacyProfile, seed: Long, rulesVersion: Int = RULES_VERSION): GameState {
        val era = legacy.nextEra
        val runId = RunId("era$era-seed$seed")
        val startingGold = config.startingGold + upgradeTotal(legacy, UpgradeEffect.STARTING_GOLD)
        val startingEnergy = config.baseDailyEnergy + upgradeTotal(legacy, UpgradeEffect.STARTING_ENERGY)
        val startingIntegrity = config.startingForgeIntegrity + upgradeTotal(legacy, UpgradeEffect.STARTING_INTEGRITY)
        val startingReputation = upgradeTotal(legacy, UpgradeEffect.STARTING_REPUTATION)
        val extraStock = upgradeTotal(legacy, UpgradeEffect.STARTING_MATERIALS)  // Well-Stocked Cellar: more of every starting-kit material
        val materials = config.startingMaterials.mapNotNull { (k, v) -> content.materialById[MaterialId(k)]?.let { it.id to v + extraStock } }.toMap()
        val seedState = GameState(
            runId = runId, seed = seed, rulesVersion = rulesVersion, contentVersion = content.version, era = era, day = 1,
            phase = Phase.PLANNING, gold = startingGold, energy = startingEnergy, overworkToday = 0, reputation = startingReputation,
            rng = RngState.seeded(seed, rulesVersion), world = WorldModifiers(), materials = materials,
            supplierStock = restockedSupplier(), weapons = emptyMap(), heroes = emptyMap(),
            town = Town(integrity = startingIntegrity, militia = 5, championIds = emptyList(), nextSiegeDay = config.siegeInterval),
            factions = emptyMap(), commissions = emptyMap(), events = emptyList(), blessings = emptyList(), pendingBlessingOffer = emptyList(),
            milestones = emptySet(), legacy = legacy, discoveriesThisRun = 0, processedEndDayIds = emptySet(), lastResolution = null,
            nextWeaponSerial = 1, nextHeroSerial = 1, nextCommissionSerial = 1, nextEventSerial = 1,
        )
        val ctx = ResolutionContext(seedState, content, config)
        val world = ctx.rng(RngStream.WORLD)
        ctx.world = when (world.nextInt(3)) {
            0 -> WorldModifiers(1.0, 1.0, "Calm Season")
            1 -> WorldModifiers(1.1, 1.1, "Restless Roads")
            else -> WorldModifiers(0.95, 0.9, "Lean Harvest")
        }
        val fRng = ctx.rng(RngStream.FACTIONS)
        for (f in content.factions) ctx.factions[f.id] = FactionState(f.id, fRng.nextInt(config.startingPressureMin, config.startingPressureMax))
        val hRng = ctx.rng(RngStream.HEROES)
        val descendant = legacy.lineages.lastOrNull()
        repeat(config.startingHeroCount) { i ->
            val h = Heroes.generate(ctx, hRng, if (i == 0) descendant else null)
            ctx.updateHero(h)
        }
        ctx.emit(EventType.RUN_STARTED, 5, "Era $era begins in Emberfall under a ${ctx.world.name.lowercase()}. The first invasion is expected on day ${config.siegeInterval}.")
        descendant?.let { d ->
            val h = ctx.heroes.values.first { it.descendantOf == d.heroName }
            ctx.emit(EventType.HERO_ARRIVED, 4, "${h.fullName}, descendant of ${d.heroName} who ${d.deed}, has come to Emberfall.", listOf(h.id.value))
        }
        val state = ctx.toState()
        assertInvariants(state)
        return state
    }

    fun handle(state: GameState, command: Command): CommandOutcome {
        if (state.isEnded && command !is Command.EndDay) return CommandOutcome.Rejected(GameError.RunEnded)
        return when (command) {
            is Command.Forge -> forge(state, command)
            is Command.ToggleShelf -> toggleShelf(state, command)
            is Command.SetPrice -> setPrice(state, command)
            is Command.BuyMaterial -> buyMaterial(state, command)
            is Command.AcceptCommission -> commission(state, command.commissionId, CommissionStatus.ACCEPTED)
            is Command.DeclineCommission -> commission(state, command.commissionId, CommissionStatus.DECLINED)
            is Command.ChooseBlessing -> chooseBlessing(state, command)
            is Command.EndDay -> endDay(state, command.commandId)
        }
    }

    private fun forge(state: GameState, cmd: Command.Forge): CommandOutcome {
        val ctx = ResolutionContext(state, content, config)
        val ok = when (val v = Forge.validate(ctx, cmd)) {
            is Forge.Validation.Error -> return CommandOutcome.Rejected(v.error)
            is Forge.Validation.Ok -> v
        }
        val weapon = Forge.apply(ctx, cmd, ok)
        return accept(ctx, forgedWeaponId = weapon.id)
    }

    private fun toggleShelf(state: GameState, cmd: Command.ToggleShelf): CommandOutcome {
        val weapon = state.weapons[cmd.weaponId] ?: return CommandOutcome.Rejected(GameError.WeaponNotFound(cmd.weaponId))
        val ctx = ResolutionContext(state, content, config)
        if (cmd.listed) {
            if (!weapon.isInStorage) return CommandOutcome.Rejected(GameError.WeaponNotAvailable(weapon.id, weapon.location))
            if (state.listedWeapons().size >= config.shelfSlots) return CommandOutcome.Rejected(GameError.ShelfFull)
            val price = cmd.price ?: suggestedPrice(weapon)
            if (price < 0) return CommandOutcome.Rejected(GameError.InvalidPrice(price))
            ctx.updateWeapon(weapon.copy(location = WeaponLocation.Shelf(price)))
            ctx.emit(EventType.WEAPON_LISTED, 0, "${weapon.name} was placed on the shelf for $price gold.", listOf(weapon.id.value))
        } else {
            if (!weapon.isListed) return CommandOutcome.Rejected(GameError.WeaponNotAvailable(weapon.id, weapon.location))
            ctx.updateWeapon(weapon.copy(location = WeaponLocation.Storage))
        }
        return accept(ctx)
    }

    private fun setPrice(state: GameState, cmd: Command.SetPrice): CommandOutcome {
        val weapon = state.weapons[cmd.weaponId] ?: return CommandOutcome.Rejected(GameError.WeaponNotFound(cmd.weaponId))
        if (!weapon.isListed) return CommandOutcome.Rejected(GameError.WeaponNotAvailable(weapon.id, weapon.location))
        if (cmd.price < 0) return CommandOutcome.Rejected(GameError.InvalidPrice(cmd.price))
        val ctx = ResolutionContext(state, content, config)
        ctx.updateWeapon(weapon.copy(location = WeaponLocation.Shelf(cmd.price)))
        return accept(ctx)
    }

    private fun buyMaterial(state: GameState, cmd: Command.BuyMaterial): CommandOutcome {
        val def = content.materialById[cmd.materialId] ?: return CommandOutcome.Rejected(GameError.UnknownContent(cmd.materialId.value))
        if (cmd.quantity <= 0) return CommandOutcome.Rejected(GameError.InvalidQuantity(cmd.quantity))
        val stock = state.supplierStock[def.id]
        if (stock != null && stock < cmd.quantity) return CommandOutcome.Rejected(GameError.SupplierOutOfStock(def.id))
        val cost = (def.price * config.supplierPriceMultiplier * state.world.marketMultiplier).toInt() * cmd.quantity
        if (state.gold < cost) return CommandOutcome.Rejected(GameError.NotEnoughGold(cost, state.gold))
        val ctx = ResolutionContext(state, content, config)
        ctx.gold -= cost
        ctx.materials[def.id] = (ctx.materials[def.id] ?: 0) + cmd.quantity
        if (stock != null) ctx.supplierStock[def.id] = stock - cmd.quantity
        return accept(ctx)
    }

    fun materialPrice(state: GameState, materialId: MaterialId): Int =
        (content.material(materialId).price * config.supplierPriceMultiplier * state.world.marketMultiplier).toInt()

    fun suggestedPrice(weapon: Weapon): Int = weapon.power * config.fairGoldPerPower

    private fun commission(state: GameState, id: CommissionId, newStatus: CommissionStatus): CommandOutcome {
        val c = state.commissions[id] ?: return CommandOutcome.Rejected(GameError.CommissionNotFound(id))
        if (c.status != CommissionStatus.OFFERED) return CommandOutcome.Rejected(GameError.CommissionNotOpen(id, c.status))
        val ctx = ResolutionContext(state, content, config)
        ctx.commissions[id] = c.copy(status = newStatus)
        return accept(ctx)
    }

    private fun chooseBlessing(state: GameState, cmd: Command.ChooseBlessing): CommandOutcome {
        if (state.pendingBlessingOffer.isEmpty()) return CommandOutcome.Rejected(GameError.NoBlessingOffer)
        if (cmd.blessingId !in state.pendingBlessingOffer) return CommandOutcome.Rejected(GameError.BlessingNotOffered(cmd.blessingId))
        val def = content.blessing(cmd.blessingId)
        val ctx = ResolutionContext(state, content, config)
        ctx.blessings = ctx.blessings.filter { it.id != def.id } + ActiveBlessing(def.id, state.day + def.durationDays - 1)
        ctx.pendingBlessingOffer = emptyList()
        ctx.emit(EventType.BLESSING_CHOSEN, 3, "The smith accepted the blessing of ${def.name}.", data = mapOf("blessing" to def.id.value))
        return accept(ctx)
    }

    /**
     * Fixed resolution order (GDD 3.2). Idempotent per command ID: a retry returns the stored resolution
     * without simulating again.
     */
    private fun endDay(state: GameState, commandId: CommandId): CommandOutcome {
        if (commandId.value in state.processedEndDayIds) {
            val stored = state.lastResolution
            return if (stored != null && stored.commandId == commandId) CommandOutcome.Accepted(state, emptyList(), resolution = stored)
            else CommandOutcome.Accepted(state, emptyList(), resolution = null)
        }
        if (state.isEnded) return CommandOutcome.Rejected(GameError.RunEnded)
        val ctx = ResolutionContext(state, content, config)
        val day = ctx.day
        // 1. Lock planning; RNG state is snapshotted implicitly (streams open lazily from the saved state).
        // 2. Customers and commissions.
        Market.resolveShelfVisits(ctx)
        Market.resolveCommissions(ctx)
        // 3. Equipment/finances were applied inside purchases.
        // 4-5. Autonomous activities and encounters.
        Heroes.resolveActivities(ctx)
        // 6. Factions, world events, siege warnings.
        advanceFactions(ctx)
        WorldEvents.resolve(ctx)
        Market.maybeOfferCommission(ctx)
        Battle.warnOfSiege(ctx)
        // 7. Scheduled siege and champions.
        Battle.resolveSiegeIfDue(ctx)
        // 8. Hero-driven recovery, retirements/guilds, then champion refresh.
        Heroes.resolveRetirements(ctx)
        recover(ctx)
        // 9. Histories and blessings expiry.
        ctx.blessings = ctx.blessings.filter { it.expiresDay > day }
        // 10. Gazette and new morning.
        val dayEvents = ctx.newEvents.toList()
        val resolution = DayResolution(
            commandId = commandId, day = day, events = dayEvents, headlines = Gazette.headlines(dayEvents),
            visits = ctx.visits.toList(), replays = ctx.replays.toList(), defeated = ctx.phase == Phase.ENDED,
        )
        ctx.lastResolution = resolution
        ctx.processedEndDayIds += commandId.value
        EventCompaction.compact(ctx.events, day, config.eventRetentionDays)  // after the Gazette; keeps saves bounded (GDD 13.3)
        if (ctx.phase != Phase.ENDED) newMorning(ctx)
        return accept(ctx, resolution = resolution)
    }

    /** Faction pressure step (GDD 8); event effects come from the scripted world events that follow it. */
    private fun advanceFactions(ctx: ResolutionContext) {
        for (f in ctx.factions.values.sortedBy { it.id.value }) {
            val def = content.faction(f.id)
            val next = (f.pressure + def.dailyGrowth - f.suppressionToday).coerceIn(0, 100)
            ctx.factions[f.id] = f.copy(pressure = next, suppressionToday = 0)
        }
    }

    private fun recover(ctx: ResolutionContext) {
        if (ctx.phase == Phase.ENDED) return
        val maxIntegrity = config.startingForgeIntegrity + upgradeTotal(ctx.legacy, UpgradeEffect.STARTING_INTEGRITY)
        val cap = config.maxIntegrityRecoveryPerDay + ctx.blessingMagnitude(BlessingEffect.INTEGRITY_RECOVERY)
        val earned = minOf(cap, ctx.patrolsToday * config.recoveryPerPatrol + ctx.expeditionWinsToday * config.recoveryPerExpeditionWin)
        val applied = minOf(earned, maxIntegrity - ctx.town.integrity)
        if (applied > 0) {
            ctx.town = ctx.town.copy(integrity = ctx.town.integrity + applied)
            ctx.emit(EventType.TOWN_RECOVERED, 1, "Heroes shored up the forge defenses (+$applied integrity).", data = mapOf("amount" to applied.toString()))
        }
        ctx.town = ctx.town.copy(militia = maxOf(0, ctx.town.militia - config.militiaDecayPerDay))
        val faction = ctx.factions.values.maxByOrNull { it.pressure }?.let { content.faction(it.id) }
        if (faction != null) ctx.town = ctx.town.copy(championIds = Battle.selectChampions(ctx, faction).map { it.first.id })
    }

    private fun newMorning(ctx: ResolutionContext) {
        ctx.day += 1
        val base = config.baseDailyEnergy + upgradeTotal(ctx.legacy, UpgradeEffect.STARTING_ENERGY) + ctx.blessingMagnitude(BlessingEffect.EXTRA_ENERGY)
        ctx.energy = maxOf(0, base - ctx.overworkToday)
        ctx.overworkToday = 0
        val caravanDelayed = ctx.worldFlags[WorldEvents.FLAG_CARAVAN_DELAYED] == ctx.day
        for ((m, s) in restockedSupplier()) ctx.supplierStock[m] = if (caravanDelayed) 0 else s
        ctx.worldFlags.entries.removeIf { it.value < ctx.day }
        for (h in ctx.aliveHeroes()) if (h.lastActivity == HeroActivity.SHOP) ctx.updateHero(h.copy(lastActivity = HeroActivity.IDLE))
    }

    private fun restockedSupplier(): Map<MaterialId, Int> =
        content.materials.mapNotNull { m -> m.dailySupplierStock?.let { m.id to it } }.toMap()

    fun upgradeTotal(legacy: LegacyProfile, effect: UpgradeEffect): Int =
        content.upgrades.filter { it.effect == effect }.sumOf { it.magnitudePerLevel * legacy.upgradeLevel(it.id) }

    fun closeRun(state: GameState): RunEndResult = Legacy.closeRun(state, content, config)

    fun claimLegacy(current: LegacyProfile, runEnd: RunEndResult): LegacyOutcome = Legacy.claim(current, runEnd)

    fun purchaseUpgrade(current: LegacyProfile, upgradeId: UpgradeId): LegacyOutcome = Legacy.purchaseUpgrade(current, upgradeId, content)

    private fun accept(ctx: ResolutionContext, forgedWeaponId: WeaponId? = null, resolution: DayResolution? = null): CommandOutcome {
        val state = ctx.toState()
        assertInvariants(state)
        return CommandOutcome.Accepted(state, ctx.newEvents.toList(), forgedWeaponId, resolution)
    }

    private fun assertInvariants(state: GameState) {
        val problems = Invariants.check(state, config)
        check(problems.isEmpty()) { "Invariant violation: $problems" }
    }
}
