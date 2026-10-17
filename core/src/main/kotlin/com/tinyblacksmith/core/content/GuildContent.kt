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

enum class MissionArchetype { SUPPLY, HUNT, SABOTAGE, ESCORT, RESCUE, RECOVERY, DELVE, EMERGENCY, OPPORTUNITY }

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
    val heartfire: Int = 0,
    val supplier: Boolean = false,
    /** Winning this stage opens the deep road (rules 7). */
    val road: Boolean = false,
)

/** A weapon a package puts in storage on the first morning: plain metal with one flaw that is the point of it. */
data class StartWeaponDef(val familyId: com.tinyblacksmith.core.model.WeaponFamilyId, val coreId: MaterialId, val augmentId: MaterialId, val flaw: com.tinyblacksmith.core.model.AffixId, val quality: Int)

/**
 * An opening package (rules 6): a build direction a run can start in. [traits] names the defining trait by class (a
 * class it does not name draws one); [relicId] is in the workshop from the first morning; the drawback is the negative
 * numbers, said in [drawback]. [plan] is what the first five days can look like.
 */
data class OpeningDef(
    val id: String,
    val name: String,
    val build: String,
    val pitch: String,
    val plan: String,
    val drawback: String,
    val charters: Set<String>,
    val traits: Map<HeroClassId, String>,
    val relicId: String,
    val materials: Map<MaterialId, Int> = emptyMap(),
    val weapon: StartWeaponDef? = null,
    val goldDelta: Int = 0,
    val militiaDelta: Int = 0,
    val integrityDelta: Int = 0,
    /** The cost a number cannot say, in a few words for the opening card; the gold and health it costs are said from the deltas. */
    val costNote: String? = null,
)

/**
 * What a besieger prepares for one siege (rules 6), announced when the besieger is committed. It changes who comes
 * ([addUnit], [gruntUnit]) or how both sides start ([enemyStats], [partyStats]). [counters] are the answers the smith
 * has besides spoiling it, each a different kind of answer. [sabotage] is the second day of the job that spoils it.
 */
data class SiegePrepDef(
    val id: String,
    val factionId: FactionId,
    val name: String,
    val description: String,
    val counters: List<String>,
    val sabotageBrief: String,
    val sabotage: StageDef,
    val addUnit: String? = null,
    /** Every one of the rank and file is this unit instead. */
    val gruntUnit: String? = null,
    val enemyStats: Map<Stat, Int> = emptyMap(),
    val partyStats: Map<Stat, Int> = emptyMap(),
)

/**
 * Something different about one posted contract (rules 6): drawn when it is posted, shown on its card. The first kind
 * moves numbers ([enemyStats], [partyStats], [addUnit], [goldPercent]). The second changes what the smith has to decide:
 * what winning is ([hold] rounds to stand instead of a foe to fell, [protectUnitId] somebody to bring out), who may go
 * ([partyMax]), or what is paid ([payment] instead of gold, [militia] for the wall). [archetypes] empty = any drawn contract.
 */
data class MissionModifierDef(val id: String, val name: String, val description: String, val enemyStats: Map<Stat, Int> = emptyMap(), val partyStats: Map<Stat, Int> = emptyMap(),
    val addUnit: Map<FactionId, String> = emptyMap(), val goldPercent: Int = 100,
    val archetypes: Set<MissionArchetype> = emptySet(), val hold: Int = 0, val protectUnitId: String? = null, val partyMax: Int? = null, val payment: MaterialDraw? = null, val militia: Int = 0)

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
    /** A unit of the town that fills every empty place of the party on this contract (a rescue), or null. */
    val watchUnitId: String? = null,
)

/** The road a faction's contracts lie on. */
/** [where] is the route with its preposition, for a sentence: "on the Ashroad", "in the Flooded Crypt". */
data class RouteDef(val id: String, val name: String, val factionId: FactionId, val description: String, val where: String = "on $name")

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
    /** A second class that would sign on the first morning, at the usual fee, or null for whoever the town has. */
    val alsoOffered: HeroClassId? = null,
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

enum class ArmyKind { STANDARD, CAPTAIN, ENGINE, MONSTER }

/**
 * One of the 36 authored armies (content bible 13): its whole field in line order, front first, never more than the
 * four a side the fight allows. [cost] and [firstSiege] are the bible's authoring numbers; the live strength is the
 * siege's own scaling, applied once. [forecast] is what the scouts report when it is committed, [counters] the answers.
 */
data class ArmyDef(val id: String, val factionId: FactionId, val name: String, val kind: ArmyKind, val members: List<String>, val cost: Int, val firstSiege: Int, val forecast: String, val counters: List<String>)

/**
 * What the bestiary records of an enemy beyond its fight numbers (content bible 10.1, final design Appendix A): its
 * host, authoring cost and earliest siege, the bible's prototype numbers it was converted from once, the role in a few
 * words, the counter, the forecast line, and how faithfully it is implemented. [assetId] is the art it waits for.
 */
