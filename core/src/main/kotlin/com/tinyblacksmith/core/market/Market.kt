package com.tinyblacksmith.core.market

import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.battle.Power
import com.tinyblacksmith.core.content.FactionDef
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.BlessingEffect
import com.tinyblacksmith.core.content.ToolEffect
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.engine.Relics
import com.tinyblacksmith.core.engine.WorldEvents
import com.tinyblacksmith.core.heroes.Appearance
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.rng.Rng
import com.tinyblacksmith.core.rng.RngStream

/** Autonomous shelf visits and purchases (GDD 5 PROPOSED purchase algorithm) plus commission delivery. */
object Market {

    /**
     * [gain] is what the blade is worth in this hero's hands less what the one they carry is worth there ([valueInHand]),
     * never rounded. The flags say which of the terms of [evaluate] counted for this blade; they are recorded with the
     * visit and decide nothing.
     */
    data class Evaluation(
        val weapon: Weapon, val utility: Double, val affordable: Boolean, val gain: Double, val fit: Double, val pricePenalty: Double, val worn: Boolean = false,
        val tasteMatch: Boolean = false, val novelty: Boolean = false, val collectorPrize: Boolean = false, val storied: Boolean = false,
        /** TASTE_MATCH, PRIZED or STORIED when the blade is no gain but near enough and has what the hero's own lacks; null otherwise. */
        val sideReason: VisitReason? = null,
        /** In the siege warning: of the element the besieger is weak to; of the one it resists. */
        val countersThreat: Boolean = false, val resisted: Boolean = false,
        /** A resisted blade this hero would have bought on a day without a warning, and will not today. */
        val refusedAsResisted: Boolean = false,
    ) {
        /** May be bought if its utility allows: affordable, and a gain or a sidegrade with a reason. */
        val eligible: Boolean get() = affordable && (gain > 0 || sideReason != null)
    }

    /**
     * Who browses the shelf today (plan 4.2). Every living hero decides for themselves whether to come, one draw each;
     * the seats are then drawn among the willing by weight, one draw per seat, and the seated are served in that order.
     * The ID order of the heroes only says which hero a draw belongs to: it never decides who gets in. A hero turned
     * away [com.tinyblacksmith.core.config.CustomerConfig.maxTurnedAwayDays] days running is seated first. All draws are
     * on the PURCHASES stream: one per living hero, one per seat filled, then one per listed blade per served visitor.
     */
    fun resolveShelfVisits(ctx: ResolutionContext) {
        val config = ctx.config
        val cfg = config.customers
        val rng = ctx.rng(RngStream.PURCHASES)
        val festival = ctx.worldFlags[WorldEvents.FLAG_FESTIVAL] == ctx.day
        val capacity = cfg.shopCapacity + ctx.toolTotal(ToolEffect.EXTRA_CUSTOMERS) + (if (festival) cfg.festivalExtraSeats else 0)  // + signboard
        val stocked = ctx.weapons.values.any { it.isListed }
        // 1. Intent. A patron who collected a commission today has had their turn at the counter: one appearance per hero per day.
        val patrons = ctx.visits.filter { it.kind == VisitKind.COMMISSION }.mapNotNull { it.heroId }.toSet()
        val willing = ctx.aliveHeroes().filter { rng.chance(willingness(ctx, it, festival)) }.filter { it.id !in patrons }
        // 2a. The longest waiters first: whole groups from the highest streak down; a group that does not fit is drawn by weight.
        val seated = mutableListOf<Hero>()
        val waiting = willing.filter { it.turnedAwayStreak >= cfg.maxTurnedAwayDays }
        for (streak in waiting.map { it.turnedAwayStreak }.distinct().sortedDescending()) {
            val pool = waiting.filter { it.turnedAwayStreak == streak }.toMutableList()
            while (seated.size < capacity && pool.isNotEmpty()) seated += seat(ctx, rng, pool, pool)
        }
        // 2b. Everyone else; the first seats are spread over classes while the willing allow.
        val pool = willing.filter { it.turnedAwayStreak < cfg.maxTurnedAwayDays }.toMutableList()
        while (seated.size < capacity && pool.isNotEmpty()) {
            val classes = seated.map { it.classId }.toSet()
            seated += seat(ctx, rng, pool, if (classes.size < cfg.classSeats) pool.filter { it.classId !in classes }.ifEmpty { pool } else pool)
        }
        // A day the shelf opened empty changes nobody's standing: bad luck with stock builds no queue.
        for (hero in willing) if (hero !in seated) {
            ctx.turnedAway += hero.id
            if (stocked) ctx.updateHero(hero.copy(turnedAwayStreak = hero.turnedAwayStreak + 1))
        }
        // 3. Serve in seating order: the first seated has first pick of the stock.
        for (hero in seated) {
            if (ctx.weapons.values.none { it.isListed }) {
                // Seated at an empty shelf: a visit on the record, but neither a first visit spent nor a wait ended.
                ctx.visits += MarketVisit(hero.id, hero.fullName, null, VisitReason.EMPTY_SHELVES, seq = ctx.visits.size, customer = customer(ctx, hero, ctx.equippedWeapon(hero.id)))
                continue
            }
            val current = ctx.equippedWeapon(hero.id)
            val customer = customer(ctx, hero, current)  // before the purchase moves the old blade and the purse
            val evaluations = ctx.weapons.values.filter { it.isListed }.map { evaluate(ctx, hero, current, it, rng.nextDouble()) }
            val best = evaluations.filter { it.eligible }.maxByOrNull { it.utility }
            if (best != null && best.utility >= config.purchaseUtilityThreshold) {
                val mark = ctx.newEvents.size
                val sale = purchase(ctx, hero, best.weapon, best.weapon.listedPrice ?: 0)
                Relics.onShelfSale(ctx, ctx.weapon(best.weapon.id), sale.listedPrice ?: 0, sale.cashPaid)
                val reason = when {
                    best.gain <= 0 -> best.sideReason!!
                    best.countersThreat -> VisitReason.COUNTERS_THREAT
                    best.worn -> VisitReason.WORN_OUT
                    best.fit >= 1.0 -> VisitReason.GREAT_FIT
                    else -> VisitReason.GOOD_ENOUGH
                }
                ctx.visits += MarketVisit(
                    hero.id, hero.fullName, best.weapon.id, reason, seq = ctx.visits.size, customer = customer,
                    considered = considered(ctx, hero, current, evaluations, best), sale = sale, eventIds = ctx.newEvents.drop(mark).map { it.id },
                )
            } else {
                val reason = when {
                    evaluations.none { it.affordable } -> VisitReason.TOO_EXPENSIVE
                    evaluations.any { it.refusedAsResisted } -> VisitReason.RESISTED
                    evaluations.none { it.eligible } -> VisitReason.NOT_BETTER
                    best != null && best.pricePenalty > cfg.overpricedPenalty -> VisitReason.OVERPRICED
                    best != null && best.fit < 1.0 -> VisitReason.NOT_SUITED
                    else -> VisitReason.UNDECIDED
                }
                ctx.visits += MarketVisit(hero.id, hero.fullName, null, reason, seq = ctx.visits.size, customer = customer, considered = considered(ctx, hero, current, evaluations, null))
            }
            val bought = best != null && best.utility >= config.purchaseUtilityThreshold
            val served = ctx.hero(hero.id)
            ctx.updateHero(served.copy(
                shopVisits = served.shopVisits + 1, lastServedDay = ctx.day, turnedAwayStreak = 0,
                shopPurchases = served.shopPurchases + (if (bought) 1 else 0), lastPurchaseDay = if (bought) ctx.day else served.lastPurchaseDay,
                want = if (bought) null else wantOf(ctx, served, current, evaluations),
                sideReasons = if (bought && best!!.gain <= 0) served.sideReasons + best.sideReason!! else served.sideReasons,
            ))
        }
    }

