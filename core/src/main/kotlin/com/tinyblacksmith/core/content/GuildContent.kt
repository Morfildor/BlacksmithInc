package com.tinyblacksmith.core.content

import com.tinyblacksmith.core.combat.*
import com.tinyblacksmith.core.combat.Action.Damage
import com.tinyblacksmith.core.combat.Action.Give
import com.tinyblacksmith.core.combat.Action.Guard
import com.tinyblacksmith.core.combat.Action.Heal
import com.tinyblacksmith.core.combat.Amount.Fixed
import com.tinyblacksmith.core.combat.Amount.Strike
import com.tinyblacksmith.core.model.Danger
import com.tinyblacksmith.core.model.FactionId
import com.tinyblacksmith.core.model.HeroClassId
import com.tinyblacksmith.core.model.MaterialId

enum class MissionArchetype { SUPPLY, HUNT, SABOTAGE, ESCORT, RESCUE, RECOVERY, DELVE, EMERGENCY }

/** Materials a stage brings: [count] units drawn from the catalog between the two tiers; [scarce] draws catalysts and limited stock only. */
data class MaterialDraw(val count: Int, val minTier: Int = 1, val maxTier: Int = 2, val scarce: Boolean = false)

/**
 * One fight of a contract as content. [rosters] names the units by faction (the board picks the faction when it posts
 * the contract); an empty roster is work without a fight. [percent] is the stage's own weight on top of the day's.
 */
data class StageDef(
    val rosters: Map<FactionId, List<String>>,
    val danger: Danger,
    val percent: Int = 100,
    val objective: ObjectiveKind = ObjectiveKind.DEFEAT,
    val rounds: Int = 0,
    val protectUnitId: String? = null,
    val goldMin: Int = 0,
    val goldMax: Int = 0,
    val materials: MaterialDraw? = null,
    val suppression: Int = 0,
    val integrity: Int = 0,
    val militia: Int = 0,
    val sabotage: Boolean = false,
    val relicOffer: Boolean = false,
)

data class MissionDef(
    val id: String,
    val archetype: MissionArchetype,
    val name: String,
    val days: Int,
    val fee: Int,
    val stages: List<StageDef>,
    val description: String,
    /** What to bring, in the smith's words. */
    val prep: String,
    /** What failing costs, in plain words, shown before anybody leaves. */
    val failure: String,
    val optionalPush: Boolean = false,
    val weight: Double = 1.0,
    val minDay: Int = 1,
    /** Mornings an offer of this contract stays on the board. */
    val lifeDays: Int = 3,
)

/** The road a faction's contracts lie on. */
data class RouteDef(val id: String, val name: String, val factionId: FactionId, val description: String)

/**
 * A member's defining trait (spec 4.3): six that change how they behave, six that are a hook for a build. A trait is a
 * fight rule, a number on the contract, or both; each is disclosed before hiring.
 */
data class GuildTraitDef(
    val id: String,
    val name: String,
    val hook: Boolean,
    val description: String,
    val effects: List<EffectDef> = emptyList(),
    val tags: Set<String> = emptySet(),
    val startStats: Map<Stat, Int> = emptyMap(),
    /** Share of a stage's gold the party still brings home when it pulls back and this member is in it. */
    val retreatGoldPercent: Int = 0,
    /** Added to a won contract's gold. */
    val winGoldPercent: Int = 0,
    /** Added to the member's own share of contract gold. */
    val shareDelta: Int = 0,
)

/** A starting contract for the guild: who it begins with, what it is given and what it gives up. */
data class CharterDef(
    val id: String,
    val name: String,
    val pitch: String,
    val advantage: String,
    val constraint: String,
    val play: String,
    val startClasses: List<HeroClassId>,
    val offeredClass: HeroClassId,
    val goldDelta: Int = 0,
    val militiaDelta: Int = 0,
    val reputationDelta: Int = 0,
    val materials: Map<MaterialId, Int> = emptyMap(),
    /** The smith's part of contract gold, percent of what is left after the members' shares. */
    val smithGoldPercent: Int = 100,
    val extraOffers: Int = 0,
    /** Dangerous contracts are posted from the first day. */
    val earlyDanger: Boolean = false,
)