data class EnemyProfile(
    val unitId: String, val factionId: FactionId, val host: String, val cost: Int, val earliestSiege: Int,
    val bibleHealth: Int, val bibleAttack: Int, val bibleArmorPercent: Int,
    val job: String, val counter: String, val forecast: String,
    val status: String = "implemented", val adaptation: String = "", val assetId: String = unitId,
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
    val openings: List<OpeningDef> = emptyList(),
    val preparations: List<SiegePrepDef> = emptyList(),
    val modifiers: List<MissionModifierDef> = emptyList(),
    /** Living heroes: the authored siege bestiary's profiles and its 36 army templates (content bible 10 to 13, final design Appendix A). */
    val bestiary: List<EnemyProfile> = emptyList(),
    val armies: List<ArmyDef> = emptyList(),
) {
    val profileById: Map<String, EnemyProfile> = bestiary.associateBy { it.unitId }
    fun army(id: String?): ArmyDef? = armies.firstOrNull { it.id == id }
    fun opening(id: String?): OpeningDef? = openings.firstOrNull { it.id == id }
    fun preparation(id: String?): SiegePrepDef? = preparations.firstOrNull { it.id == id }
    fun modifier(id: String?): MissionModifierDef? = modifiers.firstOrNull { it.id == id }
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
        missions.forEach { m -> m.watchUnitId?.let { if (it !in combat.unitById) problems += "Mission ${m.id} names unknown watch unit $it" } }
        if (missions.none { it.archetype == MissionArchetype.SUPPLY && it.fee == 0 }) problems += "No free supply contract"
        if (missions.none { it.archetype == MissionArchetype.EMERGENCY && it.fee == 0 }) problems += "No free emergency work"
        problems += EffectRules.problems(traits.flatMap { it.effects } + specialities.flatMap { it.effects } + bondEffects.values.flatten() + oathEffects, units = combat.unitById)
        ranks.forEachIndexed { i, r -> if (r.rank != i + 1) problems += "Guild ranks must be numbered from 1 without gaps" }
        laws.forEach { if (it.days <= 0) problems += "Law ${it.id} lasts no days" }
        for (c in charters) {
            (c.startClasses + c.offeredClass + listOfNotNull(c.alsoOffered)).forEach { if (it !in content.classById) problems += "Charter ${c.id} names unknown class ${it.value}" }
            c.materials.keys.forEach { if (it !in content.materialById) problems += "Charter ${c.id} gives unknown material ${it.value}" }
            if (c.startClasses.size != 2) problems += "Charter ${c.id} starts with ${c.startClasses.size} members"
        }
        if (charters.isEmpty()) problems += "No charter"
        dup("opening", openings.map { it.id }); dup("preparation", preparations.map { it.id }); dup("modifier", modifiers.map { it.id })
        for (o in openings) {
            if (content.relics.isNotEmpty() && content.relic(o.relicId) == null) problems += "Opening ${o.id} names unknown relic ${o.relicId}"
            o.traits.forEach { (c, t) -> if (c !in content.classById || t !in traitById) problems += "Opening ${o.id} names unknown class or trait" }
            o.materials.keys.forEach { if (it !in content.materialById) problems += "Opening ${o.id} gives unknown material ${it.value}" }
            o.charters.forEach { if (it !in charterById) problems += "Opening ${o.id} names unknown charter $it" }
            if (o.goldDelta >= 0 && o.militiaDelta >= 0 && o.integrityDelta >= 0 && o.weapon == null) problems += "Opening ${o.id} has no drawback"
        }
        if (openings.isNotEmpty()) for (c in charters) if (openings.count { c.id in it.charters } < 3) problems += "Charter ${c.id} has fewer than three openings"
        for (p in preparations) {
            listOfNotNull(p.addUnit, p.gruntUnit).plus(p.sabotage.rosters.values.flatten()).forEach { if (it !in combat.unitById) problems += "Preparation ${p.id} names unknown unit $it" }
            if (p.counters.size < 2) problems += "Preparation ${p.id} needs two answers"
            if (!p.sabotage.sabotage) problems += "Preparation ${p.id}: its sabotage spoils nothing"
        }
        if (preparations.isNotEmpty()) content.factions.forEach { f -> if (preparations.none { it.factionId == f.id }) problems += "No preparation for ${f.id.value}" }
        modifiers.forEach { m ->
            (m.addUnit.values + listOfNotNull(m.protectUnitId)).forEach { if (it !in combat.unitById) problems += "Modifier ${m.id} names unknown unit $it" }
            if (m.partyMax != null && m.partyMax < 1) problems += "Modifier ${m.id} lets nobody go"
        }
        if (modifiers.isNotEmpty()) missions.filter { it.weight > 0.0 && it.archetype != MissionArchetype.SUPPLY && it.archetype != MissionArchetype.SABOTAGE }.forEach { m ->
            if (modifiers.none { it.archetypes.isEmpty() || m.archetype in it.archetypes }) problems += "No modifier fits ${m.id}"
        }
        if (traits.count { !it.hook } < 1 || traits.count { it.hook } < 1) problems += "Guild traits need both kinds"
        dup("army", armies.map { it.id }); dup("bestiary entry", bestiary.map { it.unitId })
        for (a in armies) {
            if (a.members.isEmpty() || a.members.size > 4) problems += "Army ${a.id} has ${a.members.size} members"
            a.members.forEach { if (it !in combat.unitById) problems += "Army ${a.id} fields unknown unit $it" }
            if (content.factions.none { it.id == a.factionId }) problems += "Army ${a.id} belongs to unknown faction ${a.factionId.value}"
            if (bestiary.isNotEmpty()) {
                val profiles = a.members.mapNotNull { profileById[it] }
                a.members.filter { it !in profileById }.forEach { problems += "Army ${a.id} fields $it, which has no bestiary entry" }
                profiles.filter { it.factionId != a.factionId }.forEach { problems += "Army ${a.id} of ${a.factionId.value} fields ${it.unitId} of ${it.factionId.value}" }
                if (profiles.size == a.members.size && profiles.sumOf { it.cost } != a.cost) problems += "Army ${a.id} costs ${a.cost}, its members ${profiles.sumOf { it.cost }}"
                if (profiles.any { it.earliestSiege > a.firstSiege }) problems += "Army ${a.id} comes before its members may"
            }
            if (a.counters.isEmpty()) problems += "Army ${a.id} has no answer"
        }
        bestiary.forEach { p -> if (p.unitId !in combat.unitById) problems += "Bestiary entry ${p.unitId} has no unit" }
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
    const val STARFALL = "starfall"
    const val SUPPLIER_ROAD = "supplier_road"
    /** Rules 7: the guild's way to open the deep road after the first siege (`engine.DeepRoad`). Posted, never drawn. */
    const val DEEP_ROAD = "deep_road"
    const val LAPSE_SUPPLIER = "supplier"
    /** Living heroes: the caravan a member once guarded (opp_missing_caravan). Posted only as a member's own offer, never drawn. */
    const val MISSING_CARAVAN = "missing_caravan"

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
        EncounterDef(COWARD_RETURNS, "The Coward Returns Alone", 6.0, maxPerRun = 4, cooldownDays = 2, description = "A member brings news of a captured guildmate who is still alive."),
        EncounterDef(SWORD_COMPLAINT, "The Sword Files a Complaint", 2.5, maxPerRun = 3, cooldownDays = 5, description = "A weapon with opinions has one about who carries it."),
        EncounterDef(ACROSS_THE_COUNTER, "Your Best Sword Is Across the Counter", 4.0, maxPerRun = 3, cooldownDays = 3, description = "A dealer knows how to get a weapon of this forge back from the enemy who carries it."),
        EncounterDef(INSURANCE_ADJUSTER, "Insurance Adjuster from the Underworld", 1.5, maxPerRun = 2, cooldownDays = 6, description = "An adjuster will insure one loan against the enemy.", minDay = 4),
        // Living heroes: callers sent by a member's own situation (guild/Hooks). The creditor comes first when a debt is due; the others are drawn.
        EncounterDef(com.tinyblacksmith.core.guild.Hooks.CREDITOR, "The Creditor Calls", 0.0, maxPerRun = 30, cooldownDays = 0, description = "A creditor wants the money a member owes.", followUp = true),
        EncounterDef(com.tinyblacksmith.core.guild.Hooks.LESSON, "A Lesson Offered", 3.0, maxPerRun = 4, cooldownDays = 3, description = "An experienced member offers to teach a new one.", minDay = 3),
        EncounterDef(com.tinyblacksmith.core.guild.Hooks.WOUNDED_FRIEND, "A Friend Asks for a Healer", 3.0, maxPerRun = 4, cooldownDays = 3, description = "A member asks for a healer for an injured friend.", minDay = 3),
    )

    /** What a blade that took the oath of `The Sword Files a Complaint` adds while its whole party is inexperienced. */
    val oathEffects = listOf(EffectDef("oath_inexperienced", "Oath of inexperienced company", Trigger(EventKind.ROUND_START, Who.ANY), listOf(Guard(Aim.AllAllies, Fixed(3)), Give(Stat.REGEN, Aim.AllAllies)), Limit(perFight = 1),
        "While the wielder and every ally are level 3 or lower, every ally starts each fight with 3 Armor and 1 Regeneration.", listOf(Condition.HolderTagged(TAG_NOVICE_PARTY))))

    private fun roster(a: List<String>, h: List<String>, e: List<String>) = mapOf(A to a, H to h, E to e)

    val missions = listOf(
        MissionDef(SUPPLY_RUN, MissionArchetype.SUPPLY, "Supply run", days = 1, fee = 0, weight = 1.0,
            description = "Bring a wagon of ordinary supplies to town past a few enemies.",
            prep = "Anybody will do. One member alone can manage it.",
            failure = "Less or nothing brought home, and wounds. Nobody dies or is captured on a supply run.",
            stages = listOf(StageDef(roster(listOf("ashclaw_scout", "ashclaw_scout"), listOf("hollow_shambler", "hollow_shambler"), listOf("ember_whelp", "ember_whelp")), Danger.SAFE, percent = 75,
                goldMin = 18, goldMax = 30, materials = MaterialDraw(3, 1, 2), suppression = 1))),
        MissionDef(HUNT, MissionArchetype.HUNT, "Hunt", days = 1, fee = 15, weight = 1.2, minDay = 2,
            description = "One of their elite fighters has been seen with a single bodyguard. Defeat both for gold, materials and a setback to their faction.",
            prep = "A weapon of the element they are weak to, and a way through whatever protects that elite.",
            failure = "Wounds and no reward. If the whole party goes down, 1 member is captured.",
            stages = listOf(StageDef(roster(listOf("ashclaw_warchief", "ashclaw_scout"), listOf("pale_knight", "hollow_shambler"), listOf("ember_drake", "ember_whelp")), Danger.DANGEROUS, percent = 130,
                goldMin = 55, goldMax = 85, materials = MaterialDraw(2, 3, 4, scarce = true), suppression = 4))),
        // Two days: in past the pickets, then the work itself. Its second day is the preparation's own (`SiegePrepDef.sabotage`); the one written here is for a siege without one.
        MissionDef(SABOTAGE, MissionArchetype.SABOTAGE, "Sabotage", days = 2, fee = 10, weight = 1.0, minDay = 1, lifeDays = 2,
            description = "Two days in their camp. On the first day the party fights past their sentries. On the second it spoils what they are preparing for the siege.",
            prep = "A party that can win two fights in a row. What the second day asks is written on the card.",
            failure = "Their preparation stays in place for the siege. If the whole party goes down, 1 member is captured.",
            stages = listOf(
                StageDef(roster(listOf("ashclaw_scout", "ashclaw_scout"), listOf("hollow_shambler", "hollow_shambler"), listOf("ember_whelp", "ember_whelp")), Danger.DANGEROUS, percent = 85, goldMin = 10, goldMax = 20),
                StageDef(roster(listOf("ashclaw_brute", "ashclaw_thief", "ashclaw_scout"), listOf("bone_warden", "hollow_shambler", "hollow_shambler"), listOf("cinder_knight", "ember_whelp", "ember_whelp")), Danger.DANGEROUS, percent = 90,
                    objective = ObjectiveKind.HOLD, rounds = 4, goldMin = 15, goldMax = 25, suppression = 5, sabotage = true))),
        // The siege's opportunities: posted with the sabotage, gone with it. One party, one of them.
        MissionDef(STARFALL, MissionArchetype.OPPORTUNITY, "Starfall site", days = 2, fee = 0, weight = 0.0, lifeDays = 2,
            description = "Something fell from the sky inside their lines. The party has two days to reach it and carry out what still burns.",
            prep = "A party that can win two fights in a row. The second is against one of their elite fighters.",
            failure = "No Heartfire. If the whole party goes down on either day, 1 member is captured.",
            stages = listOf(
                StageDef(roster(listOf("ashclaw_scout", "ashclaw_thief"), listOf("hollow_shambler", "bone_warden"), listOf("ember_whelp", "ember_whelp")), Danger.DANGEROUS, percent = 90, goldMin = 15, goldMax = 25),
                StageDef(roster(listOf("ashclaw_warchief", "ashclaw_scout"), listOf("pale_knight", "hollow_shambler"), listOf("ember_drake", "ember_whelp")), Danger.DANGEROUS, percent = 100,
                    goldMin = 20, goldMax = 30, materials = MaterialDraw(1, 4, 6, scarce = true), heartfire = 1))),
        MissionDef(SUPPLIER_ROAD, MissionArchetype.OPPORTUNITY, "The supplier's road", days = 2, fee = 0, weight = 0.0, lifeDays = 2,
            description = "Your supplier has to get a cart through before the siege closes the roads. The party guards it for two days.",
            prep = "A Guardian in front, and a way to stop enemies who get past the front. The cart must survive both days.",
            failure = "If the party is defeated or retreats on either day, the cart is lost, and for 5 days the supplier brings 1 fewer of every limited material each morning. Nobody is captured on this road. Leaving this quest on the board costs nothing.",
            stages = listOf(
                StageDef(roster(listOf("ashclaw_scout", "ashclaw_scout", "ashclaw_thief"), listOf("hollow_shambler", "hollow_shambler", "hollow_shambler"), listOf("ember_whelp", "ember_whelp", "ember_whelp")), Danger.SAFE, percent = 80,
                    objective = ObjectiveKind.HOLD, rounds = 4, protectUnitId = "escort_cart", goldMin = 15, goldMax = 25),
                StageDef(roster(listOf("ashclaw_brute", "ashclaw_thief", "ashclaw_scout"), listOf("bone_warden", "hollow_shambler", "hollow_shambler"), listOf("cinder_knight", "ember_whelp", "ember_whelp")), Danger.SAFE, percent = 90,
                    objective = ObjectiveKind.HOLD, rounds = 4, protectUnitId = "escort_cart", goldMin = 30, goldMax = 45, materials = MaterialDraw(3, 2, 4), supplier = true))),
        // Posted after the first siege while the road is shut (`DeepRoad.contractDue`), never drawn and never one of the siege's jobs.
        MissionDef(DEEP_ROAD, MissionArchetype.OPPORTUNITY, "The deep road", days = 2, fee = 0, weight = 0.0, lifeDays = 4,
            description = "The caravan master will bring rare stock down the deep road if her first train gets through. Two days beside it.",
            prep = "A Guardian in front and something for whoever goes past the front. The train has to stand both days.",
            failure = "The caravan turns back and the deep road stays shut. The quest returns to the board, and the second siege opens the road anyway. Nobody is captured on this road.",
            stages = listOf(
                StageDef(roster(listOf("ashclaw_scout", "ashclaw_scout", "ashclaw_thief"), listOf("hollow_shambler", "hollow_shambler", "hollow_shambler"), listOf("ember_whelp", "ember_whelp", "ember_whelp")), Danger.SAFE, percent = 85,
                    objective = ObjectiveKind.HOLD, rounds = 4, protectUnitId = "escort_cart", goldMin = 15, goldMax = 25),
                StageDef(roster(listOf("ashclaw_brute", "ashclaw_thief", "ashclaw_scout"), listOf("bone_warden", "hollow_shambler", "hollow_shambler"), listOf("cinder_knight", "ember_whelp", "ember_whelp")), Danger.SAFE, percent = 95,
                    objective = ObjectiveKind.HOLD, rounds = 4, protectUnitId = "escort_cart", goldMin = 25, goldMax = 40, road = true))),
        MissionDef(ESCORT, MissionArchetype.ESCORT, "Escort", days = 2, fee = 0, weight = 0.9, minDay = 3,
            description = "Guard a supplier's cart on the road for two days. If the cart is destroyed, the fight is lost.",
            prep = "A Guardian in front, and a way to stop enemies who get past the front. The cart must survive.",
            failure = "If the cart is destroyed, that day pays nothing. Nobody is captured on an escort.",
            stages = listOf(
                StageDef(roster(listOf("ashclaw_scout", "ashclaw_scout", "ashclaw_thief"), listOf("hollow_shambler", "hollow_shambler", "hollow_shambler"), listOf("ember_whelp", "ember_whelp", "ember_whelp")), Danger.SAFE, percent = 85,
                    objective = ObjectiveKind.HOLD, rounds = 4, protectUnitId = "escort_cart", goldMin = 25, goldMax = 35),
                StageDef(roster(listOf("ashclaw_brute", "ashclaw_thief", "ashclaw_thief"), listOf("bone_warden", "hollow_shambler", "gravecaller"), listOf("cinder_knight", "ember_whelp", "ember_whelp")), Danger.SAFE, percent = 95,
                    objective = ObjectiveKind.HOLD, rounds = 5, protectUnitId = "escort_cart", goldMin = 50, goldMax = 70, materials = MaterialDraw(4, 1, 3)))),
        MissionDef(RELIC_DELVE, MissionArchetype.DELVE, "Relic delve", days = 2, fee = 20, weight = 0.8, minDay = 4, optionalPush = true,
            description = "An old ruin they have not finished robbing. The first hall is worth the walk. Something waits deeper inside, and Heartfire with it.",
            prep = "A party that can last through a long fight. After the first hall, you choose the next morning whether the party goes deeper or comes home.",
            failure = "What the first hall gives is yours whatever happens deeper. If the whole party goes down in the first hall, 1 member is captured. In the deep hall, 1 member dies and 1 is captured.",
            stages = listOf(
                StageDef(roster(listOf("ashclaw_brute", "ashclaw_scout", "ashclaw_scout"), listOf("bone_warden", "hollow_shambler", "hollow_shambler"), listOf("cinder_knight", "ember_whelp", "ember_whelp")), Danger.DANGEROUS, percent = 100,
                    goldMin = 40, goldMax = 60, materials = MaterialDraw(2, 2, 4)),
                StageDef(roster(listOf("ashclaw_warchief", "ashclaw_brute", "ashclaw_thief"), listOf("pale_knight", "gravecaller", "bone_warden"), listOf("ember_drake", "cinder_knight", "ember_whelp")), Danger.LETHAL, percent = 125,
                    goldMin = 60, goldMax = 90, materials = MaterialDraw(2, 4, 6, scarce = true), relicOffer = true, heartfire = 1))),
        // Posted only for a real captive or a real blade; never drawn for the board.
        MissionDef(RESCUE, MissionArchetype.RESCUE, "Rescue", days = 2, fee = 0, weight = 0.0, lifeDays = 9, watchUnitId = "town_watch",
            description = "One of your members has been captured. On the first day the party breaks in, on the second it brings the captive out. The town sends a guard for every empty place in the party.",
            prep = "Armor and healing. On the way out, the captive must not go down. One member with two town guards can do it.",
            failure = "The captive stays where they are and their deadline does not change. The party comes home hurt. Nobody else is captured.",
            stages = listOf(
                StageDef(roster(listOf("ashclaw_brute", "ashclaw_scout", "ashclaw_scout"), listOf("bone_warden", "hollow_shambler", "hollow_shambler"), listOf("cinder_knight", "ember_whelp", "ember_whelp")), Danger.SAFE, percent = 60),
                StageDef(roster(listOf("ashclaw_thief", "ashclaw_scout", "ashclaw_scout"), listOf("hollow_shambler", "hollow_shambler", "bone_warden"), listOf("ember_whelp", "ember_whelp", "cinder_knight")), Danger.SAFE, percent = 55,
                    objective = ObjectiveKind.HOLD, rounds = 3, protectUnitId = "captive"))),
        MissionDef(RECOVERY, MissionArchetype.RECOVERY, "Recovery", days = 1, fee = 10, weight = 0.0, lifeDays = 9,
            description = "One of their fighters carries a weapon from your forge. Defeat that fighter to win it back.",
            prep = "Prepare as for a hunt. The enemy who carries your weapon is stronger with it and uses its effects. Winning it back also brings 1 Heartfire.",
            failure = "The weapon stays with them, and you still know where it is. If the whole party goes down, 1 member is captured.",
            stages = listOf(StageDef(roster(listOf("ashclaw_warchief", "ashclaw_brute"), listOf("pale_knight", "bone_warden"), listOf("ember_drake", "cinder_knight")), Danger.DANGEROUS, percent = 85, goldMin = 20, goldMax = 30, suppression = 4, heartfire = 1))),
        MissionDef(MISSING_CARAVAN, MissionArchetype.ESCORT, "The missing caravan", days = 1, fee = 0, weight = 0.0, lifeDays = 3,
            description = "A caravan failed to arrive. A member who once guarded that road knows where it would have stopped. The party finds it and drives off whoever holds it.",
            prep = "A party that can win one fight. What the caravan carried is the reward.",
            failure = "The caravan stays lost. If the whole party goes down, 1 member is captured.",
            stages = listOf(StageDef(roster(listOf("ashclaw_brute", "ashclaw_scout", "ashclaw_thief"), listOf("bone_warden", "hollow_shambler", "hollow_shambler"), listOf("cinder_knight", "ember_whelp", "ember_whelp")), Danger.DANGEROUS, percent = 95,
                goldMin = 30, goldMax = 45, materials = MaterialDraw(3, 1, 3), suppression = 2))),
        MissionDef(WALL_WORK, MissionArchetype.EMERGENCY, "Restore the forge", days = 1, fee = 0, weight = 0.0,
            description = "No fight. The members spend the day restoring the forge.",
            prep = "Any member who can stand.",
            failure = "It always succeeds: up to 5 forge health and 1 militia. The party is away tonight and cannot defend a siege.",
            stages = listOf(StageDef(emptyMap(), Danger.SAFE, integrity = 5, militia = 1))),
    )

    val routes = listOf(
        RouteDef("ashroad", "the Ashroad", A, "The raiders' road: many of them, and one leader who gives the orders."),
        RouteDef("flooded_crypt", "the Flooded Crypt", H, "The dead keep rising while the one who calls them is still standing.", where = "in the Flooded Crypt"),
        RouteDef("cinder_quarry", "the Cinder Quarry", E, "Few of them, slow and heavily guarded, and some breathe fire every third round.", where = "at the Cinder Quarry"),
    )

    private fun t(id: String, name: String, trigger: Trigger, actions: List<Action>, limit: Limit, description: String, conditions: List<Condition> = emptyList()) =
        EffectDef("trait_$id", name, trigger, actions, limit, description, conditions)

    val traits = listOf(
        GuildTraitDef("protective", "Protective", false, "When an enemy's hit leaves a party member (this one included) at 50% health or less, gives that member 3 Armor. Once per round.",
            listOf(t("protective", "Protective", Trigger(EventKind.DAMAGE, Who.ALLY_TARGET), listOf(Guard(Aim.EventTarget, Fixed(3))), Limit(perRound = 1), "When an enemy's hit leaves a party member (this one included) at 50% health or less, gives that member 3 Armor. Once per round.", listOf(Condition.SourceIsFoe, Condition.TargetHealthAtMost(50))))),
        GuildTraitDef("impatient", "Impatient", false, "At the start of the first round, before anyone acts, this member deals 65% of their Attack to the front enemy, or to a marked enemy if there is one. Once per fight.",
            listOf(t("impatient", "Impatient", Trigger(EventKind.ROUND_START, Who.ANY), listOf(Damage(Aim.Foe, Strike(65))), Limit(perFight = 1), "At the start of the first round, before anyone acts, this member deals 65% of their Attack to the front enemy, or to a marked enemy if there is one. Once per fight."))),
        GuildTraitDef("cowardly", "Cowardly", false, "Starts every fight with 4 Armor. When the party retreats with this member in it, the party still brings home 34% of that stage's gold.",
            startStats = mapOf(Stat.GUARD to 4), retreatGoldPercent = 34),
        GuildTraitDef("greedy", "Gold-Hungry", false, "+20% gold from every stage the party wins with this member in it. This member keeps an extra 5% of the quest gold as their own share.", winGoldPercent = 20, shareDelta = 5),
        GuildTraitDef("methodical", "Methodical", false, "When this member's hit uses up a Mark, this member deals 3 more damage to that enemy, on top of the Mark's own bonus. Once per round.",
            listOf(t("methodical", "Methodical", Trigger(EventKind.STAT_SPENT, Who.SELF_SOURCE, Stat.MARK), listOf(Damage(Aim.EventTarget, Fixed(3))), Limit(perRound = 1), "When this member's hit uses up a Mark, this member deals 3 more damage to that enemy, on top of the Mark's own bonus. Once per round."))),
        GuildTraitDef("loyal_guild", "Avenger", false, "When an enemy downs an ally, this member deals 100% of their Attack to that enemy. Once per fight.",
            listOf(t("loyal", "Avenger", Trigger(EventKind.DOWNED, Who.ALLY_TARGET), listOf(Damage(Aim.EventSource, Strike())), Limit(perFight = 1), "When an enemy downs an ally, this member deals 100% of their Attack to that enemy. Once per fight.", listOf(Condition.SourceIsFoe)))),
        GuildTraitDef("storm_touched", "Storm-Touched", true, "Starts every fight with 1 Charge and can Discharge with any weapon: at 3 or more Charge, this member spends all of it to deal 8 storm damage. Once per round.",
            listOf(CombatContent.CHARGE_BURST), tags = setOf(CombatContent.TAG_CHARGE), startStats = mapOf(Stat.CHARGE to 1)),
        GuildTraitDef("pain_collector", "Pain Collector", true, "When an enemy's hit damages this member, this member gains 2 Thorns. Once per round. The next enemy whose attack hits them takes damage equal to their Thorns.",
            listOf(t("pain_collector", "Pain Collector", Trigger(EventKind.DAMAGE, Who.SELF_TARGET), listOf(Give(Stat.THORNS, Aim.Self, Fixed(2))), Limit(perRound = 1), "When an enemy's hit damages this member, this member gains 2 Thorns. Once per round.", listOf(Condition.SourceIsFoe, Condition.NotTick)))),
        GuildTraitDef("rust_savant", "Rust Savant", true, "When a party member's weapon cracks (this member's included), this member deals 6 damage to the front enemy, or to a marked enemy if there is one. Twice per fight.",
            listOf(t("rust_savant", "Rust Savant", Trigger(EventKind.FRACTURED, Who.ALLY_SOURCE), listOf(Damage(Aim.Foe, Fixed(6))), Limit(perFight = 2), "When a party member's weapon cracks (this member's included), this member deals 6 damage to the front enemy, or to a marked enemy if there is one. Twice per fight."))),
        GuildTraitDef("overhealer", "Overhealer", true, "When this member's healing ability heals an ally past full health, that ally gains 1 Regeneration. Once per round.",
            listOf(t("overhealer", "Overhealer", Trigger(EventKind.OVERHEAL, Who.SELF_SOURCE), listOf(Give(Stat.REGEN, Aim.EventTarget)), Limit(perRound = 1), "When this member's healing ability heals an ally past full health, that ally gains 1 Regeneration. Once per round.", listOf(Condition.FromAction)))),
        GuildTraitDef("second_wind", "Second Wind", true, "The first time damage leaves this member at 34% health or less, they heal 10. Once per fight. It does not save a member who is already down.",
            listOf(t("second_wind", "Second Wind", Trigger(EventKind.DAMAGE, Who.SELF_TARGET), listOf(Heal(Aim.Self, Fixed(10))), Limit(perFight = 1), "The first time damage leaves this member at 34% health or less, they heal 10. Once per fight.", listOf(Condition.HolderHealthAtMost(34))))),
        GuildTraitDef("oathkeeper", "Oathkeeper", true, "While carrying a common or uncommon weapon, or no weapon, gains 2 Armor at the start of every round.",
            listOf(t("oathkeeper", "Oathkeeper", Trigger(EventKind.ROUND_START, Who.ANY), listOf(Guard(Aim.Self, Fixed(2))), Limit(perRound = 1), "While carrying a common or uncommon weapon, or no weapon, gains 2 Armor at the start of every round.", listOf(Condition.HolderTagged(CombatContent.TAG_COMMON_GEAR))))),
    )

    val charters = listOf(
        CharterDef(TOWNS_LAST_HOPE, "Town's Last Hope", "The council signs your charter because nobody else will hold the wall.",
            advantage = "A Guardian and a Warden from the first day, a Battlemage for hire on the first morning at half the recruitment fee, 3 more militia, and 1 Binding Salt.",
            constraint = "The council's levy: you start with 40 gold less.",
            play = "Protect, hit back, rebuild.",
            startClasses = listOf(LaunchContent.GUARDIAN, LaunchContent.WARDEN), offeredClass = LaunchContent.BATTLEMAGE, goldDelta = -40, militiaDelta = 3, materials = mapOf(LaunchContent.BINDING_SALT to 1)),
        CharterDef(LICENSED_BAD_IDEAS, "Licensed Bad Ideas", "A charter for experiments, signed by somebody who did not read it.",
            advantage = "A Duelist and a Battlemage from the first day, a Guardian for hire on the first morning at half the recruitment fee and a Warden at the full fee, 1 Runestone Shard, 1 Void Ink and 2 Silver.",
            constraint = "You start with 50 gold less, and every kind of quest can be posted from the first day, the dangerous ones included.",
            play = "Invent a build and take the risk it asks for.",
            startClasses = listOf(LaunchContent.DUELIST, LaunchContent.BATTLEMAGE), offeredClass = LaunchContent.GUARDIAN, alsoOffered = LaunchContent.WARDEN, goldDelta = -50, materials = mapOf(LaunchContent.RUNESTONE_SHARD to 1, LaunchContent.VOID_INK to 1, LaunchContent.SILVER to 2), earlyDanger = true),
        CharterDef(HONEST_BUSINESS, "Honest Business, Mostly", "A trading charter with a clause about adventurers that nobody mentions.",
            advantage = "A Ranger and a Warden from the first day, a Guardian for hire on the first morning at half the recruitment fee, 1 more quest on the board every day, and +2 reputation: a name that is already known.",
            constraint = "The backers take their part: the forge keeps 80% of the quest gold left after the members' shares.",
            play = "Fund specialists through trade and commissions.",
            startClasses = listOf(LaunchContent.RANGER, LaunchContent.WARDEN), offeredClass = LaunchContent.GUARDIAN, reputationDelta = 2, smithGoldPercent = 80, extraOffers = 1),
    )

    val specialities = listOf(
        // Three contracts won.
        SpecialityDef("vanguard", "Vanguard", "veteran", "+10% Health. -5% Attack.", healthPercent = 110, strikePercent = 95),
        SpecialityDef("striker", "Striker", "veteran", "+10% Attack. -10% Health.", healthPercent = 90, strikePercent = 110),
        // Went down twice and came back.
        SpecialityDef("unbroken", "Unbroken", "survivor", "The first time damage leaves this member at 25% health or less, they gain 8 Armor. Once per fight.",
            listOf(EffectDef("branch_unbroken", "Unbroken", Trigger(EventKind.DAMAGE, Who.SELF_TARGET), listOf(Guard(Aim.Self, Fixed(8))), Limit(perFight = 1), "The first time damage leaves this member at 25% health or less, they gain 8 Armor. Once per fight.", listOf(Condition.HolderHealthAtMost(25))))),
        SpecialityDef("careful", "Careful hand", "survivor", "+20% Support (the Armor and healing their own ability gives). -5% Attack.", supportPercent = 120, strikePercent = 95),
        // Swore to hold the wall, and held it.
        SpecialityDef("wallwarden", "Wallwarden", "defender", "Starts every fight with 5 Armor.",
            listOf(EffectDef("branch_wallwarden", "Wallwarden", Trigger(EventKind.ROUND_START, Who.ANY), listOf(Guard(Aim.Self, Fixed(5))), Limit(perFight = 1), "Starts every fight with 5 Armor."))),
        SpecialityDef("rallier", "Rallier", "defender", "At the start of every fight, this member gives every ally 2 Armor.",
            listOf(EffectDef("branch_rallier", "Rallier", Trigger(EventKind.ROUND_START, Who.ANY), listOf(Guard(Aim.AllAllies, Fixed(2))), Limit(perFight = 1), "At the start of every fight, this member gives every ally 2 Armor."))),
    )

    val laws = listOf(
        WorldLawDef("storm_front", "Storm front", "On quests, every fighter on both sides starts with 2 Wet. Storm damage to a Wet fighter deals 2 extra. Burn applied to a Wet enemy cancels the Wet and gives Steam to whoever applied it.", days = 3,
            partyStats = mapOf(Stat.WET to 2), enemyStats = mapOf(Stat.WET to 2)),
        WorldLawDef("eclipse", "Eclipse", "On quests, every enemy starts with a Mark, and every party member starts with 1 Bleed.", days = 3, partyStats = mapOf(Stat.BLEED to 1), enemyStats = mapOf(Stat.MARK to 1)),
        WorldLawDef("scrap_shortage", "Scrap shortage", "A weapon that cracks on a quest loses twice the usual extra condition.", days = 4, repairPercent = 200),
    )

    val ranks = listOf(
        GuildRankDef(1, "Chartered Company", "Every enemy on quests and at sieges has +15% Health, Attack and Support.", enemyPercent = 15, legacyPoints = 2),
        GuildRankDef(2, "Wardens of the March", "Every enemy on quests and at sieges has +30% Health, Attack and Support, and 1 more enemy comes to every siege.", enemyPercent = 30, extraSiegeUnit = true, legacyPoints = 4),
        GuildRankDef(3, "The Last Company", "Every enemy on quests and at sieges has +45% Health, Attack and Support, 1 more enemy comes to every siege, and quest offers stay on the board 1 day less.", enemyPercent = 45, extraSiegeUnit = true, offerLifeDelta = -1, legacyPoints = 6),
    )

    val bondEffects = mapOf(
        "comrades" to listOf(EffectDef("bond_comrades", "Comrades", Trigger(EventKind.ROUND_START, Who.ANY), listOf(Give(Stat.MARK, Aim.PriorityFoe)), Limit(perFight = 1), "When fighting beside a comrade, this member puts a Mark on the priority target at the start of the fight. The priority target is an enemy summoner first, then the enemy leader, then the enemy with the most Health. Once per fight.")),
        "protector" to listOf(EffectDef("bond_protector", "Protector", Trigger(EventKind.DAMAGE, Who.ALLY_TARGET), listOf(Guard(Aim.EventTarget, Fixed(4))), Limit(perFight = 1),
            "When fighting beside the member they rescued, this member gives 4 Armor to the first party member an enemy's hit leaves at 50% health or less. Once per fight.", listOf(Condition.SourceIsFoe, Condition.TargetHealthAtMost(50)))),
    )

    private val SWORD = LaunchContent.SWORD
    private val G = LaunchContent.GUARDIAN
    private val W = LaunchContent.WARDEN
    private val D = LaunchContent.DUELIST
    private val B = LaunchContent.BATTLEMAGE
    private val R = LaunchContent.RANGER

    /**
     * Eight packages, seven build directions. Every enabler is in the package: no opening waits on a drop. No drawback is
     * militia: the watch thins by one a day, so militia taken on the first morning is gone before the first siege whatever
     * the package (measured, docs/run_variety_evidence/REPORT.md). Names and numbers PROPOSED.
     */
    val openings = listOf(
        OpeningDef("overflow", "The Overflowing Cup", "Overheal", "Healing past full health is not wasted. It becomes Armor, and the Overhealer's also gives Regeneration.",
            plan = "Loan the Warden a staff for more healing. Keep the front topped up and let the Basin turn the rest into Armor. Verdant Sap on the front's weapon feeds it.",
            drawback = "40 gold less to start with.", charters = setOf(TOWNS_LAST_HOPE, HONEST_BUSINESS), traits = mapOf(W to "overhealer"), relicId = CombatContent.OVERFLOW_BASIN,
            materials = mapOf(LaunchContent.VERDANT_SAP to 2), goldDelta = -40),
        OpeningDef("salvage", "Break It on Purpose", "Breakage", "When a party member's weapon cracks, the Salvage Bell gives every ally Armor and the Rust Savant member deals damage.",
            plan = "Loan the Brittle sword to whoever stands in front. When their Armor breaks, the sword cracks, the Bell gives Armor and the Rust Savant member deals damage. Repair it after every fight. A Runestone Shard makes a second answer.",
            drawback = "The sword is Brittle and has to be repaired after every fight. The forge starts with 10 less health.", charters = setOf(TOWNS_LAST_HOPE, LICENSED_BAD_IDEAS), traits = mapOf(W to "rust_savant", B to "rust_savant"),
            relicId = CombatContent.SALVAGE_BELL, materials = mapOf(LaunchContent.RUNESTONE_SHARD to 1, LaunchContent.IRON to 2), weapon = StartWeaponDef(SWORD, LaunchContent.IRON, LaunchContent.EMBER_RESIN, LaunchContent.BRITTLE, 45), integrityDelta = -10,
            costNote = "The Brittle sword needs repair after every fight"),
        OpeningDef("stormwell", "The Stormwell", "Storm and Charge", "One member stores Charge with any weapon and spends it in a Discharge. The Ledger turns each Discharge into Regeneration.",
            plan = "Forge a Stormglass weapon for the Storm-Touched member. With Binding Salt in an Advanced forge, healing them stores Charge too. Three Charge make one Discharge.",
            drawback = "40 gold less to start with.", charters = setOf(TOWNS_LAST_HOPE, LICENSED_BAD_IDEAS), traits = mapOf(G to "storm_touched", D to "storm_touched"), relicId = CombatContent.STORM_LEDGER,
            materials = mapOf(LaunchContent.STORMGLASS to 1, LaunchContent.BINDING_SALT to 1), goldDelta = -40),
        OpeningDef("furnace", "Borrowed Fire", "Burn into Armor", "Put Burn on the enemies, then take it back off them. Each time, the Furnace Lung gives every ally Armor.",
            plan = "Forge fire weapons from Ember Resin. Put the Void Ink into an Advanced forge: that weapon takes Burn off the enemy and the Lung gives everybody Armor for it.",
            drawback = "40 gold less to start with.", charters = setOf(LICENSED_BAD_IDEAS, HONEST_BUSINESS), traits = mapOf(B to "second_wind", W to "protective"), relicId = CombatContent.FURNACE_LUNG,
            materials = mapOf(LaunchContent.VOID_INK to 1, LaunchContent.EMBER_RESIN to 2), goldDelta = -40),
        OpeningDef("marked", "The Marked Card", "Marks and burst", "Mark the most important enemy, and make every Mark the Methodical member consumes hit harder.",
            plan = "The Methodical member wants marked targets: a Sun Ash weapon marks on its own hit, and when the first enemy is defeated, a spectral helper joins the party. Go for the leader first.",
            drawback = "50 gold less to start with.", charters = setOf(HONEST_BUSINESS, LICENSED_BAD_IDEAS), traits = mapOf(R to "methodical", D to "methodical"), relicId = CombatContent.BONE_MUSIC_BOX,
            materials = mapOf(LaunchContent.SUN_ASH to 1, LaunchContent.GRAVE_DUST to 1), goldDelta = -50),
        OpeningDef("blood_price", "Paid in Blood", "Blood price", "The Bloodbound axe spends its wielder's health to deal twice as much damage. Heal them afterwards and they gain Armor too.",
            plan = "Loan the Bloodbound axe to somebody sturdy and keep a healer behind them. The Receipt turns that healing into Armor. Never send it out without the healer.",
            drawback = "The axe takes health from whoever carries it. The forge starts with 10 less health.", charters = setOf(TOWNS_LAST_HOPE, LICENSED_BAD_IDEAS, HONEST_BUSINESS), traits = mapOf(G to "second_wind", R to "second_wind", D to "second_wind"), relicId = CombatContent.BLOOD_RECEIPT,
            weapon = StartWeaponDef(LaunchContent.AXE, LaunchContent.IRON, LaunchContent.EMBER_RESIN, LaunchContent.BLOODBOUND, 48), integrityDelta = -10,
            costNote = "The axe drains whoever carries it"),
        OpeningDef("plain_oath", "The Plain Oath", "Common steel", "Plain weapons, carried on purpose. A member with a Common or Uncommon weapon, or none, deals extra damage and gains Armor.",
            plan = "Forge iron and bronze for the party on purpose and keep the good metal for the shelf. A Rare or better weapon in a member's hands ends the oath for them.",
            drawback = "60 gold less to start with. A member who carries a Rare or better weapon gets nothing from the Blunt Oath or the Oathkeeper trait.", charters = setOf(TOWNS_LAST_HOPE, LICENSED_BAD_IDEAS, HONEST_BUSINESS), traits = mapOf(G to "oathkeeper", D to "oathkeeper", R to "oathkeeper"),
            relicId = CombatContent.BLUNT_OATH, materials = mapOf(LaunchContent.IRON to 3), goldDelta = -60,
            costNote = "A Rare or better weapon ends a member's oath"),
        OpeningDef("careful_books", "Careful Books", "Take it and run", "A party that knows when to leave, and still gets paid: a retreat brings home part of the stage's gold.",
            plan = "Take quests a step above your strength with the Cautious retreat rule. A retreat still brings home part of the stage's gold. Spend it on the defenders.",
            drawback = "The forge starts with 15 less health.", charters = setOf(HONEST_BUSINESS, LICENSED_BAD_IDEAS), traits = mapOf(R to "cowardly", D to "cowardly"), relicId = CombatContent.COWARDS_MEDAL,
            materials = mapOf(LaunchContent.BRONZE to 2), integrityDelta = -15),
    )

    private fun spoil(a: List<String>, objective: ObjectiveKind, rounds: Int = 0, percent: Int = 90) =
        StageDef(mapOf(A to a, H to a, E to a), Danger.DANGEROUS, percent = percent, objective = objective, rounds = rounds, goldMin = 15, goldMax = 25, suppression = 5, sabotage = true)

    /** Six preparations, two a faction. Each changes what the best wall is, and each has answers besides spoiling it. */
    val preparations = listOf(
        SiegePrepDef("shield_wall", A, "Shield wall", "The raiders come behind shields. Every one of them starts with 5 Armor.",
            counters = listOf("A spear's hit still deals half of the damage their Armor blocks.", "Bleed damage ignores Armor.", "They are weak to Frost: Frost weapons deal 30% more damage and break the Armor faster."),
            sabotageBrief = "Burn the shield carts and hold the fire until they are ash.", sabotage = spoil(listOf("ashclaw_brute", "ashclaw_scout", "ashclaw_scout"), ObjectiveKind.HOLD, rounds = 4), enemyStats = mapOf(Stat.GUARD to 5)),
        SiegePrepDef("cutpurses", A, "Knives for the back", "This time all their ordinary fighters are thieves. Each one attacks the last defender in the line, past the front.",
            counters = listOf("Reserve all 3 places for the wall and put your sturdiest member third. Reserved members stand in the order you reserve them.", "Armor for the whole line also protects the back.", "A bow's second arrow hits the last enemy in their line from the first round."),
            sabotageBrief = "Find the thieves in their camp before they find your wall.", sabotage = spoil(listOf("ashclaw_thief", "ashclaw_thief", "ashclaw_thief"), ObjectiveKind.DEFEAT), gruntUnit = "ashclaw_thief"),
        SiegePrepDef("gravecaller", H, "A gravecaller behind the line", "A gravecaller marches with them. Every round it heals the weakest enemy, and every third round it summons a shambler.",
            counters = listOf("A Mark on the gravecaller makes the party attack it first.", "A bow or a storm weapon reaches the back of their line.", "Win before the third round, when it summons its first shambler."),
            sabotageBrief = "Kill the gravecaller before it marches.", sabotage = spoil(listOf("gravecaller", "bone_warden", "hollow_shambler"), ObjectiveKind.DEFEAT), addUnit = "gravecaller"),
        SiegePrepDef("grave_mist", H, "Grave mist", "A mist comes in ahead of them. Every defender starts with 2 Bleed and every enemy with 1 Regeneration.",
            counters = listOf("A healer on the wall outlasts it.", "Verdant weapons give their wielders Regeneration.", "They are weak to Sun: Sun weapons end the fight before the mist matters."),
            sabotageBrief = "Break the censers and hold the hollow until the mist thins.", sabotage = spoil(listOf("bone_warden", "hollow_shambler", "hollow_shambler"), ObjectiveKind.HOLD, rounds = 5), enemyStats = mapOf(Stat.REGEN to 1), partyStats = mapOf(Stat.BLEED to 2)),
        SiegePrepDef("second_drake", E, "A second drake", "They bring a second drake. In the third round, every defender is hit by fire breath twice.",
            counters = listOf("A drake with Chill deals 35% less damage with its next attack, its breath included.", "Armor for the whole line before the third round.", "Defeat one drake within the first 2 rounds."),
            sabotageBrief = "Kill the drake in its pen.", sabotage = spoil(listOf("ember_drake", "ember_whelp"), ObjectiveKind.DEFEAT, percent = 95), addUnit = "ember_drake"),
        SiegePrepDef("banked_fires", E, "Banked fires", "They have fed the fires for days. Every defender starts with 2 Burn and every enemy with 2 Regeneration.",
            counters = listOf("A healer on the wall, or Verdant weapons.", "Frost hits hard enough to outpace what they heal.", "A weapon made with Void Ink takes their own fire for your Armor."),
            sabotageBrief = "Douse the banked fires and hold the pits until they are cold.", sabotage = spoil(listOf("cinder_knight", "ember_whelp", "ember_whelp"), ObjectiveKind.HOLD, rounds = 4), enemyStats = mapOf(Stat.REGEN to 2), partyStats = mapOf(Stat.BURN to 2)),
    )

    val modifiers = listOf(
        MissionModifierDef("guarded", "Shields up", "They are expecting somebody. Every enemy starts with 4 Armor. +20% gold reward.", enemyStats = mapOf(Stat.GUARD to 4), goldPercent = 120),
        MissionModifierDef("reinforced", "One more of them", "Another one joined them on the way: 1 more enemy in every fight. +25% gold reward.", addUnit = mapOf(A to "ashclaw_thief", H to "gravecaller", E to "ember_whelp"), goldPercent = 125),
        MissionModifierDef("tired", "Tired sentries", "They have been awake for days. Every enemy starts with 1 Chill. -15% gold reward.", enemyStats = mapOf(Stat.CHILL to 1), goldPercent = 85),
        // The four that change the decision, not the difficulty.
        MissionModifierDef("ambush", "Hold the ford", "The militia closes the trap behind them. The party does not have to defeat the enemies: it wins if at least 1 member is still standing after 5 rounds.", archetypes = setOf(MissionArchetype.HUNT), hold = 5),
        MissionModifierDef("hostage", "A captured guard", "They have captured a guard of the militia. He stands at the back of your line and must survive: if he goes down, the fight is lost. If the party wins, he returns to the wall as 2 more militia.",
            archetypes = setOf(MissionArchetype.HUNT), protectUnitId = "captive", militia = 2),
        MissionModifierDef("narrow", "A narrow way", "Two can pass, not three: at most 2 members can go. +50% gold reward.", archetypes = setOf(MissionArchetype.HUNT, MissionArchetype.ESCORT, MissionArchetype.DELVE), partyMax = 2, goldPercent = 150),
        MissionModifierDef("in_kind", "Paid in metal", "No gold for this one. The last stage pays 1 Starsteel or 1 Sun Ash instead.", archetypes = setOf(MissionArchetype.HUNT, MissionArchetype.DELVE), goldPercent = 0,
            payment = MaterialDraw(1, 5, 5)),
    )

    val catalog = GuildCatalog(
        missions, routes, traits, charters, specialities, laws, ranks, bondEffects = bondEffects, oathEffects = oathEffects, openings = openings, preparations = preparations, modifiers = modifiers,
        siegeLeaders = mapOf(A to "warlord_krag", H to "hollow_king", E to "broodmother"),
        siegeElites = mapOf(A to "ashclaw_warchief", H to "pale_knight", E to "ember_drake"),
        siegeGrunts = mapOf(A to listOf("ashclaw_brute", "ashclaw_scout", "ashclaw_thief"), H to listOf("bone_warden", "hollow_shambler", "gravecaller"), E to listOf("cinder_knight", "ember_whelp", "ember_whelp")),
        rivalNames = listOf("the Gilded Lantern" to "Maren Voss", "the Tallow Street Irregulars" to "Dask Orrin", "the Brass Thimble Company" to "Ilse Varn"),
        bestiary = Bestiary.profiles, armies = Bestiary.armies,
    )
}
