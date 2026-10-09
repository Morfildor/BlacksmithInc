package com.tinyblacksmith.core.model

import com.tinyblacksmith.core.content.Element
import com.tinyblacksmith.core.rng.RngState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class Phase { PLANNING, ENDED }
enum class Risk { SAFE, BALANCED, RECKLESS }
enum class ForgeMode { QUICK, ADVANCED }
enum class Rarity { COMMON, UNCOMMON, RARE, EPIC, LEGENDARY }
enum class HeroActivity { REST, SHOP, EXPEDITION, PATROL, DEFEND, IDLE, GUILD, AMBITION }
enum class HeroFate { ALIVE, DEAD, RETIRED }
enum class CommissionStatus { OFFERED, ACCEPTED, COMPLETED, EXPIRED, DECLINED }
/** GDD 6 hero ambitions: a personal goal that shifts daily choices and makes news when fulfilled. */
enum class Ambition { SLAYER, DEFENDER, COLLECTOR, FORTUNE }
enum class KnowledgeState { UNKNOWN, OBSERVED, UNDERSTOOD, SIGNATURE_DISCOVERED }

/**
 * Exactly one authoritative location per weapon (GDD 7 ownership invariant).
 * The serial names are the strings every existing save stores as the `type` of a location (the class names of
 * 0.6.0); they are pinned so that renaming or moving a class cannot orphan a save. Never edit them.
 */
@Serializable
@SerialName("com.tinyblacksmith.core.model.WeaponLocation")
sealed class WeaponLocation {
    @Serializable @SerialName("com.tinyblacksmith.core.model.WeaponLocation.Storage") object Storage : WeaponLocation()
    @Serializable @SerialName("com.tinyblacksmith.core.model.WeaponLocation.Shelf") data class Shelf(val price: Int) : WeaponLocation()
    @Serializable @SerialName("com.tinyblacksmith.core.model.WeaponLocation.Owned") data class Owned(val heroId: HeroId, val equipped: Boolean) : WeaponLocation()
    @Serializable @SerialName("com.tinyblacksmith.core.model.WeaponLocation.Lost") data class Lost(val day: Int, val reason: String) : WeaponLocation() {
        companion object {
            /** [reason] while a travelling merchant holds a fallen hero's blade (GDD 7 merchant resale); [day] is the day the hero fell. */
            const val WITH_MERCHANT = "held by a travelling merchant"
        }
    }
    @Serializable @SerialName("com.tinyblacksmith.core.model.WeaponLocation.Destroyed") data class Destroyed(val day: Int) : WeaponLocation()
}

/**
 * What became of the blade a fallen hero carried (GDD 7), recorded as `data["fate"]` on the event that tells it.
 * MERCHANT is in transit: it ends RESOLD or LOST within a bounded number of days.
 */
enum class WeaponFate {
    RECOVERED, INHERITED, MERCHANT, RESOLD, SEIZED, LOST;

    companion object {
        const val KEY = "fate"
    }
}

@Serializable
data class HistoryEntry(val era: Int, val day: Int, val kind: String, val text: String, val subjectIds: List<String> = emptyList())

@Serializable
data class Weapon(
    val id: WeaponId,
    val name: String,
    val familyId: WeaponFamilyId,
    val coreId: MaterialId,
    val augmentId: MaterialId,
    val catalystId: MaterialId? = null,
    val mode: ForgeMode,
    val risk: Risk,
    val quality: Int,
    val rarity: Rarity,
    val power: Int,
    val element: Element?,
    val affixes: List<AffixId>,
    val flaws: List<AffixId>,
    val location: WeaponLocation,
    val forgedEra: Int,
    val forgedDay: Int,
    val kills: Int = 0,
    val victories: Int = 0,
    val siegesDefended: Int = 0,
    val fame: Int = 0,
    val title: String? = null,
    /** Chronological; ownership-grade kinds are kept forever, combat kinds (VICTORY, SIEGE) are bounded by `WeaponHistoryCompaction` at End Day. */
    val history: List<HistoryEntry> = emptyList(),
    /** Set when a hidden signature recipe transformed this weapon (GDD 4.5). */
    val signatureId: String? = null,
    /** The quality bonus of Command.Hone has been spent; honing again only restores [condition]. */
    val honed: Boolean = false,
    /** Wear, 100 (keen) to 0: expeditions and sieges lower it, Hone restores it (GDD 6 condition factor, GDD 7 lives). */
    val condition: Int = 100,
) {
    val isListed: Boolean get() = location is WeaponLocation.Shelf
    /** Hone is allowed on an unhoned weapon or one worn below full condition. */
    val canBeHoned: Boolean get() = !honed || condition < 100
    val isInStorage: Boolean get() = location is WeaponLocation.Storage
    val isWithMerchant: Boolean get() = (location as? WeaponLocation.Lost)?.reason == WeaponLocation.Lost.WITH_MERCHANT
    val ownerId: HeroId? get() = (location as? WeaponLocation.Owned)?.heroId
    val isEquipped: Boolean get() = (location as? WeaponLocation.Owned)?.equipped == true
    val listedPrice: Int? get() = (location as? WeaponLocation.Shelf)?.price
}

