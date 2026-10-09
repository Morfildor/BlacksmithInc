package com.tinyblacksmith.core.battle

import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.BlessingEffect
import com.tinyblacksmith.core.content.FactionDef
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.heroes.Heroes
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.rng.Rng
import com.tinyblacksmith.core.rng.RngStream
import kotlin.math.roundToInt

/** Deterministic expeditions, champion selection and scheduled sieges (GDD 8). */
object Battle {

    fun resolveExpedition(ctx: ResolutionContext, hero: Hero, weapon: Weapon?, factionId: FactionId, faction: FactionDef, eliteChanceBonus: Double = 0.0) {
        val config = ctx.config
        val rng = ctx.rng(RngStream.COMBAT)
        val pressure = ctx.factions.getValue(factionId).pressure
        val variance = 1.0 + (rng.nextDouble() * 2 - 1) * config.encounterVariance
        // GDD 8 elite variants: rarer, stronger, richer. More likely as the faction's pressure rises.
        val elite = faction.eliteNames.isNotEmpty() && rng.chance(config.eliteBaseChance + pressure * config.eliteChancePerPressure + eliteChanceBonus)
        val enemyPower = (config.encounterBasePower + config.encounterPowerPerDay * ctx.day + config.encounterPowerPerPressure * pressure) * variance *
            (if (elite) config.elitePowerMultiplier else 1.0)
        val heroPower = Power.attackPower(hero, weapon, faction, ctx.content, config, ctx.blessingMagnitude(BlessingEffect.HERO_POWER), elite)
        val winProbability = (0.5 + (heroPower - enemyPower) / config.winProbabilityScale).coerceIn(config.winProbabilityFloor, config.winProbabilityCeiling)
        val encounter = rng.pick(if (elite) faction.eliteNames else faction.encounterNames)
        val roll = rng.nextDouble()  // the single draw of Rng.chance, kept so the day can say what the blade was worth
        val won = roll < winProbability
        // The same fight with another blade in hand: same foe, same roll, same formula. Draws nothing.
        fun losesWith(other: Weapon?): Boolean = roll >= (0.5 + (Power.attackPower(hero, other, faction, ctx.content, config, ctx.blessingMagnitude(BlessingEffect.HERO_POWER), elite) - enemyPower) /
            config.winProbabilityScale).coerceIn(config.winProbabilityFloor, config.winProbabilityCeiling)
        if (weapon != null) wear(ctx, weapon.id, if (won) config.wearPerExpeditionWin else config.wearPerExpeditionLoss)
        val weaponText = weapon?.let { " using ${it.name}" } ?: " bare-handed"
        val affixDefs = weapon?.let { w -> (w.affixes + w.flaws).map { ctx.content.affix(it) } } ?: emptyList()
        if (won) {
            ctx.expeditionWinsToday += 1
            val loot = (rng.nextInt(config.expeditionGoldMin, config.expeditionGoldMax) * (if (elite) config.eliteGoldMultiplier else 1.0)).toInt()
            val f = ctx.factions.getValue(factionId)
            ctx.factions[factionId] = f.copy(suppressionToday = f.suppressionToday + config.expeditionSuppression + (if (elite) config.eliteSuppression else 0))
            // Vampiric mends, Cursed/Bloodbound bite: never lethal, never above full health.
            val health = (hero.health + affixDefs.sumOf { it.healOnWin } - affixDefs.sumOf { it.selfDamageOnWin }).coerceIn(1, 100)
            val h = hero.copy(
                gold = hero.gold + loot, health = health, kills = hero.kills + 1, victories = hero.victories + 1, expeditionWins = hero.expeditionWins + 1,
                elitesSlain = hero.elitesSlain + (if (elite) 1 else 0), fame = hero.fame + 1 + (if (elite) config.eliteFame else 0), lastActivity = HeroActivity.EXPEDITION,
            )
            if (weapon != null) {
                val w = ctx.weapon(weapon.id)
                ctx.updateWeapon(w.copy(kills = w.kills + 1, victories = w.victories + 1, fame = w.fame + 1 + (if (elite) config.eliteFame else 0)))
                ctx.addWeaponHistory(weapon.id, "VICTORY", "${hero.fullName} ${if (elite) "slew" else "routed"} $encounter.", listOf(hero.id.value))
                if (elite) ctx.updateWeapon(ctx.weapon(weapon.id).let { if (it.title == null) it.copy(title = "Slayer of $encounter") else it })
                if (w.kills + 1 >= 5) {
                    ctx.updateWeapon(ctx.weapon(weapon.id).let { if (it.title == null) it.copy(title = "Bane of the ${faction.name}") else it })
                    ctx.milestone("WEAPON_FIVE_KILLS", "${weapon.name} earned a title after five victories.")
                }
            }
            val told = if (elite) {
                val slain = ctx.emit(EventType.ELITE_SLAIN, 7, "${hero.fullName} slew $encounter$weaponText and returned with $loot gold in spoils.", listOfNotNull(hero.id.value, weapon?.id?.value))
                ctx.milestone("ELITE_SLAIN", "An elite foe fell to a hero of Emberfall: $encounter.")
                ctx.replays += fightReplay(ctx, hero, weapon, encounter, heroPower, enemyPower, winProbability, slain, heroWon = true, loot, "struck the killing blow and took the spoils", "${hero.fullName} slew $encounter")
                slain
            } else {
                ctx.emit(EventType.EXPEDITION_WON, if (weapon != null) 5 else 3, "${hero.fullName} routed $encounter$weaponText.", listOfNotNull(hero.id.value, weapon?.id?.value))
            }
            var found: EventRecord? = null
            if (elite || rng.chance(config.expeditionLootChance + affixDefs.sumOf { it.lootChanceBonus })) {
                val lootable = ctx.content.materials.filter { it.category != MaterialCategory.CATALYST }
                // Elites carry the good stuff: catalysts and high-tier materials when the catalog has them. A Lucky blade finds the same.
                val pool = if (elite || affixDefs.any { it.scarceLoot }) ctx.content.materials.filter { it.category == MaterialCategory.CATALYST || it.tier >= 3 }.ifEmpty { lootable } else lootable
                val m = rng.pick(pool)
                ctx.materials[m.id] = (ctx.materials[m.id] ?: 0) + 1
                found = ctx.emit(EventType.EXPEDITION_WON, 2, "${hero.fullName} brought ${m.name} back to the forge.", listOf(hero.id.value), mapOf("material" to m.id.value))
            }
            // A blade bought this morning is judged against the one it replaced (traded in, so still in the shop as it was).
            val bought = ctx.newEvents.lastOrNull { it.type == EventType.WEAPON_SOLD && it.subjectIds.firstOrNull() == hero.id.value }?.takeIf { weapon != null && weapon.id.value in it.subjectIds }
            ctx.field += FieldResult(
                hero.id, hero.fullName, FieldOutcome.WON, encounter, elite, factionId,
                lostBareHanded = weapon != null && losesWith(null),
                lostWithOldBlade = bought?.data?.get("tradedWeapon")?.let { losesWith(ctx.weapons[WeaponId(it)]) },
                matchupHelped = weapon != null && ((weapon.element != null && weapon.element == faction.weakTo) || affixDefs.any { it.baneFaction == faction.id }),
                gold = loot, materialId = found?.data?.get("material")?.let { MaterialId(it) }, eventIds = listOfNotNull(told.id, found?.id),
            )
            Heroes.grantXp(ctx, h, config.expeditionXp)
        } else {
            val damage = (rng.nextInt(config.expeditionDamageMin, config.expeditionDamageMax) *
                affixDefs.fold(1.0) { acc, d -> acc * d.damageTakenMultiplier } * (if (elite) config.eliteDamageMultiplier else 1.0)).toInt()
            val health = hero.health - damage
            if (health <= config.heroDeathHealthFloor) {
                val recovered = rng.chance(if (elite) config.weaponFates.eliteRecoveryChance else config.weaponRecoveryChance)
                val died = kill(ctx, hero, "fell to $encounter", recovered, weaponSeized = !recovered && rng.chance(if (elite) config.weaponFates.eliteSeizureChance else config.weaponSeizureChance))
                ctx.replays += fightReplay(ctx, hero, weapon, encounter, heroPower, enemyPower, winProbability, died, heroWon = false, damage, "struck ${hero.name} down", "${hero.fullName} fell")
                ctx.field += FieldResult(hero.id, hero.fullName, FieldOutcome.DIED, encounter, elite, factionId, eventIds = listOf(died.id))
            } else {
                ctx.updateHero(hero.copy(health = health, lastActivity = HeroActivity.EXPEDITION))
                val lost = ctx.emit(EventType.EXPEDITION_LOST, 3, "${hero.fullName} was driven back by $encounter$weaponText.", listOfNotNull(hero.id.value, weapon?.id?.value))
                if (elite) ctx.replays += fightReplay(ctx, hero, weapon, encounter, heroPower, enemyPower, winProbability, lost, heroWon = false, damage, "drove ${hero.name} from the field", "${hero.fullName} was driven back")
                ctx.field += FieldResult(hero.id, hero.fullName, FieldOutcome.DRIVEN_BACK, encounter, elite, factionId, eventIds = listOf(lost.id))
                if (health < config.heroWoundedThreshold) ctx.emit(EventType.HERO_WOUNDED, 2, "${hero.fullName} returned wounded.", listOf(hero.id.value))
                val breakChance = affixDefs.sumOf { it.breakChanceOnLoss }
                if (weapon != null && breakChance > 0 && rng.chance(breakChance)) {
                    ctx.updateWeapon(ctx.weapon(weapon.id).copy(location = WeaponLocation.Destroyed(ctx.day)))
                    ctx.addWeaponHistory(weapon.id, "BROKEN", "Shattered in ${hero.fullName}'s hands against $encounter.", listOf(hero.id.value))
                    ctx.emit(EventType.WEAPON_BROKEN, 4, "${weapon.name} shattered in ${hero.fullName}'s hands. Brittle work.", listOf(weapon.id.value, hero.id.value))
                }
            }
        }
    }