/** A branch a member's own deeds open (spec 12.3): one of two, chosen by the smith, or neither. */
data class SpecialityDef(val id: String, val name: String, val deed: String, val description: String, val effects: List<EffectDef> = emptyList(), val healthPercent: Int = 100, val strikePercent: Int = 100, val supportPercent: Int = 100)

/** A rule on everybody for a few days (spec 9.4): announced before a party can leave under it. */
data class WorldLawDef(val id: String, val name: String, val description: String, val days: Int, val partyStats: Map<Stat, Int> = emptyMap(), val enemyStats: Map<Stat, Int> = emptyMap(), val repairPercent: Int = 100, val healPercent: Int = 100, val salvageGoldPercent: Int = 100)

/** An optional difficulty after a charter is secured (spec 13.4): what it adds is written on it. */
data class GuildRankDef(val rank: Int, val name: String, val description: String, val enemyPercent: Int = 0, val extraSiegeUnit: Boolean = false, val offerLifeDelta: Int = 0, val legacyPoints: Int = 0)

data class GuildCatalog(
    val missions: List<MissionDef>,
    val routes: List<RouteDef>,
    val traits: List<GuildTraitDef>,
    val charters: List<CharterDef>,
    val specialities: List<SpecialityDef> = emptyList(),
    val laws: List<WorldLawDef> = emptyList(),
    val ranks: List<GuildRankDef> = emptyList(),
    /** Who leads a faction's siege on the charter day, and at a warlord's pressure. */
    val siegeLeaders: Map<FactionId, String> = emptyMap(),
    val siegeElites: Map<FactionId, String> = emptyMap(),
    val siegeGrunts: Map<FactionId, List<String>> = emptyMap(),
    val rivalNames: List<Pair<String, String>> = emptyList(),
    /** What a bond adds to a fight both of its members are in, by `Bond.kind`. */
    val bondEffects: Map<String, List<EffectDef>> = emptyMap(),
    val oathEffects: List<EffectDef> = emptyList(),
) {
    val missionById: Map<String, MissionDef> = missions.associateBy { it.id }
    val traitById: Map<String, GuildTraitDef> = traits.associateBy { it.id }
    val charterById: Map<String, CharterDef> = charters.associateBy { it.id }
    fun mission(id: String): MissionDef = missionById[id] ?: error("Unknown mission $id")
    fun trait(id: String): GuildTraitDef? = traitById[id]
    fun charter(id: String): CharterDef? = charterById[id]
    fun route(factionId: FactionId): RouteDef? = routes.firstOrNull { it.factionId == factionId }
    fun speciality(id: String?): SpecialityDef? = specialities.firstOrNull { it.id == id }
    fun law(id: String?): WorldLawDef? = laws.firstOrNull { it.id == id }
    fun rank(rank: Int): GuildRankDef? = ranks.firstOrNull { it.rank == rank }

    fun problems(content: ContentCatalog, combat: CombatCatalog): List<String> {
        val problems = mutableListOf<String>()
        fun dup(name: String, ids: List<String>) { ids.groupBy { it }.filterValues { it.size > 1 }.keys.forEach { problems += "Duplicate $name $it" } }
        dup("mission", missions.map { it.id }); dup("guild trait", traits.map { it.id }); dup("charter", charters.map { it.id }); dup("speciality", specialities.map { it.id }); dup("law", laws.map { it.id })
        for (m in missions) {
            if (m.days !in 1..2) problems += "Mission ${m.id} lasts ${m.days} days"
            if (m.stages.isEmpty() || m.stages.size > 2) problems += "Mission ${m.id} needs one or two stages"
            if (m.optionalPush && m.stages.size != 2) problems += "Mission ${m.id} offers a push without a second stage"
            if (m.days == 1 && m.stages.size == 2) problems += "Mission ${m.id}: a second stage needs a second day"
            for (s in m.stages) {
                if (s.rosters.isNotEmpty() && content.factions.any { it.id !in s.rosters }) problems += "Mission ${m.id} has no roster for a faction"
                s.rosters.values.flatten().forEach { if (it !in combat.unitById) problems += "Mission ${m.id} fields unknown unit $it" }
                s.protectUnitId?.let { if (it !in combat.unitById) problems += "Mission ${m.id} protects unknown unit $it" }
                if (s.objective == ObjectiveKind.HOLD && s.rounds <= 0) problems += "Mission ${m.id} holds for no rounds"
                if (s.goldMax < s.goldMin) problems += "Mission ${m.id} gold range"
            }
        }
        if (missions.none { it.archetype == MissionArchetype.SUPPLY && it.fee == 0 }) problems += "No free supply contract"
        if (missions.none { it.archetype == MissionArchetype.EMERGENCY && it.fee == 0 }) problems += "No free emergency work"
        problems += EffectRules.problems(traits.flatMap { it.effects } + specialities.flatMap { it.effects } + bondEffects.values.flatten() + oathEffects, units = combat.unitById)
        ranks.forEachIndexed { i, r -> if (r.rank != i + 1) problems += "Guild ranks must be numbered from 1 without gaps" }
        laws.forEach { if (it.days <= 0) problems += "Law ${it.id} lasts no days" }
        for (c in charters) {
            (c.startClasses + c.offeredClass).forEach { if (it !in content.classById) problems += "Charter ${c.id} names unknown class ${it.value}" }
            c.materials.keys.forEach { if (it !in content.materialById) problems += "Charter ${c.id} gives unknown material ${it.value}" }
            if (c.startClasses.size != 2) problems += "Charter ${c.id} starts with ${c.startClasses.size} members"
        }
        if (charters.isEmpty()) problems += "No charter"
        if (traits.count { !it.hook } < 1 || traits.count { it.hook } < 1) problems += "Guild traits need both kinds"
        content.factions.forEach { f ->
            if (routes.none { it.factionId == f.id }) problems += "No route for ${f.id.value}"
            listOf(siegeLeaders[f.id], siegeElites[f.id]).plus(siegeGrunts[f.id].orEmpty()).forEach { if (it == null || it !in combat.unitById) problems += "Siege roster of ${f.id.value} names $it" }
            if (siegeGrunts[f.id].isNullOrEmpty()) problems += "No siege grunts for ${f.id.value}"
        }
        return problems
    }
}

