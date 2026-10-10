package com.tinyblacksmith.core.guild

import com.tinyblacksmith.core.content.Depth
import com.tinyblacksmith.core.content.EncounterDef
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.engine.Encounters.Opt
import com.tinyblacksmith.core.engine.Encounters.PASS
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.heroes.Heroes
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.rng.Rng

/**
 * The morning visitors a guild brings (spec 11.3 to 11.5), in the visitor framework that already exists: the same
 * stored instance, the same command, the same free default. Each is about somebody or something real in this run
 * (a captive, a blade in enemy hands, a loan, a sentient blade) and is never offered in a run without a guild.
 * `EncounterCatalog` hands over to this object for the IDs it does not know.
 */
internal object GuildVisitors {
    const val HISTORY_OATH = "OATH"
    const val HISTORY_SILENCED = "SILENCED"
    const val HISTORY_INSURED = "INSURED"

    private fun pass(label: String, told: String) = Opt(PASS, label, "Nothing changes.") { told }
    private fun name(ctx: ResolutionContext, id: HeroId?): String = ctx.heroes[id]?.fullName ?: "someone"

    private fun captive(ctx: ResolutionContext): Captive? = ctx.guild?.captives?.firstOrNull { c -> ctx.guild!!.mission?.offer?.subjectHeroId != c.heroId && ctx.day < c.deadlineDay - 1 }
    private fun survivor(ctx: ResolutionContext): GuildMember? = GuildOps.present(ctx).firstOrNull()
    private fun sentient(ctx: ResolutionContext): Weapon? = ctx.weapons.values.filter { w ->
        LaunchContent.SENTIENT in w.flaws && (w.isInStorage || w.isListed || (w.isLoaned && ctx.guild?.member(w.loanedTo!!)?.status == MemberStatus.HOME)) && w.promisedTo == null &&
            w.history.none { it.kind == HISTORY_OATH || it.kind == HISTORY_SILENCED }
    }.minWithOrNull(compareBy(IdOrder.numeric) { it.id.value })
    private fun insurable(ctx: ResolutionContext): Weapon? =
        if (ctx.consequences.any { it.kind == ConsequenceKind.INSURANCE }) null
        else ctx.weapons.values.filter { it.isLoaned && ctx.guild?.member(it.loanedTo!!)?.status == MemberStatus.HOME }.maxWithOrNull(compareBy<Weapon> { Market.askingPrice(it, ctx.config) }.thenByDescending(IdOrder.numeric) { it.id.value })

    fun eligible(ctx: ResolutionContext, def: EncounterDef): Boolean {
        if (ctx.guild == null) return false
        return when (def.id) {
            GuildContent.COWARD_RETURNS -> captive(ctx) != null && survivor(ctx) != null
            GuildContent.SWORD_COMPLAINT -> sentient(ctx) != null
            GuildContent.ACROSS_THE_COUNTER -> ctx.guild!!.nemesis?.let { n -> ctx.guild!!.mission?.offer?.subjectWeaponId != n.weaponId && ctx.weapons[n.weaponId] != null } == true
            GuildContent.INSURANCE_ADJUSTER -> insurable(ctx) != null
            else -> false
        }
    }

    fun build(ctx: ResolutionContext, def: EncounterDef, @Suppress("UNUSED_PARAMETER") rng: Rng, id: String): EncounterInstance {
        val cfg = ctx.config.guild
        val base = EncounterInstance(id, def.id, ctx.day)
        return when (def.id) {
            GuildContent.COWARD_RETURNS -> captive(ctx)!!.let { c -> base.copy(heroId = c.heroId, otherHeroId = survivor(ctx)!!.heroId, weaponId = c.weaponId, amounts = mapOf("ransom" to cfg.captiveTimeGold, "days" to cfg.captiveTimeDays, "ease" to cfg.questionedPercent)) }
            GuildContent.SWORD_COMPLAINT -> sentient(ctx)!!.let { w -> base.copy(weaponId = w.id, materialId = ctx.content.materials(MaterialCategory.CATALYST).minBy { it.price }.id, amounts = mapOf("energy" to cfg.silenceEnergy, "level" to cfg.oathMaxLevel)) }
            GuildContent.ACROSS_THE_COUNTER -> ctx.guild!!.nemesis!!.let { n -> base.copy(weaponId = n.weaponId, amounts = mapOf("price" to Market.askingPrice(ctx.weapon(n.weaponId), ctx.config) * cfg.buyBackPercent / 100, "ease" to cfg.questionedPercent)) }
            GuildContent.INSURANCE_ADJUSTER -> insurable(ctx)!!.let { w ->
                val worth = Market.askingPrice(w, ctx.config)
                base.copy(weaponId = w.id, heroId = w.loanedTo, amounts = mapOf("fee" to maxOf(5, worth * cfg.insuranceFeePercent / 100), "payout" to worth * cfg.insurancePayoutPercent / 100, "until" to ctx.day + cfg.insuranceDays))
            }
            else -> error("No offer for encounter ${def.id}")
        }
    }

