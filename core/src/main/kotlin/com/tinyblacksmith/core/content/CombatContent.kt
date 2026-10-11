package com.tinyblacksmith.core.content

import com.tinyblacksmith.core.combat.*
import com.tinyblacksmith.core.combat.Action.Damage
import com.tinyblacksmith.core.combat.Action.Give
import com.tinyblacksmith.core.combat.Action.Guard
import com.tinyblacksmith.core.combat.Action.Heal
import com.tinyblacksmith.core.combat.Amount.Fixed
import com.tinyblacksmith.core.combat.Amount.Strike
import com.tinyblacksmith.core.combat.Amount.Support
import com.tinyblacksmith.core.model.AffixId
import com.tinyblacksmith.core.model.HeroClassId
import com.tinyblacksmith.core.model.MaterialId
import com.tinyblacksmith.core.model.WeaponFamilyId

/**
 * What a class does in a fight and what it is worth there. Health, strike and support are on the fight's own scale;
 * a hero's stored 0..100 health is the share of [health] they enter with.
 */
data class ClassKitDef(
    val classId: HeroClassId,
    val kit: KitDef,
    val health: Int,
    val healthPerLevel: Int,
    val strike: Int,
    val strikePerLevel: Int,
    val support: Int,
    val supportPerLevel: Int,
    /** One line for the roster: what this class does by itself. */
    val role: String,
    /** One line for the smith: which blades it makes worth forging. */
    val forgeHint: String,
)

/** How a weapon family delivers a class's action (spec 7.1): its share of strike and support, and a rule of its own. */
data class FamilyCombatDef(val familyId: WeaponFamilyId, val strikePercent: Int, val supportPercent: Int, val effects: List<EffectDef>, val pattern: String)

/**
 * What a metal gives a weapon in a fight: a share of strike and support, and at most one rule of its own. [role] is its
 * physical character in one line, [constraint] what choosing it costs. Read only for a weapon of `WeaponRules.CRAFT_CORES`
 * or later: a weapon forged before metals had a rule keeps fighting as it did.
 */
data class CoreCombatDef(val coreId: MaterialId, val role: String, val constraint: String, val effects: List<EffectDef> = emptyList(), val strikePercent: Int = 100, val supportPercent: Int = 100)

/** An augment or a catalyst in the smith's words: what it is chosen for ([role]) and what the choice costs ([constraint]). Its rules are the element's or the catalyst's own. */
data class IngredientNote(val role: String, val constraint: String)

/**
 * A relationship between rules that a smith can learn and reuse, never one exact recipe. [pieces] are the weapon rules
 * that take part: forging one of them is what lets the notebook suggest the path. [shown] lists the ways a real fight
 * can show it: every rule of one set has to have fired for the party.
 */
data class InteractionDef(val id: String, val name: String, val description: String, val constraint: String, val pieces: Set<String>, val shown: List<Set<String>>)

/**
 * Everything the interaction engine reads from content: kits, the rule each element, affix, catalyst, family and
 * signature gives a blade, the party rules of relics, and the units that can stand on a field. All numbers here are
 * catalog numbers, PROPOSED.
 */
data class CombatCatalog(
    val kits: List<ClassKitDef>,
    val families: List<FamilyCombatDef>,
    val elementEffects: Map<Element, List<EffectDef>>,
    val affixEffects: Map<AffixId, List<EffectDef>>,
    /** Extra rules an affix has only at home (a siege) or only on the road (a mission). */
    val affixHomeEffects: Map<AffixId, List<EffectDef>> = emptyMap(),
    val affixRoadEffects: Map<AffixId, List<EffectDef>> = emptyMap(),
    val catalystEffects: Map<MaterialId, List<EffectDef>>,
    val signatureEffects: Map<String, List<EffectDef>>,
    val relicEffects: Map<String, List<EffectDef>>,
    val units: List<UnitDef>,
    /** Weapon power per point of strike. */
    val powerPerStrike: Int = 5,
    val cores: List<CoreCombatDef> = emptyList(),
    val notes: Map<MaterialId, IngredientNote> = emptyMap(),
    val interactions: List<InteractionDef> = emptyList(),
    /**
     * Rules rewritten for weapons of `WeaponRules.CRAFT_CORES` or later, by the ID of the rule each replaces. A weapon
     * forged before keeps the old rule under the old ID. A rewritten rule has an ID of its own.
     */
    val revisions: Map<String, EffectDef> = emptyMap(),
) {
    val coreById: Map<MaterialId, CoreCombatDef> = cores.associateBy { it.coreId }
    val kitByClass: Map<HeroClassId, ClassKitDef> = kits.associateBy { it.classId }
    val familyById: Map<WeaponFamilyId, FamilyCombatDef> = families.associateBy { it.familyId }
    val unitById: Map<String, UnitDef> = units.associateBy { it.id }
    fun unit(id: String): UnitDef = unitById[id] ?: error("Unknown unit $id")

    val allEffects: List<EffectDef> get() = families.flatMap { it.effects } + cores.flatMap { it.effects } + revisions.values + elementEffects.values.flatten() + affixEffects.values.flatten() + affixHomeEffects.values.flatten() +
        affixRoadEffects.values.flatten() + catalystEffects.values.flatten() + signatureEffects.values.flatten() + relicEffects.values.flatten()

    fun problems(): List<String> = EffectRules.problems(allEffects, kits.map { it.kit }, unitById) +
        relicEffects.values.flatten().filter { !it.party }.map { "Relic rule ${it.id} is not a party rule" } +
        revisions.filter { (old, new) -> old == new.id || allEffects.none { it.id == old } }.keys.map { "Revision of $it needs a known rule and an ID of its own" } +
        allEffects.map { it.id }.toSet().let { known -> interactions.flatMap { i -> (i.pieces + i.shown.flatten()).filter { it !in known }.map { "Interaction ${i.id} names unknown rule $it" } } }
}

/** IDs and definitions of the launch combat content. An ID is stable once a save can hold a timeline that names it. */
object CombatContent {
    const val TAG_INTERCEPT = "intercept"
    const val TAG_CHARGE = "charge"
    const val TAG_LEADER = "leader"
    const val TAG_ANCHOR = "anchor"
    const val TAG_ELITE = "elite"
    const val TAG_UNDEAD = "undead"
    const val TAG_COMMON_GEAR = "common_gear"
    const val TAG_SUMMONED = "summoned"

    const val OVERFLOW_BASIN = "overflow_basin"
    const val SALVAGE_BELL = "salvage_bell"
    const val STORM_LEDGER = "storm_ledger"
    const val FURNACE_LUNG = "furnace_lung"
    const val BONE_MUSIC_BOX = "bone_music_box"
    const val BLOOD_RECEIPT = "blood_receipt"
    const val BLUNT_OATH = "blunt_oath"
    const val COWARDS_MEDAL = "cowards_medal"
    const val WIDOWS_WHETSTONE = "widows_whetstone"
    const val OATHBREAKERS_COIN = "oathbreakers_coin"
    /** Pacts: a strong rule and its price in one relic. */
    val pacts = setOf(WIDOWS_WHETSTONE, OATHBREAKERS_COIN)

