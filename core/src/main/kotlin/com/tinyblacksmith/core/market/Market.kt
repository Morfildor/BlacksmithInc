package com.tinyblacksmith.core.market

import com.tinyblacksmith.core.battle.Power
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.BlessingEffect
import com.tinyblacksmith.core.content.ToolEffect
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.engine.WorldEvents
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.rng.RngStream

/** Autonomous shelf visits and purchases (GDD 5 PROPOSED purchase algorithm) plus commission delivery. */
object Market {

    data class Evaluation(val weapon: Weapon, val utility: Double, val affordable: Boolean, val improvement: Int, val fit: Double, val pricePenalty: Double)

    fun resolveShelfVisits(ctx: ResolutionContext) {
        val config = ctx.config
        val rng = ctx.rng(RngStream.PURCHASES)
        val listed = ctx.weapons.values.filter { it.isListed }
        val festival = ctx.worldFlags[WorldEvents.FLAG_FESTIVAL] == ctx.day
        val maxCustomers = config.maxCustomersPerDay + (if (festival) config.festivalExtraCustomers else 0)
        val festivalBonus = (if (festival) config.festivalVisitBonus else 0.0) + (ctx.blessingMagnitude(BlessingEffect.HERO_VISIT_CHANCE) + ctx.toolTotal(ToolEffect.HERO_VISIT_CHANCE)) / 100.0  // + Guild Patronage, signboard
        var customers = 0
        for (hero in ctx.aliveHeroes()) {
            if (customers >= maxCustomers) break
            val shopWeight = hero.traits.sumOf { ctx.content.trait(it).shopWeight }
            val visitChance = (config.baseVisitChance + shopWeight * 0.1 + hero.loyalty * 0.01 + ctx.reputation * 0.005 + festivalBonus).coerceIn(0.05, 0.9)
            if (!rng.chance(visitChance)) continue
            customers++
            if (listed.none { it.isListed && ctx.weapon(it.id).isListed }) {
                ctx.visits += MarketVisit(hero.id, hero.fullName, null, "EMPTY_SHELVES")
                continue
            }
            val current = ctx.equippedWeapon(hero.id)
            val evaluations = ctx.weapons.values.filter { it.isListed }.map { evaluate(ctx, hero, current, it, rng.nextDouble()) }
            val best = evaluations.filter { it.affordable && it.improvement > 0 }.maxByOrNull { it.utility }
            if (best != null && best.utility >= config.purchaseUtilityThreshold) {
                purchase(ctx, hero, best.weapon, best.weapon.listedPrice ?: 0)
                ctx.visits += MarketVisit(hero.id, hero.fullName, best.weapon.id, if (best.fit >= 1.0) "GREAT_FIT" else "GOOD_ENOUGH")
            } else {
                val reason = when {
                    evaluations.none { it.affordable } -> "TOO_EXPENSIVE"
                    evaluations.none { it.affordable && it.improvement > 0 } -> "NOT_BETTER"
                    best != null && best.pricePenalty > 0.5 -> "OVERPRICED"
                    best != null && best.fit < 1.0 -> "NOT_SUITED"
                    else -> "UNDECIDED"
                }
                ctx.visits += MarketVisit(hero.id, hero.fullName, null, reason)
            }
        }
    }

    fun evaluate(ctx: ResolutionContext, hero: Hero, current: Weapon?, weapon: Weapon, noiseRoll: Double): Evaluation {
        val config = ctx.config
        val content = ctx.content
        val price = weapon.listedPrice ?: Int.MAX_VALUE
        val fit = Power.classFit(hero, weapon, content, config)
        val currentFit = Power.classFit(hero, current, content, config)
        val currentEffective = (Power.weaponPower(current, config) * currentFit).toInt()
        val candidateEffective = (weapon.power * fit).toInt()
        val improvement = candidateEffective - currentEffective
        val elementTaste = (if (weapon.element != null && weapon.element == hero.elementTaste) 1.0 else 0.0) +
            (if (weapon.element != null) hero.traits.sumOf { content.trait(it).noveltyTaste } else 0.0)
        val drive = if (hero.ambitionDone) null else hero.ambition
        val sensitivity = hero.traits.fold(1.0) { acc, t -> acc * content.trait(t).priceSensitivity } *
            (if (drive == Ambition.FORTUNE) config.fortunePriceSensitivity else 1.0)
        val collector = if (drive == Ambition.COLLECTOR && weapon.quality >= config.ambitionCollectorQuality) config.collectorUtilityBonus else 0.0
        // GDD 7: a storied blade is wanted for its name. Counted fame is capped like its power; a COLLECTOR wants it more.
        val fame = weapon.fame.coerceIn(0, config.weaponFameCap) * config.weaponFameUtilityPerPoint * (if (drive == Ambition.COLLECTOR) config.collectorFameMultiplier else 1.0)
        val ceiling = maxOf(1, weapon.power * config.fairGoldPerPower) * priceCeilingMultiplier(ctx.reputation, hero.loyalty, config)
        val pricePenalty = maxOf(0.0, price.toDouble() / ceiling - 1.0) * sensitivity
        val noise = (noiseRoll - 0.5) * 2 * config.utilityNoise
        val utility = improvement * config.utilityImprovementWeight +
            (fit - 1.0) * config.utilityClassFitWeight +
            elementTaste * config.utilityElementTasteWeight +
            hero.loyalty * 0.01 * config.utilityLoyaltyWeight +
            collector +
            fame +
            noise -
            pricePenalty * config.utilityPricePenaltyWeight
        return Evaluation(weapon, utility, affordable = price <= hero.gold + tradeInCredit(current, config), improvement = improvement, fit = fit, pricePenalty = pricePenalty)
    }