    /** The chance that a living hero wants to visit the shop today. Under Guild Patronage a guild member is as willing as anyone can be. */
    fun willingness(ctx: ResolutionContext, hero: Hero, festival: Boolean): Double {
        val cfg = ctx.config.customers
        if (hero.guildId != null && ctx.activeBlessing(BlessingEffect.GUILD_PATRONAGE) != null) return cfg.visitCeiling
        return (cfg.baseVisitChance + hero.traits.sumOf { ctx.content.trait(it).shopWeight } * cfg.visitTraitScale +
            minOf(hero.loyalty, cfg.visitLoyaltyCap) * cfg.visitPerLoyalty + minOf(ctx.reputation, cfg.visitReputationCap) * cfg.visitPerReputation +
            (if (festival) cfg.festivalVisitBonus else 0.0) +
            (if (wantMet(ctx, hero)) cfg.needWantMet else 0.0) +
            (if (hero.id in ctx.town.championIds && threat(ctx) != null) cfg.championSiegeWillingness else 0.0)   // a champion arms for the siege
            ).coerceIn(cfg.visitFloor, cfg.visitCeiling)
    }

    /**
     * Whether [weapon], as listed, is what [hero] left without: a blade of the wanted family they can pay for and would
     * take by the counter's own rule ([evaluate] at the middle of its noise). Draws nothing.
     */
    fun answersWant(ctx: ResolutionContext, hero: Hero, weapon: Weapon): Boolean {
        val want = hero.want ?: return false
        if (!weapon.isListed || weapon.familyId != want.familyId) return false
        val e = evaluate(ctx, hero, ctx.equippedWeapon(hero.id), weapon, 0.5)
        return e.eligible && e.utility >= ctx.config.purchaseUtilityThreshold
    }

    /** The need term of the visit chance and of the seat weight: the shelf holds what this hero asked for. Off when `needWantMet` is 0. */
    fun wantMet(ctx: ResolutionContext, hero: Hero): Boolean =
        ctx.config.customers.needWantMet > 0 && hero.want != null && ctx.weapons.values.any { answersWant(ctx, hero, it) }

    /**
     * What would have sold to a hero who was served and bought nothing: the family of the blade for their class they
     * valued most on the shelf (their class's first family when none suited), the power it would need before the
     * counter's rule lets them take it, and what they can pay today. A want already standing keeps its family and its
     * day, so it lapses on time however often they look in; its power and budget are today's. No draw.
     */
    fun wantOf(ctx: ResolutionContext, hero: Hero, current: Weapon?, evaluations: List<Evaluation>): Want {
        val config = ctx.config
        val familyId = hero.want?.familyId ?: evaluations.filter { it.fit >= 1.0 }.maxByOrNull { it.utility }?.weapon?.familyId ?: ctx.content.heroClass(hero.classId).preferredFamilies.first()
        val fit = ctx.content.family(familyId).classFit[hero.classId] ?: config.offFamilyFit
        val held = valueInHand(ctx, hero, current, null)
        val worn = current != null && current.condition < config.wornConditionThreshold
        val given = (fit - 1.0) * config.utilityClassFitWeight + hero.loyalty * config.customers.utilityLoyaltyScale * config.utilityLoyaltyWeight + (if (worn) config.wornReplacementUtility else 0.0)
        val gain = maxOf(0.0, (config.purchaseUtilityThreshold - given) / config.utilityImprovementWeight)
        return Want(familyId, ((held + gain) / fit).toInt() + 1, hero.gold + tradeInCredit(current, config), hero.want?.sinceDay ?: ctx.day)
    }

