package com.tinyblacksmith.core.guild

import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.battle.Power
import com.tinyblacksmith.core.combat.Combatant
import com.tinyblacksmith.core.combat.EventKind
import com.tinyblacksmith.core.combat.Fight
import com.tinyblacksmith.core.combat.FightOutcome
import com.tinyblacksmith.core.combat.FightSetup
import com.tinyblacksmith.core.combat.Objective
import com.tinyblacksmith.core.combat.Posture
import com.tinyblacksmith.core.combat.Side
import com.tinyblacksmith.core.content.BlessingEffect
import com.tinyblacksmith.core.content.ArmyKind
import com.tinyblacksmith.core.content.Depth
import com.tinyblacksmith.core.content.FactionDef
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.heroes.Heroes
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.rng.RngStream
import com.tinyblacksmith.core.text.Terms
import kotlin.math.roundToInt

/**
 * The siege of a guild run (spec 10.3, plan D7). The besieger's plan is drawn when the besieger is committed and
 * stored on the [SiegeScenario]; on the night the three who stand on the wall fight it through the interaction engine,
 * after the watch and the militia have thinned it on the outer approach. A classic run keeps the scalar siege.
 */
object SiegeFight {
    const val MAX_FORGE_NOTE = "forge"

    /** The charter warlord leads the siege of the charter day and every later one until beaten. */
    private fun charterSiege(ctx: ResolutionContext, siegeDay: Int): Boolean =
        siegeDay >= ctx.config.guild.charterDay && ctx.guild?.milestone(GuildContent.CHARTER_SECURED) == null

    /** Draws the besieger's field for the coming siege (GUILD stream: nothing, the roster is a rule of the day and the pressure) and stores it. */
    fun plan(ctx: ResolutionContext) {
        val scenario = ctx.siege?.takeIf { it.siegeDay == ctx.town.nextSiegeDay } ?: return
        if (ctx.guild == null || scenario.plan.isNotEmpty()) return
        val faction = scenario.factionId ?: Battle.besieger(ctx)?.id ?: return
        val cat = GuildOps.catalog(ctx)
        if (ctx.living && cat.armies.any { it.factionId == faction }) return planArmy(ctx, scenario, faction)
        val cfg = ctx.config.guild
        val state = ctx.factions[faction] ?: return
        val number = ctx.town.siegesSurvived + ctx.town.siegesLost + 1
        val percent = GuildOps.ranked(ctx, cfg.siegePercentBase + (cfg.siegePercentPerDay * scenario.siegeDay + cfg.siegePercentPerPressure * state.pressure).toInt())
        val charter = charterSiege(ctx, scenario.siegeDay)
        val warlord = charter || state.pressure >= ctx.config.warlordPressure
        val lead = if (warlord) cat.siegeLeaders.getValue(faction) else cat.siegeElites.getValue(faction)
        val pool = cat.siegeGrunts.getValue(faction)
        var grunts = cfg.siegeGrunts + (number - 1) / cfg.siegeGruntEvery + (if (scenario.traitId == Depth.MANY_BREACHES) 1 else 0) + (if (cat.rank(ctx.guild!!.rank)?.extraSiegeUnit == true) 1 else 0)
        grunts = grunts.coerceIn(1, cfg.fight.maxActorsPerSide - 1)
        val units = listOf(EnemySpec(lead, if (charter) Fight.pct(percent, cfg.charterWarlordPercent) else percent)) + (0 until grunts).map { EnemySpec(pool[it % pool.size], percent) }
        // What they prepare is drawn with the field (GUILD stream) and kept: the smith reads it for as long as there is to prepare.
        val prep = cat.preparations.filter { it.factionId == faction }.let { if (it.isEmpty()) null else ctx.rng(RngStream.GUILD).pick(it) }
        ctx.siege = scenario.copy(factionId = faction, plan = units, charter = charter, prepId = prep?.id)
        if (prep != null) ctx.emit(EventType.SIEGE_PREPARATION, 7, "Scouts report what the ${ctx.content.faction(faction).name} prepare for day ${scenario.siegeDay}: ${prep.name.lowercase()}. ${prep.description} Ways to answer it: ${prep.counters.joinToString(" ")}",
            data = mapOf("prep" to prep.id, "day" to scenario.siegeDay.toString(), "faction" to faction.value))
    }

