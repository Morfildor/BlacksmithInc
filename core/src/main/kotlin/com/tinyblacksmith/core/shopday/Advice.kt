package com.tinyblacksmith.core.shopday

import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.content.Element
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.market.Commissions
import com.tinyblacksmith.core.model.*

/** One thing worth doing today and the recorded facts behind it; only the fields its kind uses are set. */
data class Lead(val kind: LeadKind, val heroId: HeroId? = null, val weaponId: WeaponId? = null,
                val commissionId: CommissionId? = null, val factionId: FactionId? = null,
                val gold: Int? = null, val count: Int? = null, val days: Int? = null, val element: Element? = null,
                val familyId: WeaponFamilyId? = null, val power: Int? = null)

enum class LeadKind { FIRST_BLADE, CHOOSE_BLESSING, ANSWER_REQUEST, FORGE_FOR_REQUEST, LIST_STOCK, FORGE_STOCK,
                      PRICES_TOO_HIGH, ARM_DEFENDERS, ANSWER_WANT, FORGE_FOR_BUYERS }

object Advice {
    /**
     * The first match of a fixed order (PROPOSED). The Shop destination and the Tomorrow card call this one function, so
     * they always agree. Reads the state only: the siege outlook is the forecast's own, which draws nothing.
     */
    fun lead(state: GameState, content: ContentCatalog, config: BalanceConfig): Lead {
        val listed = state.listedWeapons()
        val stored = state.storedWeapons()
        fun alive(c: Commission) = state.heroes[c.buyerId]?.isAlive == true
        val open = state.commissions.values.filter { alive(it) }.sortedWith(compareBy<Commission> { it.deadlineDay }.thenBy(IdOrder.numeric) { it.id.value })

        if (state.day == 1 && state.weapons.isEmpty()) return Lead(LeadKind.FIRST_BLADE, count = state.aliveHeroes().size)

        if (state.pendingBlessingOffer.isNotEmpty()) return Lead(LeadKind.CHOOSE_BLESSING, count = state.pendingBlessingOffer.size)

        // An offer lapses at the End Day of its deadline: today or tomorrow is the last chance to take it.
        open.firstOrNull { it.status == CommissionStatus.OFFERED && it.deadlineDay - state.day <= 1 }?.let {
            return Lead(LeadKind.ANSWER_REQUEST, heroId = it.buyerId, commissionId = it.id, gold = it.reward, days = it.deadlineDay - state.day, element = it.element)
        }

        open.firstOrNull { it.status == CommissionStatus.ACCEPTED && it.deadlineDay - state.day <= 2 && Commissions.pick(state.weapons.values, it, config) == null }?.let {
            return Lead(LeadKind.FORGE_FOR_REQUEST, heroId = it.buyerId, commissionId = it.id, gold = it.reward, days = it.deadlineDay - state.day, element = it.element)
        }

        if (listed.isEmpty()) return if (stored.isNotEmpty()) Lead(LeadKind.LIST_STOCK, count = stored.size) else Lead(LeadKind.FORGE_STOCK)

        val yesterday = state.lastResolution?.takeIf { it.day == state.day - 1 }
        val refusedOnPrice = yesterday?.browsers?.count { it.reason == VisitReason.TOO_EXPENSIVE || it.reason == VisitReason.OVERPRICED } ?: 0
        if (refusedOnPrice >= 2) {
            val cheapest = listed.minWith(compareBy<Weapon> { it.listedPrice ?: 0 }.thenBy(IdOrder.numeric) { it.id.value })
            return Lead(LeadKind.PRICES_TOO_HIGH, weaponId = cheapest.id, gold = cheapest.listedPrice, count = refusedOnPrice)
        }

        val daysToSiege = state.town.nextSiegeDay - state.day
        if (daysToSiege in 0..2) {
            val outlook = Battle.outlook(ResolutionContext(state, content, config), state.town.nextSiegeDay)
            if (outlook != null && (outlook.odds == Battle.SiegeOdds.OUTMATCHED || outlook.odds == Battle.SiegeOdds.DIRE)) {
                return Lead(LeadKind.ARM_DEFENDERS, factionId = outlook.faction.id, days = daysToSiege, element = outlook.faction.weakTo)
            }
        }

        // A standing want nothing on the shelf answers: the oldest first (it lapses first), then by ID.
        val demand = Demand.summary(state, content, config)
        demand.wants.filter { it !in demand.wantsAnswered }.map { state.hero(it) }.minWithOrNull(compareBy<Hero> { it.want!!.sinceDay }.thenBy(IdOrder.numeric) { it.id.value })?.let { h ->
            val want = h.want!!
            return Lead(LeadKind.ANSWER_WANT, heroId = h.id, gold = want.budget, days = want.sinceDay + config.customers.wantLapseDays - state.day, familyId = want.familyId, power = want.minPower)
        }

        // Otherwise: forge for today's buyers, with the first demand fact that holds.
        return when {
            demand.unarmed.isNotEmpty() -> Lead(LeadKind.FORGE_FOR_BUYERS, count = demand.unarmed.size)
            demand.worn.isNotEmpty() -> demand.worn.first().let { Lead(LeadKind.FORGE_FOR_BUYERS, heroId = it, weaponId = state.equippedWeapon(it)?.id) }
            else -> Lead(LeadKind.FORGE_FOR_BUYERS, gold = demand.cheapestPrice, count = demand.canAffordCheapest)
        }
    }
}
