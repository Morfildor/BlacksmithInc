package com.tinyblacksmith.core.guild

import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.content.MissionDef
import com.tinyblacksmith.core.engine.Encounters.Opt
import com.tinyblacksmith.core.engine.Encounters.PASS
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.heroes.Heroes
import com.tinyblacksmith.core.heroes.Identity
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.rng.Keyed

/**
 * Hooks in a guild era (final design 7.3, 10): the sponsored offers they post on the existing board, the visitors they
 * send through the existing morning, the concerns an ignored offer can raise and the departure three of them can
 * announce. Nothing here invents a person, an item or a reward: every offer names a real member, faction or weapon and is
 * paid through the existing quest rewards.
 */
object Hooks {
    private fun g(ctx: ResolutionContext) = ctx.guild!!

    // ---- the board (step 16, through `Missions.refreshBoard`) ----

    /** A personal offer waiting for a free slot: [key] is its cooldown key ("grudge|h4"). */
    data class Candidate(val key: String, val sponsor: HeroId, val def: MissionDef, val faction: FactionId, val weapon: WeaponId?, val reason: String, val opportunityId: String)

    /** Who has a personal reason for a quest today, most pressing first. Revalidated each morning; reads only. */
    fun candidates(ctx: ResolutionContext): List<Candidate> {
        if (!Agency.on(ctx)) return emptyList()
        val cat = GuildOps.catalog(ctx)
        val cfg = ctx.config.living
        val guild = g(ctx)
        val out = ArrayList<Candidate>()
        fun cooled(key: String) = guild.cooldowns[key]?.let { ctx.day - it < cfg.offerCooldownDays } != true
        for (m in guild.members.sortedWith(compareBy(IdOrder.numeric) { it.heroId.value })) {
            val hero = ctx.heroes[m.heroId]?.takeIf { it.isAlive } ?: continue
            if (m.status == MemberStatus.CAPTURED) continue
            val hook = hero.hook?.takeIf { it.isOpen }
            if (hook?.kind == HookKind.GRUDGE && hook.factionId != null) cat.missionById[GuildContent.HUNT]?.let { def ->
                val key = "grudge|${hero.id.value}"
                if (cooled(key)) out += Candidate(key, hero.id, def, hook.factionId, null, "${hero.fullName} has an old grievance against the ${ctx.content.faction(hook.factionId).name} and asks the guild for this hunt.", "hook_grudge")
            }
            if (hook?.kind == HookKind.LOST_WEAPON) hook.weaponId?.let { ctx.weapons[it] }?.takeIf { it.location is WeaponLocation.Lost }?.let { w ->
                val key = "heirloom|${hero.id.value}"
                val faction = Keyed.rng(ctx.base.seed, "heirloom|${ctx.era}|${hero.id.value}").pick(ctx.factions.keys.sortedBy { it.value })
                if (cooled(key)) cat.missionById[GuildContent.RECOVERY]?.let { def -> out += Candidate(key, hero.id, def, faction, w.id, "${hero.fullName} has news of the family weapon, ${w.name}: one of the ${ctx.content.faction(faction).name} carries it.", "opp_returned_blade") }
            }
            // Authored opportunities with a working route (content bible 14.2): a caravan guard's missing caravan, a noble's or deserter's public deed.
            if (hero.backgroundId == "bg_caravan_guard" && ctx.day >= 4 && guild.cooldowns["caravan|${hero.id.value}"] == null) cat.missionById[GuildContent.MISSING_CARAVAN]?.let { def ->
                val faction = ctx.factions.values.sortedBy { it.id.value }.maxByOrNull { it.pressure }!!.id
                out += Candidate("caravan|${hero.id.value}", hero.id, def, faction, null, "${hero.fullName} guarded caravans before Emberfall. A caravan has failed to arrive, and ${hero.name} knows the road it took.", "opp_missing_caravan")
            }
            if ((hero.backgroundId == "bg_exiled_noble" || hero.backgroundId == "bg_deserter") && m.missionWins >= 2 && guild.cooldowns["deed|${hero.id.value}"] == null) cat.missionById[GuildContent.HUNT]?.let { def ->
                val faction = ctx.factions.values.sortedBy { it.id.value }.maxByOrNull { it.pressure }!!.id
                out += Candidate("deed|${hero.id.value}", hero.id, def, faction, null, "${hero.fullName} wants a public deed, not pity: a risky hunt the town will hear about. Winning it adds ${ctx.config.living.deedReputation} reputation.", "opp_reputation_deed")
            }
        }
        return out
    }