    /** A willing hero's weight in the draw for a seat: regulars, newcomers, those kept waiting and those in need of a blade count for more. */
    fun seatWeight(ctx: ResolutionContext, hero: Hero): Double {
        val cfg = ctx.config.customers
        val own = ctx.equippedWeapon(hero.id)
        val weight = 1.0 + cfg.seatLoyaltyWeight * hero.loyalty.coerceIn(0, cfg.seatLoyaltyCap) / cfg.seatLoyaltyCap +
            (if (hero.shopVisits == 0) cfg.seatNewcomerWeight else 0.0) +
            cfg.seatWaitWeight * hero.turnedAwayStreak +
            (if (own == null || own.condition < ctx.config.wornConditionThreshold) cfg.seatNeedWeight else 0.0) +
            (if (wantMet(ctx, hero)) cfg.seatWantWeight else 0.0)
        val browsedYesterday = hero.lastServedDay == ctx.day - 1 && hero.lastPurchaseDay != ctx.day - 1
        return if (browsedYesterday) weight * cfg.seatBrowsedYesterday else weight
    }

    /** One draw for one seat among [candidates]; the hero leaves [pool]. */
    private fun seat(ctx: ResolutionContext, rng: Rng, pool: MutableList<Hero>, candidates: List<Hero>): Hero =
        rng.pickWeighted(candidates.map { it to seatWeight(ctx, it) }).also { pool -= it }

    const val MAX_CONSIDERED = 3

    /** The customer as they walked in. Copies of what the visit read; nothing here is an input to it. */
    private fun customer(ctx: ResolutionContext, hero: Hero, current: Weapon?) = CustomerSnapshot(
        heroId = hero.id, name = hero.fullName, classId = hero.classId, level = hero.level,
        appearance = Appearance.keyOf(hero),
        traits = hero.traits, elementTaste = hero.elementTaste, ambition = hero.ambition, gold = hero.gold, loyalty = hero.loyalty,
        regular = isRegular(hero, ctx.config), guildId = hero.guildId, mentorName = hero.mentorName, equipped = current?.let { WeaponSnapshot.of(it) },
    )

    /** At most [MAX_CONSIDERED] of the blades a visitor weighed: the one they took first, then the ones they valued most, each with the facts that counted. */
    private fun considered(ctx: ResolutionContext, hero: Hero, current: Weapon?, evaluations: List<Evaluation>, chosen: Evaluation?): List<Considered> {
        val funds = hero.gold + tradeInCredit(current, ctx.config) + stipend(ctx, hero)
        val regular = isRegular(hero, ctx.config)
        val ranked = listOfNotNull(chosen) + evaluations.filter { it !== chosen }.sortedWith(compareByDescending<Evaluation> { it.utility }.thenBy(IdOrder.numeric) { it.weapon.id.value })
        return ranked.take(MAX_CONSIDERED).map { e ->
            val price = e.weapon.listedPrice ?: 0
            Considered(
                e.weapon.id, price,
                listOfNotNull(
                    if (e.fit >= 1.0) VisitFactor.SUITS_CLASS else VisitFactor.OFF_CLASS,
                    VisitFactor.ELEMENT_TASTE.takeIf { e.tasteMatch }, VisitFactor.LIKES_NOVELTY.takeIf { e.novelty },
                    when { current == null -> VisitFactor.UNARMED; e.gain > 0 -> VisitFactor.STRONGER_THAN_OWN; else -> VisitFactor.NOT_STRONGER_THAN_OWN },
                    VisitFactor.OWN_BLADE_WORN.takeIf { e.worn }, VisitFactor.STORIED_BLADE.takeIf { e.storied }, VisitFactor.COLLECTOR_PRIZE.takeIf { e.collectorPrize },
                    VisitFactor.COUNTERS_THREAT.takeIf { e.countersThreat }, VisitFactor.THREAT_RESISTS.takeIf { e.resisted },
                    if (e.affordable) VisitFactor.CAN_AFFORD else VisitFactor.CANNOT_AFFORD,
                    VisitFactor.ABOVE_THEIR_CEILING.takeIf { e.pricePenalty > 0 }, VisitFactor.REGULAR.takeIf { regular },
                ),
                shortBy = if (e.affordable) null else price - funds,
            )
        }
    }

    /**
     * What a blade is worth in [hero]'s hands: its power as worn, by class fit, by what its affixes and its fame add to a
     * strike (both bounded, `battle.Power`), and by the matchup against [threat] when a siege warning is out. The same
     * terms on the blade in hand and on the one on the shelf; bare hands count as `unarmedPower`.
     */
    fun valueInHand(ctx: ResolutionContext, hero: Hero, weapon: Weapon?, threat: FactionDef?): Double =
        Power.weaponPower(weapon, ctx.config) * Power.conditionFactor(weapon, ctx.config) * Power.classFit(hero, weapon, ctx.content, ctx.config) *
            Power.affixAttackMultiplier(weapon, ctx.content) * Power.fameFactor(weapon, ctx.config) * (if (threat != null) Power.matchup(weapon, threat, ctx.config) else 1.0)

