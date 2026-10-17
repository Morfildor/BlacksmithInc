package com.tinyblacksmith.core.guild

import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.battle.Power
import com.tinyblacksmith.core.combat.EventKind
import com.tinyblacksmith.core.combat.FightResult
import com.tinyblacksmith.core.content.Element
import com.tinyblacksmith.core.content.FactionDef
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.content.SpecialityDef
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.heroes.Heroes
import com.tinyblacksmith.core.heroes.Identity
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.rng.Keyed

/**
 * What a member decides for themselves in a living-heroes guild era (final design 8, 9): a visit to the smith's counter
 * with their own purse, an announced return of a loan for a weapon of their own, and the Speciality their deeds open,
 * chosen after a lean the smith can see. Every decision reads real records and is written down with its reason. Draws are
 * keyed to the member and the day; no gameplay stream moves. A legacy era never reaches any of it.
 */
object Agency {
    fun on(ctx: ResolutionContext): Boolean = ctx.living && ctx.guild != null

    // ---- shared reads ----

    /** Whether [hero]'s oath allows [weapon] (final design 7.3). No oath, or no weapon, allows anything. */
    fun oathAllows(hero: Hero, weapon: Weapon?): Boolean {
        val hook = hero.hook?.takeIf { it.kind == HookKind.OATH && it.isOpen } ?: return true
        if (weapon == null) return true
        return when (hook.oath) {
            OathKind.PLAIN_WORK -> weapon.rarity <= Rarity.UNCOMMON
            OathKind.NO_GRAVE -> weapon.element != Element.GRAVE
            OathKind.FAVOURED_FAMILY -> weapon.familyId == hook.familyId
            null -> true
        }
    }

    /** The besieger a member weighs weapons against: the committed one, once the siege's field is known. */
    fun knownThreat(ctx: ResolutionContext): FactionDef? = ctx.siege?.takeIf { it.siegeDay == ctx.town.nextSiegeDay }?.factionId?.let { ctx.content.faction(it) }

    /** Attachment to [weapon] from what [hero] and it really shared (final design 8.2): sieges held, a recovery, its fame. Bounded. */
    fun attachment(ctx: ResolutionContext, hero: Hero, weapon: Weapon?): Double {
        if (weapon == null) return 0.0
        val cfg = ctx.config.living
        val sieges = weapon.history.count { it.kind == "SIEGE" && hero.id.value in it.subjectIds }.coerceAtMost(cfg.attachmentSiegeCap)
        val recovered = weapon.history.any { it.kind == "RECOVERED" && hero.id.value in it.subjectIds }
        return sieges * cfg.attachmentPerSiege + (if (recovered) cfg.attachmentRecovered else 0.0) + weapon.fame.coerceIn(0, cfg.attachmentFameCap) * cfg.attachmentPerFame
    }

    fun value(ctx: ResolutionContext, hero: Hero, weapon: Weapon?, threat: FactionDef?): Double = Market.valueInHand(ctx, hero, weapon, threat)

    private fun ownedLegal(ctx: ResolutionContext, hero: Hero): List<Weapon> =
        ctx.weapons.values.filter { it.ownerId == hero.id && oathAllows(hero, it) }.sortedWith(compareBy(IdOrder.numeric) { it.id.value })

    private fun members(ctx: ResolutionContext): List<GuildMember> = ctx.guild?.members.orEmpty().sortedWith(compareBy(IdOrder.numeric) { it.heroId.value })

    // ---- the counter (step 6, after the town's visitors) ----

    /** Why [m] would come to the counter today, or null: no plausible need, no visit (final design 8.1). */
    fun need(ctx: ResolutionContext, m: GuildMember): String? {
        val hero = ctx.heroes[m.heroId]?.takeIf { it.isAlive } ?: return null
        val loan = GuildOps.loanOf(ctx, hero.id)
        val own = ctx.equippedWeapon(hero.id)
        val threat = knownThreat(ctx)
        return when {
            loan != null && !oathAllows(hero, loan) -> "oath"
            loan == null && own == null -> "unarmed"
            loan == null && own != null && own.condition < ctx.config.wornConditionThreshold -> "worn"
            loan == null && own != null && !oathAllows(hero, own) -> "oath"
            hero.want != null -> "want"
            threat != null && threat.weakTo != null && (loan ?: own)?.element != threat.weakTo -> "threat"
            else -> null
        }
    }

