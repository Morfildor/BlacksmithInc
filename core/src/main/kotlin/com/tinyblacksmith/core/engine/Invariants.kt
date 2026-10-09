package com.tinyblacksmith.core.engine

import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.Phase
import com.tinyblacksmith.core.model.WeaponLocation
import com.tinyblacksmith.core.rng.RngStream

/** GDD 15.1 invariants, asserted after every accepted command. */
object Invariants {
    /**
     * Never throws; an empty list means sound. With a [content] catalog every content ID the run stores is also
     * resolved against it (`Compatibility.admit` passes one, so a save from other content is refused, not crashed on).
     */
    fun check(state: GameState, config: BalanceConfig, shelfSlots: Int = config.shelfSlots, content: ContentCatalog? = null): List<String> {
        val problems = mutableListOf<String>()
        if (content != null) problems += unknownContent(state, content)
        RngStream.entries.forEach { if (it !in state.rng.streams) problems += "Missing RNG stream $it" }
        if ((state.phase == Phase.ENDED) != (state.town.integrity == 0)) problems += "Phase ${state.phase} with forge integrity ${state.town.integrity}"
        if (state.gold < 0) problems += "Negative gold ${state.gold}"
        if (state.energy < 0) problems += "Negative energy ${state.energy}"
        if (state.overworkToday < 0 || state.overworkToday > config.maxOverworkPerDay) problems += "Overwork out of bounds ${state.overworkToday}"
        state.materials.forEach { (m, n) -> if (n < 0) problems += "Negative material ${m.value}" }
        if (state.town.integrity < 0) problems += "Negative integrity"
        if (state.town.armory !in 0..config.armoryMax) problems += "Armory out of bounds ${state.town.armory}"
        if (state.listedWeapons().size > shelfSlots) problems += "Shelf overfull"
        state.weapons.values.forEach { w ->
            val loc = w.location
            if (loc is WeaponLocation.Owned) {
                val owner = state.heroes[loc.heroId]
                if (owner == null) problems += "Weapon ${w.id.value} owned by unknown hero"
                else if (!owner.isAlive) problems += "Weapon ${w.id.value} owned by ${owner.fate.name.lowercase()} hero ${owner.fullName}"
            }
            if (loc is WeaponLocation.Shelf && loc.price < 0) problems += "Weapon ${w.id.value} has negative price"
            if (loc is WeaponLocation.Lost && w.isWithMerchant) {
                // GDD 7 merchant resale is bounded: sold or carried off by the last End Day of the merchant's stay.
                val leaves = loc.day + config.weaponFates.merchantDelayDays + maxOf(1, config.weaponFates.merchantStayDays)
                if (state.day > leaves) problems += "Weapon ${w.id.value} still held by a merchant after day $leaves"
            }
            if (w.condition !in 0..100) problems += "Weapon ${w.id.value} condition out of bounds ${w.condition}"
            if (w.quality !in 1..100) problems += "Weapon ${w.id.value} quality out of bounds ${w.quality}"
            if (w.power < 0 || w.kills < 0 || w.victories < 0 || w.siegesDefended < 0 || w.fame < 0) problems += "Weapon ${w.id.value} has a negative power or record"
        }
        state.weapons.values.filter { it.isEquipped }.groupingBy { it.ownerId!! }.eachCount().forEach { (heroId, equipped) ->
            if (equipped > 1) problems += "Hero ${state.heroes[heroId]?.fullName ?: heroId.value} has $equipped equipped weapons"
        }
        state.heroes.values.forEach { h ->
            if (h.gold < 0) problems += "Hero ${h.fullName} has negative gold"
            if (h.health > 100 || h.health < 0 || (h.isAlive && h.health <= config.heroDeathHealthFloor)) problems += "Hero ${h.fullName} health out of bounds ${h.health}"
            if (h.level !in 1..config.heroMaxLevel) problems += "Hero ${h.fullName} level out of bounds ${h.level}"
            if (h.xp < 0 || h.loyalty < 0 || h.fame < 0 || h.kills < 0 || h.victories < 0) problems += "Hero ${h.fullName} has a negative record"
        }
        if (state.town.championIds.size > config.championCount) problems += "More than ${config.championCount} champions"
        if (state.town.championIds.toSet().size != state.town.championIds.size) problems += "A champion is listed twice"
        state.town.championIds.forEach { id ->
            val h = state.heroes[id]
            if (h == null || !h.isAlive) problems += "Dead or missing champion ${id.value}"
        }
        state.factions.values.forEach { f -> if (f.pressure !in 0..100) problems += "Faction pressure out of range" }
        state.town.guilds.forEach { g -> if (g.founderId !in state.heroes) problems += "Guild ${g.id} founded by unknown hero" }
        state.eventCounters.forEach { (id, n) ->
            if (id == WorldEvents.RUMOUR) return@forEach   // not a pooled event: the run's rumours, capped by `customers.maxRumoursPerRun` where they are told
            val def = WorldEvents.all.firstOrNull { it.id == id }
            if (def == null) problems += "Unknown world event counter $id" else if (n > def.maxPerRun) problems += "World event $id fired $n times (max ${def.maxPerRun})"
        }
        return problems
    }

    /** Every content ID a run stores: family, material, affix, class, trait, blessing, tool, upgrade, faction. */
    private fun unknownContent(state: GameState, content: ContentCatalog): List<String> {
        val problems = mutableListOf<String>()
        fun need(known: Boolean, kind: String, id: String, where: String) { if (!known) problems += "Unknown $kind $id ($where)" }
        (state.materials.keys + state.supplierStock.keys).forEach { need(it in content.materialById, "material", it.value, "stock") }
        state.weapons.values.forEach { w ->
            val where = "weapon ${w.id.value}"
            need(w.familyId in content.familyById, "family", w.familyId.value, where)
            listOfNotNull(w.coreId, w.augmentId, w.catalystId).forEach { need(it in content.materialById, "material", it.value, where) }
            (w.affixes + w.flaws + w.dormantAffixes).forEach { need(it in content.affixById, "affix", it.value, where) }
        }
        state.heroes.values.forEach { h ->
            need(h.classId in content.classById, "class", h.classId.value, "hero ${h.id.value}")
            h.traits.forEach { need(it in content.traitById, "trait", it.value, "hero ${h.id.value}") }
        }
        state.commissions.values.forEach { need(it.familyId in content.familyById, "family", it.familyId.value, "commission ${it.id.value}") }
        (state.blessings.map { it.id } + state.pendingBlessingOffer).forEach { need(it in content.blessingById, "blessing", it.value, "blessings") }
        state.tools.keys.forEach { need(content.tool(it) != null, "tool", it, "tools") }
        state.legacy.upgrades.keys.forEach { need(it in content.upgradeById, "upgrade", it.value, "legacy") }
        state.factions.keys.forEach { need(it in content.factionById, "faction", it.value, "factions") }
        return problems
    }
}
