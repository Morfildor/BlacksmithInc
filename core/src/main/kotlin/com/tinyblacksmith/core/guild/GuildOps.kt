package com.tinyblacksmith.core.guild

import com.tinyblacksmith.core.combat.Combatant
import com.tinyblacksmith.core.combat.EffectDef
import com.tinyblacksmith.core.combat.Stat
import com.tinyblacksmith.core.content.CharterDef
import com.tinyblacksmith.core.content.GuildCatalog
import com.tinyblacksmith.core.content.RelicEffect
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.heroes.Heroes
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.rng.RngStream

/**
 * The roster of the player's guild: who is contracted, what they carry on loan, who is kept for the wall (spec 4).
 * Every function here works on a guild run; `GameEngine` rejects the commands on any other. Draws only on GUILD.
 */
object GuildOps {
    const val LOST_WITH_CAPTIVE = "held with a captive"
    const val LOST_TAKEN = "taken from the guild's party"

    fun catalog(ctx: ResolutionContext): GuildCatalog = ctx.content.guild ?: error("This catalog has no guild content")
    fun charter(ctx: ResolutionContext): CharterDef? = ctx.guild?.let { catalog(ctx).charter(it.charterId) }

    private fun update(ctx: ResolutionContext, f: (GuildRunState) -> GuildRunState) { ctx.guild = f(ctx.guild!!) }
    fun updateMember(ctx: ResolutionContext, id: HeroId, f: (GuildMember) -> GuildMember) = update(ctx) { g -> g.copy(members = g.members.map { if (it.heroId == id) f(it) else it }) }

    // ---- reads ----

    fun loanOf(ctx: ResolutionContext, heroId: HeroId): Weapon? = ctx.weapons.values.firstOrNull { (it.location as? WeaponLocation.Loaned)?.heroId == heroId }

    /** The blade a member fights with: the loan, else their own. */
    fun weaponOf(ctx: ResolutionContext, heroId: HeroId): Weapon? = loanOf(ctx, heroId) ?: ctx.equippedWeapon(heroId)

    /** A wound that keeps a member at home today. A scar does not. */
    fun laidUp(ctx: ResolutionContext, m: GuildMember): Wound? = m.wound?.takeIf { it.kind != WoundKind.SCARRED && it.untilDay > ctx.day }

    /** Why [m] cannot join a party or stand on the wall today, or null. */
    fun unavailable(ctx: ResolutionContext, m: GuildMember): String? {
        val hero = ctx.heroes[m.heroId]
        return when {
            hero == null || !hero.isAlive -> "is gone"
            m.status == MemberStatus.CAPTURED -> "is held by the enemy"
            m.status == MemberStatus.AWAY -> "is away with the party"
            else -> laidUp(ctx, m)?.let { "is ${it.kind.name.lowercase()} until day ${it.untilDay}" }
        }
    }

    fun present(ctx: ResolutionContext): List<GuildMember> = ctx.guild?.members.orEmpty().filter { unavailable(ctx, it) == null }

    /** The party rules the workshop's relics give, as IDs (what a departing party takes with it) and as rules. */
    fun relicIds(ctx: ResolutionContext): List<String> = ctx.relics.map { it.id }.filter { ctx.content.relic(it)?.effect == RelicEffect.COMBAT }
    fun relicRules(ctx: ResolutionContext, ids: List<String>): List<EffectDef> = ids.flatMap { ctx.content.combat?.relicEffects?.get(it).orEmpty() }