    /**
     * GDD 6/11: a short replay of a significant expedition (an elite fight, or one the hero did not survive), built only
     * from numbers the resolver has already rolled: the two powers, the odds and the decisive [amount] (spoils in gold
     * for a win, the wound for a loss). It draws no RNG and touches no state; the wording varies with the odds.
     */
    private fun fightReplay(
        ctx: ResolutionContext, hero: Hero, weapon: Weapon?, encounter: String, heroPower: Double, enemyPower: Double, winProbability: Double,
        event: EventRecord, heroWon: Boolean, amount: Int, finish: String, outcome: String,
    ): CombatReplay {
        val foe = encounter.replaceFirstChar { it.uppercase() }
        val stand = when {
            winProbability >= 0.65 -> "gave ground"
            winProbability <= 0.35 -> "pressed hard"
            else -> "stood firm"
        }
        val rounds = listOf(
            CombatRound(hero.fullName, encounter, heroPower.roundToInt(), weapon?.let { "met $encounter with ${it.name}" } ?: "met $encounter bare-handed", hero.id.value),
            CombatRound(foe, hero.fullName, enemyPower.roundToInt(), stand),
            if (heroWon) CombatRound(hero.fullName, encounter, amount, finish, hero.id.value) else CombatRound(foe, hero.fullName, amount, finish),
        )
        return CombatReplay("${hero.fullName} vs $encounter", ctx.day, rounds, outcome, ReplayKind.EXPEDITION, event.id)
    }