    const val SPECTRAL_HELPER = "spectral_helper"

    private fun e(
        id: String, name: String, trigger: Trigger, actions: List<Action>, limit: Limit, description: String, conditions: List<Condition> = emptyList(), cost: Cost? = null,
        party: Boolean = false, perMember: Boolean = false, echo: Boolean = false,
    ) = EffectDef(id, name, trigger, actions, limit, description, conditions, cost, party, perMember, echo)

    private val onOwnHit = Trigger(EventKind.DAMAGE, Who.SELF_SOURCE)
    private val struck = listOf(Condition.FromAction)

    // ---- the shared Charge burst: every rule that stores Charge on a blade spends it through this one ----
    val CHARGE_BURST = e("charge_burst", "Charge burst", Trigger(EventKind.STAT_GAINED, Who.SELF_TARGET, Stat.CHARGE), listOf(Damage(Aim.Foe, Fixed(8), Element.STORM)), Limit(perRound = 1),
        "At 3 Charge, spends all of it and strikes for 8 storm damage. Once a round.", cost = Cost.Spend(Stat.CHARGE, 3, all = true))

    // ---- classes ----
    val GUARDIAN_KIT = KitDef("guardian", "Guardian", tags = setOf(TAG_INTERCEPT), moves = listOf(
        Move("hold_the_line", "Hold the line", Cadence.Always, listOf(Guard(Aim.Self, Support()), Damage(Aim.Foe, Strike()))),
    ), passives = listOf(
        e("guardian_riposte", "Riposte", Trigger(EventKind.GUARD_BROKEN, Who.SELF_TARGET), listOf(Damage(Aim.EventSource, Strike(50))), Limit(perRound = 1),
            "When an enemy breaks this Guardian's Guard, strikes back for half a strike. Once a round.", listOf(Condition.SourceIsFoe)),
    ))
    val WARDEN_KIT = KitDef("warden", "Warden", moves = listOf(
        Move("mend", "Mend", Cadence.Always, listOf(Heal(Aim.WeakestAlly, Support()), Damage(Aim.Foe, Strike(60)))),
    ))
    val BATTLEMAGE_KIT = KitDef("battlemage", "Battlemage", moves = listOf(
        Move("opening_sigil", "Opening sigil", Cadence.Opener, listOf(Give(Stat.CHARGE, Aim.AllyTagged(TAG_CHARGE)), Give(Stat.MARK, Aim.PriorityFoe), Damage(Aim.Foe, Strike(70)))),
        Move("bolt", "Bolt", Cadence.Always, listOf(Damage(Aim.Foe, Strike()))),
    ), passives = listOf(
        e("battlemage_echo", "Echo", Trigger(EventKind.STAT_GAINED, Who.FOE_TARGET), listOf(Action.Echo(1)), Limit(perRound = 1),
            "When an ally puts Burn, Chill or Wet on an enemy, puts one more on it. Once a round. An echo is never echoed.", listOf(Condition.StatIn(setOf(Stat.BURN, Stat.CHILL, Stat.WET))), echo = true),
    ))
    val RANGER_KIT = KitDef("ranger", "Ranger", moves = listOf(
        Move("mark_and_loose", "Mark and loose", Cadence.Always, listOf(Damage(Aim.Foe, Strike()), Give(Stat.MARK, Aim.PriorityFoe))),
    ))
    val DUELIST_KIT = KitDef("duelist", "Duelist", moves = listOf(
        Move("double_cut", "Double cut", Cadence.Always, listOf(Damage(Aim.Foe, Strike(60)), Damage(Aim.Foe, Strike(60)))),
    ))

    val kits = listOf(
        ClassKitDef(LaunchContent.GUARDIAN, GUARDIAN_KIT, health = 40, healthPerLevel = 3, strike = 4, strikePerLevel = 1, support = 4, supportPerLevel = 1,
            role = "Raises Guard every round and takes the blows meant for others while it holds. Strikes back when it breaks.",
            forgeHint = "Heavy, guarding and breaking weapons. Guard that breaks is the start of something."),
        ClassKitDef(LaunchContent.WARDEN, WARDEN_KIT, health = 32, healthPerLevel = 2, strike = 4, strikePerLevel = 1, support = 6, supportPerLevel = 1,
            role = "Heals whoever is worst off every round, and strikes lightly.",
            forgeHint = "Weapons that turn healing into something else, and weapons that cost their wielder health."),
        ClassKitDef(LaunchContent.BATTLEMAGE, BATTLEMAGE_KIT, health = 26, healthPerLevel = 2, strike = 6, strikePerLevel = 1, support = 2, supportPerLevel = 0,
            role = "Opens with a Charge for an ally who can store it and a Mark on the foe that matters. Echoes Burn, Chill and Wet.",
            forgeHint = "Elemental weapons. One state on a foe becomes two."),
        ClassKitDef(LaunchContent.RANGER, RANGER_KIT, health = 28, healthPerLevel = 2, strike = 6, strikePerLevel = 1, support = 2, supportPerLevel = 0,
            role = "Strikes, then Marks the foe that matters for the next ally's hit.",
            forgeHint = "Finishers, and anything that wants a marked target."),
        ClassKitDef(LaunchContent.DUELIST, DUELIST_KIT, health = 30, healthPerLevel = 2, strike = 5, strikePerLevel = 1, support = 2, supportPerLevel = 0,
            role = "Cuts twice a round, each lighter than a full strike.",
            forgeHint = "Weapons that do something on every hit: two cuts, two chances."),
    )

    // ---- weapon families: the delivery pattern ----
    val families = listOf(
        FamilyCombatDef(LaunchContent.SWORD, 100, 100, emptyList(), "One clean strike."),
        FamilyCombatDef(LaunchContent.AXE, 120, 90, emptyList(), "A heavier strike. A little less support."),
        FamilyCombatDef(LaunchContent.SPEAR, 95, 100, listOf(
            e("spear_reach", "Reach", Trigger(EventKind.GUARD_ABSORBED, Who.SELF_SOURCE), listOf(Damage(Aim.EventTarget, Amount.OfEvent(50), pierce = true)), Limit(perRound = 1),
                "Half of what an enemy's Guard stops goes through it. Once a round.", struck)), "Reaches past Guard."),
        FamilyCombatDef(LaunchContent.BOW, 90, 100, listOf(
            e("bow_volley", "Second arrow", Trigger(EventKind.ACTED, Who.SELF_SOURCE), listOf(Damage(Aim.BackFoe, Strike(35))), Limit(perRound = 1),
                "After the wielder acts, a second arrow hits the last enemy in the line for a third of a strike. Once a round.", struck)), "A second arrow at the back of the line."),
        FamilyCombatDef(LaunchContent.DAGGER, 80, 100, listOf(
            e("dagger_flurry", "Off-hand cut", Trigger(EventKind.ACTED, Who.SELF_SOURCE), listOf(Damage(Aim.Foe, Strike(45))), Limit(perRound = 1),
                "After the wielder acts, one more light cut. Once a round.", struck)), "Light strikes, one more of them."),
        FamilyCombatDef(LaunchContent.STAFF, 80, 130, emptyList(), "A lighter strike. Much more support."),
    )