/** The launch guild content. Names and numbers PROPOSED. */
object GuildContent {
    private val A = LaunchContent.ASHCLAW
    private val H = LaunchContent.HOLLOWBOUND
    private val E = LaunchContent.EMBERMAW

    const val SUPPLY_RUN = "supply_run"
    const val HUNT = "hunt"
    const val SABOTAGE = "sabotage"
    const val ESCORT = "escort"
    const val RELIC_DELVE = "relic_delve"
    const val RESCUE = "rescue"
    const val RECOVERY = "recovery"
    const val WALL_WORK = "wall_work"

    const val TOWNS_LAST_HOPE = "towns_last_hope"
    const val LICENSED_BAD_IDEAS = "licensed_bad_ideas"
    const val HONEST_BUSINESS = "honest_business"

    const val CHARTER_SECURED = "charter_secured"

    const val COWARD_RETURNS = "coward_returns"
    const val SWORD_COMPLAINT = "sword_complaint"
    const val ACROSS_THE_COUNTER = "across_the_counter"
    const val INSURANCE_ADJUSTER = "insurance_adjuster"
    const val TAG_NOVICE_PARTY = "novice_party"

    /** Visitors only a guild run can have (`guild.GuildVisitors`). With the eight older ones: twelve scenes. Weights, limits and cooldowns PROPOSED. */
    val encounters = listOf(
        EncounterDef(COWARD_RETURNS, "The Coward Returns Alone", 6.0, maxPerRun = 4, cooldownDays = 2, description = "One of the party is back without another, who is alive in enemy hands."),
        EncounterDef(SWORD_COMPLAINT, "The Sword Files a Complaint", 2.5, maxPerRun = 3, cooldownDays = 5, description = "A blade with opinions has one about who carries it."),
        EncounterDef(ACROSS_THE_COUNTER, "Your Best Sword Is Across the Counter", 4.0, maxPerRun = 3, cooldownDays = 3, description = "A dealer knows how to part the enemy from a blade of this forge."),
        EncounterDef(INSURANCE_ADJUSTER, "Insurance Adjuster from the Underworld", 1.5, maxPerRun = 2, cooldownDays = 6, description = "An adjuster will insure one loan against the enemy.", minDay = 4),
    )