    /**
     * One member a day may come to the counter after the town's visitors (final design 8.1): alive, at home, not captured,
     * not on tonight's wall. The member with a need who was considered least recently comes, then by ID. They buy with their
     * own gold, trading in only a weapon they own, never a loan; the sale is the smith's, through the ordinary purchase.
     */
    fun counter(ctx: ResolutionContext) {
        if (!on(ctx) || ctx.weapons.values.none { it.isListed }) return
        val g = ctx.guild!!
        val tonight = ctx.town.nextSiegeDay == ctx.day
        val cfg = ctx.config
        val eligible = members(ctx).filter { m ->
            m.status == MemberStatus.HOME && ctx.heroes[m.heroId]?.isAlive == true && !(tonight && m.heroId in g.reserved) && m.concern?.departure == null && need(ctx, m) != null
        }.sortedWith(compareBy<GuildMember> { it.counterDay }.thenBy(IdOrder.numeric) { it.heroId.value })
        for (m in eligible.take(cfg.living.memberVisitsPerDay)) {
            GuildOps.updateMember(ctx, m.heroId) { it.copy(counterDay = ctx.day) }
            val hero = ctx.hero(m.heroId)
            val why = need(ctx, m)
            val rng = Keyed.rng(ctx.base.seed, "counter|${ctx.era}|${ctx.day}|${hero.id.value}")
            val current = ctx.equippedWeapon(hero.id)
            val friction = attachment(ctx, hero, current)
            val shelf = ctx.weapons.values.filter { it.isListed }.sortedWith(compareBy(IdOrder.numeric) { it.id.value })
            val evaluations = shelf.map { w -> w to Market.evaluate(ctx, hero, current, w, rng.nextDouble()) }.filter { (w, _) -> oathAllows(hero, w) }
            val best = evaluations.filter { it.second.eligible }.maxByOrNull { it.second.utility - friction }
            if (best != null && best.second.utility - friction >= cfg.purchaseUtilityThreshold) {
                val (w, e) = best
                val mark = ctx.newEvents.size
                val sale = Market.purchase(ctx, hero, w, e.pays, asked = w.listedPrice.takeIf { e.offered })
                ctx.visits += MarketVisit(hero.id, hero.fullName, w.id, if (e.countersThreat) VisitReason.COUNTERS_THREAT else if (e.worn) VisitReason.WORN_OUT else VisitReason.GOOD_ENOUGH,
                    seq = ctx.visits.size, kind = VisitKind.MEMBER, sale = sale, eventIds = ctx.newEvents.drop(mark).map { it.id })
                onLoanChanged(ctx, hero.id)   // a traded-in weapon may have been the one a return intent named
                val loan = GuildOps.loanOf(ctx, hero.id)
                val tail = if (loan != null) " ${hero.name} keeps fighting with your loan, ${loan.name}, for now." else ""
                Identity.note(ctx, hero.id, "bought", "Bought ${w.name} for ${e.pays} gold of their own.$tail", listOf(w.id.value))
                ctx.emit(EventType.MEMBER_PURCHASE, 4, "${hero.fullName}, a member of your guild, bought ${w.name} with their own gold (${reasonWords(why)}).$tail", listOf(hero.id.value, w.id.value), mapOf("price" to e.pays.toString(), "need" to (why ?: "")))
            } else {
                val h = ctx.hero(hero.id)
                ctx.updateHero(h.copy(want = Market.wantOf(ctx, h, current, evaluations.map { it.second })))
            }
        }
    }

    private fun reasonWords(why: String?): String = when (why) {
        "oath" -> "their oath forbids the weapon they carry"
        "unarmed" -> "they had no weapon of their own"
        "worn" -> "their own weapon was worn out"
        "want" -> "they had been looking for one"
        "threat" -> "against the coming siege"
        else -> "a better fit"
    }

    // ---- loan returns (step 13) ----

