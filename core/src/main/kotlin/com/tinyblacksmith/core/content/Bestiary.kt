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
            counter = "It moves slowly and has little Armor. Interrupt the rush, or bring it down before the rush lands.",
            forecast = "An ogre is carrying a ram toward the gate."),
        enemy("ib_troll", "Troll Mercenary", IB, 78, 12, 0, cost = 9, siege = 3, recover = Recovery(2),
            moves = listOf(attack(telegraph = "It attacks.")),
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

    // ---- Pale Procession (Hollowbound): endurance, one return, weaker healing, pressure on fragile defenders ----
    private val bones = setOf(Stat.POISON, Stat.BLEED)
    private val axeWeak = listOf(SourceBonus(AXE, 30))
    private val paleProcession = listOf(
        enemy("pp_skeleton_guard", "Skeleton Spearman", PP, 30, 7, 10, cost = 3, siege = 1, tags = setOf(UNDEAD, RAISABLE, STANDARD_UNDEAD),
            immune = bones, bonusFrom = axeWeak,
            moves = listOf(attack(telegraph = "It attacks.")),
            job = "Tires the defenders and holds a narrow breach.",
            counter = "Fragile bones. Axe attacks, or ordinary concentrated attacks.",
            forecast = "Skeleton spearmen are marching in ranks."),
        enemy("pp_skeleton_archer", "Skeleton Archer", PP, 23, 9, 0, cost = 3, siege = 1, tags = setOf(UNDEAD, RAISABLE, STANDARD_UNDEAD, SPECIALIST),
            immune = bones, bonusFrom = axeWeak,
            moves = listOf(attack(telegraph = "It shoots.")),
            job = "Tires the defenders with volleys from behind.",
            counter = "Very little Health. Bows and Storm weapons reach it at the back of their line.",
            forecast = "Bone archers are assembling behind the dead."),
        enemy("pp_zombie", "Zombie", PP, 54, 7, 0, cost = 3, siege = 1, tags = setOf(UNDEAD, RAISABLE, STANDARD_UNDEAD),
            immune = setOf(Stat.POISON), surviveOnce = true,
            moves = listOf(attack(telegraph = "It attacks.")),
            job = "Soaks up damage and drags the defence out.",
            counter = "Its attacks are weak. A second hit after its survival, or a Fire or Sun hit, finishes it.",
            forecast = "The dead are advancing slowly, but some refuse to fall."),
        enemy("pp_ghoul", "Ghoul", PP, 36, 9, 0, cost = 5, siege = 2, tags = setOf(UNDEAD, STANDARD_UNDEAD),
            moves = listOf(
                move("rotting_claw", "Rotting claw", Cadence.Actions(2, 5), "On its 2nd and 5th actions, attacks and puts 2 Poison on the defender it attacks.",
                    Damage(Aim.Foe, Strike()), Give(Stat.POISON, Aim.Foe, Fixed(2))),
                attack()),
            job = "Goes for the wounded defenders.",
            counter = "Little Health and no Armor. Healing and Purifying weapons remove its Poison.",
            forecast = "Ghouls are prowling behind the corpse line. Their claws poison the wounded."),
        enemy("pp_wight", "Wight", PP, 55, 11, 15, cost = 7, siege = 3, tags = setOf(UNDEAD, STANDARD_UNDEAD),
            moves = listOf(
                move("life_drain", "Life drain", Cadence.Actions(2, 5), "On its 2nd and 5th actions, attacks and heals itself for 40% of the Health damage it deals, at most 3. Damage that Armor blocks heals it nothing.",
                    Damage(Aim.Foe, Strike(), drainPercent = 40, drainCap = 3)),
                attack()),
            job = "Keeps a disciplined undead assault going.",
            counter = "Armor on your defenders denies its drain. Concentrated attacks and Sun weapons bring it down.",
            forecast = "An armored wight leads the dead. Its weapon restores it."),
        enemy("pp_necromancer", "Necromancer", PP, 34, 8, 0, cost = 7, siege = 3, tags = setOf(SPECIALIST),
            moves = listOf(
                prepared("raise_dead", "Raise Dead", Cadence.All(listOf(Cadence.ActionsFrom(2), Cadence.AllyDownedEligible(RAISABLE))),
                    interruptible("From its 2nd action, once a skeleton or a zombie has fallen, it starts Preparing to raise the dead. On its next action, the first of them to fall stands again with 35% of its Health and acts from the next round."),
                    ChannelDef("raise_dead", "Raise Dead", uses = 1, onInterrupt = OnInterrupt.SPENT, payload = listOf(Action.Return(RAISABLE, 35)), fallback = listOf(Damage(Aim.Foe, Strike(), Element.GRAVE)), fallbackName = "Grave attack")),
                attack(Element.GRAVE)),
            job = "Returns one fallen soldier to the assault.",
            counter = "Fragile, and an Interrupt costs it the raising for good. Or defeat the returned soldier a second time.",
            forecast = "A necromancer will try to raise the fallen."),
        enemy("pp_acolyte", "Grave Acolyte", PP, 29, 5, 0, cost = 5, siege = 2, tags = setOf(SPECIALIST),
            moves = listOf(
                move("tend_the_dead", "Tend the dead", Cadence.Actions(2, 5), "On its 2nd and 5th actions, heals its most wounded undead ally (lowest Health percentage, spirits excluded) by 4 instead of attacking.",
                    Heal(Aim.WeakestAllyTagged(UNDEAD, exclude = Tags.SPIRIT), Fixed(4))),
                attack(Element.GRAVE)),
            job = "Keeps the bound dead standing.",
            counter = "Little Health. Defeat it first, or put Poison on the undead it tends.",
            forecast = "A lantern-bearer is tending the dead."),
        enemy("pp_ogre_zombie", "Ogre Zombie", PP, 110, 14, 0, cost = 8, siege = 3, tags = setOf(UNDEAD, STANDARD_UNDEAD), immune = setOf(Stat.POISON),
            moves = listOf(
                move("beam_sweep", "Beam sweep", Cadence.Actions(3, 6), "On its 3rd and 6th actions, hits the first 2 defenders in the line for 70% of its Attack each, past a Guardian.",
                    Damage(Aim.FrontFoes(2), Strike(70))),
                attack(telegraph = "It attacks.")),
            job = "An immovable mass at the gate.",
            counter = "Very slow, with no Armor and no healing. Concentrated attacks work.",
            forecast = "An ogre corpse carries a beam toward the gate."),
        enemy("pp_banshee", "Banshee", PP, 42, 8, 0, cost = 7, siege = 3, tags = setOf(UNDEAD, Tags.SPIRIT, SPECIALIST),
            moves = listOf(
                prepared("wail", "Wail", Cadence.Actions(2),
                    interruptible("On its 2nd action, it starts Preparing a wail. On its next action, the wail deals 50% of its Attack as grave damage to every defender and puts 1 Chill on each."),
                    ChannelDef("wail", "Wail", uses = 1, onInterrupt = OnInterrupt.SPENT, payload = listOf(Damage(Aim.AllFoes, Strike(50), Element.GRAVE), Give(Stat.CHILL, Aim.AllFoes)),
                        fallback = listOf(Damage(Aim.Foe, Strike(), Element.GRAVE)), fallbackName = "Grave attack")),
                attack(Element.GRAVE)),
            job = "Weakens the whole party with a dreadful cry.",
            counter = "Fragile. Interrupt the wail, defeat it first, or heal through the one cry.",
            forecast = "A mourning spirit is preparing a dreadful cry."),
        enemy("pp_wraith", "Wraith", PP, 47, 10, 10, cost = 7, siege = 3, tags = setOf(UNDEAD, Tags.SPIRIT), resists = Element.FROST,
            moves = listOf(
                move("through_the_breach", "Through the breach", Cadence.Actions(1, 4), "On its 1st and 4th actions, attacks the back defender with grave damage, past the front.",
                    Damage(Aim.BackFoe, Strike(), Element.GRAVE)),
                attack(Element.GRAVE)),
            job = "Slips through a breach and goes for a defender at the back.",
            counter = "Modest Health and no healing. Armor on the back defender and concentrated attacks work. It resists Frost.",
            forecast = "A wraith has been seen drifting above the graves."),
        enemy("pp_black_knight", "Undead Black Knight", PP, 73, 12, 30, cost = 8, siege = 3, tags = setOf(UNDEAD, STANDARD_UNDEAD), immune = bones,
            moves = listOf(
                move("dark_guard", "Dark guard", Cadence.Actions(2, 5), "On its 2nd and 5th actions, gains 5 Armor, then attacks.", Guard(Aim.Self, Fixed(5)), Damage(Aim.Foe, Strike())),
                attack(telegraph = "It attacks.")),
            job = "Holds the breach while the support works.",
            counter = "Heavy Armor. Spears get past part of it. Defeat its support instead of hitting its plate.",
            forecast = "A black-armored corpse is guarding the procession.",
            adaptation = "It gains Armor and attacks, instead of making the defender who hit it hardest attack it (final design Appendix A)."),
        enemy("pp_grave_rats", "Grave Rat Swarm", PP, 29, 6, 0, cost = 3, siege = 2, bonusFrom = listOf(SourceBonus(Tags.AREA, 30)),
            moves = listOf(move("gnaw", "Gnaw", Cadence.Always, "Attacks the defender with the least Health, past a Guardian. An attack that hits several enemies deals 30% more damage to the swarm.",
                Damage(Aim.WeakestFoe, Strike()))),
            job = "Harasses the wounded defenders beneath the fighting.",
            counter = "Little Health. Attacks that hit several enemies deal 30% more damage to it.",
            forecast = "Grave rats are spreading through the drains."),
    )

    // ---- Thorn Covenant (Embermaw Brood): pack focus, Armor stripped away, Poison, large creatures ----
    private val thornCovenant = listOf(
        enemy("tc_dire_wolf", "Dire Wolf", TC, 41, 10, 0, cost = 4, siege = 1, tags = setOf(Tags.BEAST, Tags.PACK, CALLED), pack = 20,
            moves = listOf(attack(telegraph = "It attacks.")),
            job = "Rushes the breaches and singles out one defender.",
            counter = "No Armor. Defeat one wolf early, or protect the defender the pack is hitting.",
            forecast = "Large wolves are being driven toward the breaches."),
        enemy("tc_giant_spider", "Giant Spider", TC, 35, 7, 0, cost = 4, siege = 2, tags = setOf(Tags.BEAST, CALLED, SPECIALIST), weakTo = Element.FIRE,
            moves = listOf(
                move("web", "Web", Cadence.Actions(2, 5), "On its 2nd and 5th actions, puts 1 Chill on the back defender instead of attacking.", Give(Stat.CHILL, Aim.BackFoe)),
                attack()),
            job = "Fouls the gatehouse and hinders the defenders.",
            counter = "Modest Health. It is weak to Fire, and bows reach it at the back of their line.",
            forecast = "Giant spiders are webbing the outer timbers."),
        enemy("tc_harpy", "Harpy", TC, 31, 8, 0, cost = 5, siege = 2, tags = setOf(SPECIALIST),
            moves = listOf(
                move("shriek", "Shriek", Cadence.Actions(2, 5), "On its 2nd and 5th actions, attacks the back defender for 50% of its Attack and removes up to 4 Armor from that defender.",
                    Damage(Aim.BackFoe, Strike(50)), Take(Stat.GUARD, Aim.BackFoe, Fixed(4))),
                attack()),
            job = "Unsettles the watch from above.",
            counter = "Very little Health. Ordinary attacks reach it.",
            forecast = "Harpies are circling the wall."),
        enemy("tc_minotaur", "Minotaur", TC, 72, 14, 10, cost = 7, siege = 3,
            moves = listOf(
                prepared("horn_charge", "Horn Charge", Cadence.Actions(1),
                    interruptible("On its 1st action, it starts Preparing a horn charge at the front defender. On its next action, the charge deals 150% of its Attack to that defender and removes up to 4 Armor from them."),
                    ChannelDef("horn_charge", "Horn Charge", uses = 1, onInterrupt = OnInterrupt.SPENT, aim = Aim.FrontFoe,
                        payload = listOf(Damage(Aim.EventTarget, Strike(150)), Take(Stat.GUARD, Aim.EventTarget, Fixed(4))), fallback = listOf(Damage(Aim.Foe, Strike())))),
                attack()),
            job = "Charges the gate and breaks the front.",
            counter = "Interrupt the charge, give the front defender Armor before it lands, or bring it down first.",
            forecast = "A minotaur is clearing space for a charge."),
        enemy("tc_manticore", "Manticore", TC, 58, 12, 5, cost = 7, siege = 3, tags = setOf(SPECIALIST),
            moves = listOf(
                move("tail_spikes", "Tail spikes", Cadence.Actions(2, 5), "On its 2nd and 5th actions, hits the first 2 defenders in the line for 65% of its Attack each, past a Guardian.",
                    Damage(Aim.FrontFoes(2), Strike(65))),
                move("spike", "Spike", Cadence.Always, "Otherwise it shoots the back defender, past the front.", Damage(Aim.BackFoe, Strike()))),
            job = "Bombards the defenders from the approach.",
            counter = "Moderate Health and little Armor. Healing and concentrated attacks work.",
            forecast = "A manticore is circling beyond the palisade."),
        enemy("tc_wyvern", "Wyvern", TC, 82, 14, 10, cost = 10, siege = 4,
            moves = listOf(
                move("venom_dive", "Venom dive", Cadence.Actions(2, 5), "On its 2nd and 5th actions, attacks the defender with the least Health, past a Guardian, and puts 2 Poison on that defender.",
                    Damage(Aim.WeakestFoe, Strike()), Give(Stat.POISON, Aim.WeakestFoe, Fixed(2))),
                attack()),
            job = "Dives on the wounded defenders.",
            counter = "Healing and Purifying weapons remove its Poison. Armor and Health on your weakest defender, and concentrated attacks.",
            forecast = "A wyvern has been sighted. Prepare for venomous dives."),
        enemy("tc_treant", "Thornbound Treant", TC, 96, 12, 20, cost = 9, siege = 3, weakTo = Element.FIRE,
            passives = listOf(e("tc_treant_thorns", "Thornbark", Trigger(EventKind.DAMAGE, Who.SELF_TARGET), listOf(Damage(Aim.EventSource, Fixed(3))), Limit(perRound = 1),
                "The first time each round a defender's attack damages the Treant, the Treant deals 3 damage to that defender. Once per round.", listOf(Condition.FromAction, Condition.SourceIsFoe))),
            moves = listOf(attack()),
            job = "Tears down barricades and shelters smaller creatures.",
            counter = "Weak to Fire. A few heavy hits are better than many small ones. Burn and Bleed damage never set off its bark.",
            forecast = "A bound walking tree is approaching. Its bark catches fire."),
        enemy("tc_earth_elemental", "Earth Elemental", TC, 102, 13, 30, cost = 10, siege = 4, bonusFrom = axeWeak,
            moves = listOf(
                move("stone_skin", "Stone skin", Cadence.Actions(2, 5), "On its 2nd and 5th actions, gains 6 Armor, then attacks.", Guard(Aim.Self, Fixed(6)), Damage(Aim.Foe, Strike())),
                attack(telegraph = "It attacks.")),
            job = "Crushes a prepared breach.",
            counter = "Very slow, with no healing. Axe attacks deal 30% more damage, and spears get past part of its Armor.",
            forecast = "Bound stone is moving beneath the hillside."),
        enemy("tc_fire_elemental", "Fire Elemental", TC, 60, 10, 0, cost = 8, siege = 4, weakTo = Element.FROST, resists = Element.FIRE, immune = bones,
            moves = listOf(
                move("searing_touch", "Searing touch", Cadence.Actions(2, 5), "On its 2nd and 5th actions, attacks with fire and puts 2 Burn on the defender it attacks.",
                    Damage(Aim.Foe, Strike(), Element.FIRE), Give(Stat.BURN, Aim.Foe, Fixed(2))),
                attack(Element.FIRE, telegraph = "Its attacks deal fire damage.")),
            job = "Burns the timber and forces the defenders to protect themselves.",
            counter = "Weak to Frost, resists Fire. Armor blocks its Burn.",
            forecast = "A bound flame is advancing with the beasts."),
        enemy("tc_giant_serpent", "Giant Serpent", TC, 65, 11, 5, cost = 6, siege = 3, tags = setOf(Tags.BEAST, CALLED),
            moves = listOf(
                move("coiling_bite", "Coiling bite", Cadence.Actions(2, 5), "On its 2nd and 5th actions, attacks the front defender and puts 1 Chill on that defender.",
                    Damage(Aim.FrontFoe, Strike()), Give(Stat.CHILL, Aim.FrontFoe)),
                attack()),
            job = "Holds the breach and pins one defender's attention.",
            counter = "Little Armor. Concentrated attacks work.",
            forecast = "A giant serpent has been driven toward the gate."),
        enemy("tc_werewolf", "Werewolf", TC, 61, 11, 10, cost = 8, siege = 3, tags = setOf(CALLED), recover = Recovery(2, setOf(SILVER)), bonusFrom = listOf(SourceBonus(SILVER, 30)),
            moves = listOf(attack(telegraph = "It attacks.")),
            job = "Rushes the defenders and keeps itself going through a long fight.",
            counter = "Silver weapons, Fire hits and Burn stop its healing. Silver weapons deal 30% more damage to it.",
            forecast = "A werewolf is among the attackers. Silver stops it healing."),
        enemy("tc_beastcaller", "Beastcaller", TC, 33, 6, 0, cost = 5, siege = 2, tags = setOf(SPECIALIST),
            moves = listOf(
                move("horn_call", "Horn call", Cadence.Actions(2, 5), "On its 2nd and 5th actions, gives 3 Armor to its weakest beast (lowest Health percentage), then attacks the defender with the least Health for 50% of its Attack, past a Guardian.",
                    Guard(Aim.WeakestAllyTagged(CALLED), Fixed(3)), Damage(Aim.WeakestFoe, Strike(50))),
                attack()),
            job = "Coordinates the creatures around the weak point.",
            counter = "Little Health. Defeat it to end the coordination.",
            forecast = "A horn-bearer is directing the beasts.",
            adaptation = "It gives its beasts Armor and points its own attack at the weakest defender, instead of a damage bonus and a shared target (final design Appendix A)."),
    )

    // ---- six captains: one central rule each. Never returned, never healed by an enemy support ----
    private val captain = setOf(Tags.CAPTAIN, LEADER, ELITE)
    private val captains = listOf(
        enemy("cap_iron_marshal", "Varka the Iron Marshal", IB, 115, 15, 25, cost = 16, siege = 4, tags = captain,
            moves = listOf(
                prepared("last_advance", "Last Advance", Cadence.HealthAtMost(50),
                    interruptible("The first time the Marshal's Health is at 50% or less, the Marshal starts Preparing a last advance. On the Marshal's next action, every other attacker gains 5 Armor."),
                    ChannelDef("last_advance", "Last Advance", uses = 1, onInterrupt = OnInterrupt.SPENT, payload = listOf(Guard(Aim.OtherAllies, Fixed(5))), fallback = listOf(Damage(Aim.Foe, Strike())))),
                attack()),
            job = "An orc military contractor who values trained formations.",
            counter = "Defeat the escorts before the Marshal is wounded, interrupt the last advance, or bring the Marshal down in one push.",
            forecast = "Varka will rally the surviving troops when wounded."),
        enemy("cap_goblin_chief", "Nibba Fuse-Ear", IB, 82, 11, 10, cost = 13, siege = 3, tags = captain + SPECIALIST,
            moves = listOf(
                prepared("demolition_order", "Demolition Order", Cadence.Actions(2),
                    interruptible("On Nibba's 2nd action, Nibba starts Preparing a demolition order. On Nibba's next action, the sapper gains 5 Armor, or the weakest other attacker when no sapper stands."),
                    ChannelDef("demolition_order", "Demolition Order", uses = 1, onInterrupt = OnInterrupt.SPENT, payload = listOf(Guard(Aim.WeakestOtherAlly(STRUCTURAL), Fixed(5))), fallback = listOf(Damage(Aim.Foe, Strike())))),
                attack()),
            job = "A goblin engineer who sells breached gates to the highest bidder.",
            counter = "Defeat or interrupt the sapper, interrupt Nibba, or defeat the shield that protects the sapper.",
            forecast = "Nibba's orders will protect the sapper's charge.",
            adaptation = "The engine cannot hurry another unit's preparation: the order gives the sapper 5 Armor to protect its charge instead of making it land sooner."),
        enemy("cap_lich", "The Bellkeeper", PP, 88, 12, 10, cost = 18, siege = 5, tags = captain + UNDEAD + SPECIALIST,
            moves = listOf(
                prepared("funeral_toll", "Funeral Toll", Cadence.All(listOf(Cadence.ActionsFrom(1), Cadence.AllyDownedEligible(STANDARD_UNDEAD))),
                    interruptible("Once one of its standard undead has fallen, the Bellkeeper starts Preparing a funeral toll. On its next action, the first of them to fall stands again with 40% of its Health and acts from the next round. Leaders and spirits never return."),
                    ChannelDef("funeral_toll", "Funeral Toll", uses = 1, onInterrupt = OnInterrupt.SPENT, payload = listOf(Action.Return(STANDARD_UNDEAD, 40)), fallback = listOf(Damage(Aim.Foe, Strike(), Element.GRAVE)), fallbackName = "Grave attack")),
                attack(Element.GRAVE)),
            job = "A skeletal ritualist who carries the cracked bell of a flooded chapel.",
            counter = "Interrupt the toll, defeat the Bellkeeper, or defeat the returned soldier a second time.",
            forecast = "The Bellkeeper will try to call one fallen soldier back."),
        enemy("cap_death_knight", "Sir Carrow, the Unburied", PP, 130, 16, 30, cost = 18, siege = 4, tags = captain + UNDEAD,
            moves = listOf(
                prepared("oath_of_ruin", "Oath of Ruin", Cadence.Actions(2, 5),
                    "On his 2nd and 5th actions, he starts Preparing an oath of ruin. On his next action, the oath deals 80% of his Attack as grave damage to every defender. An Interrupt stops only that oath.",
                    ChannelDef("oath_of_ruin", "Oath of Ruin", uses = 2, onInterrupt = OnInterrupt.CYCLE, payload = listOf(Damage(Aim.AllFoes, Strike(80), Element.GRAVE)), fallback = listOf(Damage(Aim.Foe, Strike())))),
                attack()),
            job = "A former gate commander, bound by an oath to hold every wall he reaches.",
            counter = "Interrupt each oath, or heal through the two. Spears and Bleed get past his heavy Armor.",
            forecast = "The Unburied Knight is preparing an oath that strikes every defender."),
        enemy("cap_elder_treant", "Old Root-Crown", TC, 155, 15, 20, cost = 18, siege = 4, tags = captain, weakTo = Element.FIRE,
            moves = listOf(
                prepared("root_shelter", "Root Shelter", Cadence.Actions(2),
                    interruptible("On its 2nd action, Root-Crown starts Preparing a root shelter. On its next action, Root-Crown and every creature with it gain 7 Armor."),
                    ChannelDef("root_shelter", "Root Shelter", uses = 1, onInterrupt = OnInterrupt.SPENT, payload = listOf(Guard(Aim.AllAllies, Fixed(7))), fallback = listOf(Damage(Aim.Foe, Strike())))),
                attack()),
            job = "An ancient treant bound by a Covenant oath.",
            counter = "Interrupt the shelter, defeat the lighter creatures before it lands, or burn through it. Weak to Fire.",
            forecast = "Root-Crown will shelter the creatures around it."),
        enemy("cap_beast_matriarch", "The White-Fang Matriarch", TC, 112, 15, 10, cost = 16, siege = 4, tags = captain + Tags.BEAST + CALLED,
            moves = listOf(
                move("pack_prey", "Pack prey", Cadence.Actions(2, 5), "On her 2nd and 5th actions, she attacks the defender with the least Health, past a Guardian, and sets her wolves on that defender until the round ends.",
                    Action.MarkPrey(Aim.WeakestFoe), Damage(Aim.PackPrey, Strike())),
                attack()),
            job = "An unusually large dire wolf that protects its pack.",
            counter = "Armor and healing on the defender she marks, fewer wolves, or the Matriarch defeated.",
            forecast = "The white-fanged wolf will set the pack on a wounded defender."),
    )

    // ---- three engines: crew inside the same Health. Weak to Fire, no Poison or Bleed, never returned ----
    private val engine = setOf(Tags.ENGINE)
    private const val SPENT_ENGINE = "Once its last use is spent, it stands on the field but no longer acts."
    private val engines = listOf(
        enemy("eng_battering_ram", "Covered Battering Ram", IB, 90, 0, 20, cost = 10, siege = 3, tags = engine + STRUCTURAL, weakTo = Element.FIRE, immune = bones,
            moves = listOf(
                prepared("ram_stroke", "Ram Stroke", Cadence.Actions(1, 3, 5),
                    "On its 1st, 3rd and 5th actions, it starts Preparing a ram stroke: 4 damage to the defences on its next action. An Interrupt stops only that stroke. At most 3 strokes. $SPENT_ENGINE",
                    ChannelDef("ram_stroke", "Ram Stroke", uses = 3, onInterrupt = OnInterrupt.CYCLE, payload = listOf(Action.Structural(4)), neutralizeWhenSpent = true)),
                waits("It does not attack.")),
            job = "Batters the gate under a timber roof.",
            counter = "Weak to Fire. Concentrated attacks destroy it, and each Interrupt saves the defences one stroke.",
            forecast = "A covered ram is approaching. Its first stroke will come after a short preparation."),
        enemy("eng_ballista", "Field Ballista", IB, 48, 16, 5, cost = 8, siege = 3, tags = engine + SPECIALIST, weakTo = Element.FIRE, immune = bones,
            moves = listOf(
                prepared("heavy_bolt", "Heavy Bolt", Cadence.Actions(1, 3, 5),
                    "On its 1st, 3rd and 5th actions, it starts Preparing a heavy bolt. On its next action, the bolt hits the front defender and ignores up to 5 of that defender's Armor. An Interrupt wastes that bolt. At most 3 bolts. $SPENT_ENGINE",
                    ChannelDef("heavy_bolt", "Heavy Bolt", uses = 3, onInterrupt = OnInterrupt.CYCLE, payload = listOf(Damage(Aim.FrontFoe, Strike(), ignoreArmor = 5)), neutralizeWhenSpent = true)),
                waits("It does not attack otherwise.")),
            job = "Winds heavy bolts against the defenders.",
            counter = "Little Health and weak to Fire. Armor beyond the 5 its bolt ignores still blocks.",
            forecast = "A field ballista is being wound against the defenders."),
        enemy("eng_corpse_catapult", "Corpse Catapult", PP, 65, 0, 10, cost = 11, siege = 4, tags = engine + STRUCTURAL + SPECIALIST, weakTo = Element.FIRE, immune = bones,
            moves = listOf(
                prepared("grave_volley", "Grave Volley", Cadence.Actions(2),
                    "On its 2nd action, it starts Preparing a grave volley. On its next action, the volley does 3 damage to the defences and deals 2 grave damage to every defender. An Interrupt stops it for good. $SPENT_ENGINE",
                    ChannelDef("grave_volley", "Grave Volley", uses = 1, onInterrupt = OnInterrupt.SPENT, payload = listOf(Action.Structural(3), Damage(Aim.AllFoes, Fixed(2), Element.GRAVE)), neutralizeWhenSpent = true)),
                waits("It does not attack.")),
            job = "Throws one foul load at the town.",
            counter = "Weak to Fire. Interrupt it or destroy it before its single shot. Bows reach it at the back of their line.",
            forecast = "A corpse catapult is preparing one foul volley."),
    )

    // ---- three great monsters: late, named, never returned ----
    private val boss = setOf(Tags.BOSS, ELITE)
    private val monsters = listOf(
        enemy("boss_hill_giant", "Hill Giant", IB, 180, 22, 10, cost = 23, siege = 5, tags = boss + STRUCTURAL,
            moves = listOf(
                prepared("boulder_cast", "Boulder Cast", Cadence.Actions(2),
                    interruptible("On its 2nd action, it starts Preparing a boulder cast: 6 damage to the defences on its next action."),
                    ChannelDef("boulder_cast", "Boulder Cast", uses = 1, onInterrupt = OnInterrupt.SPENT, payload = listOf(Action.Structural(6)), fallback = listOf(Damage(Aim.Foe, Strike())))),
                move("club_sweep", "Club sweep", Cadence.Actions(5), "On its 5th action, hits the first 2 defenders in the line for 70% of its Attack each, past a Guardian.", Damage(Aim.FrontFoes(2), Strike(70))),
                attack()),
            job = "A towering giant hired to break the gate with stones.",
            counter = "Very slow, with no healing. Interrupt the boulder, then keep up your attacks and your Armor.",
            forecast = "A hill giant is bringing stones large enough to break the gate."),
        enemy("boss_bone_colossus", "Bone Colossus", PP, 170, 18, 25, cost = 24, siege = 5, tags = boss + UNDEAD, immune = bones, bonusFrom = axeWeak,
            passives = listOf(e("boss_bone_colossus_crack", "Cracked shell", Trigger(EventKind.DAMAGE, Who.SELF_TARGET), listOf(Take(Stat.GUARD, Aim.Self, Fixed(14))), Limit(perFight = 1),
                "The first time the Colossus is damaged to 50% of its Health or less, its shell cracks and it loses all its Armor. Once per fight.", listOf(Condition.HolderHealthAtMost(50)))),
            moves = listOf(
                move("bone_shell", "Bone shell", Cadence.Actions(2, 5), "On its 2nd and 5th actions, gains 6 Armor, then attacks.", Guard(Aim.Self, Fixed(6)), Damage(Aim.Foe, Strike())),
                attack(telegraph = "It attacks.")),
            job = "A mass of bound bones shaped into a giant.",
            counter = "Axe attacks deal 30% more damage. Bring it below half Health to crack its shell.",
            forecast = "A giant of bound bones is approaching. Break its outer shell.",
            adaptation = "The crack removes its Armor once, but a later Bone shell still gains Armor: nothing can stop a gain from 50% Health on."),
        enemy("boss_ash_dragon", "Ash Dragon", TC, 150, 17, 20, cost = 26, siege = 6, tags = boss, weakTo = Element.FROST, resists = Element.FIRE,
            moves = listOf(
                prepared("ash_breath", "Ash Breath", Cadence.Actions(2),
                    interruptible("On its 2nd action, it starts Preparing its ash breath. On its next action, the breath deals 70% of its Attack as fire damage to every defender and puts 2 Burn on the defender with the least Health."),
                    ChannelDef("ash_breath", "Ash Breath", uses = 1, onInterrupt = OnInterrupt.SPENT, payload = listOf(Damage(Aim.AllFoes, Strike(70), Element.FIRE), Give(Stat.BURN, Aim.WeakestFoe, Fixed(2))),
                        fallback = listOf(Damage(Aim.Foe, Strike())))),
                attack()),
            job = "A young dragon that attacks for territory and tribute.",
            counter = "Interrupt the breath, or heal and gain Armor before it lands. Weak to Frost, resists Fire.",
            forecast = "An ash-scaled dragon demands the town's stores. Prepare for one great breath of fire."),
    )

    private val built: List<Built> = ironBanner + paleProcession + thornCovenant + captains + engines + monsters

    val units: List<UnitDef> = built.map { it.unit }
    val profiles: List<EnemyProfile> = built.map { it.profile }

    private fun army(id: String, host: Host, kind: ArmyKind, name: String, members: List<String>, cost: Int, firstSiege: Int, forecast: String, vararg counters: String) =
        ArmyDef(id, host.factionId, name, kind, members, cost, firstSiege, forecast, counters.toList())

    private val S = ArmyKind.STANDARD

    /**
     * The 36 armies of content bible 13, front first. Reordered from the bible's lists, so the screen stands in front and the
     * defining specialist keeps a place a charter escort keeps: ib_03, ib_04, ib_05, ib_06, ib_07, ib_08, pp_01, pp_03, pp_04,
     * pp_05, pp_06, tc_05, tc_08, cap_01, cap_02, cap_03, cap_05, eng_01, eng_02, eng_03. Members and costs are the bible's.
     */
    val armies: List<ArmyDef> = listOf(
        army("army_ib_01", IB, S, "Shield and Arrows", listOf("ib_hobgoblin_shield", "ib_goblin_skirmisher", "ib_hobgoblin_archer"), 11, 1,
            "Shieldbearers escort archers toward the wall.", "Defeat the Shieldbearer first.", "Bows and Storm weapons reach the archer at the back of their line."),
        army("army_ib_02", IB, S, "Stolen Colours", listOf("ib_goblin_skirmisher", "ib_goblin_skirmisher", "ib_deserter", "ib_hobgoblin_archer"), 12, 1,
            "Climbers will cover deserter crossbows.", "Give your defenders Armor: a crossbow bolt ignores only 4 of it.", "Defeat the fragile archer and crossbowman."),
        army("army_ib_03", IB, S, "Powder at the Gate", listOf("ib_hobgoblin_shield", "ib_goblin_skirmisher", "ib_goblin_sapper", "ib_goblin_skirmisher"), 13, 2,
            "A protected sapper is preparing a powder charge.", "Interrupt the sapper or defeat it before its charge lands.", "The charge lands only once."),
        army("army_ib_04", IB, S, "Drums and Fangs", listOf("ib_wolf_rider", "ib_goblin_skirmisher", "ib_war_drummer", "ib_goblin_skirmisher"), 13, 2,
            "War drums will drive a wolf rider at the wounded.", "Defeat the drummer.", "Heal or protect the defender with the least Health."),
        army("army_ib_05", IB, S, "Broken Shields", listOf("ib_orc_breaker", "ib_hobgoblin_shield", "ib_deserter"), 15, 2,
            "Heavy mauls will strip Armor away for the crossbow bolts.", "Armor gained every round replaces what the mauls remove.", "Defeat the crossbowman."),
        army("army_ib_06", IB, S, "The Side Wall", listOf("ib_bugbear", "ib_hobgoblin_shield", "ib_orc_shaman", "ib_hobgoblin_archer"), 19, 3,
            "A climber and a war shaman will test the back of your line.", "Put a sturdy defender at the back of your line for the climber's first attack.", "Defeat the shaman."),
        army("army_ib_07", IB, S, "Ogre at the Door", listOf("ib_ogre", "ib_goblin_skirmisher", "ib_war_drummer"), 16, 3,
            "An ogre carries a ram under the war drums.", "Interrupt the ogre's gate rush.", "Defeat the drummer, and concentrate your attacks on the ogre."),
        army("army_ib_08", IB, S, "The Paid Troll", listOf("ib_troll", "ib_hobgoblin_shield", "ib_deserter"), 18, 3,
            "A healing troll escorts armored mercenaries.", "Fire hits and Burn stop the troll healing.", "Concentrate your attacks on the troll.", "Spears and Bleed get past the mercenaries' Armor."),

        army("army_pp_01", PP, S, "Old Spears", listOf("pp_zombie", "pp_skeleton_guard", "pp_skeleton_guard", "pp_skeleton_archer"), 12, 1,
            "Skeleton spears and bows advance behind a shambling corpse.", "Axe attacks deal 30% more damage to skeletons.", "Ordinary concentrated attacks work as well."),
        army("army_pp_02", PP, S, "Slow Advance", listOf("pp_zombie", "pp_zombie", "pp_skeleton_archer", "pp_skeleton_archer"), 12, 1,
            "Shambling corpses screen a line of bone archers.", "Bows and Storm weapons reach the archers at the back of their line.", "Clearing the corpses at the front quickly works too."),
        army("army_pp_03", PP, S, "Hungry Dead", listOf("pp_zombie", "pp_ghoul", "pp_grave_rats"), 11, 2,
            "A ghoul and grave rats will go for the wounded defenders.", "Healing and Purifying weapons remove the ghoul's Poison.", "Defeat the fragile ghoul early."),
        army("army_pp_04", PP, S, "Black Lantern", listOf("pp_skeleton_guard", "pp_skeleton_guard", "pp_acolyte", "pp_skeleton_archer"), 14, 2,
            "A lantern-bearer is healing the skeleton line.", "Defeat the Grave Acolyte.", "Its healing is small: concentrated attacks outpace it."),
        army("army_pp_05", PP, S, "A Second March", listOf("pp_skeleton_guard", "pp_skeleton_guard", "pp_necromancer", "pp_skeleton_archer"), 16, 3,
            "A necromancer will try to raise one fallen skeleton.", "Interrupt the raising.", "Be ready to defeat one skeleton twice."),
        army("army_pp_06", PP, S, "Mourning at the Gate", listOf("pp_zombie", "pp_zombie", "pp_banshee", "pp_skeleton_archer"), 16, 3,
            "A banshee will weaken the party with a single wail.", "Interrupt the wail, or defeat the banshee before it lands.", "The wail comes only once: heal through it."),
        army("army_pp_07", PP, S, "The Empty Armour", listOf("pp_black_knight", "pp_wraith", "pp_skeleton_archer"), 18, 3,
            "A black knight holds the gate while a wraith goes for the back of your line.", "Spears and Bleed get past the knight's Armor.", "Protect the defender at the back of your line."),
        army("army_pp_08", PP, S, "The Great Corpse", listOf("pp_ogre_zombie", "pp_wight", "pp_grave_rats"), 18, 3,
            "A giant corpse advances beside a life-draining wight.", "Armor on your defenders denies the wight's drain.", "Concentrate your attacks to keep the fight short."),

        army("army_tc_01", TC, S, "Driven Pack", listOf("tc_dire_wolf", "tc_dire_wolf", "tc_dire_wolf"), 12, 1,
            "Three driven wolves are gathering at the breach.", "Defeat one wolf early: each wolf hits harder after another has hit the same defender."),
        army("army_tc_02", TC, S, "Horn and Web", listOf("tc_dire_wolf", "tc_giant_spider", "tc_beastcaller"), 13, 2,
            "A horn-bearer directs a wolf while a spider webs the wall.", "Defeat the Beastcaller.", "The spider is weak to Fire."),
        army("army_tc_03", TC, S, "Horns Below, Wings Above", listOf("tc_minotaur", "tc_harpy"), 12, 3,
            "A minotaur will charge while a harpy strips Armor from your defenders.", "Interrupt the horn charge.", "Give the front defender Armor before the charge lands.", "Defeat the harpy."),
        army("army_tc_04", TC, S, "Roots and Teeth", listOf("tc_treant", "tc_dire_wolf"), 13, 3,
            "A bound walking tree shelters a driven wolf.", "Fire and a few heavy hits work best against the tree.", "Concentrate your attacks on the wolf."),
        army("army_tc_05", TC, S, "Spines and Coils", listOf("tc_giant_serpent", "tc_dire_wolf", "tc_manticore"), 17, 3,
            "A giant serpent holds the breach beneath falling tail spikes.", "Healing keeps the party ahead of the spikes.", "Concentrate your attacks on one enemy at a time."),
        army("army_tc_06", TC, S, "Venom from Above", listOf("tc_wyvern", "tc_harpy"), 15, 4,
            "A wyvern will dive while a harpy strips away Armor.", "Healing and Purifying weapons remove the wyvern's Poison.", "Protect the defender with the least Health.", "Defeat the fragile harpy."),
        army("army_tc_07", TC, S, "Bound Stone, Bound Flame", listOf("tc_earth_elemental", "tc_fire_elemental"), 18, 4,
            "Bound stone and flame are advancing together.", "Axe attacks deal 30% more damage to the stone.", "Weapons without Fire work against the flame, and Frost works best."),
        army("army_tc_08", TC, S, "The Marked Hunt", listOf("tc_werewolf", "tc_dire_wolf", "tc_beastcaller"), 17, 3,
            "A horn-bearer leads a werewolf and a dire wolf.", "Defeat the Beastcaller.", "Silver weapons, Fire hits and Burn stop the werewolf healing."),

        army("army_cap_01", IB, ArmyKind.CAPTAIN, "The Last Advance", listOf("ib_hobgoblin_shield", "cap_iron_marshal", "ib_hobgoblin_archer"), 25, 4,
            "Varka will rally the surviving troops when wounded.", "Defeat the escorts first.", "Interrupt the last advance."),
        army("army_cap_02", IB, ArmyKind.CAPTAIN, "Fuse-Ear's Breach", listOf("ib_hobgoblin_shield", "ib_goblin_skirmisher", "ib_goblin_sapper", "cap_goblin_chief"), 24, 3,
            "Nibba will protect the sapper's powder charge.", "Interrupt Nibba's order or the sapper's charge.", "Defeat the sapper before its charge lands."),
        army("army_cap_03", PP, ArmyKind.CAPTAIN, "The Funeral Toll", listOf("pp_skeleton_guard", "pp_skeleton_guard", "cap_lich", "pp_skeleton_archer"), 27, 5,
            "The Bellkeeper will call one fallen soldier back.", "Interrupt the funeral toll.", "The returned soldier comes back only once, with part of its Health."),
        army("army_cap_04", PP, ArmyKind.CAPTAIN, "The Unburied Oath", listOf("cap_death_knight", "pp_grave_rats", "pp_skeleton_archer"), 24, 4,
            "The Unburied Knight prepares an oath that strikes the whole party.", "Interrupt each oath.", "Spears and Bleed get past his Armor."),
        army("army_cap_05", TC, ArmyKind.CAPTAIN, "Under Root-Crown", listOf("cap_elder_treant", "tc_dire_wolf", "tc_giant_spider"), 26, 4,
            "Root-Crown will shelter the creatures around it.", "Interrupt the root shelter.", "Defeat the lighter creatures before it lands.", "Fire deals 30% more damage to Root-Crown."),
        army("army_cap_06", TC, ArmyKind.CAPTAIN, "White-Fang's Pack", listOf("cap_beast_matriarch", "tc_dire_wolf", "tc_dire_wolf"), 24, 4,
            "The white-fanged matriarch will set her pack on a wounded defender.", "Protect and heal the defender with the least Health.", "Defeat the wolves to thin the pack."),

        army("army_eng_01", IB, ArmyKind.ENGINE, "Under the Timber Roof", listOf("ib_hobgoblin_shield", "eng_battering_ram", "ib_deserter"), 19, 3,
            "A covered ram is approaching beneath shields and crossbows.", "Fire deals 30% more damage to the ram.", "Each Interrupt stops one stroke."),
        army("army_eng_02", IB, ArmyKind.ENGINE, "The Heavy Bolt", listOf("ib_goblin_skirmisher", "ib_goblin_skirmisher", "eng_ballista", "ib_hobgoblin_archer"), 16, 3,
            "A field ballista is winding a bolt against the defenders.", "Armor on the front defender beyond the 5 a bolt ignores still blocks.", "Fire deals 30% more damage to the fragile engine."),
        army("army_eng_03", PP, ArmyKind.ENGINE, "A Foul Volley", listOf("pp_zombie", "pp_zombie", "eng_corpse_catapult", "pp_skeleton_archer"), 20, 4,
            "A corpse catapult is preparing one foul volley.", "Interrupt or destroy the catapult before its single shot.", "Fire deals 30% more damage to it."),

        army("army_boss_01", IB, ArmyKind.MONSTER, "Stones for the Gate", listOf("boss_hill_giant", "ib_goblin_skirmisher", "ib_goblin_skirmisher"), 27, 5,
            "A hill giant is bringing a boulder toward the gate.", "Interrupt the boulder cast.", "Keep up your attacks and your Armor: it is slow but hard to bring down."),
        army("army_boss_02", PP, ArmyKind.MONSTER, "The Walking Ossuary", listOf("boss_bone_colossus", "pp_skeleton_guard", "pp_skeleton_guard"), 30, 5,
            "A giant of bound bones is approaching with a brittle outer shell.", "Axe attacks deal 30% more damage to it.", "Below half Health its shell cracks and its Armor is gone."),
        army("army_boss_03", TC, ArmyKind.MONSTER, "Tribute in Ash", listOf("boss_ash_dragon"), 26, 6,
            "An ash-scaled dragon demands tribute. Prepare for one great breath of fire.", "Interrupt the breath, or heal through it.", "Weapons without Fire work best, Frost most of all."),
    )
}
