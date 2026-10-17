package com.tinyblacksmith.core.guild

import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.combat.Combatant
import com.tinyblacksmith.core.combat.EventKind
import com.tinyblacksmith.core.combat.Fight
import com.tinyblacksmith.core.combat.FightOutcome
import com.tinyblacksmith.core.combat.FightEvent
import com.tinyblacksmith.core.combat.FightResult
import com.tinyblacksmith.core.combat.FightSetup
import com.tinyblacksmith.core.combat.Objective
import com.tinyblacksmith.core.combat.Posture
import com.tinyblacksmith.core.combat.Side
import com.tinyblacksmith.core.content.CombatContent
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.content.MaterialDraw
import com.tinyblacksmith.core.content.MissionArchetype
import com.tinyblacksmith.core.content.MissionDef
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.DeepRoad
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.heroes.Heroes
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.rng.Rng
import com.tinyblacksmith.core.rng.RngStream
import com.tinyblacksmith.core.text.Terms

/**
 * Contracts (spec 5): the board, the plan, the party's days on the road and what comes of them.
 *
 * Everything random about a contract is drawn when it is posted (GUILD stream) and stored on the offer: who stands
 * there, how strong, what it pays, the seed of each fight. Planning, reading and reloading draw nothing. A stage is
 * resolved inside End Day and what it won is the forge's at once ("secured"); what a later stage would add is "at risk"
 * only in the sense that it has not been won yet. Nothing secured is ever taken back.
 */
object Missions {
    private fun update(ctx: ResolutionContext, f: (GuildRunState) -> GuildRunState) { ctx.guild = f(ctx.guild!!) }

    // ---- the board ----

    /** A contract the board draws for a free slot, as opposed to one posted for a real captive or blade, or the standing work. */
    private fun drawn(def: MissionDef): Boolean = def.weight > 0.0
    private fun special(defId: String): Boolean = defId == GuildContent.RESCUE || defId == GuildContent.RECOVERY || defId == GuildContent.DEEP_ROAD

