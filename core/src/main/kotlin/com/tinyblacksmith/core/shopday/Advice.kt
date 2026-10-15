package com.tinyblacksmith.core.shopday

import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.content.Element
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.guild.SiegeFight
import com.tinyblacksmith.core.market.Commissions
import com.tinyblacksmith.core.model.*

/** One thing worth doing today and the recorded facts behind it; only the fields its kind uses are set. */
data class Lead(val kind: LeadKind, val heroId: HeroId? = null, val weaponId: WeaponId? = null,
                val commissionId: CommissionId? = null, val factionId: FactionId? = null,
                val gold: Int? = null, val count: Int? = null, val days: Int? = null, val element: Element? = null,
                val familyId: WeaponFamilyId? = null, val power: Int? = null,
                val readiness: ReadinessGap? = null)

enum class ReadinessGap { EMPTY_PLACE, UNARMED, WORN, UNAVAILABLE }

enum class LeadKind { FIRST_BLADE, CHOOSE_BLESSING, ANSWER_REQUEST, FORGE_FOR_REQUEST, LIST_STOCK, FORGE_STOCK,
                      PRICES_TOO_HIGH, ARM_DEFENDERS, ANSWER_WANT, FORGE_FOR_BUYERS,
                      REVIEW_WALL, GUILD_RESCUE, GUILD_CHECKPOINT, DAY_SETTLED }

object Advice {
    /**
     * Explicit precedence: onboarding, accepted work due now, a rescue's last departure, checkpoint, concrete siege
     * gaps, upcoming accepted work, then optional shop work. Shop and Tomorrow share this pure policy.
     */
    fun lead(state: GameState, content: ContentCatalog, config: BalanceConfig): Lead {
        val listed = state.listedWeapons()
        val stored = state.storedWeapons()
        fun alive(c: Commission) = state.heroes[c.buyerId]?.isAlive == true
        val open = state.commissions.values.filter { alive(it) }.sortedWith(compareBy<Commission> { it.deadlineDay }.thenBy(IdOrder.numeric) { it.id.value })

        if (state.day == 1 && state.weapons.isEmpty()) return Lead(LeadKind.FIRST_BLADE, count = state.aliveHeroes().size)

        if (state.pendingBlessingOffer.isNotEmpty()) return Lead(LeadKind.CHOOSE_BLESSING, count = state.pendingBlessingOffer.size)

        fun unfinished(c: Commission) = c.status == CommissionStatus.ACCEPTED && Commissions.pick(state.weapons.values, c, config) == null
        fun request(c: Commission) = Lead(LeadKind.FORGE_FOR_REQUEST, heroId = c.buyerId, commissionId = c.id, gold = c.reward, days = c.deadlineDay - state.day, element = c.element)
        open.firstOrNull { unfinished(it) && it.deadlineDay <= state.day }?.let { return request(it) }

        state.guild?.let { g ->
            // A rescue offer expires before the captive dies. An already planned/active rescue is a settled decision.
            g.captives.sortedWith(compareBy<Captive> { it.deadlineDay }.thenBy { it.heroId.value }).firstOrNull { c ->
                g.offers.any { it.subjectHeroId == c.heroId && it.expiresDay == state.day && it.id != g.planned?.offerId } &&
                    g.mission?.offer?.subjectHeroId != c.heroId
            }?.let { return Lead(LeadKind.GUILD_RESCUE, heroId = it.heroId, days = it.deadlineDay - state.day) }
            if (g.mission?.let { it.outcome == null && it.stage == 1 && it.offer.optionalPush && it.choice == null } == true)
                return Lead(LeadKind.GUILD_CHECKPOINT)
        }

        siegeReadiness(state, content, config)?.let { return it }

        open.firstOrNull { unfinished(it) && it.deadlineDay - state.day <= 2 }?.let { return request(it) }

        // Keeping the company home or a ready siege plan is legitimate. Do not invent more work on siege night.
        if (state.town.nextSiegeDay == state.day) return Lead(LeadKind.DAY_SETTLED)

        // An offer lapses at the End Day of its deadline: today or tomorrow is the last chance to take it.
        open.firstOrNull { it.status == CommissionStatus.OFFERED && it.deadlineDay - state.day <= 1 }?.let {
            return Lead(LeadKind.ANSWER_REQUEST, heroId = it.buyerId, commissionId = it.id, gold = it.reward, days = it.deadlineDay - state.day, element = it.element)
        }

        if (listed.isEmpty()) return if (stored.isNotEmpty()) Lead(LeadKind.LIST_STOCK, count = stored.size) else Lead(LeadKind.FORGE_STOCK)

        val yesterday = state.lastResolution?.takeIf { it.day == state.day - 1 }
        val refusedOnPrice = yesterday?.browsers?.count { it.reason == VisitReason.TOO_EXPENSIVE || it.reason == VisitReason.OVERPRICED } ?: 0
        if (refusedOnPrice >= 2) {
            val cheapest = listed.minWith(compareBy<Weapon> { it.listedPrice ?: 0 }.thenBy(IdOrder.numeric) { it.id.value })
            return Lead(LeadKind.PRICES_TOO_HIGH, weaponId = cheapest.id, gold = cheapest.listedPrice, count = refusedOnPrice)
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

    /** Current forecast facts only. No fight, counter-coverage guess, hidden plan or Guild scalar odds. */
    fun siegeReadiness(state: GameState, content: ContentCatalog, config: BalanceConfig): Lead? {
        val days = state.town.nextSiegeDay - state.day
        if (days !in 0..2) return null
        val ctx = ResolutionContext(state, content, config)
        if (state.guild != null) {
            val wall = SiegeFight.forecast(ctx) ?: return null
            fun gap(issue: ReadinessGap, heroId: HeroId? = null, count: Int? = null) =
                Lead(LeadKind.REVIEW_WALL, heroId = heroId, days = days, count = count, readiness = issue)
            if (wall.defenders.size < config.guild.defenders) return gap(ReadinessGap.EMPTY_PLACE, count = config.guild.defenders - wall.defenders.size)
            wall.defenders.firstOrNull { it.second == null }?.let { return gap(ReadinessGap.UNARMED, it.first.id) }
            wall.defenders.firstOrNull { it.second!!.condition < config.wornConditionThreshold }?.let { return gap(ReadinessGap.WORN, it.first.id) }
            // Resting an unreserved member is legitimate when armed substitutes fill the wall.
            wall.missing.firstOrNull { it.first.id in state.guild.reserved }?.let { return gap(ReadinessGap.UNAVAILABLE, it.first.id) }
            return null
        }
        val outlook = Battle.outlook(ctx, state.town.nextSiegeDay) ?: return null
        if (outlook.odds != Battle.SiegeOdds.OUTMATCHED && outlook.odds != Battle.SiegeOdds.DIRE) return null
        val revealed = Battle.warnedFaction(ctx)
        return Lead(LeadKind.ARM_DEFENDERS, factionId = revealed?.id, days = days, element = revealed?.weakTo)
    }
}
