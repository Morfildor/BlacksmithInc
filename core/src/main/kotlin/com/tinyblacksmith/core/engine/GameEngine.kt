package com.tinyblacksmith.core.engine

import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.BlessingEffect
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.content.ToolEffect
import kotlin.math.roundToInt
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
import com.tinyblacksmith.core.persistence.WeaponHistoryCompaction
import com.tinyblacksmith.core.persistence.WeaponPruning
import com.tinyblacksmith.core.rng.RngState
import com.tinyblacksmith.core.rng.RngStream
import com.tinyblacksmith.core.shopday.Recognitions

/**
 * Pure, deterministic command handler (GDD 13.2). No Android, no coroutines, no platform randomness.
 * Same content version + seed + command sequence => identical state and ordered events.
 */
class GameEngine(val content: ContentCatalog = com.tinyblacksmith.core.content.LaunchContent.catalog, val config: BalanceConfig = BalanceConfig.DEFAULT) {

    companion object {
        const val RULES_VERSION = 3

        /**
         * The rules version that salts every stream seed ([RngState.seeded]). Rules 2 is a number only (a run now has to be
         * admitted before it is played, see [Compatibility]) and its outcome changes are rule fixes that draw from the same
         * streams, so new runs keep the rules-1 seeds: each fix re-records only the seed-pinned tests and golden lines it
         * moves. Rules 3 (fair customer selection: a new draw order on the PURCHASES stream, heroes in numeric ID order) keeps
         * them too. Raised only if a later rules version is meant to re-seed every run.
         */
        const val STREAM_SEED_VERSION = 1
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
            runId = runId, seed = seed, rulesVersion = rulesVersion, contentVersion = content.version, balanceVersion = config.version, era = era, day = 1,
            phase = Phase.PLANNING, gold = startingGold, energy = startingEnergy, overworkToday = 0, reputation = startingReputation,
            rng = RngState.seeded(seed, STREAM_SEED_VERSION), world = WorldModifiers(), materials = materials,
            supplierStock = restockedSupplier(legacy), weapons = emptyMap(), heroes = emptyMap(),
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
        // Every class has a buyer from day one: until all are present, a starting hero draws from the classes still missing (a descendant keeps the lineage's and counts).
        val missing = content.classes.toMutableList()
        repeat(config.customers.startingHeroes) { i ->
            val h = Heroes.generate(ctx, hRng, if (i == 0) descendant else null, missing.ifEmpty { content.classes })
            missing.removeAll { it.id == h.classId }
            ctx.updateHero(h)
        }
        // Known Name: the shop is known before its doors open. One starting hero per level is already a regular with coin saved for a blade.
        val regulars = ctx.aliveHeroes().take(content.upgrades.filter { it.effect == UpgradeEffect.STARTING_REPUTATION }.sumOf { legacy.upgradeLevel(it.id) })
        for (h in regulars) ctx.updateHero(h.copy(loyalty = config.regularLoyaltyThreshold, gold = h.gold + config.legacyTracks.knownNameRegularGold))
        ctx.emit(EventType.RUN_STARTED, 5, "Era $era begins in Emberfall under a ${ctx.world.name.lowercase()}. The first invasion is expected on day ${config.siegeInterval}.")
        descendant?.let { d ->
            val h = ctx.heroes.values.first { it.lineageId == d.id }
            ctx.emit(EventType.HERO_ARRIVED, 4, "${h.fullName}, descendant of ${d.heroName} who ${d.deed}, has come to Emberfall.", listOf(h.id.value))
        }
        if (regulars.isNotEmpty()) {
            val names = regulars.map { it.fullName }
            val who = if (names.size == 1) "${names[0]} is already a regular" else "${names.dropLast(1).joinToString(", ")} and ${names.last()} are already regulars"
            ctx.emit(EventType.RUN_STARTED, 4, "The forge's name went before it: $who of the shop.", regulars.map { it.id.value })
        }
        val state = ctx.toState()
        assertInvariants(state)
        return state
    }