    /** Every morning: what has lapsed leaves, the standing work is there, free slots are filled, a captive or a taken blade has its contract. */
    fun refreshBoard(ctx: ResolutionContext) {
        val g = ctx.guild ?: return
        val cat = GuildOps.catalog(ctx)
        val cfg = ctx.config.guild
        val rng = ctx.rng(RngStream.GUILD)
        var offers = g.offers.filter { o ->
            o.expiresDay >= ctx.day && cat.missionById[o.defId] != null && (o.defId != GuildContent.DEEP_ROAD || DeepRoad.contractDue(ctx)) &&
                (o.subjectHeroId == null || g.captives.any { it.heroId == o.subjectHeroId }) && (o.subjectWeaponId == null || g.nemesis?.weaponId == o.subjectWeaponId || Hooks.isHookWeapon(ctx, o.subjectWeaponId)) &&
                (o.sponsorHeroId == null || o.defId == GuildContent.SABOTAGE || g.member(o.sponsorHeroId)?.let { ctx.heroes[it.heroId]?.isAlive } == true)
        }
        // A member's own offer that left the board untaken is weighed as a missed chance (living heroes, final design 10.3).
        g.offers.filter { o -> o.expiresDay < ctx.day && o.sponsorHeroId != null }.forEach { Hooks.lapsed(ctx, it) }
        var serial = g.nextMissionSerial
        fun post(def: MissionDef, faction: FactionId, hero: HeroId? = null, weapon: WeaponId? = null, reason: String? = null, expires: Int = offerExpiry(ctx, def), modifier: com.tinyblacksmith.core.content.MissionModifierDef? = null, lapse: String? = null,
            sponsor: HeroId? = null, opportunity: String? = null) {
            offers = offers + generate(ctx, def, faction, rng, "m${serial++}", hero, weapon, reason, expires, modifier, lapse).copy(sponsorHeroId = sponsor, opportunityId = opportunity)
        }
        val factions = ctx.factions.values.sortedBy { it.id.value }
        fun anyFaction(): FactionId = rng.pickWeighted(factions.map { it.id to (it.pressure + 10).toDouble() })
        if (factions.isEmpty()) return

        // The standing work: one card, always the same, renewed each morning (no draw).
        cat.missionById[GuildContent.WALL_WORK]?.let { def -> if (offers.none { it.defId == def.id }) post(def, factions.first().id, expires = Int.MAX_VALUE) }
        // A free, safe contract is always there.
        val supply = cat.missions.first { it.archetype == MissionArchetype.SUPPLY && it.fee == 0 }
        if (offers.none { it.defId == supply.id }) post(supply, anyFaction())
        // What spoils the coming siege is posted while there is still time to do it, once per siege.
        val untilSiege = ctx.town.nextSiegeDay - ctx.day
        val charter = GuildOps.charter(ctx)
        fun open(def: MissionDef) = ctx.day >= def.minDay || (charter?.earlyDanger == true)
        // The siege's jobs: once its besieger is committed, the sabotage and one opportunity are posted together and leave together.
        // Each takes two days and there is one party, so taking one lets the other go.
        val scenario = ctx.siege?.takeIf { it.siegeDay == ctx.town.nextSiegeDay && it.factionId != null }
        val jobsKey = "siegeJobs"
        var jobsPosted = false
        cat.missions.firstOrNull { it.archetype == MissionArchetype.SABOTAGE }?.let { def ->
            // The party has to be home on the siege's morning: the last day to leave on a two-day job is two days before it.
            val last = minOf(ctx.day + cfg.siegeJobMornings - 1, ctx.town.nextSiegeDay - def.days)
            if (scenario != null && g.completed[jobsKey] != scenario.siegeDay && scenario.sabotaged == 0 && last >= ctx.day && open(def)) {
                jobsPosted = true
                val prep = cat.preparation(scenario.prepId)
                val gone = "Leaves the board after day $last."
                // Living heroes: a member who knows the attackers' ways (a scout, a militia veteran, a deserter) sponsors the sabotage of a field that brings a breach (opp_powder_trail).
                val scout = Hooks.powderScout(ctx, scenario)
                post(if (prep != null) def.copy(stages = listOf(def.stages.first(), prep.sabotage)) else def, scenario.factionId!!, reason = (scout?.let { "${ctx.hero(it).fullName} knows the route their sappers use. " } ?: "") + (prep?.let { "${it.sabotageBrief} " } ?: "") + gone, expires = last,
                    sponsor = scout, opportunity = scout?.let { "opp_powder_trail" })
                val chances = cat.missions.filter { it.archetype == MissionArchetype.OPPORTUNITY && it.stages.none { s -> s.road } }
                if (chances.isNotEmpty()) rng.pick(chances).let { chance ->
                    post(chance, scenario.factionId, reason = "$gone The guild has one party: taking this quest means the sabotage cannot be done.", expires = last, lapse = GuildContent.LAPSE_SUPPLIER.takeIf { chance.stages.any { s -> s.supplier } })
                }
            }
        }
        // The deep road (rules 7): after the first siege, until it is won or the second siege opens the road. A party out on it keeps it off the board.
        cat.missionById[GuildContent.DEEP_ROAD]?.let { def ->
            val last = ctx.town.nextSiegeDay - def.days
            if (DeepRoad.contractDue(ctx) && last >= ctx.day && offers.none { it.defId == def.id } && g.mission?.offer?.defId != def.id)
                post(def, factions.maxBy { it.pressure }.id, reason = "Leaves the board after day $last.", expires = last)
        }
        val slots = cfg.offersOnBoard + (charter?.extraOffers ?: 0)
        fun ordinary() = offers.count { !special(it.defId) && it.defId != GuildContent.WALL_WORK }
        // A member's personal offer takes an ordinary slot, one at a time, and waits (queued, never counted as ignored) while the board is full.
        var queued = g.queued
        var cooldowns = g.cooldowns
        if (ctx.living) {
            val onBoard = offers.count { Hooks.sponsored(it) }
            val candidates = Hooks.candidates(ctx).filter { c -> offers.none { o -> o.sponsorHeroId == c.sponsor && o.opportunityId == c.opportunityId } }
            val next = candidates.firstOrNull { it.key == queued } ?: candidates.firstOrNull()
            if (next != null && onBoard < ctx.config.living.sponsoredOnBoard) {
                if (ordinary() < slots && open(next.def)) {
                    post(next.def, next.faction, weapon = next.weapon, reason = next.reason, sponsor = next.sponsor, opportunity = next.opportunityId)
                    cooldowns = (cooldowns + (next.key to ctx.day)).entries.sortedBy { it.value }.takeLast(24).associate { it.key to it.value }
                    queued = null
                    ctx.heroes[next.sponsor]?.hook?.takeIf { it.kind == HookKind.GRUDGE }?.let { h -> ctx.updateHero(ctx.hero(next.sponsor).copy(hook = h.copy(lastOfferDay = ctx.day))) }
                    ctx.emit(EventType.OPPORTUNITY_POSTED, 4, "${next.reason} The quest is on the board.", listOf(next.sponsor.value), mapOf("opportunity" to next.opportunityId))
                } else queued = next.key
            }
        }
        while (ordinary() < slots) {
            val pool = cat.missions.filter { drawn(it) && it.archetype != MissionArchetype.SUPPLY && it.archetype != MissionArchetype.SABOTAGE && open(it) && offers.none { o -> o.defId == it.id } }
            if (pool.isEmpty()) break
            val def = rng.pickWeighted(pool.map { it to it.weight })
            val faction = anyFaction()
            post(def, faction, modifier = if (cat.modifiers.isNotEmpty() && rng.chance(cfg.modifierChance)) rng.pick(cat.modifiers.filter { it.archetypes.isEmpty() || def.archetype in it.archetypes }) else null)
        }
        for (c in g.captives) if (offers.none { it.subjectHeroId == c.heroId }) cat.missionById[GuildContent.RESCUE]?.let { def ->
            val hero = ctx.heroes[c.heroId] ?: return@let
            val blade = c.weaponId?.let { ctx.weapons[it] }
            post(def, c.factionId, hero = c.heroId, reason = "${hero.fullName} is alive ${cat.route(c.factionId)?.where ?: "in their camp"}." + (blade?.let { " The loaned ${it.name} is with the captor." } ?: ""), expires = c.deadlineDay - 1)
        }
        g.nemesis?.let { n -> if (offers.none { it.subjectWeaponId == n.weaponId }) cat.missionById[GuildContent.RECOVERY]?.let { def ->
            ctx.weapons[n.weaponId]?.let { blade -> post(def, n.factionId, weapon = n.weaponId, reason = "${n.name} carries ${blade.name}, a weapon of this forge.") }
        } }
        update(ctx) { it.copy(offers = offers, nextMissionSerial = serial, completed = if (jobsPosted) it.completed + (jobsKey to scenario!!.siegeDay) else it.completed, queued = queued, cooldowns = cooldowns) }
    }

    /** The supplier's answer to what the guild did: more of every limited material each morning for a few days, or less. One mood at a time. */
    fun supplier(ctx: ResolutionContext, grateful: Boolean, why: String): String {
        val cfg = ctx.config.guild
        ctx.consequences.removeAll { it.kind == ConsequenceKind.SUPPLIER_GRATEFUL || it.kind == ConsequenceKind.SUPPLIER_SHAKEN }
        val until = ctx.day + cfg.supplierDays
        ctx.consequences += ScheduledConsequence("supplier-${ctx.day}", if (grateful) ConsequenceKind.SUPPLIER_GRATEFUL else ConsequenceKind.SUPPLIER_SHAKEN, until)
        val text = if (grateful) "$why Until day $until the supplier brings ${cfg.supplierGratefulStock} more of every limited material each morning."
            else "$why Until day $until the supplier brings ${cfg.supplierShakenStock} fewer of every limited material each morning."
        ctx.emit(EventType.SUPPLIER_NEWS, 6, text, data = mapOf("grateful" to grateful.toString(), "until" to until.toString()))
        return text
    }