    /** What a blade that took the oath of `The Sword Files a Complaint` adds while its whole party is inexperienced. */
    val oathEffects = listOf(EffectDef("oath_inexperienced", "Oath of inexperienced company", Trigger(EventKind.ROUND_START, Who.ANY), listOf(Guard(Aim.AllAllies, Fixed(3)), Give(Stat.REGEN, Aim.AllAllies)), Limit(perFight = 1),
        "While everyone in the party is inexperienced, every ally starts a fight with 3 Guard and 1 Regeneration.", listOf(Condition.HolderTagged(TAG_NOVICE_PARTY))))

    private fun roster(a: List<String>, h: List<String>, e: List<String>) = mapOf(A to a, H to h, E to e)

    val missions = listOf(
        MissionDef(SUPPLY_RUN, MissionArchetype.SUPPLY, "Supply run", days = 1, fee = 0, weight = 1.0,
            description = "Bring a wagon of ordinary stock in past a few of them.",
            prep = "Anybody will do. One member alone can manage it.",
            failure = "A smaller haul or none, and wounds. Nobody dies on a supply run and nobody is taken.",
            stages = listOf(StageDef(roster(listOf("ashclaw_scout", "ashclaw_scout"), listOf("hollow_shambler", "hollow_shambler"), listOf("ember_whelp", "ember_whelp")), Danger.SAFE, percent = 75,
                goldMin = 18, goldMax = 30, materials = MaterialDraw(3, 1, 2), suppression = 1))),
        MissionDef(HUNT, MissionArchetype.HUNT, "Hunt", days = 1, fee = 15, weight = 1.2, minDay = 2,
            description = "A named one of theirs has been seen with its guard. What it carries is worth having.",
            prep = "A blade that bites their weakness, and a way through whatever protects the big one.",
            failure = "Wounds and no prize. If the whole party goes down, one of them is taken.",
            stages = listOf(StageDef(roster(listOf("ashclaw_warchief", "ashclaw_scout"), listOf("pale_knight", "hollow_shambler"), listOf("ember_drake", "ember_whelp")), Danger.DANGEROUS, percent = 105,
                goldMin = 55, goldMax = 85, materials = MaterialDraw(2, 3, 6, scarce = true), suppression = 6))),
        MissionDef(SABOTAGE, MissionArchetype.SABOTAGE, "Sabotage", days = 1, fee = 10, weight = 1.0, minDay = 3,
            description = "Get into their camp, spoil what they are preparing for the siege, and hold long enough to get out.",
            prep = "Guard and healing. Nobody has to fall; the party has to last the rounds it says.",
            failure = "What they prepared stays prepared. If the whole party goes down, one of them is taken.",
            stages = listOf(StageDef(roster(listOf("ashclaw_brute", "ashclaw_thief", "ashclaw_scout"), listOf("bone_warden", "hollow_shambler", "hollow_shambler"), listOf("cinder_knight", "ember_whelp", "ember_whelp")), Danger.DANGEROUS, percent = 100,
                objective = ObjectiveKind.HOLD, rounds = 4, goldMin = 15, goldMax = 25, suppression = 5, sabotage = true))),
        MissionDef(ESCORT, MissionArchetype.ESCORT, "Escort", days = 2, fee = 0, weight = 0.9, minDay = 3,
            description = "A supplier's cart, two days on the road. They will come for the cart, not for you.",
            prep = "A Guardian in front and something for the one who goes past the front. The cart has to stand.",
            failure = "If the cart is lost the payment is lost with it. Nobody is taken on an escort.",
            stages = listOf(
                StageDef(roster(listOf("ashclaw_scout", "ashclaw_scout", "ashclaw_thief"), listOf("hollow_shambler", "hollow_shambler", "hollow_shambler"), listOf("ember_whelp", "ember_whelp", "ember_whelp")), Danger.SAFE, percent = 85,
                    objective = ObjectiveKind.HOLD, rounds = 4, protectUnitId = "escort_cart", goldMin = 25, goldMax = 35),
                StageDef(roster(listOf("ashclaw_brute", "ashclaw_thief", "ashclaw_thief"), listOf("bone_warden", "hollow_shambler", "gravecaller"), listOf("cinder_knight", "ember_whelp", "ember_whelp")), Danger.SAFE, percent = 95,
                    objective = ObjectiveKind.HOLD, rounds = 5, protectUnitId = "escort_cart", goldMin = 50, goldMax = 70, materials = MaterialDraw(4, 1, 3)))),
        MissionDef(RELIC_DELVE, MissionArchetype.DELVE, "Relic delve", days = 2, fee = 20, weight = 0.8, minDay = 4, optionalPush = true,
            description = "An old place they have not finished robbing. The first hall is worth the walk. Something sits deeper.",
            prep = "A party that can take a long fight. Deeper is a choice you make at the door, the morning after.",
            failure = "What the first hall gave is yours whatever happens deeper. If the whole party goes down in the deep hall, one dies and one is taken.",
            stages = listOf(
                StageDef(roster(listOf("ashclaw_brute", "ashclaw_scout", "ashclaw_scout"), listOf("bone_warden", "hollow_shambler", "hollow_shambler"), listOf("cinder_knight", "ember_whelp", "ember_whelp")), Danger.DANGEROUS, percent = 100,
                    goldMin = 40, goldMax = 60, materials = MaterialDraw(2, 2, 4)),
                StageDef(roster(listOf("ashclaw_warchief", "ashclaw_brute", "ashclaw_thief"), listOf("pale_knight", "gravecaller", "bone_warden"), listOf("ember_drake", "cinder_knight", "ember_whelp")), Danger.LETHAL, percent = 125,
                    goldMin = 60, goldMax = 90, materials = MaterialDraw(2, 4, 6, scarce = true), relicOffer = true))),
        // Posted only for a real captive or a real blade; never drawn for the board.
        MissionDef(RESCUE, MissionArchetype.RESCUE, "Rescue", days = 2, fee = 0, weight = 0.0, lifeDays = 9,
            description = "One of yours is held. Break in on the first day, bring them out on the second.",
            prep = "Enough to beat the guards, then Guard and healing: on the way out the captive has to stay on their feet.",
            failure = "The captive stays where they are and their time keeps running. If the whole party goes down, one more is taken.",
            stages = listOf(
                StageDef(roster(listOf("ashclaw_brute", "ashclaw_scout", "ashclaw_scout"), listOf("bone_warden", "hollow_shambler", "hollow_shambler"), listOf("cinder_knight", "ember_whelp", "ember_whelp")), Danger.DANGEROUS, percent = 85),
                StageDef(roster(listOf("ashclaw_thief", "ashclaw_scout", "ashclaw_brute"), listOf("hollow_shambler", "hollow_shambler", "gravecaller"), listOf("ember_whelp", "ember_whelp", "cinder_knight")), Danger.DANGEROUS, percent = 80,
                    objective = ObjectiveKind.HOLD, rounds = 4, protectUnitId = "captive"))),
        MissionDef(RECOVERY, MissionArchetype.RECOVERY, "Recovery", days = 1, fee = 10, weight = 0.0, lifeDays = 9,
            description = "A blade of your forge is in one of their hands. The one who carries it has to fall.",
            prep = "The same as a hunt. What it took from you makes it harder.",
            failure = "The blade stays with them, and you still know where. If the whole party goes down, one of them is taken.",
            stages = listOf(StageDef(roster(listOf("ashclaw_warchief", "ashclaw_brute"), listOf("pale_knight", "bone_warden"), listOf("ember_drake", "cinder_knight")), Danger.DANGEROUS, percent = 95, goldMin = 20, goldMax = 30, suppression = 4))),
        MissionDef(WALL_WORK, MissionArchetype.EMERGENCY, "Work on the wall", days = 1, fee = 0, weight = 0.0,
            description = "No fight: a day of timber and stone at the forge's own wall.",
            prep = "Any member who can stand. It always succeeds.",
            failure = "Nothing can go wrong, and little is gained: it mends, it does not arm.",
            stages = listOf(StageDef(emptyMap(), Danger.SAFE, integrity = 5, militia = 1))),
    )