    /** The name the bearer of a lost family weapon goes by on the card: one of the faction's named fighters. */
    fun holderName(ctx: ResolutionContext, faction: FactionId, weaponId: WeaponId): String {
        val names = ctx.content.faction(faction).eliteNames.ifEmpty { listOf("a captain of the ${ctx.content.faction(faction).name}") }
        return Keyed.rng(ctx.base.seed, "holder|${ctx.era}|${weaponId.value}").pick(names).removePrefix("an ").removePrefix("a ").removePrefix("the ").replaceFirstChar { it.uppercase() }
    }

    /** A member who knows the route of the sappers in a committed field with a breach (opp_powder_trail), or null. */
    fun powderScout(ctx: ResolutionContext, scenario: SiegeScenario): HeroId? {
        if (!Agency.on(ctx)) return null
        val units = ctx.content.combat?.unitById ?: return null
        if (scenario.plan.none { SiegeFight.STRUCTURAL in (units[it.unitId]?.tags ?: emptySet()) }) return null
        return GuildOps.present(ctx).mapNotNull { ctx.heroes[it.heroId] }.firstOrNull { it.backgroundId in setOf("bg_scout", "bg_militia", "bg_deserter", "bg_mason") }?.id
    }

    /** Whether an offer is a member's personal one. */
    fun sponsored(o: MissionOffer): Boolean = o.sponsorHeroId != null && o.defId != GuildContent.SABOTAGE

    /** A personal offer left the board without being taken: counted against the concern it served, once per offer, when it was a fair chance. */
    fun lapsed(ctx: ResolutionContext, o: MissionOffer) {
        if (!Agency.on(ctx) || !sponsored(o)) return
        val id = o.sponsorHeroId!!
        val m = g(ctx).member(id) ?: return
        val hero = ctx.heroes[id]?.takeIf { it.isAlive } ?: return
        // A fair chance: the member was home and fit to go, and the smith could pay the fee. A full board or a missing ingredient is never a miss.
        if (m.status != MemberStatus.HOME || GuildOps.laidUp(ctx, m) != null || ctx.gold < o.fee) return
        val kind = when (hero.hook?.kind) { HookKind.GRUDGE -> ConcernKind.GRUDGE; HookKind.LOST_WEAPON -> ConcernKind.HEIRLOOM; else -> return }
        if (o.opportunityId != "hook_grudge" && o.opportunityId != "opp_returned_blade") return
        miss(ctx, id, kind, o.id, if (kind == ConcernKind.GRUDGE) "a hunt against the ${o.factionId.value.let { ctx.content.faction(o.factionId).name }}" else "the recovery of the family weapon")
    }

    private fun miss(ctx: ResolutionContext, heroId: HeroId, kind: ConcernKind, offerId: String, remedy: String) {
        val m = g(ctx).member(heroId) ?: return
        val c = m.concern?.takeIf { it.kind == kind } ?: Concern(kind, ctx.day, remedy)
        if (offerId in c.missed) return
        val next = c.copy(missed = c.missed + offerId)
        GuildOps.updateMember(ctx, heroId) { it.copy(concern = next) }
        val hero = ctx.hero(heroId)
        if (next.missed.size == 1) ctx.emit(EventType.CONCERN_RAISED, 4, "${hero.fullName} noticed that ${remedy} was left on the board.", listOf(heroId.value), mapOf("concern" to kind.name))
    }