    /** What the supplier's mood adds to (or takes from) a limited material's stock this morning. */
    fun supplierStockDelta(ctx: ResolutionContext): Int = ctx.consequences.firstOrNull { (it.kind == ConsequenceKind.SUPPLIER_GRATEFUL || it.kind == ConsequenceKind.SUPPLIER_SHAKEN) && ctx.day <= it.dueDay }
        ?.let { if (it.kind == ConsequenceKind.SUPPLIER_GRATEFUL) ctx.config.guild.supplierGratefulStock else -ctx.config.guild.supplierShakenStock } ?: 0

    /** Supply runs in the last `supplyMemoryDays` days: a road worked often carries less. */
    fun recentSupplyRuns(ctx: ResolutionContext): Int = ctx.guild?.supplyDays.orEmpty().count { ctx.day - it < ctx.config.guild.supplyMemoryDays }

    private fun drawMaterials(ctx: ResolutionContext, rng: Rng, draw: MaterialDraw?): Map<MaterialId, Int> {
        if (draw == null) return emptyMap()
        val pool = ctx.content.materials.filter { it.tier in draw.minTier..draw.maxTier && (if (draw.scarce) it.category == MaterialCategory.CATALYST || it.dailySupplierStock != null else it.category != MaterialCategory.CATALYST) }
            .ifEmpty { ctx.content.materials.filter { it.category != MaterialCategory.CATALYST } }
        val got = HashMap<MaterialId, Int>()
        repeat(draw.count) { val m = rng.pick(pool); got[m.id] = (got[m.id] ?: 0) + 1 }
        return got.toSortedMap(compareBy { it.value })
    }

    /** The last day an ordinary card of [def] posted today stands on the board: its life, shortened or lengthened by the guild's rank. */
    private fun offerExpiry(ctx: ResolutionContext, def: MissionDef): Int = ctx.day + maxOf(1, def.lifeDays + (GuildOps.catalog(ctx).rank(ctx.guild?.rank ?: 0)?.offerLifeDelta ?: 0)) - 1

    fun generate(ctx: ResolutionContext, def: MissionDef, faction: FactionId, rng: Rng, id: String, hero: HeroId? = null, weapon: WeaponId? = null, reason: String? = null, expires: Int = offerExpiry(ctx, def),
        modifier: com.tinyblacksmith.core.content.MissionModifierDef? = null, lapse: String? = null): MissionOffer {
        val cfg = ctx.config.guild
        val pressure = ctx.factions[faction]?.pressure ?: 0
        val base = GuildOps.ranked(ctx, cfg.enemyPercentBase + (cfg.enemyPercentPerDay * ctx.day + cfg.enemyPercentPerPressure * pressure).toInt())
        // Safe work is a safety net: it does not grow richer with the day, and a road worked often carries less.
        val supply = def.archetype == MissionArchetype.SUPPLY
        val worked = if (supply) recentSupplyRuns(ctx) else 0
        val thin = cfg.supplyThinPercent.getOrElse(worked) { cfg.supplyThinPercent.lastOrNull() ?: 100 }
        val stages = def.stages.mapIndexed { i, s ->
            val percent = Fight.pct(base, s.percent)
            val drawn = if (s.goldMax > 0) rng.nextInt(s.goldMin, s.goldMax) else 0
            val gold = (if (supply) drawn * thin / 100 else (drawn * (1.0 + cfg.rewardPercentPerDay * ctx.day / 100.0)).toInt() * (modifier?.goldPercent ?: 100) / 100) * cfg.contractGoldPercent / 100
            val last = i == def.stages.lastIndex
            val roster = s.rosters[faction].orEmpty().let { r -> if (r.isEmpty()) r else r + listOfNotNull(modifier?.addUnit?.get(faction)) }
            // A modifier may change what winning is: rounds to stand instead of a foe to fell, or somebody to bring out alive.
            val fought = roster.isNotEmpty()
            val hold = modifier?.hold?.takeIf { it > 0 && fought && s.objective == com.tinyblacksmith.core.combat.ObjectiveKind.DEFEAT }
            val protect = s.protectUnitId ?: modifier?.protectUnitId?.takeIf { fought }
            MissionStage(
                enemies = roster.map { EnemySpec(it, percent) },
                objective = if (hold != null) Objective(com.tinyblacksmith.core.combat.ObjectiveKind.HOLD, hold, protect?.let { "protected" }) else Objective(s.objective, s.rounds, protect?.let { "protected" }), danger = s.danger, seed = rng.nextLong(),
                reward = Reward(gold, drawMaterials(ctx, rng, if (last && modifier?.payment != null) modifier.payment else s.materials), s.suppression, s.integrity, s.militia + (if (last) modifier?.militia ?: 0 else 0), s.sabotage, s.relicOffer, weapon.takeIf { last }, hero.takeIf { last }, s.heartfire, s.supplier, s.road && last),
                protectUnitId = protect,
            )
        }
        return MissionOffer(id, def.id, faction, ctx.day, if (def.id == GuildContent.WALL_WORK) Int.MAX_VALUE else expires, def.days, def.fee, stages, def.optionalPush,
            reason ?: "Supply runs used this road $worked ${if (worked == 1) "time" else "times"} in the last ${cfg.supplyMemoryDays} days, so this supply run pays $thin% of the usual gold.".takeIf { worked > 0 }, hero, weapon, modifier?.id, lapse)
    }

    // ---- planning ----

    fun plan(ctx: ResolutionContext, cmd: Command.PlanDeployment): GameError? {
        val g = ctx.guild!!
        if (g.mission != null) return GameError.PartyAway
        val offer = g.offers.firstOrNull { it.id == cmd.offerId } ?: return GameError.MissionNotOffered(cmd.offerId)
        val ids = cmd.heroIds
        val most = partyMax(ctx, offer)
        if (ids.isEmpty() || ids.size > most || ids.toSet().size != ids.size) return GameError.PartyInvalid("A party for this quest has 1 to $most members.")
        for (id in ids) {
            val m = g.member(id) ?: return GameError.NotAMember(id)
            GuildOps.unavailable(ctx, m)?.let { return GameError.MemberUnavailable(id, it) }
            if (id in g.reserved) return GameError.MemberUnavailable(id, "is reserved for the wall")
            Hooks.departureBlocks(ctx, id, ctx.day + offer.days, remedy = offer.sponsorHeroId == id)?.let { return GameError.MemberUnavailable(id, it) }
        }
        // The fee of a plan made earlier today comes back before this one's is taken: replacing a plan never costs twice.
        val back = g.planned?.let { p -> g.offers.firstOrNull { it.id == p.offerId }?.fee } ?: 0
        if (ctx.gold + back < offer.fee) return GameError.NotEnoughGold(offer.fee, ctx.gold + back)
        ctx.gold += back - offer.fee
        update(ctx) { it.copy(planned = PlannedDeployment(offer.id, ids, cmd.posture)) }
        return null
    }