    /** The day's report: the siege first (it alone feeds the diorama), then the most significant fights, capped so the report and the save stay small. */
    fun dayReplays(ctx: ResolutionContext): List<CombatReplay> {
        val priority = ctx.newEvents.associate { it.id to it.priority }
        val (fights, sieges) = ctx.replays.partition { it.kind == ReplayKind.EXPEDITION }
        return sieges + fights.sortedByDescending { priority[it.eventId] ?: 0 }.take(ctx.config.weaponFates.maxExpeditionReplaysPerDay)
    }

    /** A fight or a siege wears the blade; Hone restores it (GDD 6 condition factor). */
    private fun wear(ctx: ResolutionContext, weaponId: WeaponId, amount: Int) {
        val w = ctx.weapon(weaponId)
        ctx.updateWeapon(w.copy(condition = maxOf(0, w.condition - amount)))
    }

    /**
     * GDD 7 artifact cycle on death, for the blade the hero carried. The caller rolls whether comrades recovered it and,
     * if not, whether the enemy seized it, at odds set by where and how the hero fell. A blade the enemy did not take may
     * pass to a living guildmate instead of the forge, and one that would be lost may surface with a travelling merchant
     * a few days later ([Market.resolveMerchant]). Spare blades are lost with the hero, as before.
     * Draws on the COMBAT stream, after the caller's: the guild's claim (only with a living guildmate), then the merchant
     * (only for a blade that would otherwise be lost).
     */
    fun kill(ctx: ResolutionContext, hero: Hero, cause: String, weaponRecovered: Boolean, weaponSeized: Boolean = false): EventRecord {
        val config = ctx.config
        val rng = ctx.rng(RngStream.COMBAT)
        ctx.updateHero(hero.copy(health = 0, fate = HeroFate.DEAD, diedOnDay = ctx.day, lastActivity = HeroActivity.IDLE))
        val died = ctx.emit(EventType.HERO_DIED, 8, "${hero.fullName} $cause and will not return.", listOf(hero.id.value))
        for (w in ctx.weapons.values.filter { it.ownerId == hero.id }) {
            val heir = if (w.isEquipped && (weaponRecovered || !weaponSeized)) guildHeir(ctx, hero, w) else null
            if (heir != null && roll(rng, config.weaponFates.guildInheritanceChance)) {
                // The rule Heroes.retire applies to a mentee: equipped only if it beats their own blade by worn power, else kept as a spare.
                ctx.addWeaponHistory(w.id, "INHERITED", "Inherited by ${heir.fullName}, guildmate of the fallen ${hero.fullName}.", listOf(heir.id.value, hero.id.value))
                Market.giveAndEquip(ctx, heir, ctx.weapon(w.id))
                ctx.emit(EventType.WEAPON_INHERITED, 5, "${w.name} passed from the fallen ${hero.fullName} to guildmate ${heir.fullName}.", listOf(w.id.value, heir.id.value, hero.id.value), mapOf(WeaponFate.KEY to WeaponFate.INHERITED.name))
            } else if (w.isEquipped && weaponRecovered) {
                ctx.updateWeapon(w.copy(location = WeaponLocation.Storage))
                ctx.addWeaponHistory(w.id, "RECOVERED", "Recovered after ${hero.fullName}'s death and returned to the forge.", listOf(hero.id.value))
                ctx.emit(EventType.WEAPON_RECOVERED, 5, "${w.name} was recovered from ${hero.fullName}'s body and returned to the forge.", listOf(w.id.value, hero.id.value), mapOf(WeaponFate.KEY to WeaponFate.RECOVERED.name))
            } else if (w.isEquipped && weaponSeized) {
                ctx.updateWeapon(w.copy(location = WeaponLocation.Lost(ctx.day, "seized")))
                ctx.addWeaponHistory(w.id, "SEIZED", "Seized by the enemy when ${hero.fullName} fell.", listOf(hero.id.value))
                ctx.emit(EventType.WEAPON_STOLEN, 5, "${w.name} was seized by the enemy from ${hero.fullName}'s body.", listOf(w.id.value, hero.id.value), mapOf(WeaponFate.KEY to WeaponFate.SEIZED.name))
            } else if (w.isEquipped && roll(rng, merchantChance(w, config))) {
                ctx.updateWeapon(w.copy(location = WeaponLocation.Lost(ctx.day, WeaponLocation.Lost.WITH_MERCHANT)))
                ctx.addWeaponHistory(w.id, "SCAVENGED", "Taken from the field where ${hero.fullName} fell.", listOf(hero.id.value))
                ctx.emit(EventType.WEAPON_LOST, 5, "${w.name} was gone from the field where ${hero.fullName} fell.", listOf(w.id.value, hero.id.value), mapOf(WeaponFate.KEY to WeaponFate.MERCHANT.name))
            } else {
                ctx.updateWeapon(w.copy(location = WeaponLocation.Lost(ctx.day, "lost with ${hero.fullName}")))
                ctx.addWeaponHistory(w.id, "LOST", "Lost when ${hero.fullName} died.", listOf(hero.id.value))
                if (w.isEquipped) ctx.emit(EventType.WEAPON_LOST, 5, "${w.name} was lost with ${hero.fullName}.", listOf(w.id.value, hero.id.value), mapOf(WeaponFate.KEY to WeaponFate.LOST.name))
            }
        }
        ctx.town = ctx.town.copy(championIds = ctx.town.championIds.filter { it != hero.id })
        return died
    }