    /** A member as a fighter: the hero and blade through [Loadout], then what the member's trait, branch, scar and the law of the day add. */
    fun fighter(ctx: ResolutionContext, hero: Hero, weapon: Weapon?, field: Loadout.Field, beside: Collection<HeroId> = emptyList()): Combatant {
        val base = Loadout.combatant(hero, weapon, ctx.content, ctx.config, field)
        val member = ctx.guild?.member(hero.id) ?: return base
        val cat = catalog(ctx)
        val trait = cat.trait(member.traitId)
        val branch = cat.speciality(member.specialityId)
        val scarred = member.wound?.takeIf { it.kind == WoundKind.SCARRED && it.untilDay > ctx.day } != null
        val max = com.tinyblacksmith.core.combat.Fight.pct(base.maxHealth, branch?.healthPercent ?: 100)
        val cap = if (scarred) com.tinyblacksmith.core.combat.Fight.pct(max, ctx.config.guild.scarredHealthPercent) else max
        // A blade that took the oath rallies a party in which nobody is past the level the oath names.
        val sworn = weapon?.history?.any { it.kind == GuildVisitors.HISTORY_OATH } == true
        val novices = sworn && (beside + hero.id).all { (ctx.heroes[it]?.level ?: 1) <= ctx.config.guild.oathMaxLevel }
        val stats = HashMap<Stat, Int>(base.stats)
        trait?.startStats?.forEach { (s, n) -> stats[s] = (stats[s] ?: 0) + n }
        return base.copy(
            maxHealth = max, health = minOf(cap, maxOf(1, (max * hero.health + 99) / 100)),
            strikeMin = maxOf(1, com.tinyblacksmith.core.combat.Fight.pct(base.strikeMin, branch?.strikePercent ?: 100)), strikeMax = maxOf(1, com.tinyblacksmith.core.combat.Fight.pct(base.strikeMax, branch?.strikePercent ?: 100)),
            support = com.tinyblacksmith.core.combat.Fight.pct(base.support, branch?.supportPercent ?: 100),
            effects = (base.effects + trait?.effects.orEmpty() + branch?.effects.orEmpty() + member.bonds.filter { it.withHeroId in beside }.flatMap { cat.bondEffects[it.kind].orEmpty() } + (if (sworn) cat.oathEffects else emptyList())).distinctBy { it.id },
            tags = base.tags + trait?.tags.orEmpty() + (if (novices) setOf(com.tinyblacksmith.core.content.GuildContent.TAG_NOVICE_PARTY) else emptySet()), stats = stats,
        )
    }

    // ---- the start of a guild run ----

    /**
     * Founds the guild under [charterId] on the first morning: the charter's two classes are contracted from the heroes
     * the town opens with (each class is present on day one), its gifts and its price are applied, and the first
     * candidates include the member it offers third.
     */
    fun found(ctx: ResolutionContext, charterId: String) {
        val cat = catalog(ctx)
        val charter = cat.charter(charterId) ?: error("Unknown charter $charterId")
        val rng = ctx.rng(RngStream.GUILD)
        ctx.guild = GuildRunState(charterId)
        ctx.gold = maxOf(0, ctx.gold + charter.goldDelta)
        ctx.town = ctx.town.copy(militia = maxOf(0, ctx.town.militia + charter.militiaDelta))
        ctx.reputation += charter.reputationDelta
        charter.materials.forEach { (m, n) -> ctx.materials[m] = (ctx.materials[m] ?: 0) + n }
        val taken = HashSet<HeroId>()
        for (cls in charter.startClasses) {
            val hero = ctx.aliveHeroes().firstOrNull { it.classId == cls && it.id !in taken } ?: Heroes.generate(ctx, rng, null, listOf(ctx.content.heroClass(cls))).also { ctx.updateHero(it) }
            taken += hero.id
            val trait = rng.pick(cat.traits.filter { t -> ctx.guild!!.members.none { it.traitId == t.id } })
            update(ctx) { it.copy(members = it.members + GuildMember(hero.id, ctx.day, 0, ctx.config.guild.memberSharePercent + trait.shareDelta, trait.id)) }
        }
        val names = ctx.guild!!.members.map { ctx.hero(it.heroId) }.joinToString(" and ") { "${it.fullName} the ${ctx.content.heroClass(it.classId).name}" }
        ctx.emit(EventType.GUILD_CHARTER, 5, "The guild opens under the charter \"${charter.name}\". $names have signed.", ctx.guild!!.members.map { it.heroId.value }, mapOf("charter" to charter.id))
        drawCandidates(ctx, charter)
    }

    // ---- candidates ----

    private fun fee(ctx: ResolutionContext, hero: Hero): Int = ctx.config.guild.signingFeeBase + ctx.config.guild.signingFeePerLevel * (hero.level - 1)