    // ---- metals: the weapon's physical character. One reuse of an existing mechanic each, never a new one. ----
    /** The numbers of the six metal rules, in one place so that `ForgeLab` can fight the same recipes with others. */
    data class CoreNumbers(val ironGuard: Int = 3, val bronzeAnswer: Int = 5, val silverCharge: Int = 3, val obsidianStrike: Int = 110, val starsteelBonus: Int = 3, val moonsteelBonus: Int = 3)

    fun cores(n: CoreNumbers = CoreNumbers()) = listOf(
        CoreCombatDef(LaunchContent.IRON, "Plain and sturdy. The wielder starts every fight behind a little Guard.", "The weakest metal. It adds the least to a strike.", listOf(
            e("core_iron", "Iron holds", Trigger(EventKind.ROUND_START, Who.ANY), listOf(Guard(Aim.Self, Fixed(n.ironGuard))), Limit(perFight = 1), "The wielder enters a fight with ${n.ironGuard} Guard."))),
        CoreCombatDef(LaunchContent.BRONZE, "Rings when struck. It answers the blow that breaks the wielder's Guard.", "Does nothing for a wielder who never has Guard.", listOf(
            e("core_bronze", "Bronze rings back", Trigger(EventKind.GUARD_BROKEN, Who.SELF_TARGET), listOf(Damage(Aim.EventSource, Fixed(n.bronzeAnswer))), Limit(perRound = 1),
                "When an enemy breaks the wielder's Guard, the weapon strikes that enemy for ${n.bronzeAnswer}. Once a round.", listOf(Condition.SourceIsFoe)))),
        CoreCombatDef(LaunchContent.SILVER, "Holds a charge. The wielder starts every fight with ${n.silverCharge} Charge stored, enough for one burst.", "Charge is worth nothing until something on the weapon spends it: Binding Salt, or a Stormcharged roll.", listOf(
            e("core_silver", "Silver holds a charge", Trigger(EventKind.ROUND_START, Who.ANY), listOf(Give(Stat.CHARGE, Aim.Self, Fixed(n.silverCharge))), Limit(perFight = 1), "The wielder enters a fight with ${n.silverCharge} Charge."))),
        CoreCombatDef(LaunchContent.OBSIDIAN, "A glass edge. It strikes ${n.obsidianStrike - 100}% harder.", "It cracks the first time an enemy breaks the wielder's Guard. It still cuts, but whoever holds the line brings it home needing the hone after most fights.", listOf(
            e("core_obsidian", "Obsidian cracks", Trigger(EventKind.GUARD_BROKEN, Who.SELF_TARGET), listOf(Action.Fracture(2)), Limit(perFight = 1),
                "The first time an enemy breaks the wielder's Guard, the weapon cracks. 2 Scrap now, a repair after the fight.", listOf(Condition.SourceIsFoe))), strikePercent = n.obsidianStrike),
        CoreCombatDef(LaunchContent.STARSTEEL, "Finds the marked place. It hits harder when it spends a Mark.", "Costly, and its rule sleeps until a Mark is there for the wielder's own action to spend.", listOf(
            e("core_starsteel", "Starsteel finds the Mark", Trigger(EventKind.STAT_SPENT, Who.SELF_SOURCE, Stat.MARK), listOf(Damage(Aim.EventTarget, Fixed(n.starsteelBonus))), Limit(perRound = 1),
                "When the wielder's own action spends a Mark, that hit lands for ${n.starsteelBonus} more. Once a round.", struck))),
        CoreCombatDef(LaunchContent.MOONSTEEL, "Cold metal. It bites deeper into an enemy that is already chilled.", "The costliest metal, and its rule needs Chill on the target before the hit lands.", listOf(
            e("core_moonsteel", "Moonsteel bites the cold", onOwnHit, listOf(Damage(Aim.EventTarget, Fixed(n.moonsteelBonus))), Limit(perRound = 1),
                "Once a round, the wielder's hit on an enemy that is already chilled deals ${n.moonsteelBonus} more. The Chill stays.", struck + Condition.TargetHas(Stat.CHILL)))),
    )
    val cores = cores()

    /** Augments and catalysts as a choice at the forge. Their rules are [elementEffects] and [catalystEffects]. */
    val notes: Map<MaterialId, IngredientNote> = mapOf(
        LaunchContent.EMBER_RESIN to IngredientNote("Fire. Each round the first hit leaves Burn, which hurts at the end of the round.", "Ashclaw and Embermaw resist fire, and Embermaw shrug off half of every Burn."),
        LaunchContent.FROST_BLOOM to IngredientNote("Frost. Each round the first hit leaves Chill, which weakens that enemy's next strike.", "Chill is spent by the strike it weakens. It deals no damage by itself."),
        LaunchContent.VERDANT_SAP to IngredientNote("Living sap. Each round the first hit gives the wielder Regeneration.", "It mends only the wielder, and only a wielder who has been hurt."),
        LaunchContent.STORMGLASS to IngredientNote("Storm. Each round the first hit arcs to the last enemy in the line.", "The arc is small. Storm pays best with Charge or a wet target."),
        LaunchContent.GRAVE_DUST to IngredientNote("Grave. Each round the first hit leaves Bleed, which hurts that enemy before it acts.", "The Hollowbound resist it and bleed half as much."),
        LaunchContent.SUN_ASH to IngredientNote("Sun. Each round the first hit leaves a Mark. The next hit on a Mark is harder, and the party aims at it.", "The Mark helps whoever strikes next, which is not always the wielder."),
        LaunchContent.BINDING_SALT to IngredientNote("Stores Charge when the wielder is healed. At 3 Charge it strikes for 8.", "It needs a healer, and healing a wielder at full health stores nothing."),
        LaunchContent.RUNESTONE_SHARD to IngredientNote("Strikes back for the party when an enemy breaks an ally's Guard.", "It sleeps until a weapon of the party has cracked."),
        LaunchContent.DRAGON_OIL to IngredientNote("Spreads fire. When the wielder's hit leaves Burn, every enemy gets Burn. It spends Steam as a lance.", "The spreading needs a fire weapon, and the lance needs Steam: fire and cold from two different hands."),
        LaunchContent.VOID_INK to IngredientNote("After the wielder acts, takes Burn off the enemy it faces and gives it to your weakest ally as Guard. It spends Steam as Guard for all.", "That Burn no longer deals damage."),
    )