    /** A fate that cannot occur draws nothing, so a config with these chances at 0 replays the v4 draw sequence exactly. */
    private fun roll(rng: Rng, chance: Double): Boolean = chance > 0.0 && rng.chance(chance)

    /** The living guildmate a fallen member's blade suits best (largest gain over the blade in hand; ties by ID), or null. Draws no RNG. */
    private fun guildHeir(ctx: ResolutionContext, fallen: Hero, blade: Weapon): Hero? {
        val guild = fallen.guildId ?: return null
        return ctx.aliveHeroes().filter { it.guildId == guild && it.id != fallen.id }
            .maxByOrNull { Market.evaluate(ctx, it, ctx.equippedWeapon(it.id), blade, 0.5).gain }
    }

    /** GDD 7: "famous artifacts increase event eligibility but are not guaranteed to return". Counted fame is capped like every fame effect, and so is the chance. */
    fun merchantChance(weapon: Weapon, config: BalanceConfig): Double =
        (config.weaponFates.merchantBaseChance + weapon.fame.coerceIn(0, config.weaponFameCap) * config.weaponFates.merchantChancePerFame).coerceAtMost(config.weaponFates.merchantMaxChance)

    /**
     * Three strongest available champions (GDD 6/8). Fewer than three is fine; none means the militia stands alone.
     * Ranked against the foe they will face: [elite] is the warlord flag of the siege, the same one their powers are valued with.
     */
    fun selectChampions(ctx: ResolutionContext, faction: FactionDef, elite: Boolean = false): List<Pair<Hero, Weapon?>> =
        ctx.aliveHeroes()
            .filter { it.health >= ctx.config.heroWoundedThreshold }
            .map { it to ctx.equippedWeapon(it.id) }
            .sortedWith(compareByDescending<Pair<Hero, Weapon?>> { Power.defensePower(it.first, it.second, faction, ctx.content, ctx.config, ctx.blessingMagnitude(BlessingEffect.HERO_POWER), elite) }.thenBy(IdOrder.numeric) { it.first.id.value })
            .take(ctx.config.championCount)