    val routes = listOf(
        RouteDef("ashroad", "the Ashroad", A, "The raiders' road: many of them, and one who gives the orders."),
        RouteDef("flooded_crypt", "the Flooded Crypt", H, "The dead keep coming back while the one who calls them stands."),
        RouteDef("cinder_quarry", "the Cinder Quarry", E, "Few, slow and guarded, and every third breath is fire."),
    )

    private fun t(id: String, name: String, trigger: Trigger, actions: List<Action>, limit: Limit, description: String, conditions: List<Condition> = emptyList()) =
        EffectDef("trait_$id", name, trigger, actions, limit, description, conditions)

    val traits = listOf(
        GuildTraitDef("protective", "Protective", false, "When an ally is hit down to half health or less, gives them 3 Guard. Once a round.",
            listOf(t("protective", "Protective", Trigger(EventKind.DAMAGE, Who.ALLY_TARGET), listOf(Guard(Aim.EventTarget, Fixed(3))), Limit(perRound = 1), "When an ally is hit down to half health or less, gives them 3 Guard. Once a round.", listOf(Condition.SourceIsFoe, Condition.TargetHealthAtMost(50))))),
        GuildTraitDef("impatient", "Impatient", false, "Strikes once before anybody acts, for two thirds of a strike. Once a fight.",
            listOf(t("impatient", "Impatient", Trigger(EventKind.ROUND_START, Who.ANY), listOf(Damage(Aim.Foe, Strike(65))), Limit(perFight = 1), "Strikes once before anybody acts, for two thirds of a strike. Once a fight."))),
        GuildTraitDef("cowardly", "Cowardly", false, "Keeps hold of the loot on the way out: a party that pulls back with them still brings home a third of that stage's gold. Starts every fight with 4 Guard.",
            startStats = mapOf(Stat.GUARD to 4), retreatGoldPercent = 34),
        GuildTraitDef("greedy", "Greedy", false, "Finds more: a won contract pays a fifth more gold. Takes 5 points more of it for themselves.", winGoldPercent = 20, shareDelta = 5),
        GuildTraitDef("methodical", "Methodical", false, "When their own hit spends a Mark, it deals 3 more. Once a round.",
            listOf(t("methodical", "Methodical", Trigger(EventKind.STAT_SPENT, Who.SELF_SOURCE, Stat.MARK), listOf(Damage(Aim.EventTarget, Fixed(3))), Limit(perRound = 1), "When their own hit spends a Mark, it deals 3 more. Once a round."))),
        GuildTraitDef("loyal_guild", "Loyal", false, "When an ally goes down, strikes the one who did it for a full strike. Once a fight.",
            listOf(t("loyal", "Loyal", Trigger(EventKind.DOWNED, Who.ALLY_TARGET), listOf(Damage(Aim.EventSource, Strike())), Limit(perFight = 1), "When an ally goes down, strikes the one who did it for a full strike. Once a fight.", listOf(Condition.SourceIsFoe)))),
        GuildTraitDef("storm_touched", "Storm-Touched", true, "Starts every fight with 1 Charge and can spend Charge with any blade: at 3 Charge, a burst of 8 storm damage.",
            listOf(CombatContent.CHARGE_BURST), tags = setOf(CombatContent.TAG_CHARGE), startStats = mapOf(Stat.CHARGE to 1)),
        GuildTraitDef("pain_collector", "Pain Collector", true, "When an enemy's blow hurts them, gains 2 Thorns: the next enemy to strike them takes them. Once a round.",
            listOf(t("pain_collector", "Pain Collector", Trigger(EventKind.DAMAGE, Who.SELF_TARGET), listOf(Give(Stat.THORNS, Aim.Self, Fixed(2))), Limit(perRound = 1), "When an enemy's blow hurts them, gains 2 Thorns. Once a round.", listOf(Condition.SourceIsFoe, Condition.NotTick)))),
        GuildTraitDef("rust_savant", "Rust Savant", true, "When an ally's blade cracks, strikes the first enemy for 6. Twice a fight.",
            listOf(t("rust_savant", "Rust Savant", Trigger(EventKind.FRACTURED, Who.ALLY_SOURCE), listOf(Damage(Aim.Foe, Fixed(6))), Limit(perFight = 2), "When an ally's blade cracks, strikes the first enemy for 6. Twice a fight."))),
        GuildTraitDef("overhealer", "Overhealer", true, "When their healing is more than an ally can use, that ally gains 1 Regeneration. Once a round.",
            listOf(t("overhealer", "Overhealer", Trigger(EventKind.OVERHEAL, Who.SELF_SOURCE), listOf(Give(Stat.REGEN, Aim.EventTarget)), Limit(perRound = 1), "When their healing is more than an ally can use, that ally gains 1 Regeneration. Once a round.", listOf(Condition.FromAction)))),
        GuildTraitDef("second_wind", "Second Wind", true, "The first time a blow leaves them at a third of their health or less, heals themselves for 10. Once a fight; it does not bring back the fallen.",
            listOf(t("second_wind", "Second Wind", Trigger(EventKind.DAMAGE, Who.SELF_TARGET), listOf(Heal(Aim.Self, Fixed(10))), Limit(perFight = 1), "The first time a blow leaves them at a third of their health or less, heals themselves for 10. Once a fight.", listOf(Condition.HolderHealthAtMost(34))))),
        GuildTraitDef("oathkeeper", "Oathkeeper", true, "While carrying a common blade or none, raises 2 Guard at the start of every round.",
            listOf(t("oathkeeper", "Oathkeeper", Trigger(EventKind.ROUND_START, Who.ANY), listOf(Guard(Aim.Self, Fixed(2))), Limit(perRound = 1), "While carrying a common blade or none, raises 2 Guard at the start of every round.", listOf(Condition.HolderTagged(CombatContent.TAG_COMMON_GEAR))))),
    )

