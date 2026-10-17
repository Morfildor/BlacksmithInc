package com.tinyblacksmith.core.content

import com.tinyblacksmith.core.combat.*
import com.tinyblacksmith.core.combat.Action.Damage
import com.tinyblacksmith.core.combat.Action.Give
import com.tinyblacksmith.core.combat.Action.Guard
import com.tinyblacksmith.core.combat.Action.Heal
import com.tinyblacksmith.core.combat.Action.Take
import com.tinyblacksmith.core.combat.Amount.Fixed
import com.tinyblacksmith.core.combat.Amount.Strike

/**
 * The living heroes siege bestiary (content bible 10 to 13, final design 12 and Appendix A): 48 enemies of three hosts
 * and the 36 armies they march in. The bible decides names, identities and what each enemy does; Appendix A decides how
 * the live engine does it. Classic eras and the old guild siege never field these units.
 *
 * Conversion, applied once ([converted], [attackRange], [armorPoints]): bible Health and Attack times 0.4, rounded to the
 * nearest integer (at least 1 when the bible's is above 0). Attack is a range of 90% to 110% of that, each end rounded to
 * the nearest integer. Armor percent becomes starting Armor points, 5% to 1 point. A siege scales the result once
 * (`Loadout.enemy`). Structural hits are already on the 100 forge health scale and are not converted.
 *
 * Elements (final design 12.2, 12.3). Every unit starts from its host's faction affinities, those of the
 * `FactionDef` (Ashclaw and Embermaw weak to Frost and resisting Fire, Hollowbound weak to Sun and resisting Grave), and
 * the faction's tick resistance (Hollowbound half Bleed, Embermaw half Burn). An element its card states overrides that
 * field: a unit weak to Fire loses the faction's Fire resistance and its half Burn. Immunities are the card's own.
 *
 * Line order (final design 12.4): the members of an army stand front first, the defining specialist in the second or
 * third place so a charter siege, which keeps three of them, keeps it. The order differs from the bible's list where its
 * screen comes first in the field.
 */
object Bestiary {
    /** A standard skeleton or zombie: what a Necromancer raises. */
    const val RAISABLE = "raisable"
    /** A standard undead that is not a spirit: what the Bellkeeper raises. */
    const val STANDARD_UNDEAD = "standard_undead"
    /** A back-rank unit: the one a Shieldbearer covers first. */
    const val SPECIALIST = "specialist"
    /** A beast or a pack-eligible unit: what a Beastcaller gives Armor to. */
    const val CALLED = "called"
    /** A unit whose prepared ability attacks the defences (`SiegeFight.STRUCTURAL`, `Hooks.powderScout`). */
    const val STRUCTURAL = "structural"

    private const val UNDEAD = CombatContent.TAG_UNDEAD
    private const val LEADER = CombatContent.TAG_LEADER
    private const val ELITE = CombatContent.TAG_ELITE
    private val AXE = "family:" + LaunchContent.AXE.value
    private val SILVER = "metal:" + LaunchContent.SILVER.value

    /** The three hosts and the existing faction each marches for. Their affinities are the faction's (`BestiaryTest` holds them equal). */
    enum class Host(val title: String, val weakTo: Element?, val resists: Element?, val tickResist: Map<Stat, Int>) {
        IRON_BANNER("Iron Banner", Element.FROST, Element.FIRE, emptyMap()),
        PALE_PROCESSION("Pale Procession", Element.SUN, Element.GRAVE, mapOf(Stat.BLEED to 50)),
        THORN_COVENANT("Thorn Covenant", Element.FROST, Element.FIRE, mapOf(Stat.BURN to 50));

        val factionId: com.tinyblacksmith.core.model.FactionId get() = when (this) {
            IRON_BANNER -> LaunchContent.ASHCLAW
            PALE_PROCESSION -> LaunchContent.HOLLOWBOUND
            THORN_COVENANT -> LaunchContent.EMBERMAW
        }
    }