    /** How many may go on [offer]: the guild's party size, or fewer when its card says so. */
    fun partyMax(ctx: ResolutionContext, offer: MissionOffer): Int = minOf(ctx.config.guild.partyMax, GuildOps.catalog(ctx).modifier(offer.modifierId)?.partyMax ?: Int.MAX_VALUE)

    fun cancelPlan(ctx: ResolutionContext): GameError? {
        val p = ctx.guild!!.planned ?: return GameError.NoDeploymentPlanned
        ctx.gold += ctx.guild!!.offers.firstOrNull { it.id == p.offerId }?.fee ?: 0   // the whole fee comes back: nobody has left
        update(ctx) { it.copy(planned = null) }
        return null
    }

    fun checkpoint(ctx: ResolutionContext, cmd: Command.ChooseMissionCheckpoint): GameError? {
        val m = ctx.guild!!.mission?.takeIf { it.id == cmd.instanceId && atCheckpoint(it) } ?: return GameError.NoMissionCheckpoint
        update(ctx) { it.copy(mission = m.copy(choice = cmd.choice)) }
        return null
    }

    /** Out after a won first stage of a contract that advertised a deeper one: the smith may still say which way. */
    fun atCheckpoint(m: MissionInstance): Boolean = m.outcome == null && m.stage == 1 && m.offer.optionalPush

    // ---- End Day ----

    /** Step 1: the planned party leaves. The fee is paid, the blades in their hands are noted, the workshop's relics as they are now go with them. */
    fun commit(ctx: ResolutionContext) {
        val g = ctx.guild ?: return
        update(ctx) { it.copy(lastMission = null, lastSiege = null) }
        val plan = g.planned ?: return
        update(ctx) { it.copy(planned = null) }
        val offer = g.offers.firstOrNull { it.id == plan.offerId } ?: return
        val party = plan.heroIds.filter { id -> g.member(id)?.let { GuildOps.unavailable(ctx, it) == null } == true }
        val def = GuildOps.catalog(ctx).mission(offer.defId)
        if (party.isEmpty() || g.mission != null) {
            // Step 1 of End Day runs before anything can hurt a member, so this is a guard, not a path of play. The fee was paid at planning and is not kept.
            ctx.earn(IncomeKind.CONTRACT, offer.fee)
            ctx.emit(EventType.MISSION_FAILED, 3, "The party for ${def.name} could not leave: " + (if (party.isEmpty()) "nobody was fit to go." else "another party is still out.") + (if (offer.fee > 0) " Its ${offer.fee} gold came back." else ""))
            return
        }
        val gear = party.mapNotNull { id -> GuildOps.weaponOf(ctx, id)?.let { id to it.id } }.toMap()
        val instance = MissionInstance(offer.id, offer, party, gear, plan.posture, GuildOps.relicIds(ctx), ctx.day, ctx.day + offer.days)
        if (def.archetype == MissionArchetype.SUPPLY) update(ctx) { it.copy(supplyDays = (it.supplyDays + ctx.day).takeLast(ctx.config.guild.supplyMemoryDays * 2)) }
        update(ctx) { it.copy(mission = instance, offers = it.offers.filter { o -> o.id != offer.id }, members = it.members.map { m -> if (m.heroId in party) m.copy(status = MemberStatus.AWAY, missions = m.missions + 1) else m }) }
        val names = party.map { ctx.hero(it).fullName }
        ctx.emit(EventType.MISSION_DEPARTED, 4, "${names.joinToString(", ")} left for ${named(ctx, offer)}." + (if (offer.fee > 0) " The ${offer.fee} gold fee was paid when the party was planned." else ""), party.map { it.value },
            mapOf("mission" to offer.id, "def" to offer.defId))
    }

    fun title(ctx: ResolutionContext, offer: MissionOffer): String {
        val cat = GuildOps.catalog(ctx)
        val def = cat.mission(offer.defId)
        return if (def.archetype == MissionArchetype.EMERGENCY) "the work to ${def.name.lowercase()}" else "${def.name.lowercase()} ${cat.route(offer.factionId)?.where ?: "on the road"}"
    }

    /** [title] with its article, for the middle of a sentence: "the hunt on the Ashroad". */
    fun named(ctx: ResolutionContext, offer: MissionOffer): String = title(ctx, offer).let { if (it.startsWith("the ")) it else "the $it" }

