package com.tinyblacksmith.core.guild

import com.tinyblacksmith.core.combat.Combatant
import com.tinyblacksmith.core.combat.EventKind
import com.tinyblacksmith.core.combat.Fight
import com.tinyblacksmith.core.combat.FightResult
import com.tinyblacksmith.core.combat.Side
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.content.MissionArchetype
import com.tinyblacksmith.core.content.SpecialityDef
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.rng.RngStream

/**
 * What a guild's days leave behind (spec 7.5, 10.7, 12, 9.4): notes of chains that really happened, the charter
 * milestone, one nemesis carrying a real blade, one rival guild, sparse bonds, branches opened by deeds, a law of the
 * season. Each is bounded by a number in `BalanceConfig.guild`; none invents a person, a blade or a fight.
 */
object Stories {
    private fun update(ctx: ResolutionContext, f: (GuildRunState) -> GuildRunState) { ctx.guild = f(ctx.guild!!) }
    private const val MAX_NOTES = 24
    private const val MAX_BONDS = 2
    const val BOND_COMRADES = "comrades"
    const val BOND_PROTECTOR = "protector"

    // ---- combo notes ----

    /** A chain of two or more different rules that ended in something: noted once per chain, with who and what did it. */
    fun noteCombos(ctx: ResolutionContext, m: MissionInstance, fight: FightResult) {
        val byId = fight.events.associateBy { it.id }
        val effectNames = (ctx.content.combat?.allEffects.orEmpty() + GuildOps.catalog(ctx).traits.flatMap { it.effects } + ctx.content.combat?.kits.orEmpty().flatMap { it.kit.passives }).associate { it.id to it.name }
        for (h in fight.highlights) {
            val chain = h.eventIds.mapNotNull(byId::get)
            val rules = chain.mapNotNull { it.effectId }.distinct()
            if (rules.size < 2) continue
            val key = rules.joinToString(">")
            if (ctx.guild!!.comboNotes.any { it.startsWith("$key|") }) continue
            val who = chain.mapNotNull { it.source }.distinct().mapNotNull { k -> m.party.firstOrNull { it.value == k } }.map { id -> ctx.heroes[id]?.name + (m.gear[id]?.let { ctx.weapons[it] }?.let { " with ${it.name}" } ?: "") }
            val text = rules.joinToString(" → ") { effectNames[it] ?: it } + " (" + who.joinToString(", ") + ")"
            update(ctx) { it.copy(comboNotes = (it.comboNotes + "$key|$text").takeLast(MAX_NOTES)) }
            ctx.emit(EventType.COMBO_NOTED, 4, "A chain worth writing down: $text.", m.party.map { it.value } + m.gear.values.map { it.value }, mapOf("chain" to key))
        }
    }

    // ---- bonds and branches ----

    private fun bond(ctx: ResolutionContext, a: HeroId, b: HeroId, kind: String, reason: String): Boolean {
        val ma = ctx.guild!!.member(a) ?: return false
        if (ctx.guild!!.member(b) == null || ma.bonds.size >= MAX_BONDS || ma.bonds.any { it.withHeroId == b }) return false
        GuildOps.updateMember(ctx, a) { it.copy(bonds = it.bonds + Bond(b, kind, ctx.day, reason)) }
        return true
    }

    /** Members who have won `bondMissions` contracts side by side become comrades: each opens a fight they share by marking the foe that matters. */
    fun afterContract(ctx: ResolutionContext, m: MissionInstance) {
        val party = m.party.filter { ctx.guild!!.member(it) != null }
        for (i in party.indices) for (j in i + 1 until party.size) {
            val key = "pair:${party[i].value}:${party[j].value}"
            val n = (ctx.guild!!.completed[key] ?: 0) + 1
            update(ctx) { it.copy(completed = it.completed + (key to n)) }
            if (n == ctx.config.guild.bondMissions) {
                val reason = "Won ${n} contracts side by side."
                val made = bond(ctx, party[i], party[j], BOND_COMRADES, reason) or bond(ctx, party[j], party[i], BOND_COMRADES, reason)
                if (made) ctx.emit(EventType.GUILD_STORY, 4, "${ctx.hero(party[i]).fullName} and ${ctx.hero(party[j]).fullName} have fought side by side long enough to move as one.", listOf(party[i].value, party[j].value), mapOf("bond" to BOND_COMRADES))
            }
        }
    }

