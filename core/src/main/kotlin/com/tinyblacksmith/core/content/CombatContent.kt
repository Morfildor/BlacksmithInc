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

    // Player-facing phrases for the aims the texts below name, as `Fight.targets` resolves them, and the rule numbers they quote.
    private const val ATTACK_TARGET = "the marked enemy, or the front enemy if none is marked"
    private const val PRIORITY_TARGET = "the priority target (an enemy summoner first, then the enemy leader, then the enemy with the most health)"
    private const val WEAKEST_ALLY = "the weakest ally (lowest health percentage)"
    private val RULES = FightRules()

    private val onOwnHit = Trigger(EventKind.DAMAGE, Who.SELF_SOURCE)
    private val struck = listOf(Condition.FromAction)

    // ---- the shared Charge burst: every rule that stores Charge on a blade spends it through this one ----
    val CHARGE_BURST = e("charge_burst", "Charge burst", Trigger(EventKind.STAT_GAINED, Who.SELF_TARGET, Stat.CHARGE), listOf(Damage(Aim.Foe, Fixed(8), Element.STORM)), Limit(perRound = 1),
        "When the wielder gains Charge and then has 3 or more, the wielder spends all of it to deal 8 storm damage to ${ATTACK_TARGET}. Once per round.", cost = Cost.Spend(Stat.CHARGE, 3, all = true))

    // ---- classes ----
    val GUARDIAN_KIT = KitDef("guardian", "Guardian", tags = setOf(TAG_INTERCEPT), moves = listOf(
        Move("hold_the_line", "Hold the line", Cadence.Always, listOf(Guard(Aim.Self, Support()), Damage(Aim.Foe, Strike()))),
    ), passives = listOf(
        e("guardian_riposte", "Riposte", Trigger(EventKind.GUARD_BROKEN, Who.SELF_TARGET), listOf(Damage(Aim.EventSource, Strike(50))), Limit(perRound = 1),
            "When an enemy breaks this Guardian's Guard, the Guardian strikes that enemy back for half its strike. Once per round.", listOf(Condition.SourceIsFoe)),
    ))
    val WARDEN_KIT = KitDef("warden", "Warden", moves = listOf(
        Move("mend", "Mend", Cadence.Always, listOf(Heal(Aim.WeakestAlly, Support()), Damage(Aim.Foe, Strike(60)))),
    ))
    val BATTLEMAGE_KIT = KitDef("battlemage", "Battlemage", moves = listOf(
        Move("opening_sigil", "Opening sigil", Cadence.Opener, listOf(Give(Stat.CHARGE, Aim.AllyTagged(TAG_CHARGE)), Give(Stat.MARK, Aim.PriorityFoe), Damage(Aim.Foe, Strike(70)))),
        Move("bolt", "Bolt", Cadence.Always, listOf(Damage(Aim.Foe, Strike()))),
    ), passives = listOf(
        e("battlemage_echo", "Echo", Trigger(EventKind.STAT_GAINED, Who.FOE_TARGET), listOf(Action.Echo(1)), Limit(perRound = 1),
            "When any enemy gains Burn, Chill or Wet, that enemy gains 1 more of the same. Once per round. This extra stack never triggers another Echo.", listOf(Condition.StatIn(setOf(Stat.BURN, Stat.CHILL, Stat.WET))), echo = true),
    ))
    val RANGER_KIT = KitDef("ranger", "Ranger", moves = listOf(
        Move("mark_and_loose", "Mark and loose", Cadence.Always, listOf(Damage(Aim.Foe, Strike()), Give(Stat.MARK, Aim.PriorityFoe))),
    ))
    val DUELIST_KIT = KitDef("duelist", "Duelist", moves = listOf(
        Move("double_cut", "Double cut", Cadence.Always, listOf(Damage(Aim.Foe, Strike(60)), Damage(Aim.Foe, Strike(60)))),
    ))

    val kits = listOf(
        ClassKitDef(LaunchContent.GUARDIAN, GUARDIAN_KIT, health = 40, healthPerLevel = 3, strike = 4, strikePerLevel = 1, support = 4, supportPerLevel = 1,
            role = "Gains Guard every round, then attacks. While the Guardian has Guard, enemies attack it instead of the front member. When an enemy breaks its Guard, it strikes back.",
            forgeHint = "Weapons that add Guard. Pairs well with effects that trigger when Guard breaks."),
        ClassKitDef(LaunchContent.WARDEN, WARDEN_KIT, health = 32, healthPerLevel = 2, strike = 4, strikePerLevel = 1, support = 6, supportPerLevel = 1,
            role = "Every round, heals the ally with the lowest health percentage, then attacks for 60% of its strike.",
            forgeHint = "Weapons with effects that trigger on healing, and weapons that cost their wielder health."),
        ClassKitDef(LaunchContent.BATTLEMAGE, BATTLEMAGE_KIT, health = 26, healthPerLevel = 2, strike = 6, strikePerLevel = 1, support = 2, supportPerLevel = 0,
            role = "In the first round, gives 1 Charge to the first ally whose weapon has a Charge burst, puts a Mark on ${PRIORITY_TARGET} and attacks for 70% of its strike. Then attacks every round. When any enemy gains Burn, Chill or Wet, adds 1 more. Once per round.",
            forgeHint = "Weapons with an element. When an enemy gains Burn, Chill or Wet, the Battlemage adds 1 more."),
        ClassKitDef(LaunchContent.RANGER, RANGER_KIT, health = 28, healthPerLevel = 2, strike = 6, strikePerLevel = 1, support = 2, supportPerLevel = 0,
            role = "Attacks, then puts a Mark on ${PRIORITY_TARGET}. The party attacks a marked enemy first, and the next hit on it deals ${RULES.markBonus} extra damage.",
            forgeHint = "Weapons with effects that use a Mark, such as bonus damage when the wielder consumes one."),
        ClassKitDef(LaunchContent.DUELIST, DUELIST_KIT, health = 30, healthPerLevel = 2, strike = 5, strikePerLevel = 1, support = 2, supportPerLevel = 0,
            role = "Attacks twice every round, each hit for 60% of its strike.",
            forgeHint = "Weapons with effects that trigger on a hit. Two hits a round give an effect with a condition two chances to trigger."),
    )

    // ---- weapon families: the delivery pattern ----
    val families = listOf(
        FamilyCombatDef(LaunchContent.SWORD, 100, 100, emptyList(), "One clean strike. 100% strike and support."),
        FamilyCombatDef(LaunchContent.AXE, 120, 90, emptyList(), "A heavier strike: 120% strike, 90% support."),
        FamilyCombatDef(LaunchContent.SPEAR, 95, 100, listOf(
            e("spear_reach", "Reach", Trigger(EventKind.GUARD_ABSORBED, Who.SELF_SOURCE), listOf(Damage(Aim.EventTarget, Amount.OfEvent(50), pierce = true)), Limit(perRound = 1),
                "When an enemy's Guard blocks the wielder's attack, half of the blocked damage (rounded up) goes through to that enemy, ignoring Guard. Once per round.", struck)), "95% strike. Reaches past Guard."),
        FamilyCombatDef(LaunchContent.BOW, 90, 100, listOf(
            e("bow_volley", "Second arrow", Trigger(EventKind.ACTED, Who.SELF_SOURCE), listOf(Damage(Aim.BackFoe, Strike(35))), Limit(perRound = 1),
                "After the wielder acts, a second arrow hits the back enemy for 35% of the wielder's strike. Once per round.", struck)), "90% strike. A second arrow at the back of the enemy line."),
        FamilyCombatDef(LaunchContent.DAGGER, 80, 100, listOf(
            e("dagger_flurry", "Off-hand cut", Trigger(EventKind.ACTED, Who.SELF_SOURCE), listOf(Damage(Aim.Foe, Strike(45))), Limit(perRound = 1),
                "After the wielder acts, the wielder cuts ${ATTACK_TARGET} again for 45% of its strike. Once per round.", struck)), "80% strike, and one extra light cut each round."),
        FamilyCombatDef(LaunchContent.STAFF, 80, 130, emptyList(), "A lighter strike, much more support: 80% strike, 130% support."),
    )

    // ---- metals: the weapon's physical character. One reuse of an existing mechanic each, never a new one. ----
    /** The numbers of the six metal rules, in one place so that `ForgeLab` can fight the same recipes with others. */
    data class CoreNumbers(val ironGuard: Int = 3, val bronzeAnswer: Int = 5, val silverCharge: Int = 3, val obsidianStrike: Int = 110, val starsteelBonus: Int = 3, val moonsteelBonus: Int = 2)

    fun cores(n: CoreNumbers = CoreNumbers()) = listOf(
        CoreCombatDef(LaunchContent.IRON, "Plain and sturdy. The wielder starts every fight with ${n.ironGuard} Guard.", "The weakest metal. It adds the least to a strike.", listOf(
            e("core_iron", "Iron holds", Trigger(EventKind.ROUND_START, Who.ANY), listOf(Guard(Aim.Self, Fixed(n.ironGuard))), Limit(perFight = 1), "The wielder starts each fight with ${n.ironGuard} Guard."))),
        CoreCombatDef(LaunchContent.BRONZE, "Rings when struck. When an enemy breaks the wielder's Guard, the weapon strikes back.", "Does nothing for a wielder who never has Guard.", listOf(
            e("core_bronze", "Bronze rings back", Trigger(EventKind.GUARD_BROKEN, Who.SELF_TARGET), listOf(Damage(Aim.EventSource, Fixed(n.bronzeAnswer))), Limit(perRound = 1),
                "When an enemy breaks the wielder's Guard, the weapon deals ${n.bronzeAnswer} damage to that enemy. Once per round.", listOf(Condition.SourceIsFoe)))),
        CoreCombatDef(LaunchContent.SILVER, "Holds a charge. The wielder starts every fight with ${n.silverCharge} Charge, enough for one Charge burst.", "Charge does nothing until something on the weapon spends it: Binding Salt, or the Stormcharged affix.", listOf(
            e("core_silver", "Silver holds a charge", Trigger(EventKind.ROUND_START, Who.ANY), listOf(Give(Stat.CHARGE, Aim.Self, Fixed(n.silverCharge))), Limit(perFight = 1), "The wielder starts each fight with ${n.silverCharge} Charge."))),
        CoreCombatDef(LaunchContent.OBSIDIAN, "A glass edge. ${if (n.obsidianStrike >= 100) "+" else ""}${n.obsidianStrike - 100}% strike.", "It cracks the first time an enemy breaks the wielder's Guard. A cracked weapon keeps working, but needs repair after the fight. On a wielder who holds the front line, that happens in most fights.", listOf(
            e("core_obsidian", "Obsidian cracks", Trigger(EventKind.GUARD_BROKEN, Who.SELF_TARGET), listOf(Action.Fracture(2)), Limit(perFight = 1),
                "The first time an enemy breaks the wielder's Guard, the weapon cracks: the wielder gains 2 Scrap, and the weapon needs repair after the fight. Once per fight.", listOf(Condition.SourceIsFoe))), strikePercent = n.obsidianStrike),
        CoreCombatDef(LaunchContent.STARSTEEL, "Finds the marked place. Deals bonus damage when the wielder consumes a Mark.", "Costly. Its effect needs a Mark on the enemy for the wielder's own attack to consume.", listOf(
            e("core_starsteel", "Starsteel finds the Mark", Trigger(EventKind.STAT_SPENT, Who.SELF_SOURCE, Stat.MARK), listOf(Damage(Aim.EventTarget, Fixed(n.starsteelBonus))), Limit(perRound = 1),
                "When the wielder's own attack consumes a Mark, the weapon deals ${n.starsteelBonus} more damage to that enemy. Once per round.", struck))),
        CoreCombatDef(LaunchContent.MOONSTEEL, "Cold metal. It bites deeper into an enemy that is already chilled.", "The costliest metal. Its effect needs Chill on the target before the hit lands.", listOf(
            e("core_moonsteel", "Moonsteel bites the cold", onOwnHit, listOf(Damage(Aim.EventTarget, Fixed(n.moonsteelBonus))), Limit(perRound = 1),
                "When the wielder's attack damages an enemy that already has Chill, the weapon deals ${n.moonsteelBonus} more damage to that enemy. Once per round. The Chill stays.", struck + Condition.TargetHas(Stat.CHILL)))),
    )
    val cores = cores()

    /** Augments and catalysts as a choice at the forge. Their rules are [elementEffects] and [catalystEffects]. */
    val notes: Map<MaterialId, IngredientNote> = mapOf(
        LaunchContent.EMBER_RESIN to IngredientNote("Fire. Once per round, the wielder's attack puts 1 Burn on the enemy it damages. Burn deals damage at the end of each round.", "Ashclaw and Embermaw enemies resist fire, and Embermaw take only half damage from Burn."),
        LaunchContent.FROST_BLOOM to IngredientNote("Frost. Once per round, the wielder's attack puts 1 Chill on the enemy it damages. A chilled enemy's next attack deals ${RULES.chillPercent}% less damage.", "The attack it weakens consumes the Chill. Chill deals no damage by itself."),
        LaunchContent.VERDANT_SAP to IngredientNote("Living sap. Once per round, the wielder's attack gives the wielder 1 Regeneration, which heals ${RULES.regenHeal} at the end of the round.", "It heals only the wielder, and only a wielder who has been hurt."),
        LaunchContent.STORMGLASS to IngredientNote("Storm. Once per round, the wielder's attack also deals 2 storm damage to the back enemy.", "The arc is small. Storm damage pays best with a Charge burst, or against a Wet enemy."),
        LaunchContent.GRAVE_DUST to IngredientNote("Grave. Once per round, the wielder's attack puts 1 Bleed on the enemy it damages. Bleed deals damage before that enemy acts and ignores Guard.", "The Hollowbound resist grave damage and take only half damage from Bleed."),
        LaunchContent.SUN_ASH to IngredientNote("Sun. Once per round, the wielder's attack puts a Mark on the enemy it damages. The party attacks a marked enemy first, and the next hit on it deals ${RULES.markBonus} extra damage.", "Whoever hits the marked enemy next gets the bonus, and that is not always the wielder."),
        LaunchContent.BINDING_SALT to IngredientNote("When the wielder is healed, the wielder gains 1 Charge, once per round. At 3 Charge, a Charge burst deals 8 storm damage.", "It needs a healer, and healing a wielder at full health stores nothing."),
        LaunchContent.RUNESTONE_SHARD to IngredientNote("When an enemy breaks an ally's Guard, the weapon deals 5 damage to that enemy.", "Works only after a weapon in the party has cracked in that fight."),
        LaunchContent.DRAGON_OIL to IngredientNote("Spreads fire. When the wielder puts Burn on an enemy, every enemy gains 1 Burn. When the wielder gains Steam, the steam lance spends 1 Steam for 5 damage to the enemy the party is attacking and 5 to the back enemy, through Guard.", "The spreading needs a fire weapon. The lance needs Steam, which needs fire and cold together, usually from two different members."),
        LaunchContent.VOID_INK to IngredientNote("After the wielder acts, removes up to 3 Burn from the enemy the party is attacking and gives that much Guard to the weakest ally. When the wielder gains Steam, the wielder spends 1 Steam and every ally gains 3 Guard.", "The removed Burn no longer deals damage."),
    )

    // ---- interaction paths: what the notebook can suggest and a real fight can show ----
    val interactions = listOf(
        InteractionDef("charge", "Charge and burst", "Effects that give the wielder Charge build toward a Charge burst: at 3 Charge, the burst spends it all for 8 storm damage. Healing, hits and Silver can all give Charge.",
            "Charge that nothing spends is wasted, and the burst waits until the wielder has 3 Charge.",
            setOf("catalyst_capacitor", "affix_stormcharged", "core_silver", "sig_stormcaller"), listOf(setOf("charge_burst"))),
        InteractionDef("steam", "Boiling point", "When a fighter puts Burn on a chilled or Wet opponent, or Chill on a burning one, the two cancel one for one and that fighter gains that much Steam. Dragon Oil spends Steam on a lance of damage. Void Ink spends it on Guard.",
            "The cancelled Burn and Chill are both gone: no Burn damage, and no weaker attack.",
            setOf("catalyst_steam_lance", "catalyst_steam_shield"), listOf(setOf("catalyst_steam_lance"), setOf("catalyst_steam_shield"))),
        InteractionDef("burn_guard", "Fire into Guard", "Burn removed from an enemy can become Guard. Void Ink removes up to 3 Burn from the enemy the party is attacking and gives that much Guard to the weakest ally.",
            "The removed Burn no longer deals damage.",
            setOf("catalyst_converting", "catalyst_drinking", "sig_dawnbrand"), listOf(setOf("catalyst_converted_guard"), setOf("sig_dawnbrand_guard"))),
        InteractionDef("guard_answer", "A broken Guard answers", "Some effects trigger when an enemy breaks the wielder's Guard. Bronze strikes back, and Thornwall gains Thorns.",
            "It needs Guard on the wielder and an enemy strong enough to break it.",
            setOf("core_bronze", "sig_thornwall"), listOf(setOf("core_bronze"), setOf("sig_thornwall"))),
        InteractionDef("salvage", "Salvage", "A weapon that cracks gives its wielder 2 Scrap and turns on the effects that wait for a crack. After a crack, a Runestone weapon strikes back when an enemy breaks an ally's Guard.",
            "A cracked weapon needs a repair after the fight.",
            setOf("core_obsidian", "affix_brittle", "catalyst_runed"), listOf(setOf("catalyst_runed"), setOf("relic_salvage_bell"))),
        InteractionDef("marks", "Marked for the kill", "The party attacks a marked enemy first, and the next hit on it consumes the Mark for ${RULES.markBonus} extra damage. Starsteel adds more when its wielder's attack consumes the Mark.",
            "A fighter holds at most 1 Mark. Whoever hits that fighter next consumes it.",
            setOf("element_sun", "core_starsteel", "sig_nightletter"), listOf(setOf("core_starsteel"), setOf("sig_nightletter"))),
        InteractionDef("shatter", "Cold to break", "Some effects use Chill on an enemy. Moonsteel deals extra damage to a chilled enemy, and a Heavy weapon shatters the Chill for damage.",
            "The chilled enemy's own next attack consumes the Chill. Put it on and use it in the same round.",
            setOf("core_moonsteel", "affix_heavy_shatter", "sig_winterwake"), listOf(setOf("core_moonsteel"), setOf("affix_heavy_shatter"), setOf("sig_winterwake"))),
    )

    // ---- elements: what a blade does on a hit even with no affix on it ----
    val elementEffects: Map<Element, List<EffectDef>> = mapOf(
        Element.FIRE to listOf(e("element_fire", "Ember", onOwnHit, listOf(Give(Stat.BURN, Aim.EventTarget)), Limit(perRound = 1), "When the wielder's attack damages an enemy, the weapon puts 1 Burn on that enemy. Once per round.", struck)),
        Element.FROST to listOf(e("element_frost", "Rime", onOwnHit, listOf(Give(Stat.CHILL, Aim.EventTarget)), Limit(perRound = 1), "When the wielder's attack damages an enemy, the weapon puts 1 Chill on that enemy. Once per round.", struck)),
        Element.STORM to listOf(e("element_storm", "Arc", onOwnHit, listOf(Damage(Aim.BackFoe, Fixed(2), Element.STORM)), Limit(perRound = 1), "When the wielder's attack damages an enemy, the weapon also deals 2 storm damage to the back enemy. Once per round.", struck)),
        Element.GRAVE to listOf(e("element_grave", "Grave chill", onOwnHit, listOf(Give(Stat.BLEED, Aim.EventTarget)), Limit(perRound = 1), "When the wielder's attack damages an enemy, the weapon puts 1 Bleed on that enemy. Once per round.", struck)),
        Element.VERDANT to listOf(e("element_verdant", "Sap", onOwnHit, listOf(Give(Stat.REGEN, Aim.Self)), Limit(perRound = 1), "When the wielder's attack damages an enemy, the wielder gains 1 Regeneration. Once per round.", struck)),
        Element.SUN to listOf(e("element_sun", "Noon light", onOwnHit, listOf(Give(Stat.MARK, Aim.EventTarget)), Limit(perRound = 1), "When the wielder's attack damages an enemy, the weapon puts a Mark on that enemy. Once per round.", struck)),
    )

    // ---- affixes, by the IDs they have always had ----
    val affixEffects: Map<AffixId, List<EffectDef>> = mapOf(
        LaunchContent.FLAMING to listOf(e("affix_flaming", "Flaming", onOwnHit, listOf(Give(Stat.BURN, Aim.EventTarget)), Limit(perRound = 1), "When the wielder's attack damages an enemy, the weapon puts 1 Burn on that enemy. Once per round. This adds to any Burn from the weapon's element.", struck)),
        LaunchContent.FROSTBOUND to listOf(e("affix_frostbound", "Frostbound", onOwnHit, listOf(Give(Stat.CHILL, Aim.EventTarget)), Limit(perRound = 1), "When the wielder's attack damages an enemy, the weapon puts 1 Chill on that enemy. Once per round. This adds to any Chill from the weapon's element.", struck)),
        LaunchContent.STORMCHARGED to listOf(
            e("affix_stormcharged", "Stormcharged", onOwnHit, listOf(Give(Stat.CHARGE, Aim.Self)), Limit(perRound = 1), "When the wielder's attack damages an enemy, the wielder gains 1 Charge. Once per round.", struck),
            CHARGE_BURST),
        LaunchContent.VAMPIRIC to listOf(e("affix_vampiric", "Vampiric", Trigger(EventKind.DOWNED, Who.SELF_SOURCE), listOf(Heal(Aim.Self, Fixed(6))), Limit(perRound = 1), "When the wielder downs an enemy, the wielder recovers 6 health. Once per round.")),
        LaunchContent.SWIFT to listOf(e("affix_swift", "Swift", Trigger(EventKind.ACTED, Who.SELF_SOURCE), listOf(Damage(Aim.Foe, Strike(30))), Limit(perRound = 1), "After the wielder acts, the wielder strikes ${ATTACK_TARGET} again for 30% of its strike. Once per round.", struck)),
        LaunchContent.GIANT_SLAYER to listOf(e("affix_giant_slayer", "Giant Slayer", onOwnHit, listOf(Damage(Aim.EventTarget, Fixed(4))), Limit(perRound = 1), "When the wielder's attack damages an elite enemy or an enemy leader, the weapon deals 4 more damage to that enemy. Once per round.", struck + Condition.TargetTagged(TAG_ELITE))),
        LaunchContent.UNDEAD_BANE to listOf(e("affix_undead_bane", "Undead Bane", onOwnHit, listOf(Damage(Aim.EventTarget, Fixed(4), Element.SUN)), Limit(perRound = 1), "When the wielder's attack damages one of the Hollowbound, the weapon deals 4 more sun damage to that enemy. Once per round.", struck + Condition.TargetTagged(TAG_UNDEAD))),
        LaunchContent.GUARDIANS to listOf(e("affix_guardians", "Guardian's", Trigger(EventKind.GUARD_GAINED, Who.SELF_TARGET), listOf(Guard(Aim.Self, Fixed(2))), Limit(perRound = 1), "When the wielder gains Guard from its own move, the wielder gains 2 more. Once per round.", struck)),
        LaunchContent.REINFORCED to listOf(e("affix_reinforced", "Reinforced", Trigger(EventKind.ROUND_START, Who.ANY), listOf(Guard(Aim.Self, Fixed(4))), Limit(perFight = 1), "The wielder starts each fight with 4 Guard.")),
        LaunchContent.RESONANT to listOf(e("affix_resonant", "Resonant", Trigger(EventKind.HEALED, Who.SELF_SOURCE), listOf(Give(Stat.REGEN, Aim.EventTarget)), Limit(perRound = 1), "When the wielder's own move heals an ally, that ally also gains 1 Regeneration. Once per round.", struck)),
        // Flaws: a cost, and for the right party a piece of a build.
        LaunchContent.BRITTLE to listOf(e("affix_brittle", "Brittle", Trigger(EventKind.GUARD_BROKEN, Who.SELF_TARGET), listOf(Action.Fracture(2)), Limit(perFight = 1),
            "The first time an enemy breaks the wielder's Guard, the weapon cracks: the wielder gains 2 Scrap, and the weapon needs repair after the fight. Once per fight. Only an enemy can crack it.", listOf(Condition.SourceIsFoe))),
        LaunchContent.HEAVY to listOf(
            e("affix_heavy_slow", "Heavy", Trigger(EventKind.ROUND_START, Who.ANY), listOf(Give(Stat.CHILL, Aim.Self)), Limit(perFight = 1), "The wielder starts each fight with 1 Chill (slowed): its first attack deals ${RULES.chillPercent}% less damage."),
            e("affix_heavy_shatter", "Shatter", onOwnHit, listOf(Action.Take(Stat.CHILL, Aim.EventTarget, Fixed(3)), Damage(Aim.EventTarget, Fixed(6))), Limit(perRound = 1),
                "When the wielder's attack damages a chilled enemy, the weapon removes up to 3 Chill from that enemy and deals 6 damage to it. Once per round.", struck + Condition.TargetHas(Stat.CHILL))),
        LaunchContent.UNSTABLE to listOf(e("affix_unstable", "Unstable", Trigger(EventKind.DAMAGE, Who.SELF_TARGET), listOf(Damage(Aim.AllFoes, Fixed(5))), Limit(perFight = 1),
            "The first time the wielder takes damage and is left at 50% health or less, the weapon discharges: the wielder pays 3 health (never going below 1) and every enemy takes 5 damage. Once per fight.", listOf(Condition.HolderHealthAtMost(50)), Cost.PayHealth(3, floor = 1))),
        LaunchContent.CURSED to listOf(e("affix_cursed", "Cursed", Trigger(EventKind.ROUND_START, Who.ANY), listOf(Give(Stat.BLEED, Aim.Self, Fixed(2))), Limit(perFight = 1), "The wielder starts each fight with 2 Bleed.")),
        LaunchContent.BLOODBOUND to listOf(e("affix_bloodbound", "Bloodbound", Trigger(EventKind.ACTION, Who.SELF_SOURCE), listOf(Damage(Aim.Foe, Amount.OfSpent(2))), Limit(perRound = 1),
            "Before the wielder acts, the wielder pays up to 4 health (never going below 12) and the weapon deals twice that much damage to ${ATTACK_TARGET}. Once per round. Paying health is not damage, so effects that react to damage do not trigger.", struck, Cost.PayHealth(4, floor = 12))),
    )
    val affixHomeEffects: Map<AffixId, List<EffectDef>> = mapOf(
        LaunchContent.SENTIENT to listOf(e("affix_sentient_home", "Sentient (at home)", Trigger(EventKind.ROUND_START, Who.ANY), listOf(Guard(Aim.AllAllies, Fixed(2))), Limit(perFight = 1), "Defending its home, the weapon rallies the line: in a siege, every ally starts the fight with 2 Guard.")),
    )
    val affixRoadEffects: Map<AffixId, List<EffectDef>> = mapOf(
        LaunchContent.SENTIENT to listOf(e("affix_sentient_road", "Sentient (on the road)", Trigger(EventKind.ROUND_START, Who.ANY), listOf(Give(Stat.CHILL, Aim.Self)), Limit(perFight = 1), "Away from home the weapon balks: on a quest, the wielder starts each fight with 1 Chill (slowed), so its first attack deals ${RULES.chillPercent}% less damage.")),
    )

    // ---- catalysts: how the blade's rule behaves ----
    val CAPACITOR = e("catalyst_capacitor", "Capacitor", Trigger(EventKind.HEALED, Who.SELF_TARGET), listOf(Give(Stat.CHARGE, Aim.Self)), Limit(perRound = 1),
        "When the wielder is healed, the wielder gains 1 Charge. Once per round. Healing at full health gives nothing.")
    val RUNED = e("catalyst_runed", "Rune of answer", Trigger(EventKind.GUARD_BROKEN, Who.ALLY_TARGET), listOf(Damage(Aim.EventSource, Fixed(5))), Limit(perRound = 1, perFight = 2),
        "Once a weapon in the party has cracked in this fight, when an enemy breaks an ally's Guard, the wielder deals 5 damage to that enemy. Once per round, at most twice per fight.", listOf(Condition.SourceIsFoe, Condition.Happened(EventKind.FRACTURED)))
    val SPREADING = e("catalyst_spreading", "Dragon oil", Trigger(EventKind.STAT_GAINED, Who.SELF_SOURCE), listOf(Action.Give(Stat.BURN, Aim.AllFoes)), Limit(perRound = 1),
        "When the wielder puts Burn or Chill on any fighter, the wielder included, every enemy gains 1 Burn. Once per round.", listOf(Condition.StatIn(setOf(Stat.BURN, Stat.CHILL)), Condition.NotTick))
    /**
     * Dragon Oil on a weapon forged since the metals had rules: it spreads the fire its wielder lights and does nothing
     * with Chill. The older rule also answered Chill with Burn on every enemy, so one frost weapon boiled its own Chill
     * into Steam every round and Frost Bloom with Dragon Oil was in the top tenth of 60% of the lab's scenarios, half
     * again as often as any other pair. Steam is meant to take a fire and a cold that meet.
     */
    val SPREADING_FIRE = e("catalyst_spreading_fire", "Dragon oil", Trigger(EventKind.STAT_GAINED, Who.SELF_SOURCE), listOf(Action.Give(Stat.BURN, Aim.AllFoes)), Limit(perRound = 1),
        "When the wielder puts Burn on an enemy, every enemy gains 1 Burn. Once per round.", listOf(Condition.StatIn(setOf(Stat.BURN)), Condition.NotTick))
    val CONVERTING = e("catalyst_converting", "Void ink", Trigger(EventKind.ROUND_END, Who.ANY), listOf(Action.Take(Stat.BURN, Aim.PriorityFoe, Fixed(3))), Limit(perRound = 1),
        "At the end of each round, the weapon removes up to 3 Burn from ${PRIORITY_TARGET}.")
    val CONVERTED_GUARD = e("catalyst_converted_guard", "Void ink (Guard)", Trigger(EventKind.STAT_SPENT, Who.SELF_SOURCE, Stat.BURN), listOf(Guard(Aim.WeakestAlly, Amount.OfEvent())), Limit(perRound = 1),
        "When the wielder removes Burn from an enemy, ${WEAKEST_ALLY} gains that much Guard. Once per round. The removed Burn deals no more damage.")
    // Boiling Point (spec 8.3): Steam is what the wielder gets for putting heat on cold or wet (or cold on heat). The two catalysts spend it differently; a blade has one.
    val STEAM_LANCE = e("catalyst_steam_lance", "Steam lance", Trigger(EventKind.STAT_GAINED, Who.SELF_TARGET, Stat.STEAM), listOf(Damage(Aim.Foe, Fixed(5), pierce = true), Damage(Aim.BackFoe, Fixed(5), pierce = true)), Limit(perRound = 1),
        "When the wielder gains Steam, the wielder spends 1 Steam to deal 5 damage to ${ATTACK_TARGET} and 5 damage to the back enemy. Both ignore Guard. Once per round.", cost = Cost.Spend(Stat.STEAM, 1))
    val STEAM_SHIELD = e("catalyst_steam_shield", "Steam veil", Trigger(EventKind.STAT_GAINED, Who.SELF_TARGET, Stat.STEAM), listOf(Guard(Aim.AllAllies, Fixed(3))), Limit(perRound = 1),
        "When the wielder gains Steam, the wielder spends 1 Steam and every ally gains 3 Guard. Once per round.", cost = Cost.Spend(Stat.STEAM, 1))
    /**
     * Void Ink as it is forged from now on. The old rule took Burn at the end of a round, after that round's Burn had
     * already ticked and faded, and only from the enemy that matters most, which the wielder is seldom striking. One
     * Burn a round never reached it. This one drinks straight after the wielder's own action, from the enemy it faces.
     */
    val DRINKING = e("catalyst_drinking", "Void ink", Trigger(EventKind.ACTED, Who.SELF_SOURCE), listOf(Action.Take(Stat.BURN, Aim.Foe, Fixed(3))), Limit(perRound = 1),
        "After the wielder acts, the weapon removes up to 3 Burn from ${ATTACK_TARGET}.")
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
            "When an enemy breaks the wielder's Guard, the wielder gains Thorns equal to half of the broken Guard (rounded up, at most 6). The next enemy whose attack hits the wielder takes damage equal to the Thorns. Once per round.", listOf(Condition.SourceIsFoe))),
        "stormcaller" to listOf(
            e("sig_stormcaller", "Stormcaller", Trigger(EventKind.STAT_SPENT, Who.ALLY_SOURCE, Stat.CHARGE), listOf(Damage(Aim.AllFoes, Fixed(3), Element.STORM)), Limit(perRound = 1),
                "When an ally spends Charge, the weapon deals 3 storm damage to every enemy. Once per round.")),
        "mourning_rod" to listOf(e("sig_mourning_rod", "Mourning Rod", Trigger(EventKind.DOWNED, Who.FOE_TARGET), listOf(Action.Summon(SPECTRAL_HELPER)), Limit(perFight = 1),
            "The first enemy to fall rises as a spectral helper for the party. Once per fight. The helper leaves when the fight ends.")),
        "winterwake" to listOf(e("sig_winterwake", "Winterwake", onOwnHit, listOf(Action.Take(Stat.CHILL, Aim.EventTarget, Fixed(2)), Guard(Aim.WeakestAlly, Fixed(5))), Limit(perRound = 1),
            "When the wielder's attack damages a chilled enemy, the weapon removes up to 2 Chill from that enemy and ${WEAKEST_ALLY} gains 5 Guard. Once per round.", struck + Condition.TargetHas(Stat.CHILL))),
        "dawnbrand" to listOf(
            e("sig_dawnbrand", "Dawnbrand", Trigger(EventKind.ROUND_END, Who.ANY), listOf(Action.Take(Stat.BURN, Aim.PriorityFoe, Fixed(2))), Limit(perRound = 1),
                "At the end of each round, the weapon removes up to 2 Burn from ${PRIORITY_TARGET}."),
            e("sig_dawnbrand_guard", "Dawnbrand (Guard)", Trigger(EventKind.STAT_SPENT, Who.SELF_SOURCE, Stat.BURN), listOf(Guard(Aim.AllAllies, Amount.OfEvent())), Limit(perRound = 1),
                "When the wielder removes Burn from an enemy, every ally gains that much Guard. Once per round. The removed Burn deals no more damage.")),
        "nightletter" to listOf(e("sig_nightletter", "Nightletter", Trigger(EventKind.STAT_SPENT, Who.SELF_SOURCE, Stat.MARK), listOf(Damage(Aim.EventTarget, Fixed(5), Element.GRAVE)), Limit(perRound = 1),
            "When the wielder's hit consumes a Mark, the weapon deals 5 more grave damage to that enemy. Once per round.")),
    )

    // ---- relics that are party rules (spec 9.2) ----
    val relicEffects: Map<String, List<EffectDef>> = mapOf(
        OVERFLOW_BASIN to listOf(e("relic_overflow_basin", "Overflow Basin", Trigger(EventKind.OVERHEAL, Who.ALLY_TARGET), listOf(Guard(Aim.EventTarget, Amount.OfEvent(cap = 4))), Limit(perRound = 1),
            "When an ally is healed past full health, the extra healing becomes Guard on that ally, at most 4. Once per round for each member. This Guard is not healing, so effects that trigger on healing ignore it.", party = true, perMember = true)),
        SALVAGE_BELL to listOf(e("relic_salvage_bell", "Salvage Bell", Trigger(EventKind.FRACTURED, Who.ALLY_SOURCE), listOf(Guard(Aim.AllAllies, Fixed(3))), Limit(perFight = 1),
            "When an ally's weapon cracks, that ally spends its 2 Scrap and every ally gains 3 Guard. Once per fight for each member.", cost = Cost.Spend(Stat.SCRAP, 2), party = true, perMember = true)),
        STORM_LEDGER to listOf(e("relic_storm_ledger", "Storm Ledger", Trigger(EventKind.STAT_SPENT, Who.ALLY_SOURCE, Stat.CHARGE), listOf(Give(Stat.REGEN, Aim.WeakestAlly)), Limit(perRound = 1),
            "When an ally spends Charge, ${WEAKEST_ALLY} gains 1 Regeneration. Once per round. The relic never gives or spends Charge itself.", party = true)),
        FURNACE_LUNG to listOf(e("relic_furnace_lung", "Furnace Lung", Trigger(EventKind.STAT_SPENT, Who.ALLY_SOURCE, Stat.BURN), listOf(Guard(Aim.AllAllies, Fixed(2))), Limit(perRound = 1),
            "When an ally removes Burn from an enemy, every ally gains 2 Guard. Once per round. The removed Burn deals no more damage.", party = true)),
        BONE_MUSIC_BOX to listOf(e("relic_bone_music_box", "Bone Music Box", Trigger(EventKind.DOWNED, Who.FOE_TARGET), listOf(Action.Summon(SPECTRAL_HELPER)), Limit(perFight = 1),
            "The first enemy to fall in a fight rises as a spectral helper for the party. Once per fight. The helper brings nothing home.", party = true)),
        BLOOD_RECEIPT to listOf(e("relic_blood_receipt", "Blood Receipt", Trigger(EventKind.HEALED, Who.ALLY_TARGET), listOf(Guard(Aim.EventTarget, Amount.OfEvent(cap = 4))), Limit(perRound = 1),
            "Once anyone in the party has paid health in this fight, healing an ally also gives that ally the same amount of Guard, at most 4. Once per round for each member.", listOf(Condition.Happened(EventKind.HEALTH_PAID)), party = true, perMember = true)),
        BLUNT_OATH to listOf(e("relic_blunt_oath", "The Blunt Oath", Trigger(EventKind.ACTED, Who.ALLY_SOURCE), listOf(Damage(Aim.Foe, Fixed(3)), Guard(Aim.Self, Fixed(2))), Limit(perRound = 1),
            "After a member with a Common or Uncommon weapon (or no weapon) acts, that member deals 3 damage to ${ATTACK_TARGET} and gains 2 Guard. Once per round for each member. A member with a finer weapon gets nothing from it.", struck + Condition.HolderTagged(TAG_COMMON_GEAR), party = true, perMember = true)),
        WIDOWS_WHETSTONE to listOf(e("relic_widows_whetstone", "Widow's Whetstone", Trigger(EventKind.ACTED, Who.ALLY_SOURCE), listOf(Damage(Aim.Foe, Amount.OfSpent(3))), Limit(perRound = 1),
            "A pact. After a member acts, that member pays up to 2 health (never going below 6). For each health paid, that member deals 3 damage to ${ATTACK_TARGET}. Once per round for each member. Paying health is not damage.", struck, Cost.PayHealth(2, floor = 6), party = true, perMember = true)),
        OATHBREAKERS_COIN to listOf(e("relic_oathbreakers_coin", "Oathbreaker's Coin", Trigger(EventKind.ACTED, Who.ALLY_SOURCE), listOf(Guard(Aim.Self, Fixed(3)), Give(Stat.BLEED, Aim.Self)), Limit(perRound = 1),
            "A pact. After a member acts, that member gains 3 Guard and 1 Bleed. Once per round for each member.", struck, party = true, perMember = true)),
        // The Coward's Medal changes what a retreat brings home, not the fight: `Missions` reads it.
        COWARDS_MEDAL to emptyList(),
    )

    // ---- units ----
    private fun kit(id: String, name: String, vararg moves: Move, passives: List<EffectDef> = emptyList(), tags: Set<String> = emptySet()) = KitDef(id, name, moves.toList(), passives, tags)
    private fun strikeMove(id: String, name: String, vararg more: Action) = Move(id, name, Cadence.Always, listOf<Action>(Damage(Aim.Foe, Strike())) + more)

    private val ROUT = e("ashclaw_rout", "Rout", Trigger(EventKind.DOWNED, Who.ALLY_TARGET), listOf(Give(Stat.CHILL, Aim.Self, Fixed(2))), Limit(perFight = 1),
        "When their leader falls, each raider loses heart and gains 2 Chill. Once per fight.", listOf(Condition.TargetTagged(TAG_LEADER)))

    val units: List<UnitDef> = listOf(
        // Called by an effect; never brings anything home.
        UnitDef(SPECTRAL_HELPER, "Spectral helper", kit("spectral_helper", "Spectral helper", Move("haunt", "Haunt", Cadence.Always, listOf(Give(Stat.MARK, Aim.PriorityFoe), Damage(Aim.Foe, Strike())))), health = 10, strikeMin = 3, tags = setOf(TAG_SUMMONED)),

        // Ashclaw Raiders: several weak attackers around a leader who is worth reaching.
        UnitDef("ashclaw_scout", "Ashclaw scout", kit("ashclaw_scout", "Scout", strikeMove("slash", "Slash"), passives = listOf(ROUT)), health = 12, strikeMin = 3, strikeMax = 5, weakTo = Element.FROST, resists = Element.FIRE),
        UnitDef("ashclaw_brute", "Ashclaw brute", kit("ashclaw_brute", "Brute", strikeMove("club", "Club"), passives = listOf(ROUT)), health = 22, strikeMin = 5, strikeMax = 8, weakTo = Element.FROST, resists = Element.FIRE),
        UnitDef("ashclaw_thief", "Ashclaw thief", kit("ashclaw_thief", "Thief", Move("cutpurse", "Cut and run", Cadence.Always, listOf(Damage(Aim.BackFoe, Strike())), "Attacks the back member of the party, past the front and past any Guardian."), passives = listOf(ROUT)),
            health = 14, strikeMin = 4, strikeMax = 6, weakTo = Element.FROST, resists = Element.FIRE),
        UnitDef("ashclaw_warchief", "Ashclaw warchief", kit("ashclaw_warchief", "Warchief",
            Move("war_cry", "War cry", Cadence.Every(3, first = 2), listOf(Guard(Aim.AllAllies, Fixed(4))), "In round 2 and every third round after it, gives every raider 4 Guard instead of attacking."),
            strikeMove("cleave", "Cleave")), health = 36, strikeMin = 6, strikeMax = 9, tags = setOf(TAG_LEADER, TAG_ELITE), weakTo = Element.FROST, resists = Element.FIRE),
        UnitDef("warlord_krag", "Warlord Krag", kit("warlord_krag", "Warlord",
            Move("war_cry", "War cry", Cadence.Every(3, first = 2), listOf(Guard(Aim.AllAllies, Fixed(5))), "In round 2 and every third round after it, gives every raider 5 Guard instead of attacking."),
            Move("reave", "Reave", Cadence.Always, listOf(Damage(Aim.Foe, Strike()), Damage(Aim.BackFoe, Strike(50))))), health = 70, strikeMin = 8, strikeMax = 11,
            tags = setOf(TAG_LEADER, TAG_ELITE), weakTo = Element.FROST, resists = Element.FIRE),

        // Hollowbound: they come back while their anchor stands.
        UnitDef("hollow_shambler", "Hollowbound shambler", kit("hollow_shambler", "Shambler", strikeMove("claw", "Claw")), health = 15, strikeMin = 3, strikeMax = 5, tags = setOf(TAG_UNDEAD),
            weakTo = Element.SUN, resists = Element.GRAVE, tickResist = mapOf(Stat.BLEED to 50)),
        UnitDef("bone_warden", "Bone warden", kit("bone_warden", "Bone warden", Move("shield_wall", "Shield wall", Cadence.Always, listOf(Guard(Aim.Self, Fixed(3)), Damage(Aim.Foe, Strike())))), health = 26, strikeMin = 4, strikeMax = 6,
            tags = setOf(TAG_UNDEAD), weakTo = Element.SUN, resists = Element.GRAVE, tickResist = mapOf(Stat.BLEED to 50)),
        UnitDef("gravecaller", "Gravecaller", kit("gravecaller", "Gravecaller",
            Move("raise", "Raise the fallen", Cadence.Every(3, first = 3), listOf(Action.Summon("hollow_shambler")), "In round 3 and every third round after it, raises a Hollowbound shambler instead of attacking. Nothing rises if the enemy line is full."),
            Move("knit", "Knit bone", Cadence.Always, listOf(Heal(Aim.WeakestAlly, Support()), Damage(Aim.Foe, Strike())), "In other rounds, heals its most wounded ally (lowest health percentage, itself included), then attacks.")),
            health = 22, strikeMin = 2, strikeMax = 3, support = 5, tags = setOf(TAG_ANCHOR, TAG_UNDEAD), weakTo = Element.SUN, resists = Element.GRAVE, tickResist = mapOf(Stat.BLEED to 50)),
        UnitDef("pale_knight", "The Pale Knight", kit("pale_knight", "Pale Knight", Move("grave_blade", "Grave weapon", Cadence.Always, listOf(Damage(Aim.Foe, Strike()), Give(Stat.REGEN, Aim.Self)), "Every round, attacks and gains 1 Regeneration.")),
            health = 44, strikeMin = 6, strikeMax = 9, tags = setOf(TAG_ELITE, TAG_UNDEAD), weakTo = Element.SUN, resists = Element.GRAVE, tickResist = mapOf(Stat.BLEED to 50)),
        UnitDef("hollow_king", "The Hollow King", kit("hollow_king", "Hollow King",
            Move("court", "Summon the court", Cadence.Every(3, first = 2), listOf(Action.Summon("hollow_shambler")), "In round 2 and every third round after it, raises a Hollowbound shambler instead of attacking. Nothing rises if the enemy line is full."),
            Move("crown", "Crown of ash", Cadence.Always, listOf(Damage(Aim.Foe, Strike()), Heal(Aim.Self, Fixed(4))), "In other rounds, attacks and heals himself for 4.")),
            health = 76, strikeMin = 7, strikeMax = 10, tags = setOf(TAG_ANCHOR, TAG_LEADER, TAG_ELITE, TAG_UNDEAD), weakTo = Element.SUN, resists = Element.GRAVE, tickResist = mapOf(Stat.BLEED to 50)),

        // Embermaw Brood: slow, guarded, and one blow that has to be survived.
        UnitDef("ember_whelp", "Embermaw whelp", kit("ember_whelp", "Whelp", strikeMove("nip", "Nip", Give(Stat.BURN, Aim.Foe))), health = 12, strikeMin = 2, strikeMax = 4, weakTo = Element.FROST, resists = Element.FIRE, tickResist = mapOf(Stat.BURN to 50)),
        UnitDef("cinder_knight", "Cinder-knight", kit("cinder_knight", "Cinder-knight", Move("ember_guard", "Ember guard", Cadence.Always, listOf(Guard(Aim.Self, Fixed(4)), Damage(Aim.Foe, Strike())))), health = 26, strikeMin = 5, strikeMax = 7,
            weakTo = Element.FROST, resists = Element.FIRE, tickResist = mapOf(Stat.BURN to 50)),
        UnitDef("ember_drake", "Embermaw drake", kit("ember_drake", "Drake",
            Move("fire_breath", "Fire breath", Cadence.Every(3, first = 3), listOf(Damage(Aim.AllFoes, Strike(130))), "In round 3 and every third round after it, breathes fire on the whole party for 130% of its strike. A chilled drake's breath deals ${RULES.chillPercent}% less damage."),
            Move("bite", "Bite", Cadence.Always, listOf(Guard(Aim.Self, Fixed(3)), Damage(Aim.Foe, Strike())))), health = 34, strikeMin = 5, strikeMax = 8, tags = setOf(TAG_ELITE),
            weakTo = Element.FROST, resists = Element.FIRE, tickResist = mapOf(Stat.BURN to 50)),
        UnitDef("broodmother", "Broodmother Vyrash", kit("broodmother", "Broodmother",
            Move("inferno", "Inferno", Cadence.Every(3, first = 3), listOf(Damage(Aim.AllFoes, Strike(150)), Give(Stat.BURN, Aim.AllFoes)), "In round 3 and every third round after it, burns the whole party for 150% of her strike and puts 1 Burn on each member. A chilled Broodmother's Inferno deals ${RULES.chillPercent}% less damage."),
            Move("rend", "Rend", Cadence.Always, listOf(Guard(Aim.Self, Fixed(4)), Damage(Aim.Foe, Strike())))), health = 84, strikeMin = 7, strikeMax = 10, tags = setOf(TAG_LEADER, TAG_ELITE),
            weakTo = Element.FROST, resists = Element.FIRE, tickResist = mapOf(Stat.BURN to 50)),

        // What a party protects on an escort or carries out of a rescue: it does nothing but stand.
        UnitDef("escort_cart", "The supply cart", kit("escort_cart", "Cart", Move("roll", "Rolls on", Cadence.Always, emptyList())), health = 30, strikeMin = 0),
        UnitDef("town_watch", "A guard of the watch", kit("town_watch", "Watch", strikeMove("halberd", "Halberd")), health = 22, strikeMin = 4, strikeMax = 6),
        UnitDef("captive", "The captive", kit("captive", "Captive", Move("stumble", "Keeps moving", Cadence.Always, emptyList())), health = 24, strikeMin = 0),
    )

    val catalog = CombatCatalog(kits, families, elementEffects, affixEffects, affixHomeEffects, affixRoadEffects, catalystEffects, signatureEffects, relicEffects, units, cores = cores, notes = notes, interactions = interactions, revisions = revisions)
}