    val charters = listOf(
        CharterDef(TOWNS_LAST_HOPE, "Town's Last Hope", "The council signs your charter because nobody else will hold the wall.",
            advantage = "A Guardian and a Warden from the first day, three more militia, and a measure of Binding Salt.",
            constraint = "The council's levy: 40 gold less to start with.",
            play = "Protect, strike back, rebuild.",
            startClasses = listOf(LaunchContent.GUARDIAN, LaunchContent.WARDEN), offeredClass = LaunchContent.BATTLEMAGE, goldDelta = -40, militiaDelta = 3, materials = mapOf(LaunchContent.BINDING_SALT to 1)),
        CharterDef(LICENSED_BAD_IDEAS, "Licensed Bad Ideas", "A charter for experiments, signed by somebody who did not read it.",
            advantage = "A Duelist and a Battlemage, a Runestone Shard and a measure of Void Ink.",
            constraint = "80 gold less to start with, and dangerous contracts are posted from the first day.",
            play = "Invent a build and take the risk it asks for.",
            startClasses = listOf(LaunchContent.DUELIST, LaunchContent.BATTLEMAGE), offeredClass = LaunchContent.WARDEN, goldDelta = -80, materials = mapOf(LaunchContent.RUNESTONE_SHARD to 1, LaunchContent.VOID_INK to 1), earlyDanger = true),
        CharterDef(HONEST_BUSINESS, "Honest Business, Mostly", "A trading charter with a clause about adventurers that nobody mentions.",
            advantage = "A Ranger and a Warden, one more contract on the board every day, and a name that is already known.",
            constraint = "The backers take their part: the forge keeps four fifths of its contract gold.",
            play = "Fund specialists through trade and commissions.",
            startClasses = listOf(LaunchContent.RANGER, LaunchContent.WARDEN), offeredClass = LaunchContent.GUARDIAN, reputationDelta = 2, smithGoldPercent = 80, extraOffers = 1),
    )