    /** Why [hero] would hand back [loan] for a weapon of their own, and which: the first reason that holds, from real values. */
    fun returnReason(ctx: ResolutionContext, hero: Hero, loan: Weapon): Pair<ReturnReason, Weapon>? {
        val cfg = ctx.config.living
        val threat = knownThreat(ctx)
        val owned = ownedLegal(ctx, hero).filter { it.condition >= cfg.returnWornCondition }
        if (owned.isEmpty()) return null
        val best = owned.maxWithOrNull(compareBy<Weapon> { value(ctx, hero, it, threat) + attachment(ctx, hero, it) }.thenBy(IdOrder.numeric) { it.id.value })!!
        val vLoan = value(ctx, hero, loan, threat)
        val vOwn = value(ctx, hero, best, threat)
        return when {
            !oathAllows(hero, loan) -> ReturnReason.OATH to best
            loan.condition < cfg.returnWornCondition && best.condition >= ctx.config.wornConditionThreshold -> ReturnReason.WORN_LOAN to best
            threat?.weakTo != null && best.element == threat.weakTo && loan.element != threat.weakTo && vOwn * 100 >= vLoan * 90 -> ReturnReason.THREAT to best
            Power.classFit(hero, best, ctx.content, ctx.config) > Power.classFit(hero, loan, ctx.content, ctx.config) && vOwn >= vLoan -> ReturnReason.CLASS_FIT to best
            (vOwn + attachment(ctx, hero, best)) * 100 >= vLoan * cfg.returnStrongerPercent -> ReturnReason.STRONGER to best
            else -> null
        }
    }

    fun returnWords(reason: ReturnReason, owned: Weapon, threat: FactionDef?): String = when (reason) {
        ReturnReason.OATH -> "their oath forbids the loan"
        ReturnReason.WORN_LOAN -> "the loan is worn out and ${owned.name} is sound"
        ReturnReason.THREAT -> "${owned.name} answers the ${threat?.name ?: "attackers"}, who are weak to ${owned.element?.name?.lowercase() ?: "it"}"
        ReturnReason.CLASS_FIT -> "${owned.name} suits their class better"
        ReturnReason.STRONGER -> "${owned.name} is stronger in their hands"
    }

    /** Where [m] is held by a commitment a return may not touch: away, captured, or kept for a siege still to come. */
    private fun committed(ctx: ResolutionContext, m: GuildMember): Boolean =
        m.status != MemberStatus.HOME || (m.heroId in ctx.guild!!.reserved && ctx.town.nextSiegeDay > ctx.day)

    private fun loanReturns(ctx: ResolutionContext) {
        for (m0 in members(ctx)) {
            val m = ctx.guild!!.member(m0.heroId) ?: continue
            val hero = ctx.heroes[m.heroId]?.takeIf { it.isAlive } ?: continue
            val loan = GuildOps.loanOf(ctx, hero.id)
            val intent = m.loanReturn
            if (loan == null) { if (intent != null) GuildOps.updateMember(ctx, hero.id) { it.copy(loanReturn = null) }; continue }
            if (intent != null && ctx.day >= intent.resolveDay) {
                if (committed(ctx, m)) continue   // it waits for the commitment to resolve; the return never touches a frozen fight
                val now = returnReason(ctx, hero, loan)
                if (now == null) {
                    GuildOps.updateMember(ctx, hero.id) { it.copy(loanReturn = null) }
                    ctx.emit(EventType.LOAN_RETURN_ANNOUNCED, 3, "${hero.fullName} will keep fighting with ${loan.name}: the reason to hand it back is gone.", listOf(hero.id.value, loan.id.value), mapOf("cancelled" to "true"))
                    continue
                }
                val (reason, owned) = now
                ctx.updateWeapon(loan.copy(location = WeaponLocation.Storage))
                ctx.addWeaponHistory(loan.id, "LOAN_RETURNED", "Handed back by ${hero.fullName}, who preferred ${owned.name}.", listOf(hero.id.value))
                ctx.equippedWeapon(hero.id)?.takeIf { it.id != owned.id }?.let { ctx.updateWeapon(it.copy(location = WeaponLocation.Owned(hero.id, equipped = false))) }
                ctx.updateWeapon(ctx.weapon(owned.id).copy(location = WeaponLocation.Owned(hero.id, equipped = true)))
                GuildOps.updateMember(ctx, hero.id) { it.copy(loanReturn = null) }
                val text = "${hero.fullName} returned your loan, ${loan.name}, to storage and now fights with ${owned.name} (${returnWords(reason, owned, knownThreat(ctx))})."
                Identity.note(ctx, hero.id, "loan_returned", text, listOf(loan.id.value, owned.id.value))
                ctx.emit(EventType.LOAN_RETURNED, 5, text, listOf(hero.id.value, loan.id.value, owned.id.value), mapOf("reason" to reason.name))
            } else if (intent == null) {
                val (reason, owned) = returnReason(ctx, hero, loan) ?: continue
                // Shown on the next morning, resolved at that day's End Day at the earliest: one whole planning day to answer it.
                GuildOps.updateMember(ctx, hero.id) { it.copy(loanReturn = LoanReturnIntent(loan.id, owned.id, reason, ctx.day + 1, ctx.day + 1)) }
                ctx.emit(EventType.LOAN_RETURN_ANNOUNCED, 4, "${hero.fullName} would rather fight with ${owned.name} than with your loan, ${loan.name}: ${returnWords(reason, owned, knownThreat(ctx))}. Unless you lend something better, the loan returns to storage after the commitments of day ${ctx.day + 1}.",
                    listOf(hero.id.value, loan.id.value, owned.id.value), mapOf("reason" to reason.name, "day" to (ctx.day + 1).toString()))
            }
        }
    }