    fun handle(state: GameState, command: Command): CommandOutcome {
        if (state.rulesVersion != RULES_VERSION || state.contentVersion != content.version) return CommandOutcome.Rejected(GameError.IncompatibleRun(state.rulesVersion, state.contentVersion))
        if (state.isEnded && command !is Command.EndDay) return CommandOutcome.Rejected(GameError.RunEnded)
        return when (command) {
            is Command.Forge -> forge(state, command)
            is Command.ToggleShelf -> toggleShelf(state, command)
            is Command.SetPrice -> setPrice(state, command)
            is Command.BuyMaterial -> buyMaterial(state, command)
            is Command.AcceptCommission -> commission(state, command.commissionId, CommissionStatus.ACCEPTED)
            is Command.DeclineCommission -> commission(state, command.commissionId, CommissionStatus.DECLINED)
            is Command.ChooseBlessing -> chooseBlessing(state, command)
            is Command.Salvage -> salvage(state, command)
            is Command.Hone -> hone(state, command)
            is Command.DonateWeapon -> donate(state, command)
            is Command.BuyTool -> buyTool(state, command)
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
            if (state.listedWeapons().size >= shelfSlots(state)) return CommandOutcome.Rejected(GameError.ShelfFull)
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
        ctx.emit(EventType.MATERIAL_BOUGHT, 0, "The smith bought ${cmd.quantity} ${def.name} for $cost gold.", data = mapOf("material" to def.id.value, "quantity" to cmd.quantity.toString(), "cost" to cost.toString()))
        return accept(ctx)
    }

    fun materialPrice(state: GameState, materialId: MaterialId): Int =
        (content.material(materialId).price * config.supplierPriceMultiplier * state.world.marketMultiplier).toInt()

    /** The going rate for the blade as it is: worn power, plus a small premium for a storied one. */
    fun suggestedPrice(weapon: Weapon): Int = Market.askingPrice(weapon, config)

    fun shelfSlots(state: GameState): Int = config.shelfSlots + toolTotal(state, ToolEffect.SHELF_SLOTS)

    fun toolTotal(state: GameState, effect: ToolEffect): Int =
        content.tools.filter { it.effect == effect }.sumOf { it.magnitudePerLevel * (state.tools[it.id] ?: 0) }

    /** Next level's price, or null when the tool is unknown or maxed. */
    fun toolCost(state: GameState, toolId: String): Int? = content.tool(toolId)?.let { it.costPerLevel.getOrNull(state.tools[toolId] ?: 0) }

    /** Defensive power the town watch would gain from this weapon (before the armory cap). */
    fun armoryValue(weapon: Weapon): Int = maxOf(1, (weapon.power * config.armoryPowerShare).roundToInt())

    /** Read-only: how the next siege looks as things stand today. Null only without factions. */
    fun siegeForecast(state: GameState): Battle.SiegeOutlook? = Battle.outlook(ResolutionContext(state, content, config), state.town.nextSiegeDay)

    /** Spends energy, dipping into overwork like a forge does; null when accepted. */
    private fun spendEnergy(ctx: ResolutionContext, cost: Int): GameError? {
        val overworkAvailable = config.maxOverworkPerDay - ctx.overworkToday
        val shortfall = maxOf(0, cost - ctx.energy)
        if (shortfall > overworkAvailable) return GameError.NotEnoughEnergy(cost, ctx.energy, overworkAvailable)
        ctx.energy -= cost - shortfall
        ctx.overworkToday += shortfall
        return null
    }

    /** A weapon the smith can still work on: in storage or on the shelf. */
    private fun inShop(state: GameState, id: WeaponId): Pair<Weapon?, GameError?> {
        val weapon = state.weapons[id] ?: return null to GameError.WeaponNotFound(id)
        if (!weapon.isInStorage && !weapon.isListed) return null to GameError.WeaponNotAvailable(weapon.id, weapon.location)
        return weapon to null
    }

    private fun salvage(state: GameState, cmd: Command.Salvage): CommandOutcome {
        val (weapon, error) = inShop(state, cmd.weaponId)
        if (weapon == null) return CommandOutcome.Rejected(error!!)
        val ctx = ResolutionContext(state, content, config)
        spendEnergy(ctx, config.salvageEnergy)?.let { return CommandOutcome.Rejected(it) }
        ctx.materials[weapon.coreId] = (ctx.materials[weapon.coreId] ?: 0) + 1
        ctx.updateWeapon(weapon.copy(location = WeaponLocation.Destroyed(state.day)))
        ctx.addWeaponHistory(weapon.id, "SALVAGED", "Melted down for its ${content.material(weapon.coreId).name}.")
        ctx.emit(EventType.WEAPON_SALVAGED, 0, "The smith melted ${weapon.name} down for its ${content.material(weapon.coreId).name}.", listOf(weapon.id.value))
        return accept(ctx)
    }

    private fun hone(state: GameState, cmd: Command.Hone): CommandOutcome {
        val (weapon, error) = inShop(state, cmd.weaponId)
        if (weapon == null) return CommandOutcome.Rejected(error!!)
        if (!weapon.canBeHoned) return CommandOutcome.Rejected(GameError.AlreadyHoned(weapon.id))
        if ((state.materials[weapon.coreId] ?: 0) < 1) return CommandOutcome.Rejected(GameError.MissingMaterial(weapon.coreId))
        val ctx = ResolutionContext(state, content, config)
        spendEnergy(ctx, config.honeEnergy)?.let { return CommandOutcome.Rejected(it) }
        ctx.materials[weapon.coreId] = ctx.materials.getValue(weapon.coreId) - 1
        // Hone always restores condition; the quality bonus is granted the first time only.
        val quality = if (weapon.honed) weapon.quality else minOf(100, weapon.quality + config.honeQualityBonus)
        val power = weapon.power + quality / config.powerPerQualityDivisor - weapon.quality / config.powerPerQualityDivisor
        ctx.updateWeapon(weapon.copy(quality = quality, power = power, rarity = Forge.rarityFor(quality, config), honed = true, condition = 100))
        val note = if (weapon.honed) "condition ${weapon.condition} to 100" else "quality ${weapon.quality} to $quality"
        ctx.addWeaponHistory(weapon.id, "HONED", "Honed on the anvil ($note).")
        val text = if (weapon.honed) "The smith honed ${weapon.name} back to a keen edge." else "The smith honed ${weapon.name} to quality $quality."
        ctx.emit(EventType.WEAPON_HONED, 2, text, listOf(weapon.id.value), mapOf("quality" to quality.toString(), "condition" to "100"))
        return accept(ctx)
    }

    private fun donate(state: GameState, cmd: Command.DonateWeapon): CommandOutcome {
        val (weapon, error) = inShop(state, cmd.weaponId)
        if (weapon == null) return CommandOutcome.Rejected(error!!)
        if (state.town.armory >= config.armoryMax) return CommandOutcome.Rejected(GameError.ArmoryFull)
        val ctx = ResolutionContext(state, content, config)
        val gain = minOf(armoryValue(weapon), config.armoryMax - state.town.armory)
        ctx.town = ctx.town.copy(armory = ctx.town.armory + gain)
        ctx.reputation += 1
        ctx.updateWeapon(weapon.copy(location = WeaponLocation.Lost(state.day, "given to the town watch")))
        ctx.addWeaponHistory(weapon.id, "DONATED", "Given to the town watch of Emberfall.")
        ctx.emit(EventType.WEAPON_DONATED, 3, "The smith armed the town watch with ${weapon.name}.", listOf(weapon.id.value), mapOf("armory" to gain.toString()))
        return accept(ctx)
    }

    private fun buyTool(state: GameState, cmd: Command.BuyTool): CommandOutcome {
        val def = content.tool(cmd.toolId) ?: return CommandOutcome.Rejected(GameError.UnknownContent(cmd.toolId))
        val cost = toolCost(state, def.id) ?: return CommandOutcome.Rejected(GameError.ToolMaxed(def.id))
        if (state.gold < cost) return CommandOutcome.Rejected(GameError.NotEnoughGold(cost, state.gold))
        val ctx = ResolutionContext(state, content, config)
        ctx.gold -= cost
        ctx.tools[def.id] = (ctx.tools[def.id] ?: 0) + 1
        if (def.effect == ToolEffect.EXTRA_ENERGY) ctx.energy += def.magnitudePerLevel  // usable the day it is bought
        ctx.emit(EventType.TOOL_BOUGHT, 2, "The forge gained a new tool: ${def.name}.", data = mapOf("tool" to def.id, "name" to def.name, "level" to ctx.tools.getValue(def.id).toString(), "cost" to cost.toString()))
        return accept(ctx)
    }

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
        // The shelf as the day opens, before a patron or a browser takes anything from it: what every visit of the day refers to.
        val shelf = state.listedWeapons()
        // 2. Commissions, then customers: a patron collects before the browsers arrive, so the blade a request was promised is not sold first.
        Market.resolveCommissions(ctx)
        Market.resolveShelfVisits(ctx)
        Recognitions.apply(ctx)  // what the counter knows each browser by; narration, no draw
        Market.resolveMerchant(ctx)  // GDD 7 merchant resale, after the smith's own customers; draws no RNG
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
        if (ctx.phase != Phase.ENDED) Heroes.resolveAmbitions(ctx)
        Heroes.resolveRetirements(ctx)
        recover(ctx)
        // 9. Histories and blessings expiry.
        ctx.blessings = ctx.blessings.filter { it.expiresDay > day }
        // 10. Gazette and new morning. The edition is the whole day, preparation included (taken before compaction).
        val spent = ctx.events.filter { it.era == ctx.era && it.day == day && (it.type == EventType.MATERIAL_BOUGHT || it.type == EventType.TOOL_BOUGHT) }.sumOf { it.data["cost"]?.toIntOrNull() ?: 0 }
        val ledger = ShopLedger(state.gold, ctx.gold, ctx.income.toMap(), ctx.tradeInCreditToday, spent)
        // The day's counts and coin as one ordinary record: it stays in the log after the snapshots are gone, so a past edition tallies as the report did.
        ctx.emit(EventType.SHOP_DAY, 0, Gazette.shopDayText(ctx.visits), data = Gazette.shopDayData(ctx.visits, ledger, ctx.field))
        val dayEvents = ctx.events.filter { it.era == ctx.era && it.day == day }
        // A blade a patron took from storage was never on the shelf; it is added as it was this morning.
        val fromStorage = ctx.visits.mapNotNull { it.purchasedWeaponId }.filter { id -> shelf.none { it.id == id } }.map { state.weapon(it) }
        val resolution = DayResolution(
            commandId = commandId, day = day, events = dayEvents, headlines = Gazette.headlines(dayEvents),
            visits = ctx.visits.toList(), replays = Battle.dayReplays(ctx), defeated = ctx.phase == Phase.ENDED,
            ledger = ledger, field = ctx.field.toList(),
            shopWeapons = (shelf + fromStorage).map { WeaponSnapshot.of(it) }, shelfPrices = shelf.associate { it.id to (it.listedPrice ?: 0) },
            turnedAway = ctx.turnedAway.toList(), recordVersion = 1,
        )
        ctx.lastResolution = resolution
        ctx.processedEndDayIds += commandId.value
        EventCompaction.compact(ctx.events, day, config.eventRetentionDays)  // after the Gazette; keeps saves bounded (GDD 13.3)
        WeaponHistoryCompaction.compact(ctx.weapons, config.weaponHistoryCap)  // newest combat entries per weapon; ownership entries kept
        WeaponPruning.prune(ctx.weapons, day, config.weaponRetentionDays, config.legendFameThreshold)  // blades gone for good leave the save
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
        // The forecast's own champions: the same faction, warlord flag and ranking the siege will use.
        Battle.outlook(ctx, ctx.town.nextSiegeDay)?.let { o -> ctx.town = ctx.town.copy(championIds = o.champions.map { it.first.id }) }
    }