    // ---- interaction paths: what the notebook can suggest and a real fight can show ----
    val interactions = listOf(
        InteractionDef("charge", "Charge and burst", "Anything that stores Charge on a weapon builds toward one burst at 3 Charge. Healing, hits and silver all fill it.",
            "Charge that nothing spends is wasted, and the burst waits for the third Charge.",
            setOf("catalyst_capacitor", "affix_stormcharged", "core_silver", "sig_stormcaller"), listOf(setOf("charge_burst"))),
        InteractionDef("steam", "Boiling point", "Burn on a chilled enemy, or Chill on a burning one, cancels both and gives Steam to whoever did it. Dragon Oil spends Steam as a lance. Void Ink spends it as Guard.",
            "The Burn and the Chill are both gone. Nothing burns and nobody is slowed.",
            setOf("catalyst_steam_lance", "catalyst_steam_shield"), listOf(setOf("catalyst_steam_lance"), setOf("catalyst_steam_shield"))),
        InteractionDef("burn_guard", "Fire into Guard", "Burn taken off an enemy can become Guard. Void Ink takes it from the enemy its wielder faces and gives it to your weakest ally.",
            "That Burn no longer deals damage.",
            setOf("catalyst_converting", "catalyst_drinking", "sig_dawnbrand"), listOf(setOf("catalyst_converted_guard"), setOf("sig_dawnbrand_guard"))),
        InteractionDef("guard_answer", "A broken Guard answers", "Guard that an enemy breaks can be the start of something. Bronze strikes back and Thornwall grows thorns.",
            "It needs Guard on the wielder and an enemy strong enough to break it.",
            setOf("core_bronze", "sig_thornwall"), listOf(setOf("core_bronze"), setOf("sig_thornwall"))),
        InteractionDef("salvage", "Salvage", "A weapon that cracks leaves Scrap and wakes the rules that wait for a crack. A Runestone answers for the whole party afterwards.",
            "A cracked weapon needs a repair after the fight.",
            setOf("core_obsidian", "affix_brittle", "catalyst_runed"), listOf(setOf("catalyst_runed"), setOf("relic_salvage_bell"))),
        InteractionDef("marks", "Marked for the kill", "The party aims at a marked enemy, and the hit that spends the Mark lands harder. Starsteel adds to that hit.",
            "One Mark at a time. Whoever strikes next spends it.",
            setOf("element_sun", "core_starsteel", "sig_nightletter"), listOf(setOf("core_starsteel"), setOf("sig_nightletter"))),
        InteractionDef("shatter", "Cold to break", "Chill on an enemy is something to use. Moonsteel bites into it and a heavy weapon shatters it.",
            "The enemy's own strike spends Chill first. Put it on and use it in the same round.",
            setOf("core_moonsteel", "affix_heavy_shatter", "sig_winterwake"), listOf(setOf("core_moonsteel"), setOf("affix_heavy_shatter"), setOf("sig_winterwake"))),
    )

    // ---- elements: what a blade does on a hit even with no affix on it ----
    val elementEffects: Map<Element, List<EffectDef>> = mapOf(
        Element.FIRE to listOf(e("element_fire", "Ember", onOwnHit, listOf(Give(Stat.BURN, Aim.EventTarget)), Limit(perRound = 1), "The wielder's first hit each round puts 1 Burn on its target.", struck)),
        Element.FROST to listOf(e("element_frost", "Rime", onOwnHit, listOf(Give(Stat.CHILL, Aim.EventTarget)), Limit(perRound = 1), "The wielder's first hit each round puts 1 Chill on its target.", struck)),
        Element.STORM to listOf(e("element_storm", "Arc", onOwnHit, listOf(Damage(Aim.BackFoe, Fixed(2), Element.STORM)), Limit(perRound = 1), "The wielder's first hit each round arcs to the last enemy in the line for 2.", struck)),
        Element.GRAVE to listOf(e("element_grave", "Grave chill", onOwnHit, listOf(Give(Stat.BLEED, Aim.EventTarget)), Limit(perRound = 1), "The wielder's first hit each round puts 1 Bleed on its target.", struck)),
        Element.VERDANT to listOf(e("element_verdant", "Sap", onOwnHit, listOf(Give(Stat.REGEN, Aim.Self)), Limit(perRound = 1), "The wielder's first hit each round gives the wielder 1 Regeneration.", struck)),
        Element.SUN to listOf(e("element_sun", "Noon light", onOwnHit, listOf(Give(Stat.MARK, Aim.EventTarget)), Limit(perRound = 1), "The wielder's first hit each round leaves a Mark on its target.", struck)),
    )