    /** What the next siege looks like as things stand; the same numbers [resolveSiegeIfDue] uses on the day. */
    data class SiegeOutlook(
        val factionState: FactionState, val faction: FactionDef, val warlord: Boolean,
        val champions: List<Pair<Hero, Weapon?>>, val championPowers: List<Double>, val townDefense: Double, val raidPower: Double,
    ) {
        val odds: SiegeOdds get() = (townDefense / maxOf(1.0, raidPower)).let {
            when {
                it >= 1.15 -> SiegeOdds.STRONG
                it >= 1.0 -> SiegeOdds.EVEN
                it >= 0.8 -> SiegeOdds.OUTMATCHED
                else -> SiegeOdds.DIRE
            }
        }
    }

    enum class SiegeOdds { STRONG, EVEN, OUTMATCHED, DIRE }

    /** The faction that will besiege the town: the highest pressure, ties by ID (never by the order the save lists them in). */
    fun leadingFaction(ctx: ResolutionContext): FactionState? = ctx.factions.values.sortedBy { it.id.value }.maxByOrNull { it.pressure }

    fun outlook(ctx: ResolutionContext, siegeDay: Int): SiegeOutlook? {
        val config = ctx.config
        val factionState = leadingFaction(ctx) ?: return null
        val faction = ctx.content.faction(factionState.id)
        val warlord = faction.warlordName != null && factionState.pressure >= config.warlordPressure
        val blessing = ctx.blessingMagnitude(BlessingEffect.HERO_POWER)
        val champions = selectChampions(ctx, faction, warlord)
        val championPowers = champions.map { (h, w) -> Power.defensePower(h, w, faction, ctx.content, config, blessing, warlord) }
        val townDefense = championPowers.sum() + ctx.town.militia + ctx.town.armory
        val raidPower = (config.raidBase + config.raidPerDay * siegeDay + config.raidPerPressure * factionState.pressure) * config.siegeModifier *
            ctx.world.raidMultiplier * (if (warlord) config.warlordRaidMultiplier else 1.0)
        return SiegeOutlook(factionState, faction, warlord, champions, championPowers, townDefense, raidPower)
    }

