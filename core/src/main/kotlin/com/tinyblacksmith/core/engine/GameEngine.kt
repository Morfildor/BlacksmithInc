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
import com.tinyblacksmith.core.guild.GuildOps
import com.tinyblacksmith.core.guild.Loadout
import com.tinyblacksmith.core.guild.Missions
import com.tinyblacksmith.core.guild.SiegeFight
import com.tinyblacksmith.core.guild.Stories
import com.tinyblacksmith.core.heroes.Heroes
import com.tinyblacksmith.core.legacy.Legacy
import com.tinyblacksmith.core.legacy.LegacyOutcome
import com.tinyblacksmith.core.legacy.RunEndResult
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.persistence.CommissionPruning
import com.tinyblacksmith.core.persistence.EventCompaction
import com.tinyblacksmith.core.persistence.ProcessedCommands
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
        const val RULES_VERSION = 5

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

    /** With [charterId] the run is a guild run under that charter (`GuildCatalog.charters`); without one it is a classic run and plays as rules 4 did. */
    fun newRun(legacy: LegacyProfile, seed: Long, rulesVersion: Int = RULES_VERSION, charterId: String? = null): GameState {
        require(charterId == null || content.guild?.charter(charterId) != null) { "Unknown charter $charterId" }
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
        ctx.emit(EventType.RUN_STARTED, 5, "Era $era begins. World conditions are ${ctx.world.name.lowercase()}. The first siege is expected on day ${config.siegeInterval}.")
        descendant?.let { d ->
            val h = ctx.heroes.values.first { it.lineageId == d.id }
            ctx.emit(EventType.HERO_ARRIVED, 4, "${h.fullName} arrived in Emberfall. Their ancestor ${d.heroName} ${d.deed}.", listOf(h.id.value))
        }
        if (regulars.isNotEmpty()) {
            val names = regulars.map { it.fullName }
            val who = if (names.size == 1) "${names[0]} is already a regular" else "${names.dropLast(1).joinToString(", ")} and ${names.last()} are already regulars"
            ctx.emit(EventType.RUN_STARTED, 4, "Your forge is already known. $who here.", regulars.map { it.id.value })
        }
        // The first siege is on the calendar from the start (it is a plain one); the opening relic draft draws on ENCOUNTERS only.
        ctx.siege = SiegeScenario(config.siegeInterval)
        // The guild is founded before the opening draft, so the draft can offer a party's relics; it draws on GUILD only.
        if (charterId != null) { GuildOps.found(ctx, charterId); Missions.refreshBoard(ctx) }
        if (content.relics.isNotEmpty()) Relics.offerIfDue(ctx)
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
            is Command.Scrap -> scrap(state, command)
            is Command.Hone -> hone(state, command)
            is Command.DonateWeapon -> donate(state, command)
            is Command.BuyTool -> buyTool(state, command)
            is Command.ResolveEncounter -> resolveEncounter(state, command)
            is Command.ChooseRelic -> ResolutionContext(state, content, config).let { ctx -> Relics.choose(ctx, command)?.let { CommandOutcome.Rejected(it) } ?: accept(ctx) }
            is Command.DeclineRelicOffer -> if (state.pendingRelicOffer.isEmpty()) CommandOutcome.Rejected(GameError.NoRelicOffer)
                else ResolutionContext(state, content, config).let { ctx -> ctx.pendingRelicOffer = emptyList(); accept(ctx) }
            is Command.EndDay -> endDay(state, command.commandId)
            is Command.RecruitHero -> guild(state) { GuildOps.recruit(it, command.heroId) }
            is Command.DismissHero -> guild(state) { GuildOps.dismiss(it, command.heroId) }
            is Command.LoanWeapon -> guild(state) { GuildOps.loan(it, command.heroId, command.weaponId) }
            is Command.RecallLoan -> guild(state) { GuildOps.recall(it, command.weaponId) }
            is Command.PlanDeployment -> guild(state) { Missions.plan(it, command) }
            is Command.CancelPlannedDeployment -> guild(state) { Missions.cancelPlan(it) }
            is Command.ReserveDefender -> guild(state) { GuildOps.reserve(it, command.heroId, command.reserved) }
            is Command.ChooseMissionCheckpoint -> guild(state) { Missions.checkpoint(it, command) }
            is Command.RetireAfterMilestone -> guild(state) { Stories.retire(it, command.milestoneId) }
            is Command.ChooseSpeciality -> guild(state) { Stories.chooseBranch(it, command.heroId, command.specialityId) }
            is Command.SetGuildRank -> guild(state) { Stories.setRank(it, command.rank) }
        }
    }

    /** A guild command: rejected on a classic run, otherwise applied whole or not at all. */
    private fun guild(state: GameState, apply: (ResolutionContext) -> GameError?): CommandOutcome {
        if (state.guild == null) return CommandOutcome.Rejected(GameError.NotAGuildRun)
        val ctx = ResolutionContext(state, content, config)
        apply(ctx)?.let { return CommandOutcome.Rejected(it) }
        return accept(ctx)
    }

    /** The coming siege as a guild run sees it: who would stand, who is missing and why, what is known of the besieger. Read-only; null on a classic run. */
    fun wallForecast(state: GameState): SiegeFight.Forecast? = SiegeFight.forecast(ResolutionContext(state, content, config))

    /** Why a member cannot join a party or the wall today, or null. Read-only. */
    fun memberUnavailable(state: GameState, heroId: HeroId): String? = state.guild?.member(heroId)?.let { GuildOps.unavailable(ResolutionContext(state, content, config), it) }

    /** A member (or any hero) as they would enter a fight today with [weapon] in hand. Read-only. */
    fun fighterView(state: GameState, heroId: HeroId, weapon: Weapon? = state.loanOf(heroId) ?: state.equippedWeapon(heroId), home: Boolean = false): com.tinyblacksmith.core.combat.Combatant? =
        state.heroes[heroId]?.takeIf { content.combat != null }?.let { GuildOps.fighter(ResolutionContext(state, content, config), it, weapon, if (home) Loadout.Field.HOME else Loadout.Field.ROAD) }

    /** The branches a member's deeds have opened and the smith has not answered. Read-only. */
    fun openBranches(state: GameState, heroId: HeroId): List<com.tinyblacksmith.core.content.SpecialityDef> =
        state.guild?.member(heroId)?.let { Stories.openBranches(ResolutionContext(state, content, config), it) }.orEmpty()

    /** The fight of a contract's stage as [heroIds] would meet it today with [posture]: the setup only, nothing is resolved. Null for work without a fight. */
    fun missionSetup(state: GameState, offerId: String, heroIds: List<HeroId>, posture: com.tinyblacksmith.core.combat.Posture, stage: Int = 0): com.tinyblacksmith.core.combat.FightSetup? {
        val offer = state.guild?.offers?.firstOrNull { it.id == offerId } ?: return null
        val ctx = ResolutionContext(state, content, config)
        val gear = heroIds.mapNotNull { id -> GuildOps.weaponOf(ctx, id)?.let { id to it.id } }.toMap()
        return Missions.setup(ctx, MissionInstance(offer.id, offer, heroIds, gear, posture, GuildOps.relicIds(ctx), state.day, state.day + offer.days), stage.coerceIn(0, offer.stages.lastIndex))
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

    /** Exactly once per command ID: the retry of the command that answered is accepted and changes nothing. */
    private fun resolveEncounter(state: GameState, cmd: Command.ResolveEncounter): CommandOutcome {
        if (state.encounter?.let { it.id == cmd.instanceId && it.commandId == cmd.commandId.value } == true) return CommandOutcome.Accepted(state, emptyList())
        val ctx = ResolutionContext(state, content, config)
        Encounters.resolve(ctx, cmd)?.let { return CommandOutcome.Rejected(it) }
        return accept(ctx)
    }

    /** The workshop's relics with where each stands today. Read-only. */
    fun relicViews(state: GameState): List<Relics.View> = Relics.views(ResolutionContext(state, content, config))

    /** Quality the Tempering Ledger would add to a forge of [familyId] now (0 without the relic or for a family in the streak). */
    fun ledgerBonus(state: GameState, familyId: WeaponFamilyId): Int = Relics.ledgerBonus(ResolutionContext(state, content, config), familyId)

    /** This morning's visitor as the planning screen shows it: text, answers, exact costs and why an answer is closed. Read-only. */
    fun encounterView(state: GameState): Encounters.View? = Encounters.view(state, content, config)

    private fun toggleShelf(state: GameState, cmd: Command.ToggleShelf): CommandOutcome {
        val weapon = state.weapons[cmd.weaponId] ?: return CommandOutcome.Rejected(GameError.WeaponNotFound(cmd.weaponId))
        val ctx = ResolutionContext(state, content, config)
        if (cmd.listed) {
            if (!weapon.isInStorage) return CommandOutcome.Rejected(GameError.WeaponNotAvailable(weapon.id, weapon.location))
            weapon.promisedTo?.let { return CommandOutcome.Rejected(GameError.WeaponPromised(weapon.id, it)) }
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
        ctx.emit(EventType.MATERIAL_BOUGHT, 0, "Bought ${cmd.quantity} ${def.name} for $cost gold.", data = mapOf("material" to def.id.value, "quantity" to cmd.quantity.toString(), "cost" to cost.toString()))
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
        if (!ctx.canSpendEnergy(cost)) return GameError.NotEnoughEnergy(cost, ctx.energy, config.maxOverworkPerDay - ctx.overworkToday)
        ctx.spendEnergy(cost)
        return null
    }

    /** A weapon the smith can still work on: in storage or on the shelf. With [parting] it must also be free to leave the shop (not kept for an order). */
    private fun inShop(state: GameState, id: WeaponId, parting: Boolean = true): Pair<Weapon?, GameError?> {
        val weapon = state.weapons[id] ?: return null to GameError.WeaponNotFound(id)
        weapon.loanedTo?.let { return null to GameError.WeaponOnLoan(id, it) }
        if (!weapon.isInStorage && !weapon.isListed) return null to GameError.WeaponNotAvailable(weapon.id, weapon.location)
        if (parting && weapon.promisedTo != null) return null to GameError.WeaponPromised(weapon.id, weapon.promisedTo)
        return weapon to null
    }

    /** Whether melting [weapon] down today would also return its augment (Salvager's Crucible, once a day, fine quality or better). */
    fun salvageKeepsAugment(state: GameState, weapon: Weapon): Boolean = Relics.crucibleKeeps(ResolutionContext(state, content, config), weapon)

    private fun salvage(state: GameState, cmd: Command.Salvage): CommandOutcome {
        val (weapon, error) = inShop(state, cmd.weaponId)
        if (weapon == null) return CommandOutcome.Rejected(error!!)
        val ctx = ResolutionContext(state, content, config)
        spendEnergy(ctx, config.salvageEnergy)?.let { return CommandOutcome.Rejected(it) }
        ctx.materials[weapon.coreId] = (ctx.materials[weapon.coreId] ?: 0) + 1
        val kept = if (Relics.onSalvage(ctx, weapon)) " and its ${content.material(weapon.augmentId).name}" else ""
        ctx.updateWeapon(weapon.copy(location = WeaponLocation.Destroyed(state.day)))
        ctx.addWeaponHistory(weapon.id, "SALVAGED", "Melted down for its ${content.material(weapon.coreId).name}$kept.")
        ctx.emit(EventType.WEAPON_SALVAGED, 0, "Salvaged ${weapon.name}. Recovered its ${content.material(weapon.coreId).name}$kept.", listOf(weapon.id.value), if (kept.isEmpty()) emptyMap() else mapOf("augment" to weapon.augmentId.value))
        return accept(ctx)
    }

    /** What [Command.Scrap] would give back for these weapons: whole units per core material, nothing for the remainder. */
    fun scrapYield(weapons: Collection<Weapon>): Map<MaterialId, Int> =
        weapons.groupingBy { it.coreId }.eachCount().mapValues { it.value / config.saveGrowth.scrapBladesPerMaterial }.filterValues { it > 0 }

    private fun scrap(state: GameState, cmd: Command.Scrap): CommandOutcome {
        val ids = cmd.weaponIds.distinct()
        if (ids.isEmpty()) return CommandOutcome.Rejected(GameError.InvalidQuantity(0))
        val weapons = ids.map { id -> inShop(state, id).let { (weapon, error) -> weapon ?: return CommandOutcome.Rejected(error!!) } }
        val ctx = ResolutionContext(state, content, config)
        val back = scrapYield(weapons)
        back.forEach { (id, n) -> ctx.materials[id] = (ctx.materials[id] ?: 0) + n }
        weapons.forEach {
            ctx.updateWeapon(it.copy(location = WeaponLocation.Destroyed(state.day)))
            ctx.addWeaponHistory(it.id, "SALVAGED", "Carted to the scrap heap.")
        }
        val got = back.entries.joinToString(", ") { "${it.value} ${content.material(it.key).name}" }
        // One record for the lot, with its count: two hundred blades are not two hundred lines in the save.
        ctx.emit(EventType.WEAPON_SALVAGED, 0, "Scrapped ${weapons.size} ${if (weapons.size == 1) "weapon" else "weapons"}" + (if (got.isEmpty()) "." else ". Recovered $got."),
            data = mapOf("count" to weapons.size.toString()))
        return accept(ctx)
    }

    private fun hone(state: GameState, cmd: Command.Hone): CommandOutcome {
        val (weapon, error) = inShop(state, cmd.weaponId, parting = false)
        if (weapon == null) return CommandOutcome.Rejected(error!!)
        if (!weapon.canBeHoned) return CommandOutcome.Rejected(GameError.AlreadyHoned(weapon.id))
        if ((state.materials[weapon.coreId] ?: 0) < 1) return CommandOutcome.Rejected(GameError.MissingMaterial(weapon.coreId))
        val ctx = ResolutionContext(state, content, config)
        spendEnergy(ctx, config.honeEnergy)?.let { return CommandOutcome.Rejected(it) }
        ctx.materials[weapon.coreId] = ctx.materials.getValue(weapon.coreId) - 1
        // Hone always restores condition; the quality bonus is granted the first time only.
        val quality = if (weapon.honed) weapon.quality else minOf(100, weapon.quality + config.honeQualityBonus)
        // A returned legend's affixes wake under the first hone (GDD 7 "dormant"), and the power they carry with them.
        val woken = weapon.dormantAffixes
        val power = weapon.power + quality / config.powerPerQualityDivisor - weapon.quality / config.powerPerQualityDivisor + woken.sumOf { content.affix(it).power }
        ctx.updateWeapon(weapon.copy(quality = quality, power = power, rarity = Forge.rarityFor(quality, config), honed = true, condition = 100, affixes = weapon.affixes + woken, dormantAffixes = emptyList()))
        val note = if (weapon.honed) "Condition ${weapon.condition} to 100" else "Quality ${weapon.quality} to $quality"
        ctx.addWeaponHistory(weapon.id, "HONED", "Honed. $note.")
        if (woken.isNotEmpty()) ctx.addWeaponHistory(weapon.id, "AWAKENED", "Honing restored dormant properties. ${woken.joinToString(", ") { content.affix(it).name }}.")
        val text = (if (weapon.honed) "Restored ${weapon.name} to full condition." else "Honed ${weapon.name} to quality $quality.") +
            (if (woken.isNotEmpty()) " Dormant properties restored. ${woken.joinToString(", ") { content.affix(it).name }}." else "")
        ctx.emit(EventType.WEAPON_HONED, 2, text, listOf(weapon.id.value), mapOf("quality" to quality.toString(), "condition" to "100") + (if (woken.isNotEmpty()) mapOf("woke" to woken.joinToString(",") { it.value }) else emptyMap()))
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
        val bounty = Consequences.bounty(ctx)
        ctx.gold += bounty
        ctx.emit(EventType.WEAPON_DONATED, 3, "You gave ${weapon.name} to the town watch." + (if (bounty > 0) " The council paid $bounty gold for it." else ""), listOf(weapon.id.value),
            mapOf("armory" to gain.toString()) + (if (bounty > 0) mapOf("bounty" to bounty.toString()) else emptyMap()))
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
        ctx.emit(EventType.TOOL_BOUGHT, 2, "Bought ${def.name} for the forge.", data = mapOf("tool" to def.id, "name" to def.name, "level" to ctx.tools.getValue(def.id).toString(), "cost" to cost.toString()))
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
        ctx.emit(EventType.BLESSING_CHOSEN, 3, "Chose the ${def.name} blessing.", data = mapOf("blessing" to def.id.value))
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
        Missions.commit(ctx)  // a guild run: the planned party leaves before the day's business
        Encounters.expire(ctx)  // a visitor nobody answered leaves with the free answer; no draw
        // 2. Commissions, then customers: a patron collects before the browsers arrive, so the blade a request was promised is not sold first.
        Market.resolveCommissions(ctx)
        Consequences.settle(ctx)  // wagers judged, pledges learn what became of their order
        Market.resolveShelfVisits(ctx)
        Recognitions.apply(ctx)  // what the counter knows each browser by; narration, no draw
        Market.resolveMerchant(ctx)  // GDD 7 merchant resale, after the smith's own customers; draws no RNG
        // 3. Equipment/finances were applied inside purchases.
        // 4-5. The guild's party, then autonomous activities and encounters; members in town last.
        Missions.advance(ctx)
        Heroes.resolveActivities(ctx)
        GuildOps.homeDay(ctx)
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
        GuildOps.reconcile(ctx)
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
            mission = ctx.guild?.lastMission, siege = ctx.guild?.lastSiege,
        )
        ctx.lastResolution = resolution
        ctx.processedEndDayIds += commandId.value
        ProcessedCommands.trim(ctx.processedEndDayIds, config.saveGrowth.processedEndDayIdsKept)  // the newest few; a retry is of the latest
        EventCompaction.compact(ctx.events, day, config.eventRetentionDays, config.saveGrowth.routineEventRetentionDays)  // after the Gazette; keeps saves bounded (GDD 13.3)
        CommissionPruning.prune(ctx.commissions, day, config.saveGrowth.commissionRetentionDays)  // closed and past the event window
        val living = ctx.aliveHeroes().mapTo(HashSet()) { it.id.value }
        WeaponHistoryCompaction.compact(ctx.weapons, config.weaponHistoryCap, living)  // newest combat entries per weapon; a living hero's last siege line stays
        WeaponHistoryCompaction.compactEveryday(ctx.weapons, config.saveGrowth.weaponEverydayHistoryCap, ctx.era, living)  // newest everyday lines; holders remembered
        val armed = ctx.weapons.values.mapNotNullTo(HashSet()) { w -> w.ownerId.takeIf { w.isEquipped } }
        WeaponPruning.prune(ctx.weapons, day, config.weaponRetentionDays, config.legendFameThreshold, ctx.era, ctx.aliveHeroes().mapNotNullTo(HashSet()) { h -> h.id.takeIf { it !in armed } })  // blades gone for good leave the save
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
            ctx.emit(EventType.TOWN_RECOVERED, 1, "Heroes repaired the forge. Restored $applied health.", data = mapOf("amount" to applied.toString()))
        }
        ctx.town = ctx.town.copy(militia = maxOf(0, ctx.town.militia - config.militiaDecayPerDay))
        // The forecast's own champions: the same faction, warlord flag and ranking the siege will use.
        if (ctx.guild != null) ctx.town = ctx.town.copy(championIds = SiegeFight.forecast(ctx)?.defenders.orEmpty().map { it.first.id })
        else Battle.outlook(ctx, ctx.town.nextSiegeDay)?.let { o -> ctx.town = ctx.town.copy(championIds = o.champions.map { it.first.id }) }
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
            ctx.supplierStock[m] = (ctx.supplierStock[m] ?: 0) + config.worldEvents.oreMerchantStock
        }
        ctx.worldFlags.entries.removeIf { it.value < ctx.day }
        for (h in ctx.aliveHeroes()) if (h.lastActivity == HeroActivity.SHOP) ctx.updateHero(h.copy(lastActivity = HeroActivity.IDLE))
        // A want stands for the mornings after it was voiced, `wantLapseDays` of them; the planning screen never shows one End Day would ignore.
        for (h in ctx.aliveHeroes()) if (h.want != null && ctx.day - h.want.sinceDay > config.customers.wantLapseDays) ctx.updateHero(h.copy(want = null))
        // A guild run: the party that is due comes home, wounds heal, the board and the list of those who would sign are renewed.
        if (ctx.guild != null) { Missions.morning(ctx); GuildOps.morning(ctx); Stories.morning(ctx) }
        // The morning's own business last, so it reads the town as the player will: a relic offer that has fallen due, then the visitor.
        if (content.relics.isNotEmpty()) Relics.offerIfDue(ctx)
        Encounters.offerMorning(ctx)
    }

    /** The day's stock of the limited materials; Caravan Ties (CATALOG_ACCESS) deepens every one of them. */
    private fun restockedSupplier(legacy: LegacyProfile): Map<MaterialId, Int> {
        val extra = upgradeTotal(legacy, UpgradeEffect.CATALOG_ACCESS) * config.legacyTracks.catalogStockPerLevel
        return content.materials.mapNotNull { m -> m.dailySupplierStock?.let { m.id to it + extra } }.toMap()
    }

    fun upgradeTotal(legacy: LegacyProfile, effect: UpgradeEffect): Int =
        content.upgrades.filter { it.effect == effect }.sumOf { it.magnitudePerLevel * legacy.upgradeLevel(it.id) }

    fun closeRun(state: GameState): RunEndResult = Legacy.closeRun(state, content, config)

    fun claimLegacy(current: LegacyProfile, runEnd: RunEndResult): LegacyOutcome = Legacy.claim(current, runEnd, config)

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