    /** The besieger heroes shop against today ([Battle.warnedFaction]); none with `threatUtility` at 0, which switches siege demand off whole. */
    private fun threat(ctx: ResolutionContext): FactionDef? = if (ctx.config.customers.threatUtility > 0) Battle.warnedFaction(ctx) else null

    /** One-way: the candidate has it, the blade in hand does not, and this hero has not bought for it before. */
    private fun sideReason(hero: Hero, drive: Ambition?, current: Weapon, weapon: Weapon, config: BalanceConfig): VisitReason? = when {
        VisitReason.TASTE_MATCH !in hero.sideReasons && weapon.element != null && weapon.element == hero.elementTaste && current.element != hero.elementTaste -> VisitReason.TASTE_MATCH
        VisitReason.PRIZED !in hero.sideReasons && drive == Ambition.COLLECTOR && weapon.quality >= config.ambitionCollectorQuality && current.quality < config.ambitionCollectorQuality -> VisitReason.PRIZED
        VisitReason.STORIED !in hero.sideReasons && weapon.fame >= config.legendFameThreshold && current.fame < config.legendFameThreshold -> VisitReason.STORIED
        else -> null
    }

    fun evaluate(ctx: ResolutionContext, hero: Hero, current: Weapon?, weapon: Weapon, noiseRoll: Double): Evaluation {
        val config = ctx.config
        val content = ctx.content
        val cfg = config.customers
        val price = weapon.listedPrice ?: Int.MAX_VALUE
        val fit = Power.classFit(hero, weapon, content, config)
        // The same value on both sides, worn power included: a battered blade is worth less in the hand and on the shelf.
        val threat = threat(ctx)
        val held = valueInHand(ctx, hero, current, threat)
        val gain = valueInHand(ctx, hero, weapon, threat) - held
        val countersThreat = threat != null && weapon.element != null && weapon.element == threat.weakTo
        val resisted = threat != null && weapon.element != null && weapon.element == threat.resists
        val worn = current != null && current.condition < config.wornConditionThreshold
        val elementTaste = (if (weapon.element != null && weapon.element == hero.elementTaste) 1.0 else 0.0) +
            (if (weapon.element != null) hero.traits.sumOf { content.trait(it).noveltyTaste } else 0.0)
        val drive = if (hero.ambitionDone) null else hero.ambition
        val sensitivity = hero.traits.fold(1.0) { acc, t -> acc * content.trait(t).priceSensitivity } *
            (if (drive == Ambition.FORTUNE) config.fortunePriceSensitivity else 1.0)
        val collector = if (drive == Ambition.COLLECTOR && weapon.quality >= config.ambitionCollectorQuality) config.collectorUtilityBonus else 0.0
        // GDD 7: a storied blade is wanted for its name. Counted fame is capped like its power; a COLLECTOR wants it more.
        val fame = weapon.fame.coerceIn(0, config.weaponFameCap) * config.weaponFameUtilityPerPoint * (if (drive == Ambition.COLLECTOR) config.collectorFameMultiplier else 1.0)
        val ceiling = maxOf(1, fairPrice(weapon, config)) * priceCeilingMultiplier(ctx.reputation, hero.loyalty, config)
        val pricePenalty = maxOf(0.0, price.toDouble() / ceiling - 1.0) * sensitivity
        val noise = (noiseRoll - 0.5) * 2 * config.utilityNoise
        val side = if (current != null && cfg.sidegradeTolerance > 0 && gain <= 0 && gain >= -cfg.sidegradeTolerance * held) sideReason(hero, drive, current, weapon, config) else null
        val utility = gain * config.utilityImprovementWeight +
            (fit - 1.0) * config.utilityClassFitWeight +
            elementTaste * config.utilityElementTasteWeight +
            hero.loyalty * config.customers.utilityLoyaltyScale * config.utilityLoyaltyWeight +
            collector +
            fame +
            (if (worn) config.wornReplacementUtility else 0.0) +
            (if (countersThreat) cfg.threatUtility else 0.0) +
            noise -
            pricePenalty * config.utilityPricePenaltyWeight
        val affordable = price <= funds(ctx, hero, current)
        // "Refused for it": the same roll buys this blade on a calm day and not under the warning.
        val calm = if (resisted) valueInHand(ctx, hero, weapon, null) - valueInHand(ctx, hero, current, null) else gain
        val refusedAsResisted = resisted && affordable && calm > 0 && utility + (calm - gain) * config.utilityImprovementWeight >= config.purchaseUtilityThreshold &&
            !(gain > 0 && utility >= config.purchaseUtilityThreshold)
        return Evaluation(
            weapon, utility, affordable = affordable, gain = gain, fit = fit, pricePenalty = pricePenalty, worn = worn,
            tasteMatch = weapon.element != null && weapon.element == hero.elementTaste, novelty = weapon.element != null && hero.traits.sumOf { content.trait(it).noveltyTaste } > 0,
            collectorPrize = collector > 0, storied = fame > 0,
            sideReason = side, countersThreat = countersThreat, resisted = resisted, refusedAsResisted = refusedAsResisted,
        )
    }

    /** Gold heroes consider fair for this weapon as it is: power, discounted for wear. The shop's suggested price. */
    fun fairPrice(weapon: Weapon, config: BalanceConfig): Int = (weapon.power * Power.conditionFactor(weapon, config) * config.fairGoldPerPower).toInt()

    /** The going rate: the fair price plus a small premium for a storied blade (GDD 7; only the first [BalanceConfig.weaponFameCap] fame points count). */
    fun askingPrice(weapon: Weapon, config: BalanceConfig): Int =
        (fairPrice(weapon, config) * (1.0 + weapon.fame.coerceIn(0, config.weaponFameCap) * config.weaponFamePricePerPoint)).toInt()