    /** A new loan or a recall answers a return intent at once: the intent follows the loan the member now holds, or ends. */
    fun onLoanChanged(ctx: ResolutionContext, heroId: HeroId) {
        if (!on(ctx)) return
        val m = ctx.guild!!.member(heroId) ?: return
        val intent = m.loanReturn ?: return
        val hero = ctx.heroes[heroId] ?: return
        val loan = GuildOps.loanOf(ctx, heroId)
        val still = loan?.let { returnReason(ctx, hero, it) }
        if (still == null) {
            GuildOps.updateMember(ctx, heroId) { it.copy(loanReturn = null) }
            if (loan != null) ctx.emit(EventType.LOAN_RETURN_ANNOUNCED, 3, "${hero.fullName} is happy with the new loan, ${loan.name}, and will keep it.", listOf(heroId.value, loan.id.value), mapOf("cancelled" to "true"))
        } else GuildOps.updateMember(ctx, heroId) { it.copy(loanReturn = intent.copy(loanId = loan.id, ownedId = still.second.id, reason = still.first)) }
    }

    // ---- evidence (steps 8 and 12) ----

    private fun bump(ctx: ResolutionContext, heroId: HeroId, keys: Set<String>) {
        if (keys.isEmpty()) return
        val cap = ctx.config.living.evidenceCap
        GuildOps.updateMember(ctx, heroId) { m -> m.copy(evidence = m.evidence + keys.associateWith { k -> minOf(cap, (m.evidence[k] ?: 0) + 1) }) }
    }

    /** One fight's evidence for each fighter of [order]: each family at most once per fight, whatever happened in it. */
    fun afterFight(ctx: ResolutionContext, order: List<HeroId>, fight: FightResult?, won: Boolean, siege: Boolean) {
        if (!on(ctx)) return
        for ((slot, id) in order.withIndex()) {
            if (ctx.guild!!.member(id) == null) continue
            val a = fight?.actor(id.value)
            val keys = buildSet {
                if (won) add(if (siege) "held" else "won")
                if (slot == 0 && a?.downed != true) add("front")
                if (a?.downed == true) add("downed")
                if (fight?.events?.any { it.kind == EventKind.DOWNED && it.source == id.value && fight.actor(it.target ?: "")?.side == com.tinyblacksmith.core.combat.Side.ENEMY } == true) add("struck")
                if (fight?.events?.any { (it.kind == EventKind.HEALED || it.kind == EventKind.GUARD_GAINED) && it.source == id.value && it.target != id.value } == true) add("shielded")
                if (won && order.size >= 2) add("together")
            }
            bump(ctx, id, keys)
        }
    }

    // ---- Specialities (step 13) ----