    fun resolveSiegeIfDue(ctx: ResolutionContext) {
        val config = ctx.config
        if (ctx.day != ctx.town.nextSiegeDay) return
        val o = outlook(ctx, ctx.day) ?: return
        val factionState = o.factionState
        val faction = o.faction
        val champions = o.champions
        val championPowers = o.championPowers
        val townDefense = o.townDefense
        val raidPower = o.raidPower
        val attacker = if (o.warlord) "${faction.warlordName} and the ${faction.siegeName}" else "the ${faction.siegeName}"
        val won = townDefense >= raidPower
        val forgeDamage = (config.forgeDamageBase + config.forgeDamageSlope * (raidPower / maxOf(1.0, townDefense) - 1.0)).roundToInt()
            .coerceIn(0, config.maxForgeDamagePerSiege)

        val rounds = mutableListOf<CombatRound>()
        champions.forEachIndexed { i, (h, w) ->
            rounds += CombatRound(h.fullName, faction.siegeName, championPowers[i].roundToInt(), w?.let { "strikes with ${it.name}" } ?: "fights bare-handed", h.id.value)
        }
        if (ctx.town.militia > 0) rounds += CombatRound("Town militia", faction.siegeName, ctx.town.militia, "holds the gate")
        if (ctx.town.armory > 0) rounds += CombatRound("Town watch", faction.siegeName, ctx.town.armory, "fights with arms from the forge")
        rounds += CombatRound(faction.siegeName, "the forge", forgeDamage, if (won) "is driven off" else "breaks through")
        val championNames = champions.joinToString(", ") { it.first.fullName }.ifEmpty { "no champion" }
        ctx.replays += CombatReplay("Siege of Emberfall, day ${ctx.day}", ctx.day, rounds, if (won) "Town held" else "Defenses broken")

        ctx.town = ctx.town.copy(integrity = ctx.town.integrity - forgeDamage, championIds = champions.map { it.first.id }, nextSiegeDay = ctx.town.nextSiegeDay + config.siegeInterval,
            armory = (ctx.town.armory * (1.0 - config.armorySiegeWear)).toInt())
        if (forgeDamage > 0) ctx.emit(EventType.FORGE_DAMAGED, 7, "The forge took $forgeDamage damage in the siege.", data = mapOf("damage" to forgeDamage.toString()))
        for ((_, w) in champions) if (w != null) wear(ctx, w.id, config.wearPerSiege)

        if (won) {
            ctx.town = ctx.town.copy(siegesSurvived = ctx.town.siegesSurvived + 1)
            ctx.factions[factionState.id] = factionState.copy(pressure = maxOf(0, factionState.pressure - config.siegeWinPressureDrop - (if (o.warlord) config.warlordPressureDrop else 0)))
            val held = ctx.emit(EventType.SIEGE_WON, 9, "Emberfall repelled $attacker! Champions: $championNames.", champions.map { it.first.id.value },
                mapOf("raidPower" to raidPower.roundToInt().toString(), "townDefense" to townDefense.roundToInt().toString()))
            for ((h, w) in champions) {
                val hero = ctx.hero(h.id)
                ctx.updateHero(hero.copy(health = maxOf(1, hero.health - config.championSiegeDamageOnWin), fame = hero.fame + 2, lastActivity = HeroActivity.DEFEND))
                Heroes.grantXp(ctx, ctx.hero(h.id), config.siegeXp)
                if (w != null) {
                    val ww = ctx.weapon(w.id)
                    ctx.updateWeapon(ww.copy(siegesDefended = ww.siegesDefended + 1, fame = ww.fame + 2, victories = ww.victories + 1))
                    ctx.addWeaponHistory(w.id, "SIEGE", "Defended Emberfall in ${hero.fullName}'s hands on day ${ctx.day}.", listOf(hero.id.value))
                    ctx.milestone("CHAMPION_ARMED", "A champion defended the town with a weapon from this forge: ${w.name}.")
                }
                Heroes.fulfilAmbition(ctx, h.id, Ambition.DEFENDER)
                ctx.field += FieldResult(h.id, h.fullName, FieldOutcome.HELD_THE_WALL, faction.siegeName, factionId = factionState.id, eventIds = listOf(held.id))
            }
            if (o.warlord) {
                ctx.earn(IncomeKind.TRIBUTE, config.warlordTribute)
                ctx.emit(EventType.MILESTONE, 6, "${faction.warlordName} was thrown back from the walls. The town paid the smith ${config.warlordTribute} gold in thanks.", data = mapOf("tribute" to config.warlordTribute.toString()))
                ctx.milestone("WARLORD_DEFEATED", "Emberfall broke a warlord at its walls.")
            }
            ctx.milestone("SIEGE_SURVIVED", "Emberfall survived its first siege.")
            offerBlessing(ctx)
        } else {
            ctx.town = ctx.town.copy(siegesLost = ctx.town.siegesLost + 1)
            // A rout: the raid outweighs the defense so far that the champions are cut down where they stand, not driven off.
            val rout = raidPower >= townDefense * config.weaponFates.wallsRoutRatio
            val overrun = ctx.emit(EventType.SIEGE_LOST, 9, "${attacker.replaceFirstChar { it.uppercase() }} ${if (rout) "routed" else "overran"} the defenders ($championNames).", champions.map { it.first.id.value },
                mapOf("raidPower" to raidPower.roundToInt().toString(), "townDefense" to townDefense.roundToInt().toString()) + (if (rout) mapOf("rout" to "true") else emptyMap()))
            val rng = ctx.rng(RngStream.COMBAT)
            for ((h, _) in champions) {
                val hero = ctx.hero(h.id)
                val health = hero.health - (if (rout) config.weaponFates.wallsRoutDamage else config.championSiegeDamageOnLoss)
                if (health <= config.heroDeathHealthFloor) {
                    val recovered = rng.chance(config.weaponFates.wallsRecoveryChance)
                    val died = kill(ctx, hero, "died defending the walls", recovered, weaponSeized = !recovered && rng.chance(config.weaponFates.wallsSeizureChance))
                    ctx.field += FieldResult(hero.id, hero.fullName, FieldOutcome.FELL_AT_THE_WALL, faction.siegeName, factionId = factionState.id, eventIds = listOf(overrun.id, died.id))
                }
                else ctx.updateHero(hero.copy(health = health, lastActivity = HeroActivity.DEFEND))
            }
        }
        if (ctx.town.integrity <= 0) {
            ctx.town = ctx.town.copy(integrity = 0)
            ctx.phase = Phase.ENDED
            ctx.endCause = "The forge fell to the ${faction.siegeName} on day ${ctx.day}."
            ctx.emit(EventType.FORGE_DESTROYED, 10, "The forge has fallen. Emberfall's smith is no more.")
        }
    }