    /**
     * Living heroes (final design 12.4): the field is one of the authored armies, committed whole with its preparation, so
     * what the scouts report is what comes. Standard armies are the rule, captains, engines and great monsters the headline;
     * an army two newer ones of its faction have not displaced is not sent again. The charter warlord leads the charter siege,
     * with a standard army as escort, cut to fit four a side. Keyed draws: nothing else moves.
     */
    private fun planArmy(ctx: ResolutionContext, scenario: SiegeScenario, faction: FactionId) {
        val cat = GuildOps.catalog(ctx)
        val cfg = ctx.config.guild
        val state = ctx.factions.getValue(faction)
        val number = ctx.town.siegesSurvived + ctx.town.siegesLost + 1
        val percent = GuildOps.ranked(ctx, cfg.siegePercentBase + (cfg.siegePercentPerDay * scenario.siegeDay + cfg.siegePercentPerPressure * state.pressure).toInt())
        val charter = charterSiege(ctx, scenario.siegeDay)
        val rng = com.tinyblacksmith.core.rng.Keyed.rng(ctx.base.seed, "army|${ctx.era}|${scenario.siegeDay}")
        val recent = ctx.guild!!.armyLog.mapNotNull { cat.army(it) }.filter { it.factionId == faction }.takeLast(2).map { it.id }.toSet()
        val eligible = cat.armies.filter { it.factionId == faction && it.firstSiege <= number && (!charter || it.kind == ArmyKind.STANDARD) }
        val fresh = eligible.filter { it.id !in recent }.ifEmpty { eligible }
        val warlord = state.pressure >= ctx.config.warlordPressure
        val army = rng.pickWeighted(fresh.map { it to if (it.kind == ArmyKind.STANDARD) 1.0 else if (warlord) 2.0 else 0.5 })
        val max = cfg.fight.maxActorsPerSide
        val units = if (charter) listOf(EnemySpec(cat.siegeLeaders.getValue(faction), Fight.pct(percent, cfg.charterWarlordPercent))) + army.members.take(max - 1).map { EnemySpec(it, percent) }
            else army.members.take(max).map { EnemySpec(it, percent) }
        // A preparation that fits the field as authored: none that replaces its members, none that would push a fifth actor in.
        val preps = cat.preparations.filter { it.factionId == faction && it.gruntUnit == null && (it.addUnit == null || units.size < max) }
        val prep = if (preps.isEmpty()) null else rng.pick(preps)
        ctx.siege = scenario.copy(factionId = faction, plan = units, charter = charter, prepId = prep?.id, armyId = army.id)
        ctx.guild = ctx.guild!!.copy(armyLog = (ctx.guild!!.armyLog + army.id).takeLast(6))
        val who = if (charter) "${ctx.content.combat!!.unit(cat.siegeLeaders.getValue(faction)).name} leads it, escorted by ${army.name.lowercase()}" else army.name
        ctx.emit(EventType.SIEGE_PREPARATION, 7, "Scouts report the army coming on day ${scenario.siegeDay}: $who. ${army.forecast}" + (prep?.let { " They also prepare ${it.name.lowercase()}. ${it.description}" } ?: ""),
            data = mapOf("army" to army.id, "day" to scenario.siegeDay.toString(), "faction" to faction.value) + (prep?.let { mapOf("prep" to it.id) } ?: emptyMap()))
    }

    private const val TAG_PREP = "prep"
    private const val TAG_NEMESIS = "nemesis"

    /** The bearer of the smith's weapon when it belongs to the besieger and the weapon is still in enemy hands: it comes to the wall. */
    private fun nemesisAtWall(ctx: ResolutionContext, scenario: SiegeScenario): Nemesis? =
        ctx.guild?.nemesis?.takeIf { it.factionId == scenario.factionId && ctx.weapons[it.weaponId]?.location is WeaponLocation.Lost }

