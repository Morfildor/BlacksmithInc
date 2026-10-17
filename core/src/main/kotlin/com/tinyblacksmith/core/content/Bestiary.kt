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

    // ---- Pale Procession (Hollowbound): endurance, one return, weaker healing, pressure on fragile defenders ----
    private val bones = setOf(Stat.POISON, Stat.BLEED)
    private val axeWeak = listOf(SourceBonus(AXE, 30))
    private const val BONES = "Axe attacks deal 30% more damage to it. Poison and Bleed never affect it."
    private val paleProcession = listOf(
        enemy("pp_skeleton_guard", "Skeleton Spearman", PP, 30, 7, 10, cost = 3, siege = 1, tags = setOf(UNDEAD, RAISABLE, STANDARD_UNDEAD),
            immune = bones, bonusFrom = axeWeak,
            moves = listOf(attack(telegraph = "$BONES It attacks.")),
            job = "Tires the defenders and holds a narrow breach.",
            counter = "Fragile bones. Axe attacks, or ordinary concentrated attacks.",
            forecast = "Skeleton spearmen are marching in ranks."),
        enemy("pp_skeleton_archer", "Skeleton Archer", PP, 23, 9, 0, cost = 3, siege = 1, tags = setOf(UNDEAD, RAISABLE, STANDARD_UNDEAD, SPECIALIST),
            immune = bones, bonusFrom = axeWeak,
            moves = listOf(attack(telegraph = "$BONES It shoots.")),
            job = "Tires the defenders with volleys from behind.",
            counter = "Very little Health. Bows and Storm weapons reach it at the back of their line.",
            forecast = "Bone archers are assembling behind the dead."),
        enemy("pp_zombie", "Zombie", PP, 54, 7, 0, cost = 3, siege = 1, tags = setOf(UNDEAD, RAISABLE, STANDARD_UNDEAD),
            immune = setOf(Stat.POISON), surviveOnce = true,
            moves = listOf(attack(telegraph = "The first hit that would down it leaves it at 1 Health instead, once per fight. Fire and Sun hits are not stopped by it. Poison never affects it.")),
            job = "Soaks up damage and drags the defence out.",
            counter = "Slow and weak. A second hit after its survival, or a Fire or Sun hit, finishes it.",
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
                attack(telegraph = "Poison never affects it. It attacks.")),
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
                attack(telegraph = "Poison and Bleed never affect it. It attacks.")),
            job = "Holds the breach while the support works.",
            counter = "Heavy Armor. Spears get past part of it. Defeat its support instead of hitting its plate.",
            forecast = "A black-armored corpse is guarding the procession.",
            adaptation = "It does not force a defender to attack it: it gains Armor and attacks (final design Appendix A, no Taunt)."),
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
            moves = listOf(attack(telegraph = "Once per round, it deals 20% more damage to a defender another beast already damaged this round. It attacks.")),
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
            counter = "Slow. Interrupt the charge, give the front defender Armor before it lands, or bring it down first.",
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
                attack(telegraph = "Axe attacks deal 30% more damage to it. It attacks.")),
            job = "Crushes a prepared breach.",
            counter = "Very slow, with no healing. Axe attacks deal 30% more damage, and spears get past part of its Armor.",
            forecast = "Bound stone is moving beneath the hillside."),
        enemy("tc_fire_elemental", "Fire Elemental", TC, 60, 10, 0, cost = 8, siege = 4, weakTo = Element.FROST, resists = Element.FIRE, immune = bones,
            moves = listOf(
                move("searing_touch", "Searing touch", Cadence.Actions(2, 5), "On its 2nd and 5th actions, attacks with fire and puts 2 Burn on the defender it attacks.",
                    Damage(Aim.Foe, Strike(), Element.FIRE), Give(Stat.BURN, Aim.Foe, Fixed(2))),
                attack(Element.FIRE, telegraph = "Poison and Bleed never affect it. Its attacks deal fire damage.")),
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
            moves = listOf(attack(telegraph = "Before each of its actions, it heals 2 Health, unless a Fire hit, Burn or a Silver weapon damaged it since its last action. Silver weapons deal 30% more damage to it. Then it attacks.")),
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

    private val built: List<Built> = ironBanner + paleProcession + thornCovenant

    val units: List<UnitDef> = built.map { it.unit }
    val profiles: List<EnemyProfile> = built.map { it.profile }
    val armies: List<ArmyDef> = emptyList()
}