    /** Credit the shop gives for the weapon a hero currently wields when they buy a replacement. */
    fun tradeInCredit(current: Weapon?, config: BalanceConfig): Int =
        current?.let { (it.power * config.fairGoldPerPower * config.tradeInShare).toInt() } ?: 0

    /**
     * GDD 5 "Reputation ... affects willingness to pay; individual loyalty influences repeat customers": the price a hero
     * treats as fair is the base fair price times this bounded multiplier (shop reputation for everyone, the hero's own
     * loyalty on top). Fair-priced listings are unaffected; the multiplier only shrinks the overpricing penalty.
     */
    fun priceCeilingMultiplier(reputation: Int, loyalty: Int, config: BalanceConfig): Double =
        1.0 + (reputation * config.reputationPricePerPoint).coerceIn(0.0, config.reputationPriceCap) +
            (loyalty * config.loyaltyPricePerPoint).coerceIn(0.0, config.loyaltyPriceCap)

    fun isRegular(hero: Hero, config: BalanceConfig): Boolean = hero.loyalty >= config.regularLoyaltyThreshold

    fun purchase(ctx: ResolutionContext, hero: Hero, weapon: Weapon, price: Int) {
        val bonus = price * ctx.blessingMagnitude(BlessingEffect.SALE_GOLD_BONUS) / 100
        // Trade-in: the weapon being replaced comes back to the shop as part payment (GDD 7: weapons change hands).
        val old = ctx.equippedWeapon(hero.id)
        val credit = minOf(price, tradeInCredit(old, ctx.config))
        if (old != null) {
            ctx.updateWeapon(old.copy(location = WeaponLocation.Storage))
            ctx.addWeaponHistory(old.id, "TRADED_IN", "Traded in by ${hero.fullName} for ${weapon.name}.", listOf(hero.id.value))
        }
        ctx.gold += price - credit + bonus
        ctx.reputation += 1
        val loyaltyGain = hero.traits.fold(1.0) { acc, t -> acc * ctx.content.trait(t).loyaltyGain }.toInt().coerceAtLeast(1)
        ctx.updateHero(hero.copy(gold = hero.gold - (price - credit), loyalty = hero.loyalty + loyaltyGain, lastActivity = HeroActivity.SHOP))
        // Gazette-visible consequences: a regular is named as one; gold paid above the base fair price is recorded as a premium.
        val premium = price - weapon.power * ctx.config.fairGoldPerPower
        val who = if (isRegular(hero, ctx.config)) "${hero.fullName}, a regular of the shop," else hero.fullName
        val text = "$who bought ${weapon.name} for $price gold" + (if (premium > 0) ", $premium above the going rate on the shop's good name." else ".") +
            (if (old != null) " ${old.name} came back to the shop in part payment ($credit gold)." else "")
        val data = mapOf("price" to price.toString()) + (if (premium > 0) mapOf("premium" to premium.toString()) else emptyMap()) +
            (if (old != null) mapOf("tradeIn" to credit.toString(), "tradedWeapon" to old.id.value) else emptyMap())
        ctx.emit(EventType.WEAPON_SOLD, 4, text, listOf(hero.id.value, weapon.id.value), data)
        ctx.addWeaponHistory(weapon.id, "SOLD", "Sold to ${hero.fullName} for $price gold.", listOf(hero.id.value))
        giveAndEquip(ctx, ctx.hero(hero.id), ctx.weapon(weapon.id))
        ctx.milestone("FIRST_SALE", "The shop made its first sale: ${weapon.name} to ${hero.fullName}.")
    }

    /** Transfers ownership and equips when the weapon is better for this hero than the current one. */
    fun giveAndEquip(ctx: ResolutionContext, hero: Hero, weapon: Weapon) {
        val current = ctx.equippedWeapon(hero.id)
        val better = current == null ||
            weapon.power * Power.classFit(hero, weapon, ctx.content, ctx.config) > current.power * Power.classFit(hero, current, ctx.content, ctx.config)
        if (better) {
            if (current != null) ctx.updateWeapon(current.copy(location = WeaponLocation.Owned(hero.id, equipped = false)))
            ctx.updateWeapon(weapon.copy(location = WeaponLocation.Owned(hero.id, equipped = true)))
            ctx.emit(EventType.WEAPON_EQUIPPED, 2, "${hero.fullName} now wields ${weapon.name}.", listOf(hero.id.value, weapon.id.value))
            ctx.addWeaponHistory(weapon.id, "EQUIPPED", "Wielded by ${hero.fullName}.", listOf(hero.id.value))
        } else {
            ctx.updateWeapon(weapon.copy(location = WeaponLocation.Owned(hero.id, equipped = false)))
        }
    }