    /**
     * Who comes, as things stand: the stored plan, changed by the preparation while it is unspoiled, and joined by the
     * nemesis. Pure and drawn from nothing, so the forecast, the sabotage and the night itself read the same field.
     */
    fun fieldPlan(ctx: ResolutionContext, scenario: SiegeScenario): List<EnemySpec> {
        if (scenario.plan.isEmpty()) return emptyList()
        val max = ctx.config.guild.fight.maxActorsPerSide
        var units = scenario.plan
        /** Takes a place in the line: a free one, else the place of the last of the rank and file. */
        fun seat(spec: EnemySpec) {
            units = if (units.size < max) units + spec else units.indexOfLast { it.tag == null && it != units.first() }.let { i -> if (i < 0) units else units.filterIndexed { j, _ -> j != i } + spec }
        }
        val grunt = units.last().percent
        GuildOps.catalog(ctx).preparation(scenario.prepId)?.let { prep ->
            prep.gruntUnit?.let { g -> units = units.mapIndexed { i, e -> if (i == 0) e else e.copy(unitId = g) } }
            prep.addUnit?.let { seat(EnemySpec(it, grunt, TAG_PREP)) }
        }
        nemesisAtWall(ctx, scenario)?.let { seat(EnemySpec(it.unitId, grunt, TAG_NEMESIS)) }
        return units
    }

    private fun withStats(c: Combatant, add: Map<com.tinyblacksmith.core.combat.Stat, Int>): Combatant = if (add.isEmpty()) c else c.copy(stats = (c.stats.keys + add.keys).associateWith { (c.stats[it] ?: 0) + (add[it] ?: 0) })

    /** What a won sabotage does: the announced trait is spoiled if there is one, else the last of their line does not come. Once a siege. */
    fun sabotage(ctx: ResolutionContext): String {
        plan(ctx)
        val s = ctx.siege?.takeIf { it.siegeDay == ctx.town.nextSiegeDay } ?: return "There was nothing left to spoil."
        if (s.sabotaged > 0) return "Their preparations were already spoiled."
        val trait = ctx.content.siegeTrait(s.traitId)
        val prep = GuildOps.catalog(ctx).preparation(s.prepId)
        val text = if (prep != null) {
            ctx.siege = s.copy(prepId = null, prepSpoiled = prep.id, sabotaged = 1)
            "Their preparation is spoiled: ${prep.name.lowercase()}. It will not be used in the siege on day ${s.siegeDay}."
        } else if (trait != null) {
            ctx.siege = s.copy(traitId = null, sabotaged = 1, plan = if (trait.id == Depth.MANY_BREACHES && s.plan.size > 2) s.plan.dropLast(1) else s.plan)
            "Their plan for a ${trait.name} is spoiled: the siege on day ${s.siegeDay} will be an ordinary siege."
        } else if (s.plan.size > 1) {
            val gone = s.plan.last()
            ctx.siege = s.copy(plan = s.plan.dropLast(1), sabotaged = 1)
            "One enemy will not come to the siege on day ${s.siegeDay}: ${ctx.content.combat!!.unit(gone.unitId).name.lowercase()}."
        } else {
            ctx.siege = s.copy(sabotaged = 1)
            "Their camp was thrown into disorder, but the siege on day ${s.siegeDay} is unchanged."
        }
        ctx.emit(EventType.SABOTAGE_DONE, 6, "The guild's party spoiled the attackers' preparations. $text", data = mapOf("day" to s.siegeDay.toString()))
        return text
    }

    /**
     * Who would stand on the wall tonight: the members the smith reserved, in that order, while they are present and fit;
     * then the strongest of everybody else who is in town and not wounded, by the defense each is worth against the besieger.
     */
    fun defenders(ctx: ResolutionContext, faction: FactionDef?): List<Pair<Hero, Weapon?>> {
        val g = ctx.guild ?: return emptyList()
        val cfg = ctx.config.guild
        val fit = GuildOps.present(ctx).map { it.heroId }.toSet()
        val reserved = g.reserved.filter { it in fit }.mapNotNull { ctx.heroes[it] }
        val blessing = ctx.blessingMagnitude(BlessingEffect.HERO_POWER)
        val others = (GuildOps.present(ctx).mapNotNull { ctx.heroes[it.heroId] } + ctx.residents().filter { it.health >= ctx.config.heroWoundedThreshold })
            .filter { h -> reserved.none { it.id == h.id } && ctx.guild!!.planned?.heroIds?.contains(h.id) != true }
            .map { it to GuildOps.weaponOf(ctx, it.id) }
            .sortedWith(compareByDescending<Pair<Hero, Weapon?>> { (h, w) -> faction?.let { Power.defensePower(h, w, it, ctx.content, ctx.config, blessing) } ?: 0.0 }.thenBy(IdOrder.numeric) { it.first.id.value })
        return (reserved.map { it to GuildOps.weaponOf(ctx, it.id) } + others).take(cfg.defenders)
    }