    private val inclinations: Map<String, Set<TraitId>> = mapOf(
        "vanguard" to setOf(LaunchContent.CAUTIOUS, LaunchContent.LOYAL, LaunchContent.PATIENT),
        "striker" to setOf(LaunchContent.BRAVE, LaunchContent.AMBITIOUS, LaunchContent.RESTLESS),
        "unbroken" to setOf(LaunchContent.BRAVE, LaunchContent.VAIN),
        "careful" to setOf(LaunchContent.CAUTIOUS, LaunchContent.PATIENT, LaunchContent.CURIOUS),
        "wallwarden" to setOf(LaunchContent.LOYAL, LaunchContent.CAUTIOUS),
        "rallier" to setOf(LaunchContent.LOYAL, LaunchContent.AMBITIOUS, LaunchContent.GREEDY),
    )
    private val evidenceFor: Map<String, List<String>> = mapOf(
        "vanguard" to listOf("front", "held"), "striker" to listOf("struck"), "unbroken" to listOf("downed", "front"),
        "careful" to listOf("shielded"), "wallwarden" to listOf("held", "front"), "rallier" to listOf("together", "shielded"),
    )

    /** What [hero]'s record says for [def]: capped evidence, a support weapon for the steady branches, and their Personality. */
    fun score(ctx: ResolutionContext, m: GuildMember, hero: Hero, def: SpecialityDef): Int {
        val weapon = GuildOps.weaponOf(ctx, hero.id)
        val support = weapon?.let { (ctx.content.combat?.familyById?.get(it.familyId)?.supportPercent ?: 100) > 100 } == true
        val heavy = weapon?.let { (ctx.content.combat?.familyById?.get(it.familyId)?.strikePercent ?: 100) > 100 } == true
        val ev = evidenceFor[def.id].orEmpty().sumOf { m.evidence[it] ?: 0 }
        val tool = when (def.id) { "careful", "rallier" -> if (support) 1 else 0; "striker" -> if (heavy) 1 else 0; else -> 0 }
        // The class is the role the member plays every fight: a Guardian or a Warden holds and shelters, the others strike.
        val holds = hero.classId == LaunchContent.GUARDIAN || hero.classId == LaunchContent.WARDEN
        val role = when (def.id) { "vanguard", "wallwarden", "careful" -> if (holds) 2 else 0; "striker", "unbroken", "rallier" -> if (holds) 0 else 1; else -> 0 }
        return ev + tool + role + hero.traits.count { it in inclinations[def.id].orEmpty() }
    }

    /** The evidence that most supports [def], in words, from the counts themselves. */
    fun reasonFor(m: GuildMember, def: SpecialityDef): String {
        val (k, n) = evidenceFor[def.id].orEmpty().map { it to (m.evidence[it] ?: 0) }.maxByOrNull { it.second } ?: return "by temperament"
        if (n == 0) return "by temperament"
        return when (k) {
            "front" -> "has stood first in the line in $n ${if (n == 1) "fight" else "fights"}"
            "held" -> "has held the wall in $n ${if (n == 1) "siege" else "sieges"}"
            "struck" -> "has defeated enemies in $n ${if (n == 1) "fight" else "fights"}"
            "won" -> "has won $n ${if (n == 1) "quest" else "quests"}"
            "downed" -> "has gone down and come back $n ${if (n == 1) "time" else "times"}"
            "shielded" -> "has healed or armored allies in $n ${if (n == 1) "fight" else "fights"}"
            "together" -> "has won $n ${if (n == 1) "fight" else "fights"} beside others"
            else -> "by temperament"
        }
    }

