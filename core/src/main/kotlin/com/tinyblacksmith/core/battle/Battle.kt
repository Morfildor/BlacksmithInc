package com.tinyblacksmith.core.battle

import com.tinyblacksmith.core.content.BlessingEffect
import com.tinyblacksmith.core.content.FactionDef
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.heroes.Heroes
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.rng.RngStream
import kotlin.math.roundToInt

/** Deterministic expeditions, champion selection and scheduled sieges (GDD 8). */
object Battle {

    fun resolveExpedition(ctx: ResolutionContext, hero: Hero, weapon: Weapon?, factionId: FactionId, faction: FactionDef) {
        val config = ctx.config
        val rng = ctx.rng(RngStream.COMBAT)
        val pressure = ctx.factions.getValue(factionId).pressure
        val variance = 1.0 + (rng.nextDouble() * 2 - 1) * config.encounterVariance
        // GDD 8 elite variants: rarer, stronger, richer. More likely as the faction's pressure rises.
        val elite = faction.eliteNames.isNotEmpty() && rng.chance(config.eliteBaseChance + pressure * config.eliteChancePerPressure)
        val enemyPower = (config.encounterBasePower + config.encounterPowerPerDay * ctx.day + config.encounterPowerPerPressure * pressure) * variance *
            (if (elite) config.elitePowerMultiplier else 1.0)
        val heroPower = Power.attackPower(hero, weapon, faction, ctx.content, config, ctx.blessingMagnitude(BlessingEffect.HERO_POWER), elite)
        val winProbability = (0.5 + (heroPower - enemyPower) / config.winProbabilityScale).coerceIn(config.winProbabilityFloor, config.winProbabilityCeiling)
        val encounter = rng.pick(if (elite) faction.eliteNames else faction.encounterNames)
        val won = rng.chance(winProbability)
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
            if (elite) {
                ctx.emit(EventType.ELITE_SLAIN, 7, "${hero.fullName} slew $encounter$weaponText and returned with $loot gold in spoils.", listOfNotNull(hero.id.value, weapon?.id?.value), mapOf("winProbability" to "%.2f".format(winProbability)))
                ctx.milestone("ELITE_SLAIN", "An elite foe fell to a hero of Emberfall: $encounter.")
            } else {
                ctx.emit(EventType.EXPEDITION_WON, if (weapon != null) 5 else 3, "${hero.fullName} routed $encounter$weaponText.", listOfNotNull(hero.id.value, weapon?.id?.value), mapOf("winProbability" to "%.2f".format(winProbability)))
            }
            if (elite || rng.chance(config.expeditionLootChance + affixDefs.sumOf { it.lootChanceBonus })) {
                val lootable = ctx.content.materials.filter { it.category != MaterialCategory.CATALYST }
                // Elites carry the good stuff: catalysts and high-tier materials when the catalog has them.
                val pool = if (elite) ctx.content.materials.filter { it.category == MaterialCategory.CATALYST || it.tier >= 3 }.ifEmpty { lootable } else lootable
                val m = rng.pick(pool)
                ctx.materials[m.id] = (ctx.materials[m.id] ?: 0) + 1
                ctx.emit(EventType.EXPEDITION_WON, 2, "${hero.fullName} brought ${m.name} back to the forge.", listOf(hero.id.value), mapOf("material" to m.id.value))
            }
            Heroes.grantXp(ctx, h, config.expeditionXp)
        } else {
            val damage = (rng.nextInt(config.expeditionDamageMin, config.expeditionDamageMax) *
                affixDefs.fold(1.0) { acc, d -> acc * d.damageTakenMultiplier } * (if (elite) config.eliteDamageMultiplier else 1.0)).toInt()
            val health = hero.health - damage
            if (health <= config.heroDeathHealthFloor) {
                val recovered = rng.chance(config.weaponRecoveryChance)
                kill(ctx, hero, "fell to $encounter", recovered, weaponSeized = !recovered && rng.chance(config.weaponSeizureChance))
            } else {
                ctx.updateHero(hero.copy(health = health, lastActivity = HeroActivity.EXPEDITION))
                ctx.emit(EventType.EXPEDITION_LOST, 3, "${hero.fullName} was driven back by $encounter$weaponText.", listOfNotNull(hero.id.value, weapon?.id?.value))
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

    /** GDD 7 artifact cycle on death: recovered to the forge, seized by monsters (may return later) or lost with the hero. */
    fun kill(ctx: ResolutionContext, hero: Hero, cause: String, weaponRecovered: Boolean, weaponSeized: Boolean = false) {
        ctx.updateHero(hero.copy(health = 0, fate = HeroFate.DEAD, diedOnDay = ctx.day, lastActivity = HeroActivity.IDLE))
        ctx.emit(EventType.HERO_DIED, 8, "${hero.fullName} $cause and will not return.", listOf(hero.id.value))
        for (w in ctx.weapons.values.filter { it.ownerId == hero.id }) {
            if (w.isEquipped && weaponRecovered) {
                ctx.updateWeapon(w.copy(location = WeaponLocation.Storage))
                ctx.addWeaponHistory(w.id, "RECOVERED", "Recovered after ${hero.fullName}'s death and returned to the forge.", listOf(hero.id.value))
                ctx.emit(EventType.WEAPON_RECOVERED, 5, "${w.name} was recovered from ${hero.fullName}'s body and returned to the forge.", listOf(w.id.value, hero.id.value))
            } else if (w.isEquipped && weaponSeized) {
                ctx.updateWeapon(w.copy(location = WeaponLocation.Lost(ctx.day, "seized")))
                ctx.addWeaponHistory(w.id, "SEIZED", "Seized by the enemy when ${hero.fullName} fell.", listOf(hero.id.value))
                ctx.emit(EventType.WEAPON_STOLEN, 5, "${w.name} was seized by the enemy from ${hero.fullName}'s body.", listOf(w.id.value, hero.id.value))
            } else {
                ctx.updateWeapon(w.copy(location = WeaponLocation.Lost(ctx.day, "lost with ${hero.fullName}")))
                ctx.addWeaponHistory(w.id, "LOST", "Lost when ${hero.fullName} died.", listOf(hero.id.value))
                if (w.isEquipped) ctx.emit(EventType.WEAPON_LOST, 5, "${w.name} was lost with ${hero.fullName}.", listOf(w.id.value, hero.id.value))
            }
        }
        ctx.town = ctx.town.copy(championIds = ctx.town.championIds.filter { it != hero.id })
    }

    /** Three strongest available champions (GDD 6/8). Fewer than three is fine; none means the militia stands alone. */
    fun selectChampions(ctx: ResolutionContext, faction: FactionDef): List<Pair<Hero, Weapon?>> =
        ctx.aliveHeroes()
            .filter { it.health >= ctx.config.heroWoundedThreshold }
            .map { it to ctx.equippedWeapon(it.id) }
            .sortedWith(compareByDescending<Pair<Hero, Weapon?>> { Power.defensePower(it.first, it.second, faction, ctx.content, ctx.config, ctx.blessingMagnitude(BlessingEffect.HERO_POWER)) }.thenBy { it.first.id.value })
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

    fun outlook(ctx: ResolutionContext, siegeDay: Int): SiegeOutlook? {
        val config = ctx.config
        val factionState = ctx.factions.values.maxByOrNull { it.pressure } ?: return null
        val faction = ctx.content.faction(factionState.id)
        val warlord = faction.warlordName != null && factionState.pressure >= config.warlordPressure
        val blessing = ctx.blessingMagnitude(BlessingEffect.HERO_POWER)
        val champions = selectChampions(ctx, faction)
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
            rounds += CombatRound(h.fullName, faction.siegeName, championPowers[i].roundToInt(), w?.let { "strikes with ${it.name}" } ?: "fights bare-handed")
        }
        if (ctx.town.militia > 0) rounds += CombatRound("Town militia", faction.siegeName, ctx.town.militia, "holds the gate")
        if (ctx.town.armory > 0) rounds += CombatRound("Town watch", faction.siegeName, ctx.town.armory, "fights with arms from the forge")
        rounds += CombatRound(faction.siegeName, "the forge", forgeDamage, if (won) "is driven off" else "breaks through")
        val championNames = champions.joinToString(", ") { it.first.fullName }.ifEmpty { "no champion" }
        ctx.replays += CombatReplay("Siege of Emberfall, day ${ctx.day}", ctx.day, rounds, if (won) "Town held" else "Defenses broken")

        ctx.town = ctx.town.copy(integrity = ctx.town.integrity - forgeDamage, championIds = champions.map { it.first.id }, nextSiegeDay = ctx.town.nextSiegeDay + config.siegeInterval,
            armory = (ctx.town.armory * (1.0 - config.armorySiegeWear)).toInt())
        if (forgeDamage > 0) ctx.emit(EventType.FORGE_DAMAGED, 7, "The forge took $forgeDamage damage in the siege.", data = mapOf("damage" to forgeDamage.toString()))

        if (won) {
            ctx.town = ctx.town.copy(siegesSurvived = ctx.town.siegesSurvived + 1)
            ctx.factions[factionState.id] = factionState.copy(pressure = maxOf(0, factionState.pressure - config.siegeWinPressureDrop - (if (o.warlord) config.warlordPressureDrop else 0)))
            ctx.emit(EventType.SIEGE_WON, 9, "Emberfall repelled $attacker! Champions: $championNames.", champions.map { it.first.id.value },
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
            }
            if (o.warlord) {
                ctx.gold += config.warlordTribute
                ctx.emit(EventType.MILESTONE, 6, "${faction.warlordName} was thrown back from the walls. The town paid the smith ${config.warlordTribute} gold in thanks.", data = mapOf("tribute" to config.warlordTribute.toString()))
                ctx.milestone("WARLORD_DEFEATED", "Emberfall broke a warlord at its walls.")
            }
            ctx.milestone("SIEGE_SURVIVED", "Emberfall survived its first siege.")
            offerBlessing(ctx)
        } else {
            ctx.town = ctx.town.copy(siegesLost = ctx.town.siegesLost + 1)
            ctx.emit(EventType.SIEGE_LOST, 9, "${attacker.replaceFirstChar { it.uppercase() }} overran the defenders ($championNames).", champions.map { it.first.id.value },
                mapOf("raidPower" to raidPower.roundToInt().toString(), "townDefense" to townDefense.roundToInt().toString()))
            val rng = ctx.rng(RngStream.COMBAT)
            for ((h, _) in champions) {
                val hero = ctx.hero(h.id)
                val health = hero.health - config.championSiegeDamageOnLoss
                if (health <= config.heroDeathHealthFloor) {
                    val recovered = rng.chance(config.weaponRecoveryChance)
                    kill(ctx, hero, "died defending the walls", recovered, weaponSeized = !recovered && rng.chance(config.weaponSeizureChance))
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
        val pool = ctx.content.blessings.map { it.id }.toMutableList()
        val offer = mutableListOf<BlessingId>()
        repeat(minOf(ctx.config.blessingOfferSize, pool.size)) { val b = rng.pick(pool); offer += b; pool.remove(b) }
        ctx.pendingBlessingOffer = offer
        ctx.emit(EventType.BLESSING_OFFERED, 3, "The grateful town offers the smith a blessing.", data = mapOf("offer" to offer.joinToString(",") { it.value }))
    }

    fun warnOfSiege(ctx: ResolutionContext) {
        val daysLeft = ctx.town.nextSiegeDay - ctx.day
        if (daysLeft in 1..2) {
            val f = ctx.factions.values.maxByOrNull { it.pressure } ?: return
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