    /** Whoever brought a captive out watches over them afterwards. */
    fun bondRescue(ctx: ResolutionContext, rescued: HeroId) {
        val party = ctx.guild!!.mission?.party.orEmpty().filter { it != rescued }
        val rescuer = party.firstOrNull { (ctx.guild!!.member(it)?.bonds?.size ?: MAX_BONDS) < MAX_BONDS } ?: return
        if (bond(ctx, rescuer, rescued, BOND_PROTECTOR, "Brought them out on day ${ctx.day}."))
            ctx.emit(EventType.GUILD_STORY, 4, "${ctx.hero(rescuer).fullName} brought ${ctx.hero(rescued).fullName} out, and means to keep them out.", listOf(rescuer.value, rescued.value), mapOf("bond" to BOND_PROTECTOR))
    }

    /** The branches [m]'s own record has opened and the smith has not yet answered. */
    fun openBranches(ctx: ResolutionContext, m: GuildMember): List<SpecialityDef> {
        if (m.specialityId != null) return emptyList()
        val hero = ctx.heroes[m.heroId] ?: return emptyList()
        val deeds = buildSet {
            if (m.missionWins >= 3) add("veteran")
            if (m.timesDowned >= 2) add("survivor")
            if (hero.ambition == Ambition.DEFENDER && hero.ambitionDone) add("defender")
        }
        return GuildOps.catalog(ctx).specialities.filter { it.deed in deeds }
    }

    fun chooseBranch(ctx: ResolutionContext, heroId: HeroId, specialityId: String): GameError? {
        val m = ctx.guild!!.member(heroId) ?: return GameError.NotAMember(heroId)
        if (m.specialityId == specialityId) return null
        val def = openBranches(ctx, m).firstOrNull { it.id == specialityId } ?: return GameError.UnknownContent(specialityId)
        GuildOps.updateMember(ctx, heroId) { it.copy(specialityId = def.id) }
        ctx.emit(EventType.GUILD_STORY, 5, "${ctx.hero(heroId).fullName} is now known as a ${def.name}.", listOf(heroId.value), mapOf("speciality" to def.id))
        return null
    }

    // ---- the charter ----

    fun charterSiege(ctx: ResolutionContext, leader: String, beaten: Boolean) {
        val cfg = ctx.config.guild
        if (!beaten) {
            ctx.emit(EventType.GUILD_STORY, 7, "$leader was held off, not beaten. The charter is not secured: $leader leads the next siege as well.", data = mapOf("charter" to "pending"))
            return
        }
        if (ctx.guild!!.milestone(GuildContent.CHARTER_SECURED) != null) return
        update(ctx) { it.copy(milestones = it.milestones + RunMilestoneClaim(GuildContent.CHARTER_SECURED, ctx.day, points = cfg.charterLegacyPoints)) }
        ctx.earn(IncomeKind.TRIBUTE, cfg.charterTribute)
        ctx.milestones += "CHARTER_SECURED"
        ctx.emit(EventType.CHARTER_SECURED, 10, "Charter secured. $leader broke on the walls of Emberfall on day ${ctx.day}, and the council paid ${cfg.charterTribute} gold. The smith may close this chapter with honour, or go on.", data = mapOf("milestone" to GuildContent.CHARTER_SECURED, "tribute" to cfg.charterTribute.toString()))
    }