    // ---- step 13 ----

    fun reconcile(ctx: ResolutionContext) {
        val cfg = ctx.config.living
        // A creditor nobody answered this morning left without the money: that is a missed chance too.
        ctx.encounter?.takeIf { it.defId == CREDITOR && it.status == EncounterStatus.EXPIRED && it.day == ctx.day }?.let { i ->
            ctx.heroes[i.heroId]?.hook?.takeIf { it.isOpen }?.let { miss(ctx, i.heroId!!, ConcernKind.DEBT, i.id, "pay the ${it.amount} gold owed to ${it.creditor}") }
        }
        for (h0 in ctx.aliveHeroes()) {
            val hero = ctx.hero(h0.id)
            val hook = hero.hook?.takeIf { it.isOpen } ?: continue
            val member = g(ctx).member(hero.id)
            when (hook.kind) {
                HookKind.DEBT -> if (ctx.day >= (hook.dueDay ?: Int.MAX_VALUE)) {
                    if (hero.gold >= hook.amount) settle(ctx, hero.id, hero.fullName, paidBySmith = false)
                    else if (member != null && member.concern == null) {
                        GuildOps.updateMember(ctx, hero.id) { it.copy(concern = Concern(ConcernKind.DEBT, ctx.day, "pay the ${hook.amount} gold owed to ${hook.creditor}")) }
                        ctx.emit(EventType.CONCERN_RAISED, 5, "${hero.fullName} owes ${hook.amount} gold to ${hook.creditor}, due today, and has only ${hero.gold}. The creditor will call at the forge.", listOf(hero.id.value), mapOf("concern" to "DEBT"))
                    }
                }
                HookKind.GRUDGE -> if (member != null) {
                    val since = maxOf(hook.lastOfferDay ?: member.joinedDay, member.joinedDay)
                    if (ctx.day - since >= cfg.grudgeIdleDays && g(ctx).offers.none { it.sponsorHeroId == hero.id }) close(ctx, hero.id, HookStatus.CHANGED,
                        "${hero.fullName}'s grievance against the ${ctx.content.faction(hook.factionId!!).name} faded: ${cfg.grudgeIdleDays} days passed without a chance to answer it.")
                }
                HookKind.LOST_WEAPON -> {
                    val w = hook.weaponId?.let { ctx.weapons[it] }
                    if (w == null || w.location is WeaponLocation.Destroyed) {
                        // A failed heirloom becomes a grudge once (final design 10.5), never a recreated copy.
                        val faction = ctx.factions.keys.minByOrNull { it.value }
                        if (!hook.transitioned && faction != null) {
                            ctx.updateHero(hero.copy(hook = Hook(HookKind.GRUDGE, ctx.day, factionId = faction, transitioned = true, note = "The family weapon was destroyed.")))
                            ctx.emit(EventType.HOOK_CHANGED, 5, "${hero.fullName}'s family weapon is gone for good. What remains is a grievance against the ${ctx.content.faction(faction).name}.", listOf(hero.id.value))
                        } else close(ctx, hero.id, HookStatus.CHANGED, "${hero.fullName}'s family weapon is gone for good.")
                    }
                }
                HookKind.KIN -> {
                    val kin = hook.kinHeroId?.let { ctx.heroes[it] }
                    if (kin != null && kin.fate == HeroFate.DEAD && hero.reasons.none { it.kind == "kin_lost" }) {
                        // A loss changes the story and is remembered. It never starts a departure (final design 7.3, 10.3).
                        Identity.note(ctx, hero.id, "kin_lost", "Mourns ${kin.fullName}, who died on day ${kin.diedOnDay ?: ctx.day}.", listOf(kin.id.value))
                        close(ctx, hero.id, HookStatus.CHANGED, "${hero.fullName} mourns ${kin.fullName}.")
                    }
                }
                HookKind.OATH -> {}
            }
        }
    }