@Serializable
data class Hero(
    val id: HeroId,
    val name: String,
    val surname: String,
    val classId: HeroClassId,
    val level: Int,
    val xp: Int,
    val gold: Int,
    val health: Int,
    val traits: List<TraitId>,
    val elementTaste: Element?,
    val loyalty: Int = 0,
    val fame: Int = 0,
    val fate: HeroFate = HeroFate.ALIVE,
    val lastActivity: HeroActivity = HeroActivity.IDLE,
    /** The ancestor's name, for display only; kinship is [lineageId] ([LineageAnchor.id]). */
    val descendantOf: String? = null,
    val lineageId: String? = null,
    val kills: Int = 0,
    val victories: Int = 0,
    val diedOnDay: Int? = null,
    /** GDD 6 generational hybrid: guild membership, mentor and retirement day. */
    val guildId: String? = null,
    val mentorName: String? = null,
    val retiredOnDay: Int? = null,
    val ambition: Ambition? = null,
    val ambitionDone: Boolean = false,
    /** Expedition victories only (sieges excluded); drives the SLAYER ambition. */
    val expeditionWins: Int = 0,
    val elitesSlain: Int = 0,
    /** Day of the last expedition this hero was driven back from; yesterday's rout weighs on today's choice (GDD 6 "prior history"). */
    val drivenBackOnDay: Int? = null,
    /** The counter's memory of this hero (fair seating, plan 4.2). Visits and purchases count browsing at a stocked shelf, not commissions. */
    val arrivedOnDay: Int = 1,
    val shopVisits: Int = 0,
    val shopPurchases: Int = 0,
    val lastServedDay: Int? = null,
    val lastPurchaseDay: Int? = null,
    /** Willing days in a row on which the shop was full before this hero got a seat; 0 again once served. */
    val turnedAwayStreak: Int = 0,
) {
    val fullName: String get() = "$name $surname"
    val isAlive: Boolean get() = fate == HeroFate.ALIVE
}

/** A guild founded by a retiring famous hero (GDD 6). */
@Serializable
data class Guild(val id: String, val name: String, val founderId: HeroId, val foundedDay: Int)

@Serializable
data class Town(
    val integrity: Int,
    val militia: Int,
    val championIds: List<HeroId>,
    val nextSiegeDay: Int,
    val siegesSurvived: Int = 0,
    val siegesLost: Int = 0,
    val guilds: List<Guild> = emptyList(),
    /** Defensive power of the weapons the smith gave to the town watch (Command.DonateWeapon). */
    val armory: Int = 0,
)

@Serializable
data class FactionState(val id: FactionId, val pressure: Int, val suppressionToday: Int = 0)

@Serializable
data class Commission(
    val id: CommissionId,
    val buyerId: HeroId,
    val familyId: WeaponFamilyId,
    val minQuality: Int,
    val reward: Int,
    val offeredDay: Int,
    val deadlineDay: Int,
    val status: CommissionStatus,
    val deliveredWeaponId: WeaponId? = null,
    /** GDD 5 "desirable effect": when set, only a weapon of this element closes the commission. */
    val element: Element? = null,
)

@Serializable
data class ActiveBlessing(val id: BlessingId, val expiresDay: Int)