    fun text(ctx: ResolutionContext, i: EncounterInstance, def: EncounterDef): String {
        val a = i.amounts
        val blade = ctx.weapons[i.weaponId]?.name
        return when (i.defId) {
            GuildContent.COWARD_RETURNS -> ctx.guild?.captives?.firstOrNull { it.heroId == i.heroId }?.let { c ->
                "${name(ctx, i.otherHeroId)} came back from ${GuildOps.catalog(ctx).route(c.factionId)?.name ?: "their camp"} without ${name(ctx, i.heroId)}, who is alive there. They can be brought out until day ${c.deadlineDay - 1}." +
                    (blade?.let { " The loaned $it is with the captor." } ?: "")
            } ?: "${name(ctx, i.heroId)} is no longer held."
            GuildContent.SWORD_COMPLAINT -> "${blade ?: "A blade"} has opinions, and this morning it has a complaint: it will not be carried by people who think they know better. It wants inexperienced company."
            GuildContent.ACROSS_THE_COUNTER -> ctx.guild?.nemesis?.let { n -> "A dealer in things found on battlefields says ${n.name} can be parted from ${blade ?: "your blade"}: for ${a["price"]} gold it is on your counter today. Or he will say where ${n.name} sleeps." }
                ?: "The dealer's news is stale: the blade is no longer in those hands."
            GuildContent.INSURANCE_ADJUSTER -> "An adjuster with ash on his cuffs will insure one loan against the enemy: ${blade ?: "a blade"}, carried by ${name(ctx, i.heroId)}. If it is taken from the guild by day ${a["until"]}, he pays ${a["payout"]} gold, once."
            else -> def.description
        }
    }