    private fun close(ctx: ResolutionContext, heroId: HeroId, status: HookStatus, text: String) {
        val hero = ctx.hero(heroId)
        ctx.updateHero(hero.copy(hook = hero.hook?.copy(status = status, note = text)))
        Identity.note(ctx, heroId, "hook", text)
        ctx.emit(EventType.HOOK_CHANGED, 4, text, listOf(heroId.value), mapOf("status" to status.name))
        clearConcern(ctx, heroId)
    }

    /** opp_unburied_oath: a member with a grievance against the Pale Procession held the wall against its unburied knight. */
    fun unburied(ctx: ResolutionContext, heroId: HeroId, captain: String): String {
        val hero = ctx.hero(heroId)
        ctx.reputation += ctx.config.living.deedReputation
        val text = "${hero.fullName} held the wall against $captain and answered the old grievance against the ${ctx.content.faction(hero.hook!!.factionId!!).name}: +${ctx.config.living.deedReputation} reputation."
        close(ctx, heroId, HookStatus.RESOLVED, text)
        if (hero.ambition == Ambition.AVENGER) Heroes.fulfilAmbition(ctx, heroId, Ambition.AVENGER)
        return text
    }

    private fun clearConcern(ctx: ResolutionContext, heroId: HeroId) {
        val m = ctx.guild?.member(heroId) ?: return
        val c = m.concern ?: return
        GuildOps.updateMember(ctx, heroId) { it.copy(concern = null) }
        if (c.departure != null) ctx.emit(EventType.DEPARTURE_ANNOUNCED, 6, "${ctx.hero(heroId).fullName} will stay with the guild: the concern was answered.", listOf(heroId.value), mapOf("cancelled" to "true"))
    }

    /** The debt is paid, by the member's own purse or by the smith; the creditor, when a resident, receives it. */
    fun settle(ctx: ResolutionContext, heroId: HeroId, payer: String, paidBySmith: Boolean) {
        val hero = ctx.hero(heroId)
        val hook = hero.hook?.takeIf { it.kind == HookKind.DEBT && it.isOpen } ?: return
        if (!paidBySmith) ctx.updateHero(hero.copy(gold = hero.gold - hook.amount))
        hook.creditorHeroId?.let { ctx.heroes[it] }?.takeIf { it.isAlive }?.let { c -> ctx.updateHero(c.copy(gold = c.gold + hook.amount)) }
        close(ctx, heroId, HookStatus.RESOLVED, "${hero.fullName}'s debt of ${hook.amount} gold to ${hook.creditor} is paid${if (paidBySmith) " by the smith" else ""}.")
    }

    /** Three missed fair chances announce a departure, at most one at a time, never before the first siege, three planning mornings ahead. */
    fun departures(ctx: ResolutionContext) {
        val cfg = ctx.config.living
        val fought = ctx.town.siegesSurvived + ctx.town.siegesLost
        for (m0 in g(ctx).members.sortedWith(compareBy(IdOrder.numeric) { it.heroId.value })) {
            val m = g(ctx).member(m0.heroId) ?: continue
            val hero = ctx.heroes[m.heroId]?.takeIf { it.isAlive } ?: continue
            val c = m.concern ?: continue
            val dep = c.departure
            if (dep == null) {
                val ready = c.missed.size >= cfg.missesForDeparture && fought >= 1 && g(ctx).members.none { it.concern?.departure != null }
                if (!ready) continue
                val day = ctx.day + 1 + cfg.departureNoticeMornings
                val committed = g(ctx).mission?.takeIf { hero.id in it.party }?.id
                GuildOps.updateMember(ctx, hero.id) { it.copy(concern = c.copy(departure = Departure(ctx.day + 1, day, c.remedy, c.missed, committed))) }
                ctx.emit(EventType.DEPARTURE_ANNOUNCED, 7, "${hero.fullName} will leave the guild on the morning of day $day unless you ${c.remedy}. ${hero.name} keeps every commitment already made.", listOf(hero.id.value), mapOf("day" to day.toString(), "concern" to c.kind.name))
                continue
            }
            // The due check uses the next morning's day: the member leaves before it (final design B3).
            if (ctx.day + 1 < dep.day) continue
            if (m.status != MemberStatus.HOME || (hero.id in g(ctx).reserved && ctx.town.nextSiegeDay > ctx.day)) continue
            depart(ctx, hero)
        }
    }