    val specialities = listOf(
        // Three contracts won.
        SpecialityDef("vanguard", "Vanguard", "veteran", "A tenth more health; a twentieth less strike.", healthPercent = 110, strikePercent = 95),
        SpecialityDef("striker", "Striker", "veteran", "A tenth more strike; a tenth less health.", healthPercent = 90, strikePercent = 110),
        // Went down twice and came back.
        SpecialityDef("unbroken", "Unbroken", "survivor", "The first time a blow leaves them at a quarter of their health or less, they raise 8 Guard. Once a fight.",
            listOf(EffectDef("branch_unbroken", "Unbroken", Trigger(EventKind.DAMAGE, Who.SELF_TARGET), listOf(Guard(Aim.Self, Fixed(8))), Limit(perFight = 1), "The first time a blow leaves them at a quarter of their health or less, they raise 8 Guard. Once a fight.", listOf(Condition.HolderHealthAtMost(25))))),
        SpecialityDef("careful", "Careful hand", "survivor", "A fifth more support (Guard raised, healing given); a twentieth less strike.", supportPercent = 120, strikePercent = 95),
        // Swore to hold the wall, and held it.
        SpecialityDef("wallwarden", "Wallwarden", "defender", "Starts every fight with 5 Guard.",
            listOf(EffectDef("branch_wallwarden", "Wallwarden", Trigger(EventKind.ROUND_START, Who.ANY), listOf(Guard(Aim.Self, Fixed(5))), Limit(perFight = 1), "Starts every fight with 5 Guard."))),
        SpecialityDef("rallier", "Rallier", "defender", "Gives every ally 2 Guard at the start of a fight.",
            listOf(EffectDef("branch_rallier", "Rallier", Trigger(EventKind.ROUND_START, Who.ANY), listOf(Guard(Aim.AllAllies, Fixed(2))), Limit(perFight = 1), "Gives every ally 2 Guard at the start of a fight."))),
    )