    /** The enemy as it reaches the wall: the plan, each unit less what the watch and militia took from it on the way in. */
    private fun field(ctx: ResolutionContext, scenario: SiegeScenario, watch: Double): Pair<List<Combatant>, Int> {
        val combat = ctx.content.combat!!
        val cfg = ctx.config.guild
        val prep = GuildOps.catalog(ctx).preparation(scenario.prepId)
        val nemesis = nemesisAtWall(ctx, scenario)
        val units = fieldPlan(ctx, scenario).mapIndexed { i, e ->
            val foe = Loadout.enemy(combat.unit(e.unitId), "e${i + 1}", e.percent)
            withStats(if (e.tag == TAG_NEMESIS && nemesis != null) Stories.armed(ctx, foe, nemesis) else foe, prep?.enemyStats.orEmpty())
        }
        val total = units.sumOf { it.maxHealth }
        var pool = (watch * cfg.outerHealthPerPoint).roundToInt()
        // The outer line meets the rank and file first, the leader last.
        val thinned = units.asReversed().map { u ->
            val take = minOf(pool, Fight.pct(u.maxHealth, cfg.outerMaxPercent))
            pool -= take
            u.copy(health = u.maxHealth - take)
        }.asReversed()
        return thinned to total
    }

    data class Forecast(
        val faction: FactionDef?, val plan: List<EnemySpec>, val committed: Boolean, val charter: Boolean, val defenders: List<Pair<Hero, Weapon?>>,
        /** Members who will not be on the wall, and why: away with the party, hurt, held. */
        val missing: List<Pair<Hero, String>>, val watch: Int, val sabotaged: Boolean,
        /** What the besieger prepares, or what the party spoiled, and the bearer of the smith's weapon if it marches with them. */
        val prep: com.tinyblacksmith.core.content.SiegePrepDef? = null, val spoiled: com.tinyblacksmith.core.content.SiegePrepDef? = null, val nemesis: Nemesis? = null,
        /** Living heroes: the authored army committed for this siege, with its scouts' report and answers. */
        val army: com.tinyblacksmith.core.content.ArmyDef? = null,
    )

    /** What the Guild screen says of the coming siege. Reads only; simulates nothing. */
    fun forecast(ctx: ResolutionContext): Forecast? {
        val g = ctx.guild ?: return null
        val scenario = ctx.siege?.takeIf { it.siegeDay == ctx.town.nextSiegeDay }
        val faction = (scenario?.factionId ?: Battle.besieger(ctx)?.id)?.let { ctx.content.faction(it) }
        val trait = Battle.trait(ctx)
        val away = g.mission?.takeIf { it.returnDay > ctx.town.nextSiegeDay || (it.outcome == null && ctx.town.nextSiegeDay <= it.departedDay + it.offer.days - 1) }
        val missing = g.members.mapNotNull { m ->
            val hero = ctx.heroes[m.heroId] ?: return@mapNotNull null
            val why = when {
                m.status == MemberStatus.CAPTURED -> "captured"
                m.status == MemberStatus.AWAY && away != null -> "away until the morning of day ${away.returnDay}"
                GuildOps.laidUp(ctx, m)?.let { it.untilDay > ctx.town.nextSiegeDay } == true -> "${Terms.wound(m.wound!!.kind).lowercase()} until day ${m.wound.untilDay}"
                else -> null
            }
            why?.let { hero to it }
        }
        return Forecast(faction, scenario?.let { fieldPlan(ctx, it) }.orEmpty(), scenario?.factionId != null, scenario?.charter == true, defenders(ctx, faction), missing,
            ((ctx.town.militia + ctx.town.armory) * (trait?.watchMultiplier ?: 1.0)).roundToInt(), (scenario?.sabotaged ?: 0) > 0,
            GuildOps.catalog(ctx).preparation(scenario?.prepId), GuildOps.catalog(ctx).preparation(scenario?.prepSpoiled), scenario?.let { nemesisAtWall(ctx, it) }, GuildOps.catalog(ctx).army(scenario?.armyId))
    }

