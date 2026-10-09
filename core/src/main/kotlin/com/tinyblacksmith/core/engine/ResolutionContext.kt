package com.tinyblacksmith.core.engine

import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.BlessingEffect
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.content.ToolEffect
import com.tinyblacksmith.core.content.UpgradeEffect
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.rng.Rng
import com.tinyblacksmith.core.rng.RngState
import com.tinyblacksmith.core.rng.RngStream

/**
 * Mutable working copy used inside a single command/day resolution. The command boundary remains
 * immutable: GameState in, GameState out. RNG streams are opened lazily and written back in [toState].
 */
class ResolutionContext(val base: GameState, val content: ContentCatalog, val config: BalanceConfig) {
    val era = base.era
    var day = base.day
    var phase = base.phase
    var gold = base.gold
    var energy = base.energy
    var overworkToday = base.overworkToday
    var reputation = base.reputation
    var world = base.world
    val materials: MutableMap<MaterialId, Int> = base.materials.toMutableMap()
    val supplierStock: MutableMap<MaterialId, Int> = base.supplierStock.toMutableMap()
    val weapons: MutableMap<WeaponId, Weapon> = base.weapons.toMutableMap()
    val heroes: MutableMap<HeroId, Hero> = base.heroes.toMutableMap()
    var town = base.town
    val factions: MutableMap<FactionId, FactionState> = base.factions.toMutableMap()
    val commissions: MutableMap<CommissionId, Commission> = base.commissions.toMutableMap()
    val events: MutableList<EventRecord> = base.events.toMutableList()
    val newEvents: MutableList<EventRecord> = mutableListOf()
    var blessings: List<ActiveBlessing> = base.blessings
    var pendingBlessingOffer: List<BlessingId> = base.pendingBlessingOffer
    val milestones: MutableSet<String> = base.milestones.toMutableSet()
    var legacy = base.legacy
    var discoveriesThisRun = base.discoveriesThisRun
    val processedEndDayIds: MutableSet<String> = base.processedEndDayIds.toMutableSet()
    var lastResolution = base.lastResolution
    var nextWeaponSerial = base.nextWeaponSerial
    var nextHeroSerial = base.nextHeroSerial
    var nextCommissionSerial = base.nextCommissionSerial
    var nextEventSerial = base.nextEventSerial
    var endCause = base.endCause
    val eventCounters: MutableMap<String, Int> = base.eventCounters.toMutableMap()
    val eventLastDay: MutableMap<String, Int> = base.eventLastDay.toMutableMap()
    val worldFlags: MutableMap<String, Int> = base.worldFlags.toMutableMap()
    val tools: MutableMap<String, Int> = base.tools.toMutableMap()

    // Per-day scratch counters (not persisted).
    var patrolsToday = 0
    var expeditionWinsToday = 0
    val visits: MutableList<MarketVisit> = mutableListOf()
    val replays: MutableList<CombatReplay> = mutableListOf()

    private val openStreams: MutableMap<RngStream, Rng> = mutableMapOf()
    private var rngState: RngState = base.rng

    fun rng(stream: RngStream): Rng = openStreams.getOrPut(stream) { Rng(rngState.stateOf(stream)) }

    fun newWeaponId(): WeaponId = WeaponId("w${nextWeaponSerial++}")
    fun newHeroId(): HeroId = HeroId("h${nextHeroSerial++}")
    fun newCommissionId(): CommissionId = CommissionId("c${nextCommissionSerial++}")

    fun emit(
        type: EventType,
        priority: Int,
        text: String,
        subjects: List<String> = emptyList(),
        data: Map<String, String> = emptyMap(),
    ): EventRecord {
        val e = EventRecord("e${nextEventSerial++}", era, day, type, priority, text, subjects, data)
        events += e
        newEvents += e
        return e
    }

    fun milestone(key: String, text: String) {
        if (milestones.add(key)) emit(EventType.MILESTONE, 4, text, data = mapOf("milestone" to key))
    }

    fun hero(id: HeroId): Hero = heroes[id] ?: error("Unknown hero ${id.value}")
    fun weapon(id: WeaponId): Weapon = weapons[id] ?: error("Unknown weapon ${id.value}")
    fun updateHero(h: Hero) { heroes[h.id] = h }
    fun updateWeapon(w: Weapon) { weapons[w.id] = w }

    fun equippedWeapon(heroId: HeroId): Weapon? =
        weapons.values.firstOrNull { it.location is WeaponLocation.Owned && it.ownerId == heroId && it.isEquipped }

    fun aliveHeroes(): List<Hero> = heroes.values.filter { it.isAlive }.sortedBy { it.id.value }

    fun blessingMagnitude(effect: BlessingEffect): Int =
        blessings.filter { it.expiresDay >= day }.sumOf { b -> content.blessing(b.id).let { if (it.effect == effect) it.magnitude else 0 } }

    fun toolTotal(effect: ToolEffect): Int =
        content.tools.filter { it.effect == effect }.sumOf { it.magnitudePerLevel * (tools[it.id] ?: 0) }

    fun upgradeTotal(effect: UpgradeEffect): Int =
        content.upgrades.filter { it.effect == effect }.sumOf { it.magnitudePerLevel * legacy.upgradeLevel(it.id) }

    fun shelfSlots(): Int = config.shelfSlots + toolTotal(ToolEffect.SHELF_SLOTS)

    fun addWeaponHistory(weaponId: WeaponId, kind: String, text: String, subjects: List<String> = emptyList()) {
        val w = weapon(weaponId)
        updateWeapon(w.copy(history = w.history + HistoryEntry(era, day, kind, text, subjects)))
    }

    fun toState(): GameState {
        var rs = rngState
        for ((stream, rng) in openStreams) rs = rs.with(stream, rng.state)
        return GameState(
            runId = base.runId,
            seed = base.seed,
            rulesVersion = base.rulesVersion,
            contentVersion = base.contentVersion,
            balanceVersion = base.balanceVersion,
            era = era,
            day = day,
            phase = phase,
            gold = gold,
            energy = energy,
            overworkToday = overworkToday,
            reputation = reputation,
            rng = rs,
            world = world,
            materials = materials.toMap(),
            supplierStock = supplierStock.toMap(),
            weapons = weapons.toMap(),
            heroes = heroes.toMap(),
            town = town,
            factions = factions.toMap(),
            commissions = commissions.toMap(),
            events = events.toList(),
            blessings = blessings,
            pendingBlessingOffer = pendingBlessingOffer,
            milestones = milestones.toSet(),
            legacy = legacy,
            discoveriesThisRun = discoveriesThisRun,
            processedEndDayIds = processedEndDayIds.toSet(),
            lastResolution = lastResolution,
            nextWeaponSerial = nextWeaponSerial,
            nextHeroSerial = nextHeroSerial,
            nextCommissionSerial = nextCommissionSerial,
            nextEventSerial = nextEventSerial,
            endCause = endCause,
            eventCounters = eventCounters.toMap(),
            eventLastDay = eventLastDay.toMap(),
            worldFlags = worldFlags.toMap(),
            tools = tools.toMap(),
        )
    }
}