enum class EventType {
    RUN_STARTED, WEAPON_FORGED, DISCOVERY, WEAPON_LISTED, WEAPON_SOLD, WEAPON_EQUIPPED, COMMISSION_OFFERED,
    COMMISSION_COMPLETED, COMMISSION_EXPIRED, HERO_RESTED, HERO_PATROLLED, EXPEDITION_WON, EXPEDITION_LOST,
    HERO_WOUNDED, HERO_DIED, WEAPON_RECOVERED, WEAPON_LOST, HERO_LEVELED, FACTION_PRESSURE, SIEGE_WARNING,
    SIEGE_WON, SIEGE_LOST, FORGE_DAMAGED, FORGE_DESTROYED, TOWN_RECOVERED, BLESSING_OFFERED, BLESSING_CHOSEN,
    WORLD_EVENT, MILESTONE, LEGEND_RECORDED, HERO_ARRIVED,
    SIGNATURE_DISCOVERED, HERO_RETIRED, GUILD_FOUNDED, HERO_MENTORED, ARTIFACT_RETURNED, WEAPON_STOLEN, WEAPON_INHERITED,
    ELITE_SLAIN, AMBITION_FULFILLED, WEAPON_SALVAGED, WEAPON_HONED, WEAPON_DONATED, TOOL_BOUGHT, WEAPON_BROKEN,
    GUILD_TRAINED, GUILD_JOINED, GUILD_MENTORED, AMBITION_PURSUED,
    WEAPON_SURFACED, WEAPON_RESOLD,
    MATERIAL_BOUGHT,
    SHOP_DAY,
}

/** Source of truth for the Gazette and replays (GDD Appendix B). Subjects are real entity IDs. */
@Serializable
data class EventRecord(
    val id: String,
    val era: Int,
    val day: Int,
    val type: EventType,
    val priority: Int,
    val text: String,
    val subjectIds: List<String> = emptyList(),
    val data: Map<String, String> = emptyMap(),
)

/** [attackerId] is the hero who strikes, when one does (null for foes, the militia, and rounds stored before it existed). */
@Serializable
data class CombatRound(val attacker: String, val defender: String, val damage: Int, val note: String, val attackerId: String? = null)

enum class ReplayKind { SIEGE, EXPEDITION }

/** Read-only replay DTO; rendering it must never change outcomes (GDD 11). */
@Serializable
data class CombatReplay(
    val title: String, val day: Int, val rounds: List<CombatRound>, val outcome: String,
    /** Older saves only held siege replays. The siege replay feeds the diorama; a fight is text only. */
    val kind: ReplayKind = ReplayKind.SIEGE,
    /** The event record a fight replay illustrates (ELITE_SLAIN, EXPEDITION_LOST or HERO_DIED of the same day). */
    val eventId: String? = null,
)

@Serializable
data class DayResolution(
    val commandId: CommandId,
    val day: Int,
    val events: List<EventRecord>,
    val headlines: List<String>,
    val visits: List<MarketVisit>,
    val replays: List<CombatReplay>,
    val defeated: Boolean,
    /** The till and every hero's day, typed. Null and empty on a day resolved before they were recorded. */
    val ledger: ShopLedger? = null,
    val field: List<FieldResult> = emptyList(),
    /** The opening shelf with nothing sold yet, plus any stored blade a commission took: every blade a visit refers to. */
    val shopWeapons: List<WeaponSnapshot> = emptyList(),
    val shelfPrices: Map<WeaponId, Int> = emptyMap(),
    /** Heroes who came to browse and found every seat taken, in ID order. */
    val turnedAway: List<HeroId> = emptyList(),
    val recordVersion: Int = 0,                           // 0 = a 0.5.x day without snapshots, 1 = this record
) {
    /** The heroes who came to look at the shelf; a commission patron and the collector are visits of their own kind. */
    val browsers: List<MarketVisit> get() = visits.filter { it.kind == VisitKind.BROWSE }
}

@Serializable
data class Journal(
    val interactions: Map<String, KnowledgeState> = emptyMap(),
    val experiments: Map<String, Int> = emptyMap(),
) {
    companion object {
        fun coreAugmentKey(core: MaterialId, augment: MaterialId) = "ca:${core.value}|${augment.value}"
        fun augmentFamilyKey(augment: MaterialId, family: WeaponFamilyId) = "af:${augment.value}|${family.value}"
    }
    fun state(key: String): KnowledgeState = interactions[key] ?: KnowledgeState.UNKNOWN
}

@Serializable
data class LegendEntry(
    val era: Int, val weaponName: String, val title: String, val kills: Int, val fame: Int, val owners: List<String>,
    /** Recorded so a famous blade can return in a later era (GDD 7); null on entries from older profiles. */
    val familyId: WeaponFamilyId? = null,
    val coreId: MaterialId? = null,
    val augmentId: MaterialId? = null,
    val quality: Int = 0,
    val power: Int = 0,
    val element: Element? = null,
)