    val laws = listOf(
        WorldLawDef("storm_front", "Storm front", "Everybody on a field starts Wet. Storm strikes run through it for more; heat boils it off into Steam for whoever brings the heat.", days = 3,
            partyStats = mapOf(Stat.WET to 2), enemyStats = mapOf(Stat.WET to 2)),
        WorldLawDef("eclipse", "Eclipse", "Every enemy starts with a Mark on it, and every member of a party starts with 1 Bleed.", days = 3, partyStats = mapOf(Stat.BLEED to 1), enemyStats = mapOf(Stat.MARK to 1)),
        WorldLawDef("scrap_shortage", "Scrap shortage", "Mending a cracked blade costs twice the condition.", days = 4, repairPercent = 200),
    )

    val ranks = listOf(
        GuildRankDef(1, "Chartered Company", "Every enemy on a contract and at the wall is a sixth stronger.", enemyPercent = 15, legacyPoints = 2),
        GuildRankDef(2, "Wardens of the March", "Every enemy is a third stronger, and one more of them comes to every siege.", enemyPercent = 30, extraSiegeUnit = true, legacyPoints = 4),
        GuildRankDef(3, "The Last Company", "Every enemy is half again as strong, one more comes to every siege, and contracts stay on the board a day less.", enemyPercent = 45, extraSiegeUnit = true, offerLifeDelta = -1, legacyPoints = 6),
    )

    val bondEffects = mapOf(
        "comrades" to listOf(EffectDef("bond_comrades", "Comrades", Trigger(EventKind.ROUND_START, Who.ANY), listOf(Give(Stat.MARK, Aim.PriorityFoe)), Limit(perFight = 1), "Fighting beside a comrade, opens the fight by marking the foe that matters.")),
        "protector" to listOf(EffectDef("bond_protector", "Protector", Trigger(EventKind.DAMAGE, Who.ALLY_TARGET), listOf(Guard(Aim.EventTarget, Fixed(4))), Limit(perFight = 1),
            "Fighting beside the one they brought out, gives 4 Guard to the first ally hit down to half health or less. Once a fight.", listOf(Condition.SourceIsFoe, Condition.TargetHealthAtMost(50)))),
    )

    val catalog = GuildCatalog(
        missions, routes, traits, charters, specialities, laws, ranks, bondEffects = bondEffects, oathEffects = oathEffects,
        siegeLeaders = mapOf(A to "warlord_krag", H to "hollow_king", E to "broodmother"),
        siegeElites = mapOf(A to "ashclaw_warchief", H to "pale_knight", E to "ember_drake"),
        siegeGrunts = mapOf(A to listOf("ashclaw_brute", "ashclaw_scout", "ashclaw_thief"), H to listOf("bone_warden", "hollow_shambler", "gravecaller"), E to listOf("cinder_knight", "ember_whelp", "ember_whelp")),
        rivalNames = listOf("the Gilded Lantern" to "Maren Voss", "the Tallow Street Irregulars" to "Dask Orrin", "the Brass Thimble Company" to "Ilse Varn"),
    )
}