    /** Residents who may be asked: alive, not contracted, not named on an open order or by a waiting visitor. */
    private fun askable(ctx: ResolutionContext): List<Hero> {
        val named = ctx.commissions.values.filter { it.status == CommissionStatus.OFFERED || it.status == CommissionStatus.ACCEPTED }.flatMap { listOfNotNull(it.buyerId, it.recipientId) }.toSet() +
            listOfNotNull(ctx.encounter?.takeIf { it.isOpen }?.heroId, ctx.encounter?.takeIf { it.isOpen }?.otherHeroId) + ctx.consequences.mapNotNull { it.heroId }
        return ctx.residents().filter { it.id !in named }
    }

    /** [opening]: the charter's third member is among them at a lower fee (the first morning only). */
    private fun drawCandidates(ctx: ResolutionContext, opening: CharterDef? = null) {
        val cat = catalog(ctx)
        val rng = ctx.rng(RngStream.GUILD)
        val pool = askable(ctx).toMutableList()
        val picked = ArrayList<RecruitCandidate>()
        fun add(hero: Hero, feePercent: Int) {
            pool.remove(hero)
            val held = ctx.guild!!.members.map { it.traitId } + picked.map { it.traitId }
            val trait = rng.pick(cat.traits.filter { it.id !in held }.ifEmpty { cat.traits })
            picked += RecruitCandidate(hero.id, fee(ctx, hero) * feePercent / 100, ctx.config.guild.memberSharePercent + trait.shareDelta, trait.id)
        }
        opening?.let { c -> pool.firstOrNull { it.classId == c.offeredClass }?.let { add(it, ctx.config.guild.openingFeePercent) } }
        while (picked.size < ctx.config.guild.candidatesOffered && pool.isNotEmpty()) {
            // A class the guild does not have yet comes first, so a roster can be completed without waiting on luck.
            val have = ctx.guild!!.members.mapNotNull { ctx.heroes[it.heroId]?.classId }.toSet() + picked.mapNotNull { ctx.heroes[it.heroId]?.classId }
            add(rng.pick(pool.filter { it.classId !in have }.ifEmpty { pool }), 100)
        }
        update(ctx) { it.copy(candidates = picked, candidatesDay = ctx.day) }
    }

    fun refreshCandidates(ctx: ResolutionContext) {
        val g = ctx.guild ?: return
        val askable = askable(ctx).map { it.id }.toSet()
        // Somebody who offered to sign for a reason of their own (a visitor's answer) stays on the list while they live, whatever else the town asks of them.
        val standing = g.candidates.filter { it.complication != null && ctx.heroes[it.heroId]?.isAlive == true && !g.isMember(it.heroId) }
        if (ctx.day - g.candidatesDay >= ctx.config.guild.candidateRefreshDays) { drawCandidates(ctx); update(ctx) { it.copy(candidates = (standing + it.candidates).distinctBy { c -> c.heroId }) } }
        else update(ctx) { it.copy(candidates = it.candidates.filter { c -> c.heroId in askable || c in standing }) }
    }

    // ---- commands ----

    fun recruit(ctx: ResolutionContext, heroId: HeroId): GameError? {
        val g = ctx.guild!!
        if (g.isMember(heroId)) return null   // already signed: the same command again changes nothing
        val c = g.candidates.firstOrNull { it.heroId == heroId } ?: return GameError.NotACandidate(heroId)
        val hero = ctx.heroes[heroId]?.takeIf { it.isAlive } ?: return GameError.NotACandidate(heroId)
        if (g.members.size >= ctx.config.guild.maxMembers) return GameError.RosterFull
        if (ctx.gold < c.fee) return GameError.NotEnoughGold(c.fee, ctx.gold)
        ctx.gold -= c.fee
        ctx.updateHero(hero.copy(gold = hero.gold + c.fee, want = null, turnedAwayStreak = 0))
        update(ctx) { it.copy(members = it.members + GuildMember(heroId, ctx.day, c.fee, c.sharePercent, c.traitId), candidates = it.candidates.filter { x -> x.heroId != heroId }) }
        ctx.town = ctx.town.copy(championIds = ctx.town.championIds)
        val trait = catalog(ctx).trait(c.traitId)
        ctx.emit(EventType.GUILD_RECRUITED, 4, "${hero.fullName} the ${ctx.content.heroClass(hero.classId).name} signed with the guild for ${c.fee} gold${trait?.let { " (${it.name})" } ?: ""}.", listOf(heroId.value), mapOf("fee" to c.fee.toString(), "trait" to c.traitId))
        return null
    }