    fun options(ctx: ResolutionContext, i: EncounterInstance): List<Opt> {
        fun n(key: String) = i.amounts[key] ?: 0
        val g = ctx.guild
        return when (i.defId) {
            GuildContent.COWARD_RETURNS -> {
                val held = g?.captives?.firstOrNull { it.heroId == i.heroId }
                val gone = if (held == null) "They are no longer held." else null
                val rescue = g?.offers?.firstOrNull { it.subjectHeroId == i.heroId }
                listOf(
                    Opt("time", "Pay for word to be carried in", "${name(ctx, i.heroId)} can be brought out ${n("days")} days longer.", gold = n("ransom"), blocked = gone) { c ->
                        c.guild = c.guild!!.copy(captives = c.guild!!.captives.map { if (it.heroId == i.heroId) it.copy(deadlineDay = it.deadlineDay + n("days")) else it },
                            offers = c.guild!!.offers.map { if (it.subjectHeroId == i.heroId) it.copy(expiresDay = it.expiresDay + n("days")) else it })
                        "The smith paid ${n("ransom")} gold to buy ${name(c, i.heroId)} ${n("days")} more days."
                    },
                    Opt("question", "Question the survivor", "${name(ctx, i.otherHeroId)} spends today telling what they saw and cannot go out: the guards on the way in are weaker by ${100 - n("ease")} parts in a hundred. The way out is as it was.",
                        blocked = gone ?: (if (rescue == null) "The rescue is already under way." else null) ?: g?.member(i.otherHeroId!!)?.let { GuildOps.unavailable(ctx, it) }?.let { "${name(ctx, i.otherHeroId)} $it." }) { c ->
                        c.guild = c.guild!!.copy(offers = c.guild!!.offers.map { o -> if (o.subjectHeroId == i.heroId) o.copy(stages = o.stages.mapIndexed { s, st -> if (s == 0) st.copy(enemies = st.enemies.map { e -> e.copy(percent = e.percent * n("ease") / 100) }) else st }) else o })
                        GuildOps.updateMember(c, i.otherHeroId!!) { it.copy(wound = Wound(WoundKind.EXHAUSTED, c.day + 1, "Spent the day telling what they saw.")) }
                        "${name(c, i.otherHeroId)} told the smith the way in to where ${name(c, i.heroId)} is held."
                    },
                    pass("Let the trail go cold for today", "The smith did not act on ${name(ctx, i.otherHeroId)}'s news today."),
                )
            }
            GuildContent.SWORD_COMPLAINT -> {
                val w = ctx.weapons[i.weaponId]
                val gone = if (w == null || LaunchContent.SENTIENT !in w.flaws || w.location is WeaponLocation.Lost || w.location is WeaponLocation.Destroyed) "The blade is no longer at the forge." else null
                listOf(
                    Opt("oath", "\"It wants inexperienced company.\"", "The blade takes an oath: while everyone in its party is level ${n("level")} or lower, every ally starts a fight with 3 Guard and 1 Regeneration. In any other party it does nothing more than before.", blocked = gone) { c ->
                        c.addWeaponHistory(i.weaponId!!, HISTORY_OATH, "Swore to fight only beside the inexperienced.")
                        "${c.weapon(i.weaponId).name} took its oath: it rallies a party of novices."
                    },
                    Opt("silence", "\"You are a sword.\"", "The blade is worked over until it has no more to say: it loses its Sentient flaw and stays as it otherwise is.", energy = n("energy"), material = i.materialId, blocked = gone) { c ->
                        val blade = c.weapon(i.weaponId!!)
                        c.updateWeapon(blade.copy(flaws = blade.flaws - LaunchContent.SENTIENT, power = maxOf(1, blade.power - c.content.affix(LaunchContent.SENTIENT).power)))
                        c.addWeaponHistory(blade.id, HISTORY_SILENCED, "Stabilised on the anvil; it has not spoken since.")
                        "The smith worked ${blade.name} over until it stopped arguing."
                    },
                    pass("\"We'll discuss it later.\"", "The smith let ${w?.name ?: "the blade"} complain."),
                )
            }
            GuildContent.ACROSS_THE_COUNTER -> {
                val nem = g?.nemesis?.takeIf { it.weaponId == i.weaponId }
                val gone = if (nem == null) "The blade is no longer in those hands." else null
                listOf(
                    Opt("buy", "Buy it back", "${ctx.weapons[i.weaponId]?.name ?: "The blade"} returns to storage today, the very blade. ${nem?.name ?: "Its bearer"} goes unpunished.", gold = n("price"), blocked = gone) { c ->
                        val blade = c.weapon(i.weaponId!!)
                        c.updateWeapon(blade.copy(location = WeaponLocation.Storage))
                        c.addWeaponHistory(blade.id, "RECOVERED", "Bought back across the counter for ${n("price")} gold.")
                        Stories.recovered(c, blade.id)
                        "The smith bought ${blade.name} back for ${n("price")} gold."
                    },
                    Opt("lead", "Take the lead instead", "The recovery contract for it costs nothing to send and ${nem?.name ?: "its bearer"}'s guard is weaker by ${100 - n("ease")} parts in a hundred.",
                        blocked = gone ?: if (g?.offers?.none { it.subjectWeaponId == i.weaponId } == true) "No recovery contract is on the board." else null) { c ->
                        c.guild = c.guild!!.copy(offers = c.guild!!.offers.map { o -> if (o.subjectWeaponId == i.weaponId) o.copy(fee = if (c.guild!!.planned?.offerId == o.id) o.fee else 0, stages = o.stages.map { st -> st.copy(enemies = st.enemies.mapIndexed { k, e -> if (k == 0) e else e.copy(percent = e.percent * n("ease") / 100) }) }) else o })
                        "The dealer told the smith where ${nem?.name ?: "the bearer"} sleeps."
                    },
                    pass("Show him the door", "The dealer left with his news."),
                )
            }
            GuildContent.INSURANCE_ADJUSTER -> listOf(
                Opt("insure", "Insure the blade", "If the enemy takes ${ctx.weapons[i.weaponId]?.name ?: "it"} from the guild by day ${n("until")}, the forge is paid ${n("payout")} gold, once. Selling, melting, giving it away or losing it any other way pays nothing.", gold = n("fee"),
                    blocked = if (ctx.weapons[i.weaponId]?.isLoaned != true) "The blade is no longer on loan." else null) { c ->
                    c.consequences += ScheduledConsequence("q${i.id}", ConsequenceKind.INSURANCE, n("until"), weaponId = i.weaponId, amounts = mapOf("payout" to n("payout")))
                    c.addWeaponHistory(i.weaponId!!, HISTORY_INSURED, "Insured against the enemy until day ${n("until")}.")
                    "The smith insured ${c.weapon(i.weaponId).name} for ${n("fee")} gold."
                },
                pass("Decline", "The adjuster left his card."),
            )
            else -> listOf(pass("Send them away", "The visitor left."))
        }
    }