    // ---- affixes, by the IDs they have always had ----
    val affixEffects: Map<AffixId, List<EffectDef>> = mapOf(
        LaunchContent.FLAMING to listOf(e("affix_flaming", "Flaming", onOwnHit, listOf(Give(Stat.BURN, Aim.EventTarget)), Limit(perRound = 1), "The wielder's first hit each round puts 1 more Burn on its target.", struck)),
        LaunchContent.FROSTBOUND to listOf(e("affix_frostbound", "Frostbound", onOwnHit, listOf(Give(Stat.CHILL, Aim.EventTarget)), Limit(perRound = 1), "The wielder's first hit each round puts 1 more Chill on its target.", struck)),
        LaunchContent.STORMCHARGED to listOf(
            e("affix_stormcharged", "Stormcharged", onOwnHit, listOf(Give(Stat.CHARGE, Aim.Self)), Limit(perRound = 1), "The wielder's first hit each round stores 1 Charge.", struck),
            CHARGE_BURST),
        LaunchContent.VAMPIRIC to listOf(e("affix_vampiric", "Vampiric", Trigger(EventKind.DOWNED, Who.SELF_SOURCE), listOf(Heal(Aim.Self, Fixed(6))), Limit(perRound = 1), "When the wielder downs an enemy, heals the wielder for 6. Once a round.")),
        LaunchContent.SWIFT to listOf(e("affix_swift", "Swift", Trigger(EventKind.ACTED, Who.SELF_SOURCE), listOf(Damage(Aim.Foe, Strike(30))), Limit(perRound = 1), "After the wielder acts, one more quick cut for a third of a strike. Once a round.", struck)),
        LaunchContent.GIANT_SLAYER to listOf(e("affix_giant_slayer", "Giant Slayer", onOwnHit, listOf(Damage(Aim.EventTarget, Fixed(4))), Limit(perRound = 1), "The wielder's first hit each round on an elite or a leader deals 4 more.", struck + Condition.TargetTagged(TAG_ELITE))),
        LaunchContent.UNDEAD_BANE to listOf(e("affix_undead_bane", "Undead Bane", onOwnHit, listOf(Damage(Aim.EventTarget, Fixed(4), Element.SUN)), Limit(perRound = 1), "The wielder's first hit each round on one of the Hollowbound deals 4 more.", struck + Condition.TargetTagged(TAG_UNDEAD))),
        LaunchContent.GUARDIANS to listOf(e("affix_guardians", "Guardian's", Trigger(EventKind.GUARD_GAINED, Who.SELF_TARGET), listOf(Guard(Aim.Self, Fixed(2))), Limit(perRound = 1), "When the wielder raises Guard by its own action, raises 2 more. Once a round.", struck)),
        LaunchContent.REINFORCED to listOf(e("affix_reinforced", "Reinforced", Trigger(EventKind.ROUND_START, Who.ANY), listOf(Guard(Aim.Self, Fixed(4))), Limit(perFight = 1), "The wielder enters a fight with 4 Guard.")),
        LaunchContent.RESONANT to listOf(e("affix_resonant", "Resonant", Trigger(EventKind.HEALED, Who.SELF_SOURCE), listOf(Give(Stat.REGEN, Aim.EventTarget)), Limit(perRound = 1), "When the wielder's own action heals an ally, that ally also gains 1 Regeneration. Once a round.", struck)),
        // Flaws: a cost, and for the right party a piece of a build.
        LaunchContent.BRITTLE to listOf(e("affix_brittle", "Brittle", Trigger(EventKind.GUARD_BROKEN, Who.SELF_TARGET), listOf(Action.Fracture(2)), Limit(perFight = 1),
            "The first time an enemy breaks the wielder's Guard, the weapon cracks: 2 Scrap now, a repair after the fight. Guard the wielder gives up itself never cracks it.", listOf(Condition.SourceIsFoe))),
        LaunchContent.HEAVY to listOf(
            e("affix_heavy_slow", "Heavy", Trigger(EventKind.ROUND_START, Who.ANY), listOf(Give(Stat.CHILL, Aim.Self)), Limit(perFight = 1), "The wielder starts a fight slowed: its first strike is weaker."),
            e("affix_heavy_shatter", "Shatter", onOwnHit, listOf(Action.Take(Stat.CHILL, Aim.EventTarget, Fixed(3)), Damage(Aim.EventTarget, Fixed(6))), Limit(perRound = 1),
                "The wielder's first hit each round on a chilled enemy shatters up to 3 Chill on it for 6 more damage.", struck + Condition.TargetHas(Stat.CHILL))),
        LaunchContent.UNSTABLE to listOf(e("affix_unstable", "Unstable", Trigger(EventKind.DAMAGE, Who.SELF_TARGET), listOf(Damage(Aim.AllFoes, Fixed(5))), Limit(perFight = 1),
            "The first time the wielder is at half health or less after a hit, the weapon discharges: 3 health from the wielder, 5 damage to every enemy.", listOf(Condition.HolderHealthAtMost(50)), Cost.PayHealth(3, floor = 1))),
        LaunchContent.CURSED to listOf(e("affix_cursed", "Cursed", Trigger(EventKind.ROUND_START, Who.ANY), listOf(Give(Stat.BLEED, Aim.Self, Fixed(2))), Limit(perFight = 1), "The wielder starts a fight with 2 Bleed.")),
        LaunchContent.BLOODBOUND to listOf(e("affix_bloodbound", "Bloodbound", Trigger(EventKind.ACTION, Who.SELF_SOURCE), listOf(Damage(Aim.Foe, Amount.OfSpent(2))), Limit(perRound = 1),
            "Before the wielder acts, it pays up to 4 health (never below 12) and strikes for twice what it paid. Paying is not damage: nothing answers it.", struck, Cost.PayHealth(4, floor = 12))),
    )
    val affixHomeEffects: Map<AffixId, List<EffectDef>> = mapOf(
        LaunchContent.SENTIENT to listOf(e("affix_sentient_home", "Sentient (at home)", Trigger(EventKind.ROUND_START, Who.ANY), listOf(Guard(Aim.AllAllies, Fixed(2))), Limit(perFight = 1), "Defending its home, the weapon rallies the line: 2 Guard to every ally at the start.")),
    )
    val affixRoadEffects: Map<AffixId, List<EffectDef>> = mapOf(
        LaunchContent.SENTIENT to listOf(e("affix_sentient_road", "Sentient (on the road)", Trigger(EventKind.ROUND_START, Who.ANY), listOf(Give(Stat.CHILL, Aim.Self)), Limit(perFight = 1), "Away from home the weapon balks: the wielder starts slowed.")),
    )