    fun resolveCommissions(ctx: ResolutionContext) {
        for (c in ctx.commissions.values.sortedBy { it.id.value }) {
            if (c.status != CommissionStatus.ACCEPTED) continue
            val buyer = ctx.heroes[c.buyerId]
            if (buyer == null || !buyer.isAlive) {
                ctx.commissions[c.id] = c.copy(status = CommissionStatus.EXPIRED)
                ctx.emit(EventType.COMMISSION_EXPIRED, 2, "The commission for a ${ctx.content.family(c.familyId).name} lapsed; its patron is gone.", listOf(c.id.value))
                continue
            }
            val candidate = ctx.weapons.values
                .filter { (it.isInStorage || it.isListed) && it.familyId == c.familyId && it.quality >= c.minQuality && (c.element == null || it.element == c.element) }
                .maxByOrNull { it.quality }
            if (candidate != null) {
                ctx.gold += c.reward
                ctx.reputation += 2
                ctx.commissions[c.id] = c.copy(status = CommissionStatus.COMPLETED, deliveredWeaponId = candidate.id)
                ctx.updateHero(buyer.copy(loyalty = buyer.loyalty + 2))
                ctx.emit(EventType.COMMISSION_COMPLETED, 5, "${buyer.fullName} collected the commissioned ${candidate.name} and paid ${c.reward} gold.", listOf(buyer.id.value, candidate.id.value, c.id.value), mapOf("reward" to c.reward.toString()))
                ctx.addWeaponHistory(candidate.id, "COMMISSION", "Delivered to ${buyer.fullName} on commission.", listOf(buyer.id.value))
                giveAndEquip(ctx, ctx.hero(buyer.id), ctx.weapon(candidate.id))
            } else if (ctx.day >= c.deadlineDay) {
                ctx.commissions[c.id] = c.copy(status = CommissionStatus.EXPIRED)
                ctx.reputation = maxOf(0, ctx.reputation - 1)
                ctx.emit(EventType.COMMISSION_EXPIRED, 2, "${buyer.fullName}'s commission for a ${ctx.content.family(c.familyId).name} expired unfulfilled.", listOf(buyer.id.value, c.id.value))
            }
        }
        // Offered-but-unaccepted commissions quietly lapse at their deadline.
        for (c in ctx.commissions.values.filter { it.status == CommissionStatus.OFFERED && ctx.day >= it.deadlineDay }) {
            ctx.commissions[c.id] = c.copy(status = CommissionStatus.EXPIRED)
        }
    }

    fun maybeOfferCommission(ctx: ResolutionContext) {
        val rng = ctx.rng(RngStream.EVENTS)
        if (ctx.commissions.values.any { it.status == CommissionStatus.OFFERED || it.status == CommissionStatus.ACCEPTED }) return
        if (!rng.chance(ctx.config.commissionChancePerDay)) return
        val heroes = ctx.aliveHeroes()
        if (heroes.isEmpty()) return
        // Regulars come back with requests (GDD 5: loyalty influences repeat customers and story continuity).
        val buyer = rng.pickWeighted(heroes.map { it to 1.0 + minOf(it.loyalty, ctx.config.commissionLoyaltyCap) * ctx.config.commissionLoyaltyWeight })
        val cls = ctx.content.heroClass(buyer.classId)
        val family = rng.pick(cls.preferredFamilies)
        val minQuality = rng.nextInt(35, 60)
        // GDD 5 "desirable effect": half the patrons want an element, their own taste or what the looming faction fears.
        val forgeable = ctx.content.materials.mapNotNull { it.element }.toSet()
        val wanted = (buyer.elementTaste ?: ctx.factions.values.sortedBy { it.id.value }.maxByOrNull { it.pressure }?.let { ctx.content.faction(it.id).weakTo })?.takeIf { it in forgeable }
        val element = if (wanted != null && rng.chance(ctx.config.commissionElementChance)) wanted else null
        val baseReward = ctx.config.commissionRewardBase + minQuality * ctx.config.commissionRewardPerQuality
        val reward = if (element != null) (baseReward * ctx.config.commissionElementRewardMultiplier).toInt() else baseReward
        val id = ctx.newCommissionId()
        val c = Commission(id, buyer.id, family, minQuality, reward, ctx.day, ctx.day + ctx.config.commissionDeadlineDays, CommissionStatus.OFFERED, element = element)
        ctx.commissions[id] = c
        val who = if (isRegular(buyer, ctx.config)) "${buyer.fullName}, a regular of the shop," else buyer.fullName
        ctx.emit(EventType.COMMISSION_OFFERED, 3, "$who asks for a fine ${element?.let { it.name.lowercase() + " " } ?: ""}${ctx.content.family(family).name} by day ${c.deadlineDay}, offering $reward gold.", listOf(buyer.id.value, id.value))
    }
}