    /** The member leaves alive with what is theirs. Every loan of the smith comes home first, once; nothing is deleted. */
    private fun depart(ctx: ResolutionContext, hero: Hero) {
        GuildOps.release(ctx, hero.id, "Returned to the forge when ${hero.fullName} left the guild.")
        for (w in ctx.weapons.values.filter { it.ownerId == hero.id }.sortedWith(compareBy(IdOrder.numeric) { it.id.value })) {
            ctx.updateWeapon(w.copy(location = WeaponLocation.Lost(ctx.day, "carried away by ${hero.fullName}, who left the guild")))
            ctx.addWeaponHistory(w.id, "DEPARTED", "Carried away by ${hero.fullName}, who left the guild.", listOf(hero.id.value))
        }
        ctx.updateHero(ctx.hero(hero.id).copy(fate = HeroFate.DEPARTED, departedOnDay = ctx.day, lastActivity = HeroActivity.IDLE))
        ctx.town = ctx.town.copy(championIds = ctx.town.championIds - hero.id)
        ctx.emit(EventType.MEMBER_DEPARTED, 7, "${hero.fullName} left the guild with their own weapons and purse. Every weapon you had lent them is back in storage.", listOf(hero.id.value))
    }

    /** A personal quest won with its sponsor in the party: the Hook is answered, and the ambition tied to it with it. */
    fun won(ctx: ResolutionContext, m: MissionInstance) {
        if (!Agency.on(ctx)) return
        val sponsor = m.offer.sponsorHeroId ?: return
        if (sponsor !in m.party) return
        val hero = ctx.heroes[sponsor]?.takeIf { it.isAlive } ?: return
        when (m.offer.opportunityId) {
            "hook_grudge" -> {
                close(ctx, sponsor, HookStatus.RESOLVED, "${hero.fullName} answered the old grievance against the ${ctx.content.faction(m.offer.factionId).name}.")
                if (hero.ambition == Ambition.AVENGER) Heroes.fulfilAmbition(ctx, sponsor, Ambition.AVENGER)
            }
            "opp_reputation_deed" -> {
                ctx.reputation += ctx.config.living.deedReputation
                Identity.note(ctx, sponsor, "deed", "Won a public deed for the guild: +${ctx.config.living.deedReputation} reputation.")
            }
            "opp_missing_caravan" -> Identity.note(ctx, sponsor, "caravan", "Found the missing caravan on the road they used to guard.")
        }
    }

    /** The family weapon a party won back belongs to its family: it goes to the member, never to storage. */
    fun heirloomHome(ctx: ResolutionContext, weaponId: WeaponId, party: List<HeroId>): Boolean {
        val owner = ctx.aliveHeroes().firstOrNull { it.hook?.kind == HookKind.LOST_WEAPON && it.hook.weaponId == weaponId && it.hook.isOpen } ?: return false
        val w = ctx.weapon(weaponId)
        ctx.addWeaponHistory(weaponId, "RECOVERED", "Won back by the guild's party and returned to ${owner.fullName}, whose family it was.", party.map { it.value } + owner.id.value)
        Market.giveAndEquip(ctx, owner, ctx.weapon(weaponId).copy(location = WeaponLocation.Storage))
        close(ctx, owner.id, HookStatus.RESOLVED, "${owner.fullName} has the family weapon, ${w.name}, back.")
        if (owner.ambition == Ambition.HEIRLOOM) Heroes.fulfilAmbition(ctx, owner.id, Ambition.HEIRLOOM)
        return true
    }