    /** Closing the chapter by choice (spec 10.7): only with the charter secured, and the run ends as a success. */
    fun retire(ctx: ResolutionContext, milestoneId: String): GameError? {
        val g = ctx.guild!!
        val claim = g.milestone(milestoneId) ?: return GameError.MilestoneNotEarned(milestoneId)
        if (g.mission != null) return GameError.PartyAway
        update(ctx) { it.copy(retired = true, milestones = it.milestones.map { c -> if (c.id == claim.id) c.copy(claimed = true) else c }) }
        ctx.phase = Phase.ENDED
        ctx.endCause = "The smith closed the chapter with the charter secured, on day ${ctx.day}."
        ctx.emit(EventType.RUN_RETIRED, 10, "The smith closed the guild's chapter with its charter secured. The forge stands.")
        return null
    }

    /** A harder road, taken knowingly after the charter is secured: only upward, and what it adds is written on it. */
    fun setRank(ctx: ResolutionContext, rank: Int): GameError? {
        val g = ctx.guild!!
        if (g.milestone(GuildContent.CHARTER_SECURED) == null) return GameError.MilestoneNotEarned(GuildContent.CHARTER_SECURED)
        if (rank == g.rank) return null
        val def = GuildOps.catalog(ctx).rank(rank) ?: return GameError.UnknownContent("rank $rank")
        if (rank < g.rank) return GameError.PartyInvalid("A rank once taken is kept.")
        update(ctx) { it.copy(rank = rank) }
        ctx.emit(EventType.GUILD_STORY, 6, "The guild took the rank of ${def.name}. ${def.description}", data = mapOf("rank" to rank.toString()))
        return null
    }

    // ---- the nemesis ----

    /** A loan fell into enemy hands. The first such blade gets a bearer with a name; while that one lives, later ones are simply lost. */
    fun taken(ctx: ResolutionContext, weaponId: WeaponId, factionId: FactionId) {
        val g = ctx.guild ?: return
        if (g.nemesis != null) return
        val faction = ctx.content.faction(factionId)
        val unit = GuildOps.catalog(ctx).siegeElites[factionId] ?: return
        val blade = ctx.weapons[weaponId] ?: return
        val names = faction.eliteNames.ifEmpty { listOf("a captain of the ${faction.name}") }
        val name = ctx.rng(RngStream.GUILD).pick(names).removePrefix("an ").removePrefix("a ").removePrefix("the ").replaceFirstChar { it.uppercase() }
        update(ctx) { it.copy(nemesis = Nemesis(name, factionId, weaponId, ctx.day, unit)) }
        ctx.emit(EventType.GUILD_STORY, 7, "$name of the ${faction.name} now carries ${blade.name}, a blade of this forge. It can be taken back.", listOf(weaponId.value), mapOf("nemesis" to name))
    }

    fun recovered(ctx: ResolutionContext, weaponId: WeaponId) {
        val n = ctx.guild?.nemesis?.takeIf { it.weaponId == weaponId } ?: return
        update(ctx) { it.copy(nemesis = null, offers = it.offers.filter { o -> o.subjectWeaponId != weaponId }) }
        ctx.emit(EventType.GUILD_STORY, 6, "${n.name} is beaten and carries nothing of this forge any more.", listOf(weaponId.value))
    }

    /**
     * The bearer of the smith's blade as it stands on a field: stronger by the blade's worth, and with every rule of the
     * blade that needs nothing but its wielder (the blade's family, element, affixes, flaws, catalyst and signature all
     * do). Nothing of the smith's workshop, relics or legacy goes with a blade to the enemy.
     */
    fun armed(ctx: ResolutionContext, foe: Combatant, nemesis: Nemesis): Combatant {
        val blade = ctx.weapons[nemesis.weaponId] ?: return foe
        val combat = ctx.content.combat ?: return foe
        val bonus = blade.power / combat.powerPerStrike
        val cfg = ctx.config.guild
        return foe.copy(name = nemesis.name, maxHealth = Fight.pct(foe.maxHealth, cfg.nemesisPercent), health = Fight.pct(foe.maxHealth, cfg.nemesisPercent), strikeMin = foe.strikeMin + bonus, strikeMax = foe.strikeMax + bonus,
            element = blade.element, effects = (foe.effects + Loadout.weaponEffects(blade, combat)).distinctBy { it.id }, weaponName = blade.name, tags = foe.tags + "nemesis")
    }