    private fun offerBlessing(ctx: ResolutionContext) {
        val rng = ctx.rng(RngStream.LEGACY)
        // Guild Patronage needs a guild to send anybody; without one it is not among the choices.
        val pool = ctx.content.blessings.filter { ctx.town.guilds.isNotEmpty() || it.effect != BlessingEffect.GUILD_PATRONAGE }.map { it.id }.toMutableList()
        val offer = mutableListOf<BlessingId>()
        repeat(minOf(ctx.config.blessingOfferSize, pool.size)) { val b = rng.pick(pool); offer += b; pool.remove(b) }
        ctx.pendingBlessingOffer = offer
        ctx.emit(EventType.BLESSING_OFFERED, 3, "The grateful town offers the smith a blessing.", data = mapOf("offer" to offer.joinToString(",") { it.value }))
    }

    /** A warning goes out on each of the last [WARNING_DAYS] evenings before a siege. */
    const val WARNING_DAYS = 2

    /**
     * The besieger a warning is out for, on the days heroes shop under it: the [WARNING_DAYS] days that follow a warning
     * evening (the day before the siege and the siege day itself). Null on every other day. The leader as it stands today.
     */
    fun warnedFaction(ctx: ResolutionContext): FactionDef? =
        if (ctx.town.nextSiegeDay - ctx.day in 0 until WARNING_DAYS) leadingFaction(ctx)?.let { ctx.content.faction(it.id) } else null

    fun warnOfSiege(ctx: ResolutionContext) {
        val daysLeft = ctx.town.nextSiegeDay - ctx.day
        if (daysLeft in 1..WARNING_DAYS) {
            val f = leadingFaction(ctx) ?: return
            val def = ctx.content.faction(f.id)
            val led = if (def.warlordName != null && f.pressure >= ctx.config.warlordPressure) " ${def.warlordName} leads them." else ""
            val weak = def.weakTo?.let { " ${it.name.lowercase().replaceFirstChar { c -> c.uppercase() }} weapons bite them hardest." } ?: ""
            ctx.emit(EventType.SIEGE_WARNING, 6, "${def.name} gather for the day ${ctx.town.nextSiegeDay} invasion (${describePressure(f.pressure)}).$led$weak", data = mapOf("day" to ctx.town.nextSiegeDay.toString()))
        }
    }

    fun describePressure(pressure: Int): String = when {
        pressure >= 80 -> "overwhelming threat"
        pressure >= 60 -> "grave threat"
        pressure >= 40 -> "rising threat"
        pressure >= 20 -> "restless"
        else -> "quiet"
    }
}