    /** Step 7 of End Day in a guild run. */
    fun resolve(ctx: ResolutionContext) {
        val config = ctx.config
        val cfg = config.guild
        if (ctx.day != ctx.town.nextSiegeDay) return
        plan(ctx)
        val scenario = ctx.siege?.takeIf { it.siegeDay == ctx.day } ?: return
        val factionId = scenario.factionId ?: return
        val faction = ctx.content.faction(factionId)
        val factionState = ctx.factions.getValue(factionId)
        val trait = ctx.content.siegeTrait(scenario.traitId)
        val combat = ctx.content.combat!!
        val leadUnit = combat.unit(scenario.plan.first().unitId)
        val cat = GuildOps.catalog(ctx)
        val prep = cat.preparation(scenario.prepId)
        val line = fieldPlan(ctx, scenario)
        val nemesis = nemesisAtWall(ctx, scenario)
        val nemesisKey = line.indexOfFirst { it.tag == TAG_NEMESIS }.takeIf { it >= 0 && nemesis != null }?.let { "e${it + 1}" }
        // Who is not there, as the roster stands tonight: said in the report, because it is the smith's own doing.
        val absent = ctx.guild!!.members.mapNotNull { m -> ctx.heroes[m.heroId]?.takeIf { it.isAlive }?.let { h -> when { m.status == MemberStatus.AWAY -> "${h.fullName} was away with the party."; m.status == MemberStatus.CAPTURED -> "${h.fullName} was captured."; else -> GuildOps.laidUp(ctx, m)?.let { w -> "${h.fullName} was ${Terms.wound(w.kind).lowercase()}." } } } }
        val warlord = leadUnit.id == GuildOps.catalog(ctx).siegeLeaders[factionId]
        val attacker = if (warlord) "${leadUnit.name} and the ${faction.siegeName}" else "the ${faction.siegeName}"
        val watch = (ctx.town.militia + ctx.town.armory) * (trait?.watchMultiplier ?: 1.0)
        val (enemies, enemyTotal) = field(ctx, scenario, watch)
        val outerTaken = enemyTotal - enemies.sumOf { it.health }
        val outer = if (outerTaken > 0) "The militia and the armory wore them down on the way in (${outerTaken * 100 / maxOf(1, enemyTotal)}% of their health)." else "Nobody fought them before the wall."
        val standing = enemies.filter { it.health > 0 }
        val wall = defenders(ctx, faction)
        val party = wall.map { (h, w) -> withStats(GuildOps.fighter(ctx, h, w, Loadout.Field.HOME, wall.map { it.first.id }), prep?.partyStats.orEmpty()) }
        val rules = if (trait?.id == Depth.LONG_ASSAULT) cfg.fight.copy(maxRounds = cfg.fight.maxRounds + cfg.fight.maxRounds / 2) else cfg.fight
        val fight = if (party.isNotEmpty() && standing.isNotEmpty())
            Fight.resolve(FightSetup("Siege of Emberfall, day ${ctx.day}", party, standing, GuildOps.relicRules(ctx, GuildOps.relicIds(ctx)), Objective(), Posture.BALANCED, rules, ctx.rng(RngStream.COMBAT).nextLong(), combat.unitById))
        else null
        fight?.let { com.tinyblacksmith.core.crafting.Interactions.witness(ctx, it) }
        if (fight != null) Agency.afterFight(ctx, wall.map { it.first.id }, fight, fight.outcome == FightOutcome.WON || fight.outcome == FightOutcome.TIMED_OUT, siege = true)
        val left = when {
            standing.isEmpty() -> 0
            fight == null -> standing.sumOf { it.health }
            else -> fight.actors.filter { it.side == Side.ENEMY && !it.summoned }.sumOf { it.health }
        }
        val share = left * 100 / maxOf(1, enemyTotal)   // percent of the raid's whole strength still standing at the end
        val downed = fight?.actors?.filter { it.side == Side.PARTY && it.downed && !it.summoned }?.map { HeroId(it.key) }.orEmpty()
        val verdict = when {
            standing.isEmpty() || fight?.outcome == FightOutcome.WON -> if (downed.isEmpty()) SiegeVerdict.HELD else SiegeVerdict.HELD_AT_A_COST
            fight?.outcome == FightOutcome.TIMED_OUT -> SiegeVerdict.HELD_AT_A_COST
            else -> SiegeVerdict.BREACHED
        }
        val max = config.maxForgeDamagePerSiege
        val forgeDamage = when {
            standing.isEmpty() -> 0
            fight == null -> maxOf(cfg.damageBreachMin, max * cfg.damageUndefendedPercent / 100 * share / 100)
            fight.outcome == FightOutcome.WON -> cfg.damageHeldPerDowned * downed.size
            fight.outcome == FightOutcome.TIMED_OUT -> maxOf(1, max * cfg.damageCostPercent / 100 * share / 100) + cfg.damageHeldPerDowned * downed.size
            else -> maxOf(cfg.damageBreachMin, max * cfg.damageBreachPercent / 100 * share / 100)
        }.coerceIn(0, max)
        val won = verdict != SiegeVerdict.BREACHED

        ctx.town = ctx.town.copy(integrity = ctx.town.integrity - forgeDamage, championIds = wall.map { it.first.id }, nextSiegeDay = ctx.town.nextSiegeDay + config.siegeInterval,
            armory = (ctx.town.armory * (1.0 - config.armorySiegeWear)).toInt())
        if (forgeDamage > 0) ctx.emit(EventType.FORGE_DAMAGED, 7, "The forge took $forgeDamage damage in the siege.", data = mapOf("damage" to forgeDamage.toString()))
        val names = wall.joinToString(", ") { it.first.fullName }.ifEmpty { "no defenders" }
        val data = mapOf("verdict" to verdict.name, "left" to share.toString()) + (trait?.let { mapOf("trait" to it.id) } ?: emptyMap())
        val told = fight?.highlights?.firstOrNull()?.text?.let { " $it" }.orEmpty()
        val event = when (verdict) {
            SiegeVerdict.HELD -> ctx.emit(EventType.SIEGE_WON, 9, "Emberfall repelled $attacker! On the wall: $names.$told", wall.map { it.first.id.value }, data)
            // A town that holds and a forge that is hit: both are true, and the report says both (spec 1.2).
            SiegeVerdict.HELD_AT_A_COST -> ctx.emit(EventType.SIEGE_WON, 9, "Emberfall held against $attacker, at a cost: the forge was hit${if (downed.isNotEmpty()) " and ${downed.size} of the defenders went down" else ""}. On the wall: $names.$told", wall.map { it.first.id.value }, data)
            SiegeVerdict.BREACHED -> ctx.emit(EventType.SIEGE_LOST, 9, "${attacker.replaceFirstChar { it.uppercase() }} broke through ($names).$told", wall.map { it.first.id.value }, data)
        }
        // A note reads after the attacker's name: "Mira's weapon (Bronze) deals 5 damage to X" becomes "deals 5 damage to X with the weapon (Bronze)".
        fun note(text: String, name: String?): String {
            val own = text.removePrefix("$name's ")
            if (own != text && " deals " in own) return "deals ${own.substringAfter(" deals ")} with ${own.substringBefore(" deals ").let { if (it.startsWith("weapon")) "the $it" else it }}"
            return own.removePrefix("$name ")
        }
        // The wall as the day's siege replay: the blows of the fight that landed, in order, then what reached the forge.
        val rounds = fight?.events.orEmpty().filter { it.kind == EventKind.DAMAGE && it.source != null && it.target != null }.take(14).map { e ->
            val src = fight!!.actor(e.source!!); val dst = fight.actor(e.target!!)
            CombatRound(src?.name ?: "?", dst?.name ?: "?", e.amount, note(e.text.removeSuffix("."), src?.name), e.source.takeIf { src?.side == Side.PARTY && src.summoned.not() })
        } + CombatRound(faction.siegeName, "the forge", forgeDamage, if (won) "is driven off" else "breaks through")
        ctx.replays += CombatReplay("Siege of Emberfall, day ${ctx.day}", ctx.day, rounds, when (verdict) { SiegeVerdict.HELD -> "Town held"; SiegeVerdict.HELD_AT_A_COST -> "Held at a cost"; SiegeVerdict.BREACHED -> "Defenses broken" })

        // The defenders, each once.
        val rout = !won && share >= cfg.routRemainingPercent
        val firstDown = fight?.events?.firstOrNull { it.kind == EventKind.DOWNED && it.target?.let { t -> wall.any { w -> w.first.id.value == t } } == true }?.target
        for ((h, w) in wall) {
            val a = fight?.actor(h.id.value)
            val hero = ctx.hero(h.id)
            val member = ctx.guild!!.member(h.id)
            if (w != null) ctx.weapons[w.id]?.let { ww ->
                val wear = (config.wearPerSiege * (trait?.wearMultiplier ?: 1.0)).roundToInt() + (if (a?.fractured == true) cfg.fractureWear else 0)
                ctx.updateWeapon(ww.copy(condition = maxOf(0, ww.condition - wear), siegesDefended = ww.siegesDefended + (if (won) 1 else 0), fame = ww.fame + (if (won) config.combat.siegeFame else 0), victories = ww.victories + (if (won) 1 else 0)))
                if (won) ctx.addWeaponHistory(w.id, "SIEGE", "Defended Emberfall in ${hero.fullName}'s hands on day ${ctx.day}.", listOf(hero.id.value))
            }
            if (a != null && a.downed && rout && h.id.value == firstDown) {
                val rng = ctx.rng(RngStream.COMBAT)
                val recovered = rng.chance(config.weaponFates.wallsRecoveryChance)
                GuildOps.loanOf(ctx, h.id)?.let { loan ->
                    if (recovered) { ctx.updateWeapon(loan.copy(location = WeaponLocation.Storage)); ctx.addWeaponHistory(loan.id, "RECOVERED", "Recovered from the wall where ${hero.fullName} fell.", listOf(h.id.value)) }
                    else { ctx.updateWeapon(loan.copy(location = WeaponLocation.Lost(ctx.day, GuildOps.LOST_TAKEN))); ctx.addWeaponHistory(loan.id, "SEIZED", "Taken from the wall where ${hero.fullName} fell.", listOf(h.id.value)); Stories.taken(ctx, loan.id, factionId) }
                }
                val died = Battle.kill(ctx, hero, "died defending the walls", recovered, weaponSeized = !recovered && rng.chance(config.weaponFates.wallsSeizureChance))
                if (member != null) GuildOps.forget(ctx, h.id)
                ctx.field += FieldResult(hero.id, hero.fullName, FieldOutcome.FELL_AT_THE_WALL, faction.siegeName, factionId = factionId, eventIds = listOf(event.id, died.id))
                continue
            }
            val health = when {
                a == null -> hero.health
                a.downed -> cfg.downedHealth
                else -> Loadout.heroHealth(a.health, a.maxHealth)
            }.coerceAtLeast(config.heroDeathHealthFloor + 1)
            ctx.updateHero(hero.copy(health = health, fame = hero.fame + (if (won) config.combat.siegeFame else 0), lastActivity = HeroActivity.DEFEND))
            if (won) { Heroes.grantXp(ctx, ctx.hero(h.id), config.siegeXp); Heroes.fulfilAmbition(ctx, h.id, Ambition.DEFENDER) }
            if (a?.downed == true && member != null) GuildOps.updateMember(ctx, h.id) { it.copy(wound = Wound(WoundKind.INJURED, ctx.day + 1 + cfg.injuredDays, "Went down on the wall on day ${ctx.day}."), timesDowned = it.timesDowned + 1) }
            if (won) {
                if (w != null) ctx.milestone("CHAMPION_ARMED", "A defender held the wall with a weapon from this forge: ${w.name}.")
                ctx.field += FieldResult(h.id, h.fullName, FieldOutcome.HELD_THE_WALL, faction.siegeName, factionId = factionId, eventIds = listOf(event.id))
            }
        }
        // What decided it, from the night's own record.
        val notes = ArrayList<String>()
        cat.preparation(scenario.prepSpoiled)?.let { notes += "Your party had spoiled their preparation: ${it.name.lowercase()}. It was not used." }
        prep?.let { notes += "They came with what the scouts reported: ${it.name.lowercase()}. ${it.description}" }
        notes += absent
        if (wall.size < cfg.defenders) notes += "Only ${wall.size} of ${cfg.defenders} places on the wall were filled."
        fight?.actors?.filter { it.side == Side.ENEMY && !it.downed && !it.summoned }?.forEach { a ->
            val mended = fight.events.count { it.kind == EventKind.HEALED && it.source == a.key && it.target != a.key }
            if (mended > 0) notes += "${a.name} was still standing at the end and had healed the other enemies $mended ${if (mended == 1) "time" else "times"}."
        }
        notes += if (forgeDamage > 0) "The forge lost $forgeDamage health." else "The forge took no damage."
        // The lead felled by a defender's own hand is that weapon's deed, and a warlord broken on the wall leaves Heartfire.
        val beatenOutright = fight?.outcome == FightOutcome.WON
        if (beatenOutright && (scenario.charter || warlord)) {
            fight!!.events.firstOrNull { it.kind == EventKind.DOWNED && it.target == "e1" }?.source?.let { key -> wall.firstOrNull { it.first.id.value == key } }?.let { (h, w) ->
                if (w != null && ctx.weapons[w.id]?.location !is WeaponLocation.Lost) {
                    val legend = ctx.config.depth.legend
                    com.tinyblacksmith.core.crafting.Legends.deed(ctx, w.id, if (scenario.charter) legend.renownCharter else legend.renownWarlord, "${h.fullName} defeated ${leadUnit.name} with it on the wall of Emberfall.", listOf(h.id.value))
                    notes += "${h.fullName} defeated ${leadUnit.name} with ${w.name}."
                }
            }
            if (warlord) com.tinyblacksmith.core.crafting.Legends.gainHeartfire(ctx, 1, "${leadUnit.name} was defeated on the wall and left Heartfire behind.")
        }
        // The one who carried the smith's weapon came with them. Felled on a wall that held, the weapon is home.
        if (nemesis != null && nemesisKey != null) {
            val blade = ctx.weapons[nemesis.weaponId]
            if (won && fight?.actor(nemesisKey)?.downed == true && blade != null) {
                ctx.updateWeapon(blade.copy(location = WeaponLocation.Storage))
                ctx.addWeaponHistory(blade.id, "RECOVERED", "Taken back on the wall from ${nemesis.name}, who had carried it against its own forge.")
                ctx.emit(EventType.WEAPON_RECOVERED, 7, "${blade.name} is back at the forge. ${nemesis.name} carried it to the wall and was defeated there.", listOf(blade.id.value), mapOf(WeaponFate.KEY to WeaponFate.RECOVERED.name))
                Stories.recovered(ctx, blade.id)
                notes += "${nemesis.name} came carrying ${blade.name} and was defeated. It is back at the forge."
            } else if (blade != null) notes += "${nemesis.name} fought on their side with ${blade.name}, a weapon of this forge."
        }
        ctx.guild = ctx.guild!!.copy(lastSiege = SiegeReport(ctx.day, wall.map { it.first.id }, fight?.let { Missions.compact(it) }, verdict, forgeDamage, outer, notes), reserved = emptyList())

        if (won) {
            ctx.town = ctx.town.copy(siegesSurvived = ctx.town.siegesSurvived + 1)
            ctx.factions[factionId] = factionState.copy(pressure = maxOf(0, factionState.pressure - config.siegeWinPressureDrop - (if (warlord) config.warlordPressureDrop else 0)))
            val beaten = fight?.outcome == FightOutcome.WON || standing.isEmpty()
            if (warlord && beaten) {
                ctx.earn(IncomeKind.TRIBUTE, config.warlordTribute)
                ctx.emit(EventType.MILESTONE, 6, "${leadUnit.name} was thrown back from the walls. The town paid the smith ${config.warlordTribute} gold in thanks.", data = mapOf("tribute" to config.warlordTribute.toString()))
                ctx.milestone("WARLORD_DEFEATED", "Emberfall defeated a warlord at the walls.")
            }
            ctx.milestone("SIEGE_SURVIVED", "Emberfall survived its first siege.")
            if (scenario.charter) Stories.charterSiege(ctx, leadUnit.name, beaten)
            Battle.offerBlessing(ctx)
        } else {
            ctx.town = ctx.town.copy(siegesLost = ctx.town.siegesLost + 1)
        }
        if (ctx.town.integrity <= 0) {
            ctx.town = ctx.town.copy(integrity = 0)
            ctx.phase = Phase.ENDED
            ctx.endCause = "The forge fell to the ${faction.siegeName} on day ${ctx.day}."
            ctx.emit(EventType.FORGE_DESTROYED, 10, "The forge has fallen. Emberfall's smith is no more.")
        } else Battle.scheduleNext(ctx)
    }
}