    fun converted(bible: Int): Int = if (bible <= 0) 0 else maxOf(1, (bible * 4 + 5) / 10)
    fun attackRange(bible: Int): IntRange = converted(bible).let { a -> if (a == 0) 0..0 else maxOf(1, (a * 9 + 5) / 10)..maxOf(1, (a * 11 + 5) / 10) }
    fun armorPoints(percent: Int): Int = percent / 5

    private class Built(val unit: UnitDef, val profile: EnemyProfile)

    private fun e(id: String, name: String, trigger: Trigger, actions: List<Action>, limit: Limit, description: String, conditions: List<Condition> = emptyList()) =
        EffectDef(id, name, trigger, actions, limit, description, conditions)

    private fun startArmor(id: String, n: Int) = e("${id}_armor", "Armor", Trigger(EventKind.ROUND_START, Who.ANY), listOf(Guard(Aim.Self, Fixed(n))), Limit(perFight = 1),
        "This enemy starts each fight with $n Armor.")

    private fun attack(element: Element? = null, telegraph: String? = null, name: String = if (element == Element.GRAVE) "Grave attack" else if (element == Element.FIRE) "Fire attack" else "Attack") =
        Move("attack", name, Cadence.Always, listOf(Damage(Aim.Foe, Strike(), element)), telegraph)
    private fun waits(telegraph: String) = Move("hold", "Holds its place", Cadence.Always, emptyList(), telegraph)
    private fun move(id: String, name: String, cadence: Cadence, telegraph: String, vararg actions: Action) = Move(id, name, cadence, actions.toList(), telegraph)
    private fun prepared(id: String, name: String, cadence: Cadence, telegraph: String, channel: ChannelDef) = Move(id, name, cadence, emptyList(), telegraph, channel)

    private fun enemy(
        id: String, name: String, host: Host, health: Int, attack: Int, armor: Int, cost: Int, siege: Int,
        moves: List<Move>, job: String, counter: String, forecast: String,
        tags: Set<String> = emptySet(), passives: List<EffectDef> = emptyList(), startingArmor: Int = armorPoints(armor),
        weakTo: Element? = null, resists: Element? = null, immune: Set<Stat> = emptySet(), surviveOnce: Boolean = false,
        bonusFrom: List<SourceBonus> = emptyList(), recover: Recovery? = null, pack: Int = 0, adaptation: String = "",
    ): Built {
        val weak = weakTo ?: host.weakTo
        val resist = (resists ?: host.resists).takeIf { it != weak }
        val ticks = host.tickResist.filterKeys { s -> s !in immune && !(s == Stat.BURN && weak == Element.FIRE) }
        val range = attackRange(attack)
        val rules = (if (startingArmor > 0) listOf(startArmor(id, startingArmor)) else emptyList()) + passives
        val unit = UnitDef(id, name, KitDef(id, name, moves, rules), health = converted(health), strikeMin = range.first, strikeMax = range.last, tags = tags,
            weakTo = weak, resists = resist, tickResist = ticks, immune = immune, surviveOnce = surviveOnce, bonusFrom = bonusFrom, recover = recover, packBonusPercent = pack)
        return Built(unit, EnemyProfile(id, host.factionId, host.title, cost, siege, health, attack, armor, job, counter, forecast,
            status = if (adaptation.isEmpty()) "implemented" else "adapted", adaptation = adaptation))
    }

    private val IB = Host.IRON_BANNER
    private val PP = Host.PALE_PROCESSION
    private val TC = Host.THORN_COVENANT

    private fun interruptible(what: String, once: Boolean = true) = if (once) "$what An Interrupt stops it for good. Once per fight." else what