    /** The fight of [stageIndex] as the party that left would meet it. Pure: builds the setup, resolves nothing. */
    fun setup(ctx: ResolutionContext, m: MissionInstance, stageIndex: Int): FightSetup? {
        val stage = m.offer.stages[stageIndex]
        val combat = ctx.content.combat ?: return null
        if (stage.enemies.isEmpty()) return null
        val law = lawInForce(ctx)
        fun lawStats(c: Combatant, add: Map<com.tinyblacksmith.core.combat.Stat, Int>) = if (add.isEmpty()) c else c.copy(stats = (c.stats.keys + add.keys).associateWith { (c.stats[it] ?: 0) + (add[it] ?: 0) })
        val modifier = GuildOps.catalog(ctx).modifier(m.offer.modifierId)
        val party = m.party.mapNotNull { id -> ctx.heroes[id]?.takeIf { it.isAlive } }.map { h -> lawStats(lawStats(GuildOps.fighter(ctx, h, m.gear[h.id]?.let { ctx.weapons[it] }, Loadout.Field.ROAD, m.party), law?.partyStats.orEmpty()), modifier?.partyStats.orEmpty()) } +
            listOfNotNull(stage.protectUnitId?.let { Loadout.enemy(combat.unit(it), "protected").copy(side = Side.PARTY) })
        if (party.none { it.key != "protected" }) return null
        // A rescue is never walked into alone: the watch sends a guard for every empty place, and they stand in front.
        val def = GuildOps.catalog(ctx).mission(m.offer.defId)
        val watch = def.watchUnitId?.let { id -> (m.party.size until ctx.config.guild.partyMax).map { i -> Loadout.enemy(combat.unit(id), "watch${i + 1}").copy(side = Side.PARTY) } }.orEmpty()
        // The one who carries a member's family weapon carries what it does, like the nemesis (living heroes).
        val nemesis = ctx.guild?.nemesis?.takeIf { m.offer.subjectWeaponId == it.weaponId }
            ?: m.offer.subjectWeaponId?.takeIf { Hooks.isHookWeapon(ctx, it) }?.let { w -> Nemesis(Hooks.holderName(ctx, m.offer.factionId, w), m.offer.factionId, w, m.offer.postedDay, "") }
        val enemies = stage.enemies.mapIndexed { i, e ->
            val unit = combat.unit(e.unitId)
            val foe = Loadout.enemy(unit, "e${i + 1}", e.percent)
            // The one who carries the smith's blade carries what it does, too: the rules of that blade this side of the field can use.
            if (i == 0 && nemesis != null && stageIndex == m.offer.stages.lastIndex) Stories.armed(ctx, foe, nemesis) else foe
        }.map { lawStats(lawStats(it, law?.enemyStats.orEmpty()), modifier?.enemyStats.orEmpty()) }
        return FightSetup(title(ctx, m.offer).replaceFirstChar { it.uppercase() }, watch + party, enemies, GuildOps.relicRules(ctx, m.relics), stage.objective, m.posture, ctx.config.guild.fight, stage.seed, combat.unitById)
    }

    /** Step 4: the party's day. One stage, or the walk home. */
    fun advance(ctx: ResolutionContext) {
        val g = ctx.guild ?: return
        val m = g.mission ?: return
        if (m.outcome != null || m.departedDay > ctx.day) return
        if (atCheckpoint(m) && m.choice != CheckpointChoice.PUSH) {
            // The smith said "return", or said nothing: the party walks home today with what it secured.
            update(ctx) { it.copy(mission = m.copy(outcome = MissionOutcome.WON, choice = CheckpointChoice.RETURN, returnDay = ctx.day + 1)) }
            ctx.emit(EventType.MISSION_WON, 4, "The party turned back at the checkpoint of ${named(ctx, m.offer)} with the reward it had already won. It is home tomorrow morning.", m.party.map { it.value }, mapOf("mission" to m.id))
            return
        }
        val stage = m.offer.stages[m.stage]
        val def = GuildOps.catalog(ctx).mission(m.offer.defId)
        val setup = setup(ctx, m, m.stage)
        val fight = setup?.let { Fight.resolve(it) }
        fight?.let { com.tinyblacksmith.core.crafting.Interactions.witness(ctx, it) }
        val alive = m.party.filter { ctx.heroes[it]?.isAlive == true }
        val lines = ArrayList<String>()
        val outcome = when (fight?.outcome) {
            null, FightOutcome.WON -> MissionOutcome.WON
            FightOutcome.RETREATED, FightOutcome.TIMED_OUT -> MissionOutcome.RETREATED
            FightOutcome.LOST -> MissionOutcome.LOST
        }
        if (fight != null) aftermath(ctx, m, fight, setup, outcome, lines)
        if (fight != null) Agency.afterFight(ctx, alive, fight, outcome == MissionOutcome.WON, siege = false)
        val traits = alive.mapNotNull { id -> ctx.guild!!.member(id)?.let { GuildOps.catalog(ctx).trait(it.traitId) } }
        var gained = Reward()
        if (outcome == MissionOutcome.WON) {
            gained = stage.reward.copy(gold = stage.reward.gold * (100 + traits.sumOf { it.winGoldPercent }) / 100)
        } else if (outcome == MissionOutcome.RETREATED) {
            // Pulling back is not winning: only gold, and only for a party that knows how to leave.
            val keep = maxOf(traits.maxOfOrNull { it.retreatGoldPercent } ?: 0, if (CombatContent.COWARDS_MEDAL in m.relics) ctx.config.guild.medalRetreatGoldPercent else 0)
            if (keep > 0 && stage.reward.gold > 0) gained = Reward(gold = stage.reward.gold * keep / 100)
        }
        grant(ctx, m, gained, lines)
        if (outcome == MissionOutcome.LOST) losses(ctx, m, fight!!, stage.danger, lines)
        if (outcome != MissionOutcome.WON && m.offer.lapse == GuildContent.LAPSE_SUPPLIER) lines += supplier(ctx, false, "The supplier's cart was lost.")
        // A deed: the weapon that did most of the work in a rescue, a recovery or a deep hall, from the fight's own record.
        if (outcome == MissionOutcome.WON && fight != null && m.stage == m.offer.stages.lastIndex && (def.archetype == MissionArchetype.RESCUE || def.archetype == MissionArchetype.RECOVERY || stage.danger == Danger.LETHAL)) {
            m.party.filter { ctx.heroes[it]?.isAlive == true && m.gear[it]?.let { w -> ctx.weapons[w]?.isLoaned } == true }
                .maxByOrNull { id -> fight.events.count { e -> e.kind == EventKind.DOWNED && e.source == id.value } }?.let { id ->
                    com.tinyblacksmith.core.crafting.Legends.deed(ctx, m.gear.getValue(id), ctx.config.depth.legend.renownDangerous, "${ctx.hero(id).fullName} carried it through ${named(ctx, m.offer)}.", listOf(id.value))
                }
        }
        val continues = outcome == MissionOutcome.WON && m.stage + 1 < m.offer.stages.size
        val after = m.copy(stage = m.stage + 1, secured = m.secured + gained, outcome = if (continues) null else outcome, returnDay = if (continues) m.returnDay else ctx.day + 1)
        update(ctx) { it.copy(mission = after) }
        val where = title(ctx, m.offer)
        val the = named(ctx, m.offer)
        val names = alive.map { ctx.heroes[it]?.fullName ?: "?" }.joinToString(", ")
        val head = when {
            continues && atCheckpoint(after) -> "$names won the first stage of $the. Choose today: go deeper (the next stage is ${Terms.danger(m.offer.stages[m.stage + 1].danger).lowercase()} and pays more) or return. If you choose nothing, they return. Either way they are home the next morning."
            continues -> "$names won the first stage of $the. They fight the next stage tomorrow."
            outcome == MissionOutcome.WON -> "$names completed $the."
            outcome == MissionOutcome.RETREATED -> "$names retreated from $the."
            else -> "$names were defeated at $the."
        }
        val told = fight?.highlights?.firstOrNull()?.text?.let { " $it" }.orEmpty()
        ctx.emit(if (outcome == MissionOutcome.WON) EventType.MISSION_WON else EventType.MISSION_FAILED, if (outcome == MissionOutcome.LOST) 8 else 6, head + told, m.party.map { it.value } + m.gear.values.map { it.value },
            mapOf("mission" to m.id, "def" to m.offer.defId, "outcome" to outcome.name, "stage" to (m.stage + 1).toString()))
        if (outcome == MissionOutcome.WON && !continues) {
            update(ctx) { it.copy(completed = it.completed + (def.archetype.name to (it.completed[def.archetype.name] ?: 0) + 1), members = it.members.map { x -> if (x.heroId in m.party) x.copy(missionWins = x.missionWins + 1) else x }) }
        }
        fight?.let { Stories.noteCombos(ctx, m, it) }
        update(ctx) { it.copy(lastMission = MissionReport(m.id, m.offer.defId, where.replaceFirstChar { c -> c.uppercase() }, ctx.day, m.stage, m.party, outcome, continues, fight?.let { compact(it) }, gained, lines)) }
        if (outcome == MissionOutcome.WON && !continues) { Stories.afterContract(ctx, m); Hooks.won(ctx, m); Agency.ledHome(ctx, m.party) }
    }