    /** Credit the shop gives for the weapon a hero currently wields when they buy a replacement. */
    fun tradeInCredit(current: Weapon?, config: BalanceConfig): Int =
        current?.let { (fairPrice(it, config) * config.tradeInShare).toInt() } ?: 0

    /**
     * GDD 5 "Reputation ... affects willingness to pay; individual loyalty influences repeat customers": the price a hero
     * treats as fair is the base fair price times this bounded multiplier (shop reputation for everyone, the hero's own
     * loyalty on top). Fair-priced listings are unaffected; the multiplier only shrinks the overpricing penalty.
     */
    fun priceCeilingMultiplier(reputation: Int, loyalty: Int, config: BalanceConfig): Double =
        1.0 + (reputation * config.reputationPricePerPoint).coerceIn(0.0, config.reputationPriceCap) +
            (loyalty * config.loyaltyPricePerPoint).coerceIn(0.0, config.loyaltyPriceCap)

    fun isRegular(hero: Hero, config: BalanceConfig): Boolean = hero.loyalty >= config.regularLoyaltyThreshold

    /**
     * What [hero] can put on the counter today: their purse, the credit for the blade they carry ([current]) and a guild
     * stipend not yet spent. The one rule for "can pay": [evaluate] and `Demand.funds` both read it.
     */
    fun funds(ctx: ResolutionContext, hero: Hero, current: Weapon?): Int = hero.gold + tradeInCredit(current, ctx.config) + stipend(ctx, hero)

    /** Guild Patronage: the gold [hero]'s guild would put toward a blade today; once per member per blessing, 0 without the blessing or a guild. */
    fun stipend(ctx: ResolutionContext, hero: Hero): Int {
        val patronage = ctx.activeBlessing(BlessingEffect.GUILD_PATRONAGE) ?: return 0
        return if (hero.guildId != null && hero.stipendSpentFor != patronage.expiresDay) ctx.config.customers.patronageStipend else 0
    }

    fun purchase(ctx: ResolutionContext, hero: Hero, weapon: Weapon, price: Int): Sale {
        val bonus = price * ctx.blessingMagnitude(BlessingEffect.SALE_GOLD_BONUS) / 100
        // Trade-in: the weapon being replaced comes back to the shop as part payment (GDD 7: weapons change hands).
        val old = ctx.equippedWeapon(hero.id)
        val credit = minOf(price, tradeInCredit(old, ctx.config))
        if (old != null) {
            ctx.updateWeapon(old.copy(location = WeaponLocation.Storage))
            ctx.addWeaponHistory(old.id, "TRADED_IN", "Traded in by ${hero.fullName} for ${weapon.name}.", listOf(hero.id.value))
        }
        // Guild Patronage: the guild pays its share of what is left after the trade-in, straight to the till.
        val stipend = minOf(price - credit, stipend(ctx, hero))
        val paid = price - credit - stipend
        ctx.earn(IncomeKind.SHELF_SALE, paid)
        if (stipend > 0) ctx.earn(IncomeKind.STIPEND, stipend)
        if (bonus > 0) ctx.earn(IncomeKind.SALE_BONUS, bonus)
        ctx.tradeInCreditToday += credit
        ctx.reputation += ctx.config.customers.saleReputation
        val loyaltyGain = hero.traits.fold(1.0) { acc, t -> acc * ctx.content.trait(t).loyaltyGain }.toInt().coerceAtLeast(1)
        ctx.updateHero(hero.copy(
            gold = hero.gold - paid, loyalty = hero.loyalty + loyaltyGain, lastActivity = HeroActivity.SHOP, want = null,
            stipendSpentFor = if (stipend > 0) ctx.activeBlessing(BlessingEffect.GUILD_PATRONAGE)?.expiresDay else hero.stipendSpentFor,
        ))
        // Gazette-visible consequences: a regular is named as one; gold paid above the base fair price is recorded as a premium.
        val premium = price - askingPrice(weapon, ctx.config)
        val who = if (isRegular(hero, ctx.config)) "${hero.fullName}, a regular of the shop," else hero.fullName
        val text = "$who bought ${weapon.name} for $price gold" + (if (premium > 0) ", $premium above the going rate on the shop's good name." else ".") +
            (if (old != null) " ${old.name} came back to the shop in part payment ($credit gold)." else "") +
            (if (stipend > 0) " Their guild paid $stipend gold of the price." else "")
        val data = mapOf("price" to price.toString()) + (if (premium > 0) mapOf("premium" to premium.toString()) else emptyMap()) + (if (bonus > 0) mapOf("bonus" to bonus.toString()) else emptyMap()) +
            (if (old != null) mapOf("tradeIn" to credit.toString(), "tradedWeapon" to old.id.value) else emptyMap()) + (if (stipend > 0) mapOf("stipend" to stipend.toString()) else emptyMap())
        ctx.emit(EventType.WEAPON_SOLD, 4, text, listOf(hero.id.value, weapon.id.value), data)
        ctx.addWeaponHistory(weapon.id, "SOLD", "Sold to ${hero.fullName} for $price gold.", listOf(hero.id.value))
        giveAndEquip(ctx, ctx.hero(hero.id), ctx.weapon(weapon.id))
        ctx.milestone("FIRST_SALE", "The shop made its first sale: ${weapon.name} to ${hero.fullName}.")
        return Sale(listedPrice = price, tradeInCredit = credit, tradeInWeaponId = old?.id, cashPaid = paid, saleBonus = bonus, stipend = stipend)
    }