    // ---- Iron Banner (Ashclaw Raiders): organized protection, timed breaches, pressure on the wounded ----
    private val ironBanner = listOf(
        enemy("ib_goblin_skirmisher", "Goblin Skirmisher", IB, 24, 7, 0, cost = 2, siege = 1, startingArmor = 2,
            moves = listOf(attack()),
            job = "Climbs the rubble and crowds the defenders while the specialists work.",
            counter = "Its Armor is there only at the start. A second hit, or an attack that hits several enemies, defeats it.",
            forecast = "Goblin climbers are gathering below the wall."),
        enemy("ib_goblin_sapper", "Goblin Sapper", IB, 26, 5, 0, cost = 4, siege = 2, tags = setOf(SPECIALIST, STRUCTURAL),
            moves = listOf(
                prepared("powder_charge", "Powder Charge", Cadence.Actions(2),
                    interruptible("On its 2nd action, it starts Preparing a powder charge: 4 damage to the defences on its next action."),
                    ChannelDef("powder_charge", "Powder Charge", uses = 1, onInterrupt = OnInterrupt.SPENT, payload = listOf(Action.Structural(4)), fallback = listOf(Damage(Aim.Foe, Strike())))),
                attack()),
            job = "Destroys the gate while the others cover it.",
            counter = "Interrupt the charge or defeat the sapper before it lands. Its charge lands only once.",
            forecast = "Powder sappers are preparing a breach."),
        enemy("ib_wolf_rider", "Goblin Wolf Rider", IB, 38, 9, 0, cost = 4, siege = 2,
            moves = listOf(
                move("run_down", "Run down", Cadence.Actions(1, 4), "On its 1st and 4th actions, attacks the defender with the least Health, past a Guardian.", Damage(Aim.WeakestFoe, Strike())),
                attack()),
            job = "Rushes an opening and harries the wounded defenders.",
            counter = "It has no Armor and no healing. Healing and Armor on your weakest defender blunt its charge.",
            forecast = "Wolf riders will strike at wounded defenders."),
        enemy("ib_hobgoblin_shield", "Hobgoblin Shieldbearer", IB, 55, 7, 25, cost = 5, siege = 1,
            moves = listOf(
                move("cover", "Cover an ally", Cadence.Actions(1, 3), "On its 1st and 3rd actions, gives 3 Armor to its weakest ally (lowest Health percentage, a specialist behind the line first) instead of attacking. It never covers itself.",
                    Guard(Aim.WeakestOtherAlly(SPECIALIST), Fixed(3))),
                attack()),
            job = "Escorts an archer, a sapper or an officer.",
            counter = "Defeat it first: then nobody shields the specialists. Spears and Bleed get past its Armor.",
            forecast = "Shieldbearers are escorting the enemy's specialists.",
            adaptation = "It gives an ally 3 Armor instead of taking 30% of the ally's damage (final design Appendix A: damage redirection is deferred)."),
        enemy("ib_hobgoblin_archer", "Hobgoblin Archer", IB, 30, 10, 10, cost = 4, siege = 1, tags = setOf(SPECIALIST),
            moves = listOf(
                move("covering_fire", "Covering fire", Cadence.Actions(2, 5), "On its 2nd and 5th actions, shoots the back defender, past the front.", Damage(Aim.BackFoe, Strike())),
                attack()),
            job = "Keeps up disciplined covering fire.",
            counter = "Little Health and light Armor. Bows and Storm weapons reach it at the back of their line.",
            forecast = "Hobgoblin archers are taking positions."),
        enemy("ib_war_drummer", "War Drummer", IB, 34, 4, 5, cost = 5, siege = 2, tags = setOf(SPECIALIST),
            moves = listOf(
                move("war_drums", "War drums", Cadence.Actions(2, 5), "On its 2nd and 5th actions, gives every other attacker 2 Armor, then attacks for 50% of its Attack.",
                    Guard(Aim.OtherAllies, Fixed(2)), Damage(Aim.Foe, Strike(50))),
                attack()),
            job = "Keeps the assault in step.",
            counter = "Its own attacks are weak. Defeat it and its drums stop.",
            forecast = "War drums are keeping the raiders in step."),
        enemy("ib_orc_breaker", "Orc Gatebreaker", IB, 58, 13, 10, cost = 6, siege = 2,
            moves = listOf(
                move("crushing_blow", "Crushing blow", Cadence.Actions(2, 5), "On its 2nd and 5th actions, removes up to 4 Armor from the front defender, then attacks.",
                    Take(Stat.GUARD, Aim.FrontFoe, Fixed(4)), Damage(Aim.Foe, Strike())),
                attack()),
            job = "Batters defenders and doors.",
            counter = "It cannot protect itself. Armor gained every round replaces what its maul removes, and a quick defeat ends the danger.",
            forecast = "Heavy mauls have been sighted among the raiders."),
        enemy("ib_orc_shaman", "Orc War Shaman", IB, 32, 8, 0, cost = 5, siege = 3, tags = setOf(SPECIALIST),
            moves = listOf(
                move("charm", "Binding charm", Cadence.Actions(2, 5), "On its 2nd and 5th actions, interrupts the back defender if that defender is Preparing an ability, else puts 1 Chill on that defender. Then it deals 50% of its Attack to that defender as grave damage.",
                    Action.Interrupt(Aim.BackFoe, otherwise = listOf(Give(Stat.CHILL, Aim.BackFoe))), Damage(Aim.BackFoe, Strike(50), Element.GRAVE)),
                attack()),
            job = "Disrupts the defender at the back of the line.",
            counter = "Little Health and no healing. Concentrated attacks defeat it quickly.",
            forecast = "A war shaman carries charms against the back of your line."),
        enemy("ib_bugbear", "Bugbear Ambusher", IB, 44, 11, 5, cost = 5, siege = 2,
            moves = listOf(
                move("ambush", "Ambush", Cadence.Actions(1), "On its 1st action, attacks the back defender for 125% of its Attack, past the front.", Damage(Aim.BackFoe, Strike(125))),
                attack()),
            job = "Climbs a weak point and strikes behind the line.",
            counter = "Its opening attack comes once, on its 1st action. Armor and Health on the back defender absorb it.",
            forecast = "Large climbers are moving toward the side wall."),
        enemy("ib_ogre", "Ogre Ram-Bearer", IB, 95, 16, 5, cost = 9, siege = 3, tags = setOf(STRUCTURAL),
            moves = listOf(
                prepared("gate_rush", "Gate Rush", Cadence.Actions(2),
                    interruptible("On its 2nd action, it starts Preparing a gate rush: 5 damage to the defences on its next action."),
                    ChannelDef("gate_rush", "Gate Rush", uses = 1, onInterrupt = OnInterrupt.SPENT, payload = listOf(Action.Structural(5)), fallback = listOf(Damage(Aim.Foe, Strike())))),
                attack()),
            job = "A battering ram that walks.",
            counter = "Slow and lightly armored. Interrupt the rush, or bring it down before the rush lands.",
            forecast = "An ogre is carrying a ram toward the gate."),
        enemy("ib_troll", "Troll Mercenary", IB, 78, 12, 0, cost = 9, siege = 3, recover = Recovery(2),
            moves = listOf(attack(telegraph = "Before each of its actions, it heals 2 Health, unless a Fire hit or Burn damaged it since its last action. Then it attacks.")),
            job = "Absorbs the defenders' effort and keeps advancing.",
            counter = "Fire hits and Burn stop its healing. Concentrated heavy attacks also outpace it.",
            forecast = "A troll marches with the mercenaries. Fire will stop it healing.",
            adaptation = "Acid does not exist in this game: only Fire hits and Burn stop its healing."),
        enemy("ib_deserter", "Deserter Crossbowman", IB, 35, 11, 10, cost = 4, siege = 1, tags = setOf(SPECIALIST),
            moves = listOf(
                move("heavy_bolt", "Heavy bolt", Cadence.Actions(2, 4, 6, 8, 10, 12), "On every second action, shoots a bolt that ignores up to 4 of the target's Armor.",
                    Damage(Aim.Foe, Strike(), ignoreArmor = 4)),
                attack()),
            job = "Gives cover fire that gets past Armor.",
            counter = "Ordinary Health. Armor beyond the 4 its bolt ignores still blocks.",
            forecast = "Deserters are bringing heavy crossbows."),
    )

    private val built: List<Built> = ironBanner

    val units: List<UnitDef> = built.map { it.unit }
    val profiles: List<EnemyProfile> = built.map { it.profile }
    val armies: List<ArmyDef> = emptyList()
}