    /** What a fight did to those who fought it: health, wounds, wear and cracks, experience. Applied once, here. */
    private fun aftermath(ctx: ResolutionContext, m: MissionInstance, fight: FightResult, setup: FightSetup, outcome: MissionOutcome, lines: MutableList<String>) {
        val cfg = ctx.config.guild
        for (id in m.party) {
            val hero = ctx.heroes[id]?.takeIf { it.isAlive } ?: continue
            val a = fight.actor(id.value) ?: continue
            val health = if (a.downed) cfg.downedHealth else Loadout.heroHealth(a.health, a.maxHealth)
            val won = outcome == MissionOutcome.WON
            ctx.updateHero(hero.copy(health = health, lastActivity = HeroActivity.EXPEDITION, fame = hero.fame + (if (won) cfg.missionFame else 0), victories = hero.victories + (if (won) 1 else 0),
                expeditionWins = hero.expeditionWins + (if (won) 1 else 0), kills = hero.kills + fight.events.count { it.kind == EventKind.DOWNED && it.source == id.value },
                drivenBackOnDay = if (won) hero.drivenBackOnDay else ctx.day))
            Heroes.grantXp(ctx, ctx.hero(id), if (won) cfg.missionXpWin else cfg.missionXpLoss)
            if (a.downed) {
                wound(ctx, id, WoundKind.INJURED, cfg.injuredDays, "Went down at ${named(ctx, m.offer)}.")
                GuildOps.updateMember(ctx, id) { it.copy(timesDowned = it.timesDowned + 1) }
                lines += "${hero.fullName} went down and was carried home: injured for ${cfg.injuredDays} days."
            } else if (health < cfg.exhaustedBelowHealth) {
                wound(ctx, id, WoundKind.EXHAUSTED, cfg.exhaustedDays, "Came home with less than ${cfg.exhaustedBelowHealth} health from ${named(ctx, m.offer)}.")
                lines += "${hero.fullName} came home with less than ${cfg.exhaustedBelowHealth} health and is exhausted for ${cfg.exhaustedDays} ${if (cfg.exhaustedDays == 1) "day" else "days"}."
            }
            m.gear[id]?.let { ctx.weapons[it] }?.let { w ->
                val wear = cfg.missionWear + (if (a.fractured) fractureWear(ctx) else 0)
                ctx.updateWeapon(w.copy(condition = maxOf(0, w.condition - wear), victories = w.victories + (if (won) 1 else 0), fame = w.fame + (if (won) 1 else 0),
                    kills = w.kills + fight.events.count { it.kind == EventKind.DOWNED && it.source == id.value }))
                if (a.fractured) {
                    ctx.addWeaponHistory(w.id, "CRACKED", "Cracked in ${hero.fullName}'s hands at ${named(ctx, m.offer)} and was brought home for repair.", listOf(id.value))
                    lines += "${w.name} cracked in the fight and needs repair (condition ${maxOf(0, w.condition - wear)})."
                } else if (won) ctx.addWeaponHistory(w.id, "VICTORY", "${hero.fullName} carried it on loan through ${named(ctx, m.offer)}.", listOf(id.value))
            }
        }
        if (setup.objective.protect != null && fight.actor("protected")?.downed == true) lines += "The one the party had to protect went down, so the fight was lost."
    }

    /** Condition a weapon cracked in a contract's fight loses on top of the wear: the repair bill, as the law in force sets it. */
    internal fun fractureWear(ctx: ResolutionContext): Int = Fight.pct(ctx.config.guild.fractureWear, lawInForce(ctx)?.repairPercent ?: 100)

    /** The law of the season on [ctx]'s day: announced a morning ahead, it is in force from `fromDay` until `untilDay`. */
    private fun lawInForce(ctx: ResolutionContext) = GuildOps.catalog(ctx).law(ctx.guild?.law?.takeIf { ctx.day in it.fromDay until it.untilDay }?.id)