    /** Transfers ownership and equips when the weapon is better for this hero than the current one; against [against] (a siege-prep order) by what each is worth in that fight. */
    fun giveAndEquip(ctx: ResolutionContext, hero: Hero, weapon: Weapon, against: FactionDef? = null) {
        val current = ctx.equippedWeapon(hero.id)
        val better = current == null || (if (against != null) valueInHand(ctx, hero, weapon, against) > valueInHand(ctx, hero, current, against) else
            weapon.power * Power.conditionFactor(weapon, ctx.config) * Power.classFit(hero, weapon, ctx.content, ctx.config) >
            current.power * Power.conditionFactor(current, ctx.config) * Power.classFit(hero, current, ctx.content, ctx.config))
        if (better) {
            if (current != null) ctx.updateWeapon(current.copy(location = WeaponLocation.Owned(hero.id, equipped = false)))
            ctx.updateWeapon(weapon.copy(location = WeaponLocation.Owned(hero.id, equipped = true)))
            ctx.emit(EventType.WEAPON_EQUIPPED, 2, "${hero.fullName} now wields ${weapon.name}.", listOf(hero.id.value, weapon.id.value))
            ctx.addWeaponHistory(weapon.id, "EQUIPPED", "Wielded by ${hero.fullName}.", listOf(hero.id.value))
        } else {
            ctx.updateWeapon(weapon.copy(location = WeaponLocation.Owned(hero.id, equipped = false)))
        }
    }

    /**
     * GDD 7 merchant resale. A fallen hero's blade a travelling merchant picked up ([WeaponLocation.Lost.WITH_MERCHANT],
     * dated the day the hero fell) reaches Emberfall `weaponFates.merchantDelayDays` days later and is offered at the
     * going rate for `weaponFates.merchantStayDays` End Days. The living hero who values it most by the shelf's own
     * [evaluate], and can pay in full, buys it: the gold goes to the merchant and leaves the economy (no trade-in, nothing
     * for the smith). Unsold, the merchant moves on and the blade is lost for good. Draws no RNG.
     */
    fun resolveMerchant(ctx: ResolutionContext) {
        val config = ctx.config
        for (w in ctx.weapons.values.filter { it.isWithMerchant }.sortedWith(compareBy(IdOrder.numeric) { it.id.value })) {
            val arrives = (w.location as WeaponLocation.Lost).day + config.weaponFates.merchantDelayDays
            if (ctx.day < arrives) continue
            val fallenId = w.history.lastOrNull { it.kind == "SCAVENGED" }?.subjectIds?.firstOrNull()
            val fallen = fallenId?.let { ctx.heroes[HeroId(it)]?.fullName } ?: "a fallen hero"
            if (ctx.day == arrives) ctx.emit(EventType.WEAPON_SURFACED, 4, "A travelling merchant reached Emberfall offering ${w.name}, the blade $fallen fell with.", listOfNotNull(w.id.value, fallenId))
            val price = askingPrice(w, config)
            val offer = w.copy(location = WeaponLocation.Shelf(price))
            val buyer = ctx.aliveHeroes().filter { it.gold >= price }
                .map { it to evaluate(ctx, it, ctx.equippedWeapon(it.id), offer, 0.5) }
                .filter { (_, e) -> e.gain > 0 && e.utility >= config.purchaseUtilityThreshold }
                .maxByOrNull { (_, e) -> e.utility }?.first
            if (buyer != null) {
                ctx.updateHero(buyer.copy(gold = buyer.gold - price, want = null))
                ctx.addWeaponHistory(w.id, "RESOLD", "Sold to ${buyer.fullName} by a travelling merchant for $price gold.", listOf(buyer.id.value))
                giveAndEquip(ctx, ctx.hero(buyer.id), ctx.weapon(w.id))
                ctx.emit(EventType.WEAPON_RESOLD, 5, "${buyer.fullName} bought ${w.name}, the blade $fallen fell with, from a travelling merchant for $price gold.", listOf(buyer.id.value, w.id.value), mapOf("price" to price.toString(), WeaponFate.KEY to WeaponFate.RESOLD.name))
            } else if (ctx.day >= arrives + config.weaponFates.merchantStayDays - 1) {
                ctx.updateWeapon(w.copy(location = WeaponLocation.Lost(ctx.day, "carried off by a travelling merchant")))
                ctx.addWeaponHistory(w.id, "LOST", "Carried off unsold by a travelling merchant.", listOfNotNull(fallenId))
                ctx.emit(EventType.WEAPON_LOST, 4, "The travelling merchant left Emberfall with ${w.name} unsold.", listOfNotNull(w.id.value, fallenId), mapOf(WeaponFate.KEY to WeaponFate.LOST.name))
            }
        }
    }