    // ---- catalysts: how the blade's rule behaves ----
    val CAPACITOR = e("catalyst_capacitor", "Capacitor", Trigger(EventKind.HEALED, Who.SELF_TARGET), listOf(Give(Stat.CHARGE, Aim.Self)), Limit(perRound = 1),
        "When the wielder is healed for at least 1, the weapon stores 1 Charge. Once a round. Healing at full health stores nothing.")
    val RUNED = e("catalyst_runed", "Rune of answer", Trigger(EventKind.GUARD_BROKEN, Who.ALLY_TARGET), listOf(Damage(Aim.EventSource, Fixed(5))), Limit(perRound = 1, perFight = 2),
        "After a weapon of the party has cracked, when an enemy breaks an ally's Guard the wielder strikes that enemy for 5. Once a round, twice a fight.", listOf(Condition.SourceIsFoe, Condition.Happened(EventKind.FRACTURED)))
    val SPREADING = e("catalyst_spreading", "Dragon oil", Trigger(EventKind.STAT_GAINED, Who.SELF_SOURCE), listOf(Action.Give(Stat.BURN, Aim.AllFoes)), Limit(perRound = 1),
        "When the wielder's hit puts Burn or Chill on an enemy, every enemy gets 1 Burn. Once a round.", listOf(Condition.StatIn(setOf(Stat.BURN, Stat.CHILL)), Condition.NotTick))
    /**
     * Dragon Oil on a weapon forged since the metals had rules: it spreads the fire its wielder lights and does nothing
     * with Chill. The older rule also answered Chill with Burn on every enemy, so one frost weapon boiled its own Chill
     * into Steam every round and Frost Bloom with Dragon Oil was in the top tenth of 60% of the lab's scenarios, half
     * again as often as any other pair. Steam is meant to take a fire and a cold that meet.
     */
    val SPREADING_FIRE = e("catalyst_spreading_fire", "Dragon oil", Trigger(EventKind.STAT_GAINED, Who.SELF_SOURCE), listOf(Action.Give(Stat.BURN, Aim.AllFoes)), Limit(perRound = 1),
        "When the wielder's hit puts Burn on an enemy, every enemy gets 1 Burn. Once a round.", listOf(Condition.StatIn(setOf(Stat.BURN)), Condition.NotTick))
    val CONVERTING = e("catalyst_converting", "Void ink", Trigger(EventKind.ROUND_END, Who.ANY), listOf(Action.Take(Stat.BURN, Aim.PriorityFoe, Fixed(3))), Limit(perRound = 1),
        "At the end of each round, takes up to 3 Burn off the enemy that matters most.")
    val CONVERTED_GUARD = e("catalyst_converted_guard", "Void ink (Guard)", Trigger(EventKind.STAT_SPENT, Who.SELF_SOURCE, Stat.BURN), listOf(Guard(Aim.WeakestAlly, Amount.OfEvent())), Limit(perRound = 1),
        "The Burn the weapon took becomes that much Guard on the weakest ally. The Burn no longer burns.")
    // Boiling Point (spec 8.3): Steam is what the wielder gets for putting heat on cold or wet (or cold on heat). The two catalysts spend it differently; a blade has one.
    val STEAM_LANCE = e("catalyst_steam_lance", "Steam lance", Trigger(EventKind.STAT_GAINED, Who.SELF_TARGET, Stat.STEAM), listOf(Damage(Aim.Foe, Fixed(5), pierce = true), Damage(Aim.BackFoe, Fixed(5), pierce = true)), Limit(perRound = 1),
        "When the wielder gains Steam, spends 1: 5 damage through Guard to the foe in front and 5 to the last in the line. Once a round.", cost = Cost.Spend(Stat.STEAM, 1))
    val STEAM_SHIELD = e("catalyst_steam_shield", "Steam veil", Trigger(EventKind.STAT_GAINED, Who.SELF_TARGET, Stat.STEAM), listOf(Guard(Aim.AllAllies, Fixed(3))), Limit(perRound = 1),
        "When the wielder gains Steam, spends 1: every ally gains 3 Guard. Once a round.", cost = Cost.Spend(Stat.STEAM, 1))
    /**
     * Void Ink as it is forged from now on. The old rule took Burn at the end of a round, after that round's Burn had
     * already ticked and faded, and only from the enemy that matters most, which the wielder is seldom striking. One
     * Burn a round never reached it. This one drinks straight after the wielder's own action, from the enemy it faces.
     */
    val DRINKING = e("catalyst_drinking", "Void ink", Trigger(EventKind.ACTED, Who.SELF_SOURCE), listOf(Action.Take(Stat.BURN, Aim.Foe, Fixed(3))), Limit(perRound = 1),
        "After the wielder acts, takes up to 3 Burn off the enemy it faces.")
    val revisions: Map<String, EffectDef> = mapOf(CONVERTING.id to DRINKING, SPREADING.id to SPREADING_FIRE)
    val catalystEffects: Map<MaterialId, List<EffectDef>> = mapOf(
        LaunchContent.BINDING_SALT to listOf(CAPACITOR, CHARGE_BURST),
        LaunchContent.RUNESTONE_SHARD to listOf(RUNED),
        LaunchContent.DRAGON_OIL to listOf(SPREADING, STEAM_LANCE),
        LaunchContent.VOID_INK to listOf(CONVERTING, CONVERTED_GUARD, STEAM_SHIELD),
    )

    // ---- signatures with a rule of their own (spec 7.4); the other eighteen keep their granted affixes ----
    val signatureEffects: Map<String, List<EffectDef>> = mapOf(
        "thornwall" to listOf(e("sig_thornwall", "Thornwall", Trigger(EventKind.GUARD_BROKEN, Who.SELF_TARGET), listOf(Give(Stat.THORNS, Aim.Self, Amount.OfEvent(50, cap = 6))), Limit(perRound = 1),
            "Half of the Guard an enemy breaks on the wielder becomes Thorns (at most 6): the next enemy to strike the wielder takes them.", listOf(Condition.SourceIsFoe))),
        "stormcaller" to listOf(
            e("sig_stormcaller", "Stormcaller", Trigger(EventKind.STAT_SPENT, Who.ALLY_SOURCE, Stat.CHARGE), listOf(Damage(Aim.AllFoes, Fixed(3), Element.STORM)), Limit(perRound = 1),
                "When an ally spends Charge, the staff chains 3 storm damage through every enemy. Once a round.")),
        "mourning_rod" to listOf(e("sig_mourning_rod", "Mourning Rod", Trigger(EventKind.DOWNED, Who.FOE_TARGET), listOf(Action.Summon(SPECTRAL_HELPER)), Limit(perFight = 1),
            "The first enemy to fall rises as one spectral helper for the party. One helper a fight. It leaves when the fight ends.")),
        "winterwake" to listOf(e("sig_winterwake", "Winterwake", onOwnHit, listOf(Action.Take(Stat.CHILL, Aim.EventTarget, Fixed(2)), Guard(Aim.WeakestAlly, Fixed(5))), Limit(perRound = 1),
            "The wielder's first hit each round on a chilled enemy takes up to 2 Chill off it and gives the weakest ally 5 Guard.", struck + Condition.TargetHas(Stat.CHILL))),
        "dawnbrand" to listOf(
            e("sig_dawnbrand", "Dawnbrand", Trigger(EventKind.ROUND_END, Who.ANY), listOf(Action.Take(Stat.BURN, Aim.PriorityFoe, Fixed(2))), Limit(perRound = 1),
                "At the end of each round, takes up to 2 Burn off the enemy that matters most."),
            e("sig_dawnbrand_guard", "Dawnbrand (Guard)", Trigger(EventKind.STAT_SPENT, Who.SELF_SOURCE, Stat.BURN), listOf(Guard(Aim.AllAllies, Amount.OfEvent())), Limit(perRound = 1),
                "The Burn the weapon took becomes that much Guard on every ally. The Burn no longer burns.")),
        "nightletter" to listOf(e("sig_nightletter", "Nightletter", Trigger(EventKind.STAT_SPENT, Who.SELF_SOURCE, Stat.MARK), listOf(Damage(Aim.EventTarget, Fixed(5), Element.GRAVE)), Limit(perRound = 1),
            "When the wielder's hit spends a Mark, it cuts for 5 more. Once a round.")),
    )