    private fun wound(ctx: ResolutionContext, id: HeroId, kind: WoundKind, days: Int, cause: String) {
        GuildOps.updateMember(ctx, id) { m -> if (m.wound != null && m.wound.untilDay >= ctx.day + 1 + days && m.wound.kind != WoundKind.SCARRED) m else m.copy(wound = Wound(kind, ctx.day + 1 + days, cause)) }
        ctx.emit(EventType.HERO_WOUNDED, 3, "${ctx.hero(id).fullName} is ${Terms.wound(kind).lowercase()}: $cause", listOf(id.value), mapOf("wound" to kind.name))
    }

    /** What a won stage brings is the forge's at once: it is never taken back, whatever the party meets next. */
    private fun grant(ctx: ResolutionContext, m: MissionInstance, r: Reward, lines: MutableList<String>) {
        if (r.isEmpty) return
        val charter = GuildOps.charter(ctx)
        if (r.gold > 0) {
            var left = r.gold
            for (id in m.party) {
                val member = ctx.guild!!.member(id) ?: continue
                val hero = ctx.heroes[id]?.takeIf { it.isAlive } ?: continue
                val share = r.gold * member.sharePercent / 100
                left -= share
                ctx.updateHero(hero.copy(gold = hero.gold + share))
            }
            val forge = left * (charter?.smithGoldPercent ?: 100) / 100
            ctx.earn(IncomeKind.CONTRACT, forge)
            lines += "Quest gold: ${r.gold}. The forge's share: $forge."
        }
        if (r.materials.isNotEmpty()) {
            r.materials.forEach { (id, n) -> ctx.materials[id] = (ctx.materials[id] ?: 0) + n }
            lines += "Brought back: " + r.materials.entries.joinToString(", ") { "${it.value} ${ctx.content.material(it.key).name}" } + "."
        }
        if (r.suppression > 0) ctx.factions[m.offer.factionId]?.let { f -> ctx.factions[f.id] = f.copy(pressure = maxOf(0, f.pressure - r.suppression)); val drop = f.pressure - maxOf(0, f.pressure - r.suppression); lines += if (drop > 0) "The ${ctx.content.faction(f.id).name} were set back (-$drop pressure)." else "The ${ctx.content.faction(f.id).name} were set back." }
        if (r.integrity > 0) {
            val max = ctx.config.startingForgeIntegrity + ctx.upgradeTotal(com.tinyblacksmith.core.content.UpgradeEffect.STARTING_INTEGRITY)
            // The day's recovery allowance, and the guild's extra for work on the wall, bound it like every other repair (owner decision B).
            val allowance = ctx.recoveryAllowance() + ctx.config.guild.wallWorkExtraRecovery - ctx.contractMendedToday
            val mended = minOf(r.integrity, allowance, max - ctx.town.integrity).coerceAtLeast(0)
            ctx.town = ctx.town.copy(integrity = ctx.town.integrity + mended)
            ctx.contractMendedToday += mended
            lines += when {
                ctx.town.integrity >= max -> if (mended > 0) "Forge health restored (+$mended)." else "The forge was already at full health."
                mended > 0 -> "Forge health restored (+$mended), as much as one day allows."
                else -> "The forge has already had all the restoring one day allows."
            }
        }
        if (r.militia > 0) ctx.town = ctx.town.copy(militia = minOf(ctx.config.militiaMax, ctx.town.militia + r.militia))
        if (r.sabotage) lines += SiegeFight.sabotage(ctx)
        if (r.heartfire > 0) {
            com.tinyblacksmith.core.crafting.Legends.gainHeartfire(ctx, r.heartfire, "The party brought Heartfire back from ${named(ctx, m.offer)}.")
            lines += "Heartfire came back with them. The forge holds ${ctx.heartfire}."
        }
        if (r.supplier) lines += supplier(ctx, true, "The supplier got through.")
        if (r.road) lines += DeepRoad.openTomorrow(ctx, "The caravan master's train got through.")
        if (r.relicOffer && com.tinyblacksmith.core.engine.Relics.offer(ctx, "The party brought something out of the deep hall: you may take a relic.")) lines += "A relic came back with them."
        r.weaponId?.let { id -> ctx.weapons[id]?.let { w ->
            // A member's family weapon goes home to its family, not to the smith's storage.
            if (Hooks.heirloomHome(ctx, id, m.party)) { lines += "${w.name} is back with its family."; return@let }
            ctx.updateWeapon(w.copy(location = WeaponLocation.Storage))
            ctx.addWeaponHistory(id, "RECOVERED", "Won back by the guild's party and returned to the forge.", m.party.map { it.value })
            ctx.emit(EventType.WEAPON_RECOVERED, 6, "${w.name} is back at the forge: the guild's party took it from the enemy who carried it.", listOf(id.value) + m.party.map { it.value }, mapOf(WeaponFate.KEY to WeaponFate.RECOVERED.name))
            lines += "${w.name} is back at the forge."
            Stories.recovered(ctx, id)
        } }
        r.captiveId?.let { id -> rescued(ctx, id, lines) }
    }

    private fun rescued(ctx: ResolutionContext, id: HeroId, lines: MutableList<String>) {
        val g = ctx.guild!!
        val c = g.captives.firstOrNull { it.heroId == id } ?: return
        val hero = ctx.heroes[id]?.takeIf { it.isAlive } ?: return
        update(ctx) { it.copy(captives = it.captives.filter { x -> x.heroId != id }, offers = it.offers.filter { o -> o.subjectHeroId != id },
            members = it.members.map { m -> if (m.heroId == id) m.copy(status = MemberStatus.AWAY, wound = Wound(WoundKind.SCARRED, ctx.day + 1 + ctx.config.guild.scarredDays, "Captured for ${ctx.day - c.capturedDay} days.")) else m }) }
        c.weaponId?.let { ctx.weapons[it] }?.takeIf { (it.location as? WeaponLocation.Lost)?.reason == GuildOps.LOST_WITH_CAPTIVE }?.let { w ->
            ctx.updateWeapon(w.copy(location = WeaponLocation.Loaned(id)))
            ctx.addWeaponHistory(w.id, "RECOVERED", "Brought out with ${hero.fullName}.", listOf(id.value))
        }
        ctx.emit(EventType.MEMBER_RESCUED, 8, "${hero.fullName} was brought out alive.", listOf(id.value))
        lines += "${hero.fullName} is free, and scarred by it for ${ctx.config.guild.scarredDays} days."
        Stories.bondRescue(ctx, id)
    }