    /**
     * What the guild adds to visitors that already existed. The festival gains a tournament for a member; a family blade
     * restored for its heir makes that heir willing to sign (a benefit the collector's coin does not have).
     */
    fun extend(ctx: ResolutionContext, i: EncounterInstance, options: List<Opt>): List<Opt> {
        if (ctx.guild == null) return options
        val cfg = ctx.config.guild
        return when (i.defId) {
            Depth.FESTIVAL_CONTRACT -> {
                val champion = GuildOps.present(ctx).mapNotNull { ctx.heroes[it.heroId] }.maxWithOrNull(compareBy<Hero> { it.level }.thenByDescending(IdOrder.numeric) { it.id.value })
                val enter = Opt("tournament", "Enter ${champion?.fullName ?: "a member"} in the tournament", "${champion?.name ?: "They"} fights for the crowd today and is spent tomorrow: ${cfg.tournamentGold} gold, +${cfg.tournamentReputation} reputation, and a name.",
                    blocked = if (champion == null) "No member is fit to enter." else if (ctx.guild!!.planned?.heroIds?.contains(champion.id) == true) "${champion.name} is in the party you planned for today." else null) { c ->
                    val h = c.hero(champion!!.id)
                    c.gold += cfg.tournamentGold
                    c.reputation += cfg.tournamentReputation
                    c.updateHero(h.copy(fame = h.fame + cfg.tournamentFame))
                    Heroes.grantXp(c, c.hero(h.id), cfg.missionXpWin)
                    GuildOps.updateMember(c, h.id) { it.copy(wound = Wound(WoundKind.EXHAUSTED, c.day + 2, "Fought in the festival tournament.")) }
                    c.guild = c.guild!!.copy(reserved = c.guild!!.reserved - h.id)
                    "${h.fullName} fought in the festival tournament for the guild and won the crowd."
                }
                options.dropLast(1) + enter + options.last()
            }
            Depth.CRACKED_FAMILY_BLADE -> options.map { o ->
                if (o.id != "restore") o else Opt(o.id, o.label, o.effect + " ${name(ctx, i.heroId)} will also be willing to sign with the guild at half the fee.", o.gold, o.energy, o.material, o.blocked, o.closes) { c ->
                    val told = o.apply(c)
                    val heir = c.heroes[i.heroId]?.takeIf { it.isAlive }
                    val g = c.guild!!
                    if (heir != null && !g.isMember(heir.id) && g.candidates.none { it.heroId == heir.id }) {
                        val trait = GuildOps.catalog(c).traits.first { t -> g.members.none { it.traitId == t.id } && g.candidates.none { it.traitId == t.id } }
                        val fee = (cfg.signingFeeBase + cfg.signingFeePerLevel * (heir.level - 1)) * cfg.openingFeePercent / 100
                        c.guild = g.copy(candidates = g.candidates + RecruitCandidate(heir.id, fee, cfg.memberSharePercent + trait.shareDelta, trait.id, "Carries a family blade the smith made whole."))
                    }
                    told
                }
            }
            else -> options
        }
    }
}