    private fun leans(ctx: ResolutionContext) {
        val cat = GuildOps.catalog(ctx)
        for (m0 in members(ctx)) {
            val m = ctx.guild!!.member(m0.heroId) ?: continue
            val hero = ctx.heroes[m.heroId]?.takeIf { it.isAlive } ?: continue
            if (m.specialityId != null) continue
            val lean = m.lean
            if (lean == null) {
                val open = Stories.openBranches(ctx, m).ifEmpty { continue }
                val deed = open.first().deed
                val options = open.filter { it.deed == deed }
                val top = options.maxWithOrNull(compareBy<SpecialityDef> { score(ctx, m, hero, it) }.thenByDescending { options.indexOf(it) })!!
                GuildOps.updateMember(ctx, hero.id) { it.copy(lean = SpecialityLean(deed, options.map { o -> o.id }, top.id, reasonFor(m, top), ctx.day + 1, ctx.day + 1)) }
                ctx.emit(EventType.SPECIALITY_LEAN, 5, "${hero.fullName} has earned a Speciality: ${options.joinToString(" or ") { it.name }}. ${hero.name} is leaning to ${top.name}, having ${reasonFor(m, top).removePrefix("has ")}. The choice is made at the End Day of day ${ctx.day + 1} or later, once ${hero.name} is home.",
                    listOf(hero.id.value), mapOf("lean" to top.id, "day" to (ctx.day + 1).toString()))
                continue
            }
            val options = lean.options.mapNotNull { cat.speciality(it) }
            if (options.isEmpty()) { GuildOps.updateMember(ctx, hero.id) { it.copy(lean = null) }; continue }
            val scored = options.map { it to score(ctx, m, hero, it) }.sortedWith(compareByDescending<Pair<SpecialityDef, Int>> { it.second }.thenBy { options.indexOf(it.first) })
            if (ctx.day < lean.resolveDay || m.status != MemberStatus.HOME) {
                // The lean follows the record until the choice: a new quest or weapon can turn a close one.
                if (scored.first().first.id != lean.leaningTo) GuildOps.updateMember(ctx, hero.id) { it.copy(lean = lean.copy(leaningTo = scored.first().first.id, reason = reasonFor(m, scored.first().first))) }
                continue
            }
            val sure = scored.size < 2 || scored[0].second - scored[1].second >= ctx.config.living.leanSureMargin
            val chosen = if (sure) scored[0].first
                else Keyed.rng(ctx.base.seed, "speciality|${ctx.era}|${hero.id.value}").pickWeighted(scored.map { it.first to (it.second + 1).toDouble() })
            GuildOps.updateMember(ctx, hero.id) { it.copy(specialityId = chosen.id, lean = null) }
            val why = reasonFor(m, chosen)
            val text = "${hero.fullName} chose the Speciality ${chosen.name}, having ${why.removePrefix("has ")}. ${chosen.description}"
            Identity.note(ctx, hero.id, "speciality", "Chose ${chosen.name}, having ${why.removePrefix("has ")}.")
            ctx.emit(EventType.SPECIALITY_CHOSEN, 6, text, listOf(hero.id.value), mapOf("speciality" to chosen.id))
        }
    }

    // ---- ambitions of the guild ----

    /** A won quest led from the front: the Captain ambition counts it. Nobody dies or is captured in a won fight. */
    fun ledHome(ctx: ResolutionContext, party: List<HeroId>) {
        if (!on(ctx)) return
        val lead = party.firstOrNull { ctx.guild!!.member(it) != null } ?: return
        GuildOps.updateMember(ctx, lead) { it.copy(ledWins = it.ledWins + 1) }
        val hero = ctx.hero(lead)
        if (hero.ambition == Ambition.CAPTAIN && !hero.ambitionDone && ctx.guild!!.member(lead)!!.ledWins >= 3) Heroes.fulfilAmbition(ctx, lead, Ambition.CAPTAIN)
    }

    /** A weapon earned a deed: whoever carries it and wants to make a name has done so. */
    fun onDeed(ctx: ResolutionContext, weaponId: WeaponId) {
        if (!on(ctx)) return
        val w = ctx.weapons[weaponId] ?: return
        val holder = w.loanedTo ?: w.ownerId?.takeIf { w.isEquipped } ?: return
        val hero = ctx.heroes[holder]?.takeIf { it.isAlive && it.ambition == Ambition.NAME && !it.ambitionDone } ?: return
        if (ctx.guild?.member(holder) != null) Heroes.fulfilAmbition(ctx, hero.id, Ambition.NAME)
    }

    // ---- step 13 ----

    /** End Day step 13 for the members: Hooks and concerns, leans and choices, loan returns, departures. After retirements and reconciliation. */
    fun reconcile(ctx: ResolutionContext) {
        if (!on(ctx) || ctx.phase == Phase.ENDED) return
        Hooks.reconcile(ctx)
        leans(ctx)
        loanReturns(ctx)
        Hooks.departures(ctx)
    }
}