    fun dismiss(ctx: ResolutionContext, heroId: HeroId): GameError? {
        val m = ctx.guild!!.member(heroId) ?: return GameError.NotAMember(heroId)
        if (m.status != MemberStatus.HOME) return GameError.MemberUnavailable(heroId, if (m.status == MemberStatus.AWAY) "is away with the party" else "is held by the enemy")
        val hero = ctx.hero(heroId)
        release(ctx, heroId, "Returned to the forge when ${hero.fullName} left the guild.")
        ctx.emit(EventType.GUILD_DISMISSED, 3, "${hero.fullName} left the guild.", listOf(heroId.value))
        return null
    }

    fun loan(ctx: ResolutionContext, heroId: HeroId, weaponId: WeaponId): GameError? {
        val m = ctx.guild!!.member(heroId) ?: return GameError.NotAMember(heroId)
        val weapon = ctx.weapons[weaponId] ?: return GameError.WeaponNotFound(weaponId)
        (weapon.location as? WeaponLocation.Loaned)?.let { return if (it.heroId == heroId) null else GameError.WeaponOnLoan(weaponId, it.heroId) }
        if (!weapon.isInStorage && !weapon.isListed) return GameError.WeaponNotAvailable(weaponId, weapon.location)
        weapon.promisedTo?.let { return GameError.WeaponPromised(weaponId, it) }
        if (m.status != MemberStatus.HOME) return GameError.MemberUnavailable(heroId, if (m.status == MemberStatus.AWAY) "is away with the party" else "is held by the enemy")
        val hero = ctx.hero(heroId)
        loanOf(ctx, heroId)?.let { old ->
            ctx.updateWeapon(old.copy(location = WeaponLocation.Storage))
            ctx.addWeaponHistory(old.id, "LOAN_RETURNED", "Handed back by ${hero.fullName}.", listOf(heroId.value))
        }
        ctx.updateWeapon(ctx.weapon(weaponId).copy(location = WeaponLocation.Loaned(heroId)))
        ctx.addWeaponHistory(weaponId, "LOANED", "Loaned to ${hero.fullName} of the guild.", listOf(heroId.value))
        ctx.emit(EventType.WEAPON_LOANED, 1, "${weapon.name} was loaned to ${hero.fullName}.", listOf(weaponId.value, heroId.value))
        return null
    }

    fun recall(ctx: ResolutionContext, weaponId: WeaponId): GameError? {
        val weapon = ctx.weapons[weaponId] ?: return GameError.WeaponNotFound(weaponId)
        if (weapon.isInStorage) return null
        val holder = (weapon.location as? WeaponLocation.Loaned)?.heroId ?: return GameError.NotOnLoan(weaponId)
        val m = ctx.guild!!.member(holder)
        if (m != null && m.status != MemberStatus.HOME) return GameError.MemberUnavailable(holder, "is away with the party")
        ctx.updateWeapon(weapon.copy(location = WeaponLocation.Storage))
        ctx.addWeaponHistory(weaponId, "LOAN_RETURNED", "Recalled from ${ctx.heroes[holder]?.fullName ?: "its bearer"}.", listOf(holder.value))
        return null
    }