    fun resolveCommissions(ctx: ResolutionContext) {
        for (c in ctx.commissions.values.sortedWith(compareBy(IdOrder.numeric) { it.id.value })) {
            if (c.status != CommissionStatus.ACCEPTED) continue
            val buyer = ctx.heroes[c.buyerId]
            if (buyer == null || !buyer.isAlive) {
                release(ctx, c)
                ctx.commissions[c.id] = c.copy(status = CommissionStatus.EXPIRED)
                ctx.emit(EventType.COMMISSION_EXPIRED, 2, "The commission for a ${ctx.content.family(c.familyId).name} lapsed; its patron is gone.", listOf(c.id.value))
                continue
            }
            val candidate = Commissions.pick(ctx.weapons.values, c, ctx.config)
            if (candidate != null) {
                val mark = ctx.newEvents.size
                val customer = customer(ctx, buyer, ctx.equippedWeapon(buyer.id))
                ctx.earn(IncomeKind.COMMISSION, c.reward)
                ctx.reputation += ctx.config.customers.commissionReputation
                ctx.commissions[c.id] = c.copy(status = CommissionStatus.COMPLETED, deliveredWeaponId = candidate.id)
                release(ctx, c)
                ctx.updateHero(buyer.copy(loyalty = buyer.loyalty + ctx.config.customers.commissionLoyalty, want = null))
                // A first blade is carried by the hero it was ordered for; should they be gone by now, the patron keeps it.
                val receiver = c.recipientId?.let { ctx.heroes[it] }?.takeIf { it.isAlive }?.also { ctx.updateHero(it.copy(want = null)) } ?: buyer
                val forWhom = if (receiver.id != buyer.id) " for ${receiver.fullName}" else ""
                ctx.emit(EventType.COMMISSION_COMPLETED, 5, "${buyer.fullName} collected the commissioned ${candidate.name}$forWhom and paid ${c.reward} gold.", listOf(buyer.id.value, candidate.id.value, c.id.value),
                    mapOf("reward" to c.reward.toString(), "kind" to c.kind.name))
                ctx.addWeaponHistory(candidate.id, "COMMISSION", if (receiver.id != buyer.id) "Ordered by ${buyer.fullName} and delivered to ${receiver.fullName}." else "Delivered to ${buyer.fullName} on commission.", listOf(receiver.id.value))
                WorldEvents.rumour(ctx, "${buyer.fullName}, collecting the commission,", buyer.id)   // a satisfied patron talks (plan 4.6 E3)
                // A blade ordered for the wall is judged against the besieger: the champion takes it up if it is worth more to them in that fight.
                giveAndEquip(ctx, ctx.hero(receiver.id), ctx.weapon(candidate.id), if (c.kind == CommissionKind.SIEGE_PREP) Battle.besieger(ctx)?.let { ctx.content.faction(it.id) } else null)
                // The patron is a visit of its own kind: the reward is the coin; the shelf price, if the blade had one, is only what it was listed at.
                ctx.visits += MarketVisit(
                    buyer.id, buyer.fullName, candidate.id, VisitReason.COMMISSION_DELIVERED, seq = ctx.visits.size, kind = VisitKind.COMMISSION, customer = customer,
                    considered = listOf(Considered(candidate.id, candidate.listedPrice ?: 0)), sale = Sale(listedPrice = candidate.listedPrice, cashPaid = c.reward, commissionId = c.id),
                    eventIds = ctx.newEvents.drop(mark).map { it.id },
                )
            } else if (ctx.day >= c.deadlineDay) {
                release(ctx, c)
                ctx.commissions[c.id] = c.copy(status = CommissionStatus.EXPIRED)
                ctx.reputation = maxOf(0, ctx.reputation - ctx.config.customers.commissionExpiredReputation)
                ctx.emit(EventType.COMMISSION_EXPIRED, 2, "${buyer.fullName}'s commission for a ${ctx.content.family(c.familyId).name} expired unfulfilled.", listOf(buyer.id.value, c.id.value))
            }
        }
        // Offered-but-unaccepted commissions quietly lapse at their deadline.
        for (c in ctx.commissions.values.filter { it.status == CommissionStatus.OFFERED && ctx.day >= it.deadlineDay }) {
            ctx.commissions[c.id] = c.copy(status = CommissionStatus.EXPIRED)
        }
    }

    /** A blade kept for an order is the shop's again when the order closes, however it closes. */
    private fun release(ctx: ResolutionContext, c: Commission) {
        ctx.weapons.values.filter { it.promisedTo == c.id }.forEach { ctx.updateWeapon(it.copy(promisedTo = null)) }
    }

    /** Requests that are offered or accepted, and the heroes named on them: nobody has two at once. */
    fun openCommissions(ctx: ResolutionContext): List<Commission> = ctx.commissions.values.filter { it.status == CommissionStatus.OFFERED || it.status == CommissionStatus.ACCEPTED }

    /**
     * Who has a reason to ask today, by kind (plan 4.6 E4); read from the state, no draw. [free] are the living heroes
     * with no open request. A kind with nobody behind it, or with weight 0, is absent; ORDINARY needs no reason.
     */
    fun commissionSituations(ctx: ResolutionContext, free: List<Hero>): Map<CommissionKind, List<Hero>> {
        val config = ctx.config
        val w = config.commissions
        val daysToSiege = ctx.town.nextSiegeDay - ctx.day
        val forgeable = ctx.content.materials.mapNotNull { it.element }.toSet()
        val feared = Battle.besieger(ctx)?.let { ctx.content.faction(it.id).weakTo }?.takeIf { it in forgeable }
        fun carriedThisEra(h: Hero) = ctx.weapons.values.any { blade -> h.id in com.tinyblacksmith.core.legacy.Legacy.holders(blade, ctx.era) }
        val newcomers = free.filter { it.shopPurchases == 0 && ctx.equippedWeapon(it.id) == null }
        return linkedMapOf(
            CommissionKind.ORDINARY to (if (w.ordinaryWeight > 0) free else emptyList()),
            CommissionKind.REPLACEMENT to (if (w.replacementWeight > 0) free.filter { h -> ctx.equippedWeapon(h.id).let { own -> if (own == null) carriedThisEra(h) else own.condition < config.wornConditionThreshold } } else emptyList()),
            // A champion who already carries the element the besieger fears has no reason to ask.
            CommissionKind.SIEGE_PREP to (if (w.siegePrepWeight > 0 && feared != null && daysToSiege in 1..w.siegePrepDays) free.filter { it.id in ctx.town.championIds && ctx.equippedWeapon(it.id)?.element != feared } else emptyList()),
            CommissionKind.AMBITION to (if (w.ambitionWeight > 0) free.filter { it.ambition == Ambition.COLLECTOR && !it.ambitionDone } else emptyList()),
            CommissionKind.FIRST_BLADE to (if (w.firstBladeWeight > 0) free.filter { h -> h.guildId != null && newcomers.any { it.id != h.id } } else emptyList()),
        ).filterValues { it.isNotEmpty() }
    }