/** [id] is the era and the hero's ID ("era2-h7"); a lineage written before schema 4 was given "era2" by the migration. Names never identify a lineage. */
@Serializable
data class LineageAnchor(val era: Int, val heroName: String, val surname: String, val classId: HeroClassId, val fame: Int, val deed: String, val id: String = "")

@Serializable
data class EraSummary(val era: Int, val daysSurvived: Int, val pointsAwarded: Int, val cause: String)

/** Persists across runs (GDD 9 persistence matrix). */
@Serializable
data class LegacyProfile(
    val version: Int = 1,
    val points: Int = 0,
    val upgrades: Map<UpgradeId, Int> = emptyMap(),
    val journal: Journal = Journal(),
    val legendBoard: List<LegendEntry> = emptyList(),
    val lineages: List<LineageAnchor> = emptyList(),
    val claimedRunIds: Set<String> = emptySet(),
    val eras: List<EraSummary> = emptyList(),
    val totalPointsEarned: Int = 0,
) {
    fun upgradeLevel(id: UpgradeId): Int = upgrades[id] ?: 0
    val nextEra: Int get() = eras.size + 1
}

@Serializable
data class WorldModifiers(val raidMultiplier: Double = 1.0, val marketMultiplier: Double = 1.0, val name: String = "Calm Season")

@Serializable
data class GameState(
    val runId: RunId,
    val seed: Long,
    val rulesVersion: Int,
    val contentVersion: Int,
    val era: Int,
    val day: Int,
    val phase: Phase,
    val gold: Int,
    val energy: Int,
    val overworkToday: Int,
    val reputation: Int,
    val rng: RngState,
    val world: WorldModifiers,
    val materials: Map<MaterialId, Int>,
    val supplierStock: Map<MaterialId, Int>,
    val weapons: Map<WeaponId, Weapon>,
    val heroes: Map<HeroId, Hero>,
    val town: Town,
    val factions: Map<FactionId, FactionState>,
    val commissions: Map<CommissionId, Commission>,
    val events: List<EventRecord>,
    val blessings: List<ActiveBlessing>,
    val pendingBlessingOffer: List<BlessingId>,
    val milestones: Set<String>,
    val legacy: LegacyProfile,
    val discoveriesThisRun: Int,
    val processedEndDayIds: Set<String>,
    val lastResolution: DayResolution?,
    val nextWeaponSerial: Int,
    val nextHeroSerial: Int,
    val nextCommissionSerial: Int,
    val nextEventSerial: Int,
    val endCause: String? = null,
    /** GDD 10 scripted world events: times each event fired this run and the day it last fired (cooldowns). */
    val eventCounters: Map<String, Int> = emptyMap(),
    val eventLastDay: Map<String, Int> = emptyMap(),
    /** Transient day-keyed flags set by world events (e.g. festival/caravan day); dropped once past. */
    val worldFlags: Map<String, Int> = emptyMap(),
    /** Level of each in-run workshop tool (ToolDef.id). */
    val tools: Map<String, Int> = emptyMap(),
    /** `BalanceConfig.version` the run was last admitted under (`Compatibility.admit`); 0 = written before it was tracked. */
    val balanceVersion: Int = 0,
) {
    val isEnded: Boolean get() = phase == Phase.ENDED
    fun weapon(id: WeaponId): Weapon = weapons[id] ?: error("Unknown weapon ${id.value}")
    fun hero(id: HeroId): Hero = heroes[id] ?: error("Unknown hero ${id.value}")
    fun equippedWeapon(heroId: HeroId): Weapon? =
        weapons.values.firstOrNull { it.location is WeaponLocation.Owned && it.ownerId == heroId && it.isEquipped }
    fun listedWeapons(): List<Weapon> = weapons.values.filter { it.isListed }.sortedWith(compareBy(IdOrder.numeric) { it.id.value })
    fun storedWeapons(): List<Weapon> = weapons.values.filter { it.isInStorage }.sortedWith(compareBy(IdOrder.numeric) { it.id.value })
    fun aliveHeroes(): List<Hero> = heroes.values.filter { it.isAlive }.sortedWith(compareBy(IdOrder.numeric) { it.id.value })
    fun retiredHeroes(): List<Hero> = heroes.values.filter { it.fate == HeroFate.RETIRED }.sortedWith(compareBy(IdOrder.numeric) { it.id.value })
    fun eventsForDay(day: Int): List<EventRecord> = events.filter { it.day == day }
}