    fun reserve(ctx: ResolutionContext, heroId: HeroId, reserved: Boolean): GameError? {
        val g = ctx.guild!!
        val m = g.member(heroId) ?: return GameError.NotAMember(heroId)
        if (!reserved) { update(ctx) { it.copy(reserved = it.reserved - heroId) }; return null }
        if (heroId in g.reserved) return null
        if (m.status != MemberStatus.HOME) return GameError.MemberUnavailable(heroId, "is not in town")
        if (g.planned?.heroIds?.contains(heroId) == true) return GameError.MemberUnavailable(heroId, "is in the party you planned for today")
        if (g.reserved.size >= ctx.config.guild.defenders) return GameError.PartyInvalid("Only ${ctx.config.guild.defenders} places on the wall.")
        update(ctx) { it.copy(reserved = it.reserved + heroId) }
        return null
    }

    // ---- leaving the guild, by any road ----

    /** Takes [heroId] off the roster. A loan in their hands goes back to storage with [loanNote]; reservations and plans forget them. */
    fun release(ctx: ResolutionContext, heroId: HeroId, loanNote: String) {
        loanOf(ctx, heroId)?.let { w ->
            ctx.updateWeapon(w.copy(location = WeaponLocation.Storage))
            ctx.addWeaponHistory(w.id, "LOAN_RETURNED", loanNote, listOf(heroId.value))
        }
        forget(ctx, heroId)
    }

    /** Removes every reference to [heroId] from the guild's records, the loan aside. */
    fun forget(ctx: ResolutionContext, heroId: HeroId) = update(ctx) { g ->
        g.copy(members = g.members.filter { it.heroId != heroId }.map { m -> m.copy(bonds = m.bonds.filter { it.withHeroId != heroId }) }, reserved = g.reserved - heroId,
            planned = g.planned?.takeIf { heroId !in it.heroIds }, candidates = g.candidates.filter { it.heroId != heroId }, captives = g.captives.filter { it.heroId != heroId })
    }

    /**
     * Whatever else a day did, the roster is true afterwards: a member who died or retired outside a mission is taken
     * off it and their loan comes back, a candidate who left town is no longer one.
     */
    fun reconcile(ctx: ResolutionContext) {
        val g = ctx.guild ?: return
        for (m in g.members) {
            val hero = ctx.heroes[m.heroId]
            if (hero == null || !hero.isAlive) release(ctx, m.heroId, "Came back to the forge after ${hero?.fullName ?: "its bearer"} was gone.")
        }
        // A loan with no member behind it (a save edited by hand, a rule added later) goes home rather than hang in the air.
        for (w in ctx.weapons.values.toList()) (w.location as? WeaponLocation.Loaned)?.let { l -> if (ctx.guild!!.member(l.heroId) == null) ctx.updateWeapon(w.copy(location = WeaponLocation.Storage)) }
        update(ctx) { it.copy(candidates = it.candidates.filter { c -> ctx.heroes[c.heroId]?.isAlive == true && !it.isMember(c.heroId) }) }
    }

    // ---- days ----

    /** A member in town who is not on a wall or a road: the hurt rest, the fit train. One activity a day, like anybody. */
    fun homeDay(ctx: ResolutionContext) {
        val g = ctx.guild ?: return
        for (m in g.members.filter { it.status == MemberStatus.HOME }) {
            val hero = ctx.heroes[m.heroId]?.takeIf { it.isAlive } ?: continue
            if (hero.lastActivity == HeroActivity.DEFEND && ctx.town.nextSiegeDay == ctx.day) continue
            if (hero.health < 100 || laidUp(ctx, m) != null) ctx.updateHero(hero.copy(health = minOf(100, hero.health + ctx.config.heroRestHeal), lastActivity = HeroActivity.REST))
            else Heroes.grantXp(ctx, hero.copy(lastActivity = HeroActivity.GUILD), ctx.config.guild.homeTrainingXp)
        }
    }

    /** The morning's roster: wounds that have healed are gone, the list of those who would sign is kept current. */
    fun morning(ctx: ResolutionContext) {
        val g = ctx.guild ?: return
        update(ctx) { it.copy(members = it.members.map { m -> if (m.wound != null && m.wound.untilDay <= ctx.day) m.copy(wound = null) else m }) }
        reconcile(ctx)
        refreshCandidates(ctx)
        if (g.reserved.isNotEmpty()) update(ctx) { it.copy(reserved = it.reserved.filter { id -> it.isMember(id) }) }
    }
}