    /** The whole party went down. What that costs is what the contract said it would: nothing more on a safe one, a captive on a dangerous one, a death and a captive in a lethal hall. */
    private fun losses(ctx: ResolutionContext, m: MissionInstance, fight: FightResult, danger: Danger, lines: MutableList<String>) {
        if (danger == Danger.SAFE) { lines += "The whole party went down. The town's guards found them on the road and brought them all home."; return }
        val order = fight.events.filter { it.kind == EventKind.DOWNED && it.target != null }.mapNotNull { e -> m.party.firstOrNull { it.value == e.target } }.distinct()
        val standing = order.filter { ctx.heroes[it]?.isAlive == true }
        var taken: HeroId? = standing.lastOrNull()
        if (danger == Danger.LETHAL) standing.firstOrNull()?.let { dead ->
            val hero = ctx.hero(dead)
            seize(ctx, m, dead, GuildOps.LOST_TAKEN)
            Battle.kill(ctx, hero, "fell at ${named(ctx, m.offer)}", weaponRecovered = false, weaponSeized = false)
            GuildOps.forget(ctx, dead)
            lines += "${hero.fullName} did not come back."
            if (taken == dead) taken = null
        }
        taken?.let { id ->
            val hero = ctx.hero(id)
            val blade = seize(ctx, m, id, GuildOps.LOST_WITH_CAPTIVE)
            val deadline = ctx.day + ctx.config.guild.captiveDays
            update(ctx) { it.copy(captives = it.captives + Captive(id, m.offer.factionId, ctx.day, deadline, blade, m.offer.defId), reserved = it.reserved - id,
                members = it.members.map { x -> if (x.heroId == id) x.copy(status = MemberStatus.CAPTURED) else x }) }
            ctx.emit(EventType.MEMBER_CAPTURED, 8, "${hero.fullName} was captured at ${named(ctx, m.offer)}." + (blade?.let { " ${ctx.weapon(it).name} went with them." } ?: ""), listOfNotNull(id.value, blade?.value), mapOf("deadline" to deadline.toString()))
            lines += "${hero.fullName} was captured. Send a rescue quest by day ${deadline - 1} to bring them home."
        }
        lines += "The others came home."
    }

    /** The loan [id] carried leaves the guild's hands with them. Their own blade is theirs: `Battle.kill` and capture do not touch it here. */
    private fun seize(ctx: ResolutionContext, m: MissionInstance, id: HeroId, reason: String): WeaponId? {
        val w = GuildOps.loanOf(ctx, id) ?: return null
        ctx.updateWeapon(w.copy(location = WeaponLocation.Lost(ctx.day, reason)))
        ctx.addWeaponHistory(w.id, if (reason == GuildOps.LOST_TAKEN) "SEIZED" else "HELD", if (reason == GuildOps.LOST_TAKEN) "Taken by the enemy when ${ctx.hero(id).fullName} fell at ${named(ctx, m.offer)}." else "Taken with ${ctx.hero(id).fullName}, who was captured.", listOf(id.value))
        if (reason == GuildOps.LOST_TAKEN) {
            ctx.emit(EventType.WEAPON_STOLEN, 6, "${w.name}, on loan to ${ctx.hero(id).fullName}, was taken by the enemy.", listOf(w.id.value, id.value), mapOf(WeaponFate.KEY to WeaponFate.SEIZED.name))
            Stories.taken(ctx, w.id, m.offer.factionId)
        }
        return w.id
    }

    /**
     * The record a save keeps of a fight: every line that says something, re-linked past the silent ones. From
     * [FightResult.RECORD] on, a silent line that moves a number is kept too (a blow Guard took in part, healing past
     * full, a state spent, the start of a tick), so the replay and the report can account for every point; only the
     * markers of a round's end and of an actor having acted go.
     */
    fun compact(r: FightResult): FightResult {
        val byId = r.events.associateBy { it.id }
        fun kept(e: FightEvent): Boolean = e.text.isNotEmpty() || (r.record >= FightResult.RECORD && e.kind != EventKind.ROUND_END && e.kind != EventKind.ACTED)
        fun keptParent(id: Int?): Int? = generateSequence(id?.let(byId::get)) { it.parentId?.let(byId::get) }.firstOrNull(::kept)?.id
        return r.copy(events = r.events.filter(::kept).map { it.copy(parentId = keptParent(it.parentId)) })
    }

    // ---- morning ----

    /** A party whose work is done is home this morning. A captive whose time ran out is not coming. */
    fun morning(ctx: ResolutionContext) {
        val g = ctx.guild ?: return
        g.mission?.let { m ->
            if (m.outcome != null && ctx.day >= m.returnDay) {
                update(ctx) { it.copy(mission = null, members = it.members.map { x -> if (x.status == MemberStatus.AWAY) x.copy(status = MemberStatus.HOME) else x }) }
            }
        }
        for (c in ctx.guild!!.captives.filter { ctx.day >= it.deadlineDay && ctx.guild!!.mission?.offer?.subjectHeroId != it.heroId }) {
            val hero = ctx.heroes[c.heroId] ?: continue
            c.weaponId?.let { ctx.weapons[it] }?.let { w ->
                ctx.updateWeapon(w.copy(location = WeaponLocation.Lost(ctx.day, GuildOps.LOST_TAKEN)))
                ctx.addWeaponHistory(w.id, "SEIZED", "Kept by the captors of ${hero.fullName}.", listOf(hero.id.value))
                Stories.taken(ctx, w.id, c.factionId)
            }
            if (hero.isAlive) Battle.kill(ctx, hero, "was not brought out of ${GuildOps.catalog(ctx).route(c.factionId)?.name ?: "the enemy's camp"} in time", weaponRecovered = false)
            GuildOps.forget(ctx, c.heroId)
        }
        refreshBoard(ctx)
    }
}