    private fun newMorning(ctx: ResolutionContext) {
        ctx.day += 1
        val base = config.baseDailyEnergy + upgradeTotal(ctx.legacy, UpgradeEffect.STARTING_ENERGY) + ctx.blessingMagnitude(BlessingEffect.EXTRA_ENERGY) + ctx.toolTotal(ToolEffect.EXTRA_ENERGY)
        ctx.energy = maxOf(0, base - ctx.overworkToday)
        ctx.overworkToday = 0
        val caravanDelayed = ctx.worldFlags[WorldEvents.FLAG_CARAVAN_DELAYED] == ctx.day
        for ((m, s) in restockedSupplier(ctx.legacy)) ctx.supplierStock[m] = if (caravanDelayed) 0 else s
        // The ore merchant announced last night sells this morning, on top of the restock.
        for ((flag, flagDay) in ctx.worldFlags) if (flagDay == ctx.day && flag.startsWith(WorldEvents.FLAG_ORE_MERCHANT)) {
            val m = MaterialId(flag.removePrefix(WorldEvents.FLAG_ORE_MERCHANT))
            ctx.supplierStock[m] = (ctx.supplierStock[m] ?: 0) + WorldEvents.ORE_MERCHANT_STOCK
        }
        ctx.worldFlags.entries.removeIf { it.value < ctx.day }
        for (h in ctx.aliveHeroes()) if (h.lastActivity == HeroActivity.SHOP) ctx.updateHero(h.copy(lastActivity = HeroActivity.IDLE))
    }

    /** The day's stock of the limited materials; Caravan Ties (CATALOG_ACCESS) deepens every one of them. */
    private fun restockedSupplier(legacy: LegacyProfile): Map<MaterialId, Int> {
        val extra = upgradeTotal(legacy, UpgradeEffect.CATALOG_ACCESS) * config.legacyTracks.catalogStockPerLevel
        return content.materials.mapNotNull { m -> m.dailySupplierStock?.let { m.id to it + extra } }.toMap()
    }

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
        val problems = Invariants.check(state, config, shelfSlots(state))
        check(problems.isEmpty()) { "Invariant violation: $problems" }
    }
}