    /** Whether a lost weapon is a member's or resident's family weapon with an open Hook: the board keeps its recovery. */
    fun isHookWeapon(ctx: ResolutionContext, weaponId: WeaponId?): Boolean =
        weaponId != null && ctx.heroes.values.any { it.isAlive && it.hook?.kind == HookKind.LOST_WEAPON && it.hook.weaponId == weaponId && it.hook.isOpen }

    /** A new assignment that would keep a departing member past their date is refused, unless it is the remedy (final design 10.4). */
    fun departureBlocks(ctx: ResolutionContext, heroId: HeroId, lastDay: Int, remedy: Boolean): String? {
        val dep = ctx.guild?.member(heroId)?.concern?.departure ?: return null
        if (remedy || lastDay < dep.day - 1) return null
        return "leaves the guild on the morning of day ${dep.day} and could not finish this before then"
    }

    // ---- visitors (through `GuildVisitors`) ----

    const val CREDITOR = "creditor_calls"
    const val LESSON = "a_lesson"
    const val WOUNDED_FRIEND = "wounded_friend"

    /** The member whose debt is due and unpaid: the creditor calls for them first thing (a priority visitor, like a follow-up). */
    fun debtor(ctx: ResolutionContext): Hero? = if (!Agency.on(ctx)) null else ctx.aliveHeroes().firstOrNull { h ->
        g(ctx).member(h.id)?.concern?.kind == ConcernKind.DEBT && h.hook?.let { it.kind == HookKind.DEBT && it.isOpen } == true && (ctx.eventLastDay[com.tinyblacksmith.core.engine.Encounters.COUNTER_PREFIX + CREDITOR] ?: -9) < ctx.day - 1
    }

    fun mentorPair(ctx: ResolutionContext): Pair<Hero, Hero>? {
        if (!Agency.on(ctx)) return null
        val home = GuildOps.present(ctx).mapNotNull { ctx.heroes[it.heroId] }.filter { g(ctx).planned?.heroIds?.contains(it.id) != true }
        val pupil = home.filter { it.level <= 3 }.minWithOrNull(compareBy<Hero> { it.level }.thenBy(IdOrder.numeric) { it.id.value }) ?: return null
        val mentor = home.filter { it.id != pupil.id && it.level >= pupil.level + 2 }.maxWithOrNull(compareBy<Hero> { it.level }.thenByDescending(IdOrder.numeric) { it.id.value }) ?: return null
        return mentor to pupil
    }

    fun woundedFriend(ctx: ResolutionContext): Pair<Hero, Hero>? {
        if (!Agency.on(ctx)) return null
        for (m in g(ctx).members) {
            val wound = m.wound?.takeIf { it.kind == WoundKind.INJURED && it.untilDay > ctx.day + 1 } ?: continue
            val friend = g(ctx).members.firstOrNull { f -> f.status == MemberStatus.HOME && f.bonds.any { it.withHeroId == m.heroId } } ?: continue
            return (ctx.heroes[friend.heroId] ?: continue) to (ctx.heroes[m.heroId] ?: continue)
        }
        return null
    }

    fun eligible(ctx: ResolutionContext, defId: String): Boolean = when (defId) {
        LESSON -> mentorPair(ctx) != null
        WOUNDED_FRIEND -> woundedFriend(ctx) != null
        else -> false   // the creditor only ever comes as a priority caller
    }

    fun build(ctx: ResolutionContext, defId: String, id: String): EncounterInstance = when (defId) {
        CREDITOR -> debtor(ctx)!!.let { h -> EncounterInstance(id, defId, ctx.day, heroId = h.id, amounts = mapOf("amount" to h.hook!!.amount)) }
        LESSON -> mentorPair(ctx)!!.let { (a, b) -> EncounterInstance(id, defId, ctx.day, heroId = a.id, otherHeroId = b.id, amounts = mapOf("xp" to ctx.config.living.lessonXp)) }
        WOUNDED_FRIEND -> woundedFriend(ctx)!!.let { (a, b) -> EncounterInstance(id, defId, ctx.day, heroId = a.id, otherHeroId = b.id, amounts = mapOf("fee" to ctx.config.living.healerFee)) }
        else -> error("No offer for $defId")
    }