    private fun kindWeight(kind: CommissionKind, config: BalanceConfig): Double = when (kind) {
        // WALL_PLEDGE and HEIRLOOM are only ever made by a visitor's answer; the daily offer never draws them.
        CommissionKind.ORDINARY, CommissionKind.NOBLE, CommissionKind.WALL_PLEDGE, CommissionKind.HEIRLOOM -> config.commissions.ordinaryWeight
        CommissionKind.REPLACEMENT -> config.commissions.replacementWeight
        CommissionKind.SIEGE_PREP -> config.commissions.siegePrepWeight
        CommissionKind.AMBITION -> config.commissions.ambitionWeight
        CommissionKind.FIRST_BLADE -> config.commissions.firstBladeWeight
    }

    /**
     * The daily request (EVENTS stream): the chance, then a kind among today's situations, then the patron among the
     * heroes with that reason, then the family, the band and the element as before. Up to
     * `customers.maxOpenCommissions` are open at once and no hero is named on two.
     */
    fun maybeOfferCommission(ctx: ResolutionContext) {
        val rng = ctx.rng(RngStream.EVENTS)
        val config = ctx.config
        val open = openCommissions(ctx)
        if (open.size >= config.customers.maxOpenCommissions) return
        if (!rng.chance(config.commissionChancePerDay)) return
        val named = open.flatMap { listOfNotNull(it.buyerId, it.recipientId) }.toSet()
        val free = ctx.aliveHeroes().filter { it.id !in named }
        val situations = commissionSituations(ctx, free)
        if (situations.isEmpty()) return
        val kind = rng.pickWeighted(situations.keys.map { it to kindWeight(it, config) })
        // Regulars come back with requests (GDD 5: loyalty influences repeat customers and story continuity).
        val buyer = rng.pickWeighted(situations.getValue(kind).map { it to 1.0 + minOf(it.loyalty, config.commissionLoyaltyCap) * config.commissionLoyaltyWeight })
        // A first blade is for the newcomer of the patron's own guild when there is one, else the earliest arrival (no draw).
        val recipient = if (kind != CommissionKind.FIRST_BLADE) null else free.filter { it.id != buyer.id && it.shopPurchases == 0 && ctx.equippedWeapon(it.id) == null }
            .minWithOrNull(compareBy<Hero> { it.guildId != buyer.guildId }.thenBy(IdOrder.numeric) { it.id.value })
        val cls = ctx.content.heroClass((recipient ?: buyer).classId)
        val family = rng.pick(cls.preferredFamilies)
        // Always a band floor, so the word on the request is what the rule checks (one draw, as the old 35..60 roll was). A collector asks for the floor the ambition needs.
        val fine = rng.chance(config.commissions.fineShare)
        val minQuality = (if (fine || kind == CommissionKind.AMBITION) QualityBand.FINE else QualityBand.DECENT).floor(config)
        // GDD 5 "desirable effect": half the patrons want an element, their own taste or what the looming faction fears; a champion before a siege always wants the latter.
        val forgeable = ctx.content.materials.mapNotNull { it.element }.toSet()
        val feared = Battle.besieger(ctx)?.let { ctx.content.faction(it.id).weakTo }?.takeIf { it in forgeable }
        val wanted = (buyer.elementTaste ?: feared)?.takeIf { it in forgeable }
        val asksElement = wanted != null && rng.chance(config.commissionElementChance)
        val element = if (kind == CommissionKind.SIEGE_PREP) feared else if (asksElement) wanted else null
        val baseReward = config.commissionRewardBase + minQuality * config.commissionRewardPerQuality
        val reward = if (element != null) (baseReward * config.commissionElementRewardMultiplier).toInt() else baseReward
        // Due on the siege day at the latest when it is for the wall: commissions are collected before the raid that evening.
        val deadline = if (kind == CommissionKind.SIEGE_PREP) ctx.town.nextSiegeDay else ctx.day + config.commissionDeadlineDays
        val id = ctx.newCommissionId()
        val c = Commission(id, buyer.id, family, minQuality, reward, ctx.day, deadline, CommissionStatus.OFFERED, element = element, kind = kind, recipientId = recipient?.id)
        ctx.commissions[id] = c
        val who = if (isRegular(buyer, config)) "${buyer.fullName}, a regular of the shop," else buyer.fullName
        val why = Commissions.why(c, buyer.fullName, recipient?.fullName)?.let { " $it" }.orEmpty()
        ctx.emit(EventType.COMMISSION_OFFERED, 3, "$who asks for a ${Commissions.describe(c, ctx.content, config)} by day ${c.deadlineDay}, offering $reward gold.$why",
            listOf(buyer.id.value, id.value), mapOf("kind" to kind.name) + (recipient?.let { mapOf("recipient" to it.id.value) } ?: emptyMap()))
    }
}