    // ---- relics that are party rules (spec 9.2) ----
    val relicEffects: Map<String, List<EffectDef>> = mapOf(
        OVERFLOW_BASIN to listOf(e("relic_overflow_basin", "Overflow Basin", Trigger(EventKind.OVERHEAL, Who.ALLY_TARGET), listOf(Guard(Aim.EventTarget, Amount.OfEvent(cap = 4))), Limit(perRound = 1),
            "Healing an ally cannot use becomes Guard on them, up to 4 a round each. It is not healing: nothing that answers healing sees it.", party = true, perMember = true)),
        SALVAGE_BELL to listOf(e("relic_salvage_bell", "Salvage Bell", Trigger(EventKind.FRACTURED, Who.ALLY_SOURCE), listOf(Guard(Aim.AllAllies, Fixed(3))), Limit(perFight = 1),
            "When an ally's weapon cracks, its 2 Scrap are spent and every ally gains 3 Guard. Once a fight for each cracked weapon.", cost = Cost.Spend(Stat.SCRAP, 2), party = true, perMember = true)),
        STORM_LEDGER to listOf(e("relic_storm_ledger", "Storm Ledger", Trigger(EventKind.STAT_SPENT, Who.ALLY_SOURCE, Stat.CHARGE), listOf(Give(Stat.REGEN, Aim.WeakestAlly)), Limit(perRound = 1),
            "The first time an ally spends Charge in a round, the weakest ally gains 1 Regeneration. It never stores or spends Charge itself.", party = true)),
        FURNACE_LUNG to listOf(e("relic_furnace_lung", "Furnace Lung", Trigger(EventKind.STAT_SPENT, Who.ALLY_SOURCE, Stat.BURN), listOf(Guard(Aim.AllAllies, Fixed(2))), Limit(perRound = 1),
            "When an ally takes Burn off an enemy, every ally gains 2 Guard. Once a round. The Burn taken deals no more damage.", party = true)),
        BONE_MUSIC_BOX to listOf(e("relic_bone_music_box", "Bone Music Box", Trigger(EventKind.DOWNED, Who.FOE_TARGET), listOf(Action.Summon(SPECTRAL_HELPER)), Limit(perFight = 1),
            "The first enemy to fall in a fight rises as one spectral helper for the party. One a fight. It brings nothing home.", party = true)),
        BLOOD_RECEIPT to listOf(e("relic_blood_receipt", "Blood Receipt", Trigger(EventKind.HEALED, Who.ALLY_TARGET), listOf(Guard(Aim.EventTarget, Amount.OfEvent(cap = 4))), Limit(perRound = 1),
            "After an ally has paid health to a weapon, healing that ally also gives them that much Guard, up to 4 a round each.", listOf(Condition.Happened(EventKind.HEALTH_PAID)), party = true, perMember = true)),
        BLUNT_OATH to listOf(e("relic_blunt_oath", "The Blunt Oath", Trigger(EventKind.ACTED, Who.ALLY_SOURCE), listOf(Damage(Aim.Foe, Fixed(3)), Guard(Aim.Self, Fixed(2))), Limit(perRound = 1),
            "A member carrying a common or uncommon weapon (or none) strikes for 3 more and raises 2 Guard when they act. A finer weapon in their hands ends it for them.", struck + Condition.HolderTagged(TAG_COMMON_GEAR), party = true, perMember = true)),
        WIDOWS_WHETSTONE to listOf(e("relic_widows_whetstone", "Widow's Whetstone", Trigger(EventKind.ACTED, Who.ALLY_SOURCE), listOf(Damage(Aim.Foe, Amount.OfSpent(3))), Limit(perRound = 1),
            "A pact. When a member acts they pay 2 health (never below 6) and strike for 6 more. Paying is not damage.", struck, Cost.PayHealth(2, floor = 6), party = true, perMember = true)),
        OATHBREAKERS_COIN to listOf(e("relic_oathbreakers_coin", "Oathbreaker's Coin", Trigger(EventKind.ACTED, Who.ALLY_SOURCE), listOf(Guard(Aim.Self, Fixed(3)), Give(Stat.BLEED, Aim.Self)), Limit(perRound = 1),
            "A pact. When a member acts they raise 3 Guard and take 1 Bleed.", struck, party = true, perMember = true)),
        // The Coward's Medal changes what a retreat brings home, not the fight: `Missions` reads it.
        COWARDS_MEDAL to emptyList(),
    )

    // ---- units ----
    private fun kit(id: String, name: String, vararg moves: Move, passives: List<EffectDef> = emptyList(), tags: Set<String> = emptySet()) = KitDef(id, name, moves.toList(), passives, tags)
    private fun strikeMove(id: String, name: String, vararg more: Action) = Move(id, name, Cadence.Always, listOf<Action>(Damage(Aim.Foe, Strike())) + more)

    private val ROUT = e("ashclaw_rout", "Rout", Trigger(EventKind.DOWNED, Who.ALLY_TARGET), listOf(Give(Stat.CHILL, Aim.Self, Fixed(2))), Limit(perFight = 1),
        "When the warchief falls, the raiders lose heart: 2 Chill.", listOf(Condition.TargetTagged(TAG_LEADER)))