    fun text(ctx: ResolutionContext, i: EncounterInstance): String {
        val a = ctx.heroes[i.heroId]?.fullName ?: "someone"
        val b = ctx.heroes[i.otherHeroId]?.fullName ?: "someone"
        return when (i.defId) {
            CREDITOR -> "${ctx.heroes[i.heroId]?.hook?.creditor ?: "A creditor"} is at the door about the ${i.amounts["amount"]} gold $a owes. $a cannot pay it."
            LESSON -> "$a offers to spend today teaching $b. They would train together instead of going anywhere."
            WOUNDED_FRIEND -> "$a asks whether the guild can pay a healer for $b, who is injured."
            else -> ""
        }
    }

    internal fun options(ctx: ResolutionContext, i: EncounterInstance): List<Opt> {
        val n = i.amounts
        val a = ctx.heroes[i.heroId]
        val b = ctx.heroes[i.otherHeroId]
        return when (i.defId) {
            CREDITOR -> listOf(
                Opt("pay", "Pay ${n["amount"]} gold for ${a?.name}", "The debt is settled for good, and ${a?.name}'s concern with it.", gold = n["amount"] ?: 0,
                    blocked = if (a?.hook?.let { it.kind == HookKind.DEBT && it.isOpen } != true) "The debt is already settled." else null) { c ->
                    settle(c, i.heroId!!, "the smith", paidBySmith = true); "The smith paid ${a?.fullName}'s debt."
                },
                Opt(PASS, "Let ${a?.name} settle it", "Nothing is paid today. ${a?.name} counts it as help not given.") { c ->
                    miss(c, i.heroId!!, ConcernKind.DEBT, i.id, "pay the ${n["amount"]} gold owed to ${a?.hook?.creditor}"); "The creditor left without the money."
                },
            )
            LESSON -> listOf(
                Opt("teach", "Let them train together today", "${b?.name} gains ${n["xp"]} experience and learns from ${a?.name}. Both stay home today: neither can join a quest or stand on the wall tonight.",
                    blocked = listOfNotNull(a, b).firstNotNullOfOrNull { h -> ctx.guild?.member(h.id)?.let { GuildOps.unavailable(ctx, it) }?.let { "${h.name} $it." } }) { c ->
                    for (h in listOfNotNull(a, b)) GuildOps.updateMember(c, h.id) { it.copy(wound = Wound(WoundKind.EXHAUSTED, c.day + 1, "Spent the day on a lesson.")) }
                    c.guild = c.guild!!.copy(reserved = c.guild!!.reserved - listOfNotNull(a?.id, b?.id).toSet())
                    Heroes.grantXp(c, c.hero(b!!.id).copy(mentorName = b.mentorName ?: a!!.fullName), n["xp"] ?: 0)
                    Identity.note(c, b.id, "lesson", "Trained with ${a!!.fullName}.")
                    "${a.fullName} spent the day teaching ${b.fullName}."
                },
                Opt(PASS, "Not today", "Nothing changes.") { "There was no lesson today." },
            )
            WOUNDED_FRIEND -> listOf(
                Opt("healer", "Pay ${n["fee"]} gold for a healer", "${b?.name}'s injury ends one day sooner.", gold = n["fee"] ?: 0,
                    blocked = if (ctx.guild?.member(i.otherHeroId!!)?.wound?.kind != WoundKind.INJURED) "${b?.name} is no longer injured." else null) { c ->
                    GuildOps.updateMember(c, i.otherHeroId!!) { m -> m.copy(wound = m.wound?.let { w -> w.copy(untilDay = maxOf(c.day + 1, w.untilDay - 1)) }) }
                    "A healer saw to ${b?.fullName}."
                },
                Opt(PASS, "Not today", "Nothing changes.") { "No healer was called." },
            )
            else -> emptyList()
        }
    }
}
