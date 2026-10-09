package com.tinyblacksmith.core.engine

import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.WeaponLocation

/** GDD 15.1 invariants, asserted after every accepted command. */
object Invariants {
    fun check(state: GameState, config: BalanceConfig, shelfSlots: Int = config.shelfSlots): List<String> {
        val problems = mutableListOf<String>()
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
        }
        state.weapons.values.filter { it.isEquipped }.groupingBy { it.ownerId!! }.eachCount().forEach { (heroId, equipped) ->
            if (equipped > 1) problems += "Hero ${state.heroes[heroId]?.fullName ?: heroId.value} has $equipped equipped weapons"
        }
        state.heroes.values.forEach { h -> if (h.gold < 0) problems += "Hero ${h.fullName} has negative gold" }
        state.town.championIds.forEach { id ->
            val h = state.heroes[id]
            if (h == null || !h.isAlive) problems += "Dead or missing champion ${id.value}"
        }
        state.factions.values.forEach { f -> if (f.pressure !in 0..100) problems += "Faction pressure out of range" }
        state.town.guilds.forEach { g -> if (g.founderId !in state.heroes) problems += "Guild ${g.id} founded by unknown hero" }
        state.eventCounters.forEach { (id, n) ->
            val def = WorldEvents.all.firstOrNull { it.id == id }
            if (def == null) problems += "Unknown world event counter $id" else if (n > def.maxPerRun) problems += "World event $id fired $n times (max ${def.maxPerRun})"
        }
        return problems
    }
}