    val units: List<UnitDef> = listOf(
        // Called by an effect; never brings anything home.
        UnitDef(SPECTRAL_HELPER, "Spectral helper", kit("spectral_helper", "Spectral helper", Move("haunt", "Haunt", Cadence.Always, listOf(Give(Stat.MARK, Aim.PriorityFoe), Damage(Aim.Foe, Strike())))), health = 10, strikeMin = 3, tags = setOf(TAG_SUMMONED)),

        // Ashclaw Raiders: several weak attackers around a leader who is worth reaching.
        UnitDef("ashclaw_scout", "Ashclaw scout", kit("ashclaw_scout", "Scout", strikeMove("slash", "Slash"), passives = listOf(ROUT)), health = 12, strikeMin = 3, strikeMax = 5, weakTo = Element.FROST, resists = Element.FIRE),
        UnitDef("ashclaw_brute", "Ashclaw brute", kit("ashclaw_brute", "Brute", strikeMove("club", "Club"), passives = listOf(ROUT)), health = 22, strikeMin = 5, strikeMax = 8, weakTo = Element.FROST, resists = Element.FIRE),
        UnitDef("ashclaw_thief", "Ashclaw thief", kit("ashclaw_thief", "Thief", Move("cutpurse", "Cut and run", Cadence.Always, listOf(Damage(Aim.BackFoe, Strike())), "Strikes the last member of the party, past the front."), passives = listOf(ROUT)),
            health = 14, strikeMin = 4, strikeMax = 6, weakTo = Element.FROST, resists = Element.FIRE),
        UnitDef("ashclaw_warchief", "Ashclaw warchief", kit("ashclaw_warchief", "Warchief",
            Move("war_cry", "War cry", Cadence.Every(3, first = 2), listOf(Guard(Aim.AllAllies, Fixed(4))), "Every third round, from the second, gives every raider 4 Guard."),
            strikeMove("cleave", "Cleave")), health = 36, strikeMin = 6, strikeMax = 9, tags = setOf(TAG_LEADER, TAG_ELITE), weakTo = Element.FROST, resists = Element.FIRE),
        UnitDef("warlord_krag", "Warlord Krag", kit("warlord_krag", "Warlord",
            Move("war_cry", "War cry", Cadence.Every(3, first = 2), listOf(Guard(Aim.AllAllies, Fixed(5))), "Every third round, from the second, gives every raider 5 Guard."),
            Move("reave", "Reave", Cadence.Always, listOf(Damage(Aim.Foe, Strike()), Damage(Aim.BackFoe, Strike(50))))), health = 70, strikeMin = 8, strikeMax = 11,
            tags = setOf(TAG_LEADER, TAG_ELITE), weakTo = Element.FROST, resists = Element.FIRE),

        // Hollowbound: they come back while their anchor stands.
        UnitDef("hollow_shambler", "Hollowbound shambler", kit("hollow_shambler", "Shambler", strikeMove("claw", "Claw")), health = 15, strikeMin = 3, strikeMax = 5, tags = setOf(TAG_UNDEAD),
            weakTo = Element.SUN, resists = Element.GRAVE, tickResist = mapOf(Stat.BLEED to 50)),
        UnitDef("bone_warden", "Bone warden", kit("bone_warden", "Bone warden", Move("shield_wall", "Shield wall", Cadence.Always, listOf(Guard(Aim.Self, Fixed(3)), Damage(Aim.Foe, Strike())))), health = 26, strikeMin = 4, strikeMax = 6,
            tags = setOf(TAG_UNDEAD), weakTo = Element.SUN, resists = Element.GRAVE, tickResist = mapOf(Stat.BLEED to 50)),
        UnitDef("gravecaller", "Gravecaller", kit("gravecaller", "Gravecaller",
            Move("raise", "Raise the fallen", Cadence.Every(3, first = 3), listOf(Action.Summon("hollow_shambler")), "Every third round raises a shambler while there is room in the line."),
            Move("knit", "Knit bone", Cadence.Always, listOf(Heal(Aim.WeakestAlly, Support()), Damage(Aim.Foe, Strike())), "Every round mends the weakest of the dead.")),
            health = 22, strikeMin = 2, strikeMax = 3, support = 5, tags = setOf(TAG_ANCHOR, TAG_UNDEAD), weakTo = Element.SUN, resists = Element.GRAVE, tickResist = mapOf(Stat.BLEED to 50)),
        UnitDef("pale_knight", "The Pale Knight", kit("pale_knight", "Pale Knight", Move("grave_blade", "Grave weapon", Cadence.Always, listOf(Damage(Aim.Foe, Strike()), Give(Stat.REGEN, Aim.Self)), "Every strike renews it.")),
            health = 44, strikeMin = 6, strikeMax = 9, tags = setOf(TAG_ELITE, TAG_UNDEAD), weakTo = Element.SUN, resists = Element.GRAVE, tickResist = mapOf(Stat.BLEED to 50)),
        UnitDef("hollow_king", "The Hollow King", kit("hollow_king", "Hollow King",
            Move("court", "Summon the court", Cadence.Every(3, first = 2), listOf(Action.Summon("hollow_shambler")), "Every third round, from the second, raises a shambler."),
            Move("crown", "Crown of ash", Cadence.Always, listOf(Damage(Aim.Foe, Strike()), Heal(Aim.Self, Fixed(4))), "Every strike mends him for 4.")),
            health = 76, strikeMin = 7, strikeMax = 10, tags = setOf(TAG_ANCHOR, TAG_LEADER, TAG_ELITE, TAG_UNDEAD), weakTo = Element.SUN, resists = Element.GRAVE, tickResist = mapOf(Stat.BLEED to 50)),

        // Embermaw Brood: slow, guarded, and one blow that has to be survived.
        UnitDef("ember_whelp", "Embermaw whelp", kit("ember_whelp", "Whelp", strikeMove("nip", "Nip", Give(Stat.BURN, Aim.Foe))), health = 12, strikeMin = 2, strikeMax = 4, weakTo = Element.FROST, resists = Element.FIRE, tickResist = mapOf(Stat.BURN to 50)),
        UnitDef("cinder_knight", "Cinder-knight", kit("cinder_knight", "Cinder-knight", Move("ember_guard", "Ember guard", Cadence.Always, listOf(Guard(Aim.Self, Fixed(4)), Damage(Aim.Foe, Strike())))), health = 26, strikeMin = 5, strikeMax = 7,
            weakTo = Element.FROST, resists = Element.FIRE, tickResist = mapOf(Stat.BURN to 50)),
        UnitDef("ember_drake", "Embermaw drake", kit("ember_drake", "Drake",
            Move("fire_breath", "Fire breath", Cadence.Every(3, first = 3), listOf(Damage(Aim.AllFoes, Strike(130))), "Every third round breathes fire on the whole party. A chilled drake breathes weaker."),
            Move("bite", "Bite", Cadence.Always, listOf(Guard(Aim.Self, Fixed(3)), Damage(Aim.Foe, Strike())))), health = 34, strikeMin = 5, strikeMax = 8, tags = setOf(TAG_ELITE),
            weakTo = Element.FROST, resists = Element.FIRE, tickResist = mapOf(Stat.BURN to 50)),
        UnitDef("broodmother", "Broodmother Vyrash", kit("broodmother", "Broodmother",
            Move("inferno", "Inferno", Cadence.Every(3, first = 3), listOf(Damage(Aim.AllFoes, Strike(150)), Give(Stat.BURN, Aim.AllFoes)), "Every third round burns the whole party. A chilled Broodmother burns weaker."),
            Move("rend", "Rend", Cadence.Always, listOf(Guard(Aim.Self, Fixed(4)), Damage(Aim.Foe, Strike())))), health = 84, strikeMin = 7, strikeMax = 10, tags = setOf(TAG_LEADER, TAG_ELITE),
            weakTo = Element.FROST, resists = Element.FIRE, tickResist = mapOf(Stat.BURN to 50)),

        // What a party protects on an escort or carries out of a rescue: it does nothing but stand.
        UnitDef("escort_cart", "The supply cart", kit("escort_cart", "Cart", Move("roll", "Rolls on", Cadence.Always, emptyList())), health = 30, strikeMin = 0),
        UnitDef("town_watch", "A guard of the watch", kit("town_watch", "Watch", strikeMove("halberd", "Halberd")), health = 22, strikeMin = 4, strikeMax = 6),
        UnitDef("captive", "The captive", kit("captive", "Captive", Move("stumble", "Keeps moving", Cadence.Always, emptyList())), health = 24, strikeMin = 0),
    )

    val catalog = CombatCatalog(kits, families, elementEffects, affixEffects, affixHomeEffects, affixRoadEffects, catalystEffects, signatureEffects, relicEffects, units, cores = cores, notes = notes, interactions = interactions, revisions = revisions)
}