    // ---- the rival, the law of the season ----

    /** The morning's news of the region. Draws on GUILD. */
    fun morning(ctx: ResolutionContext) {
        val g = ctx.guild ?: return
        val cfg = ctx.config.guild
        val cat = GuildOps.catalog(ctx)
        val rng = ctx.rng(RngStream.GUILD)
        if (g.rival == null && ctx.day >= cfg.rivalFirstDay && cat.rivalNames.isNotEmpty()) {
            val (name, leader) = rng.pick(cat.rivalNames)
            val wants = rng.pick(listOf(MissionArchetype.HUNT, MissionArchetype.DELVE, MissionArchetype.ESCORT)).name
            update(ctx) { it.copy(rival = RivalGuild(name, leader, wants, nextMoveDay = ctx.day + cfg.rivalMoveEveryDays)) }
            ctx.emit(EventType.GUILD_STORY, 5, "Another guild has taken rooms in Emberfall: $name, under $leader. They will take a contract off the board on day ${ctx.day + cfg.rivalMoveEveryDays}, a ${wants.lowercase()} if there is one.", data = mapOf("rival" to name))
        }
        ctx.guild!!.rival?.let { r ->
            if (ctx.day >= r.nextMoveDay) {
                // What the smith has already planned for today is the smith's: the rival never takes an accepted contract.
                val planned = ctx.guild!!.planned?.offerId
                val open = ctx.guild!!.offers.filter { o -> o.id != planned && o.subjectHeroId == null && o.subjectWeaponId == null && cat.mission(o.defId).let { it.weight > 0.0 && it.archetype != MissionArchetype.SUPPLY } }
                val pick = open.firstOrNull { cat.mission(it.defId).archetype.name == r.wants } ?: open.minByOrNull { it.postedDay }
                val line = pick?.let { Missions.title(ctx, it) }
                update(ctx) { it.copy(offers = it.offers.filter { o -> o.id != pick?.id }, rival = r.copy(nextMoveDay = ctx.day + cfg.rivalMoveEveryDays, taken = (r.taken + listOfNotNull(line?.let { l -> "Day ${ctx.day}: $l" })).takeLast(5))) }
                ctx.emit(EventType.GUILD_STORY, 4, if (line != null) "${r.name} took the contract for $line. They move again on day ${ctx.day + cfg.rivalMoveEveryDays}." else "${r.name} found nothing on the board worth their while. They look again on day ${ctx.day + cfg.rivalMoveEveryDays}.", data = mapOf("rival" to r.name))
            }
        }
        val law = ctx.guild!!.law
        if (law != null && ctx.day >= law.untilDay) update(ctx) { it.copy(law = null) }
        if (ctx.guild!!.law == null && ctx.day >= cfg.lawFirstDay && cat.laws.isNotEmpty() && rng.chance(cfg.lawChance)) {
            val def = rng.pick(cat.laws)
            // Announced a morning ahead: no party leaves under a law it could not read first.
            update(ctx) { it.copy(law = WorldLaw(def.id, ctx.day + 1, ctx.day + 1 + def.days)) }
            ctx.emit(EventType.GUILD_STORY, 5, "From tomorrow, for ${def.days} days: ${def.name}. ${def.description}", data = mapOf("law" to def.id))
        }
    }

    /** Fighters of a party who share a bond bring it to a fight they share. Read by `GuildOps.fighter` through the party's IDs. */
    fun bondsIn(ctx: ResolutionContext, heroId: HeroId, party: Collection<HeroId>): List<Bond> = ctx.guild?.member(heroId)?.bonds.orEmpty().filter { it.withHeroId in party }

    /** Whether any enemy of [fight] was downed by [heroId]: for records that ask. */
    fun downedAFoe(fight: FightResult, heroId: HeroId): Boolean = fight.events.any { e -> e.kind == EventKind.DOWNED && e.source == heroId.value && fight.actor(e.target ?: "")?.side == Side.ENEMY }
}
