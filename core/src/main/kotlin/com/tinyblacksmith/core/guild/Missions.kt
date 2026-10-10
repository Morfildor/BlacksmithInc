package com.tinyblacksmith.core.guild

import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.combat.Combatant
import com.tinyblacksmith.core.combat.EventKind
import com.tinyblacksmith.core.combat.Fight
import com.tinyblacksmith.core.combat.FightOutcome
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
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.heroes.Heroes
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.rng.Rng
import com.tinyblacksmith.core.rng.RngStream

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
    private fun special(defId: String): Boolean = defId == GuildContent.RESCUE || defId == GuildContent.RECOVERY

    /** Every morning: what has lapsed leaves, the standing work is there, free slots are filled, a captive or a taken blade has its contract. */
    fun refreshBoard(ctx: ResolutionContext) {
        val g = ctx.guild ?: return
        val cat = GuildOps.catalog(ctx)
        val cfg = ctx.config.guild
        val rng = ctx.rng(RngStream.GUILD)
        var offers = g.offers.filter { o ->
            o.expiresDay >= ctx.day && cat.missionById[o.defId] != null &&
                (o.subjectHeroId == null || g.captives.any { it.heroId == o.subjectHeroId }) && (o.subjectWeaponId == null || g.nemesis?.weaponId == o.subjectWeaponId)
        }
        var serial = g.nextMissionSerial
        fun post(def: MissionDef, faction: FactionId, hero: HeroId? = null, weapon: WeaponId? = null, reason: String? = null, expires: Int = ctx.day + def.lifeDays - 1) {
            offers = offers + generate(ctx, def, faction, rng, "m${serial++}", hero, weapon, reason, expires)
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
        cat.missions.firstOrNull { it.archetype == MissionArchetype.SABOTAGE }?.let { def ->
            if (untilSiege in 0..cfg.sabotageWindowDays && open(def) && (ctx.siege?.sabotaged ?: 0) == 0 && offers.none { it.defId == def.id }) post(def, Battle.besieger(ctx)?.id ?: anyFaction())
        }
        val slots = cfg.offersOnBoard + (charter?.extraOffers ?: 0)
        fun ordinary() = offers.count { !special(it.defId) && it.defId != GuildContent.WALL_WORK }
        while (ordinary() < slots) {
            val pool = cat.missions.filter { drawn(it) && it.archetype != MissionArchetype.SUPPLY && it.archetype != MissionArchetype.SABOTAGE && open(it) && offers.none { o -> o.defId == it.id } }
            if (pool.isEmpty()) break
            post(rng.pickWeighted(pool.map { it to it.weight }), anyFaction())
        }
        for (c in g.captives) if (offers.none { it.subjectHeroId == c.heroId }) cat.missionById[GuildContent.RESCUE]?.let { def ->
            val hero = ctx.heroes[c.heroId] ?: return@let
            val blade = c.weaponId?.let { ctx.weapons[it] }
            post(def, c.factionId, hero = c.heroId, reason = "${hero.fullName} is alive ${cat.route(c.factionId)?.where ?: "in their camp"}." + (blade?.let { " The loaned ${it.name} is with the captor." } ?: ""), expires = c.deadlineDay - 1)
        }
        g.nemesis?.let { n -> if (offers.none { it.subjectWeaponId == n.weaponId }) cat.missionById[GuildContent.RECOVERY]?.let { def ->
            ctx.weapons[n.weaponId]?.let { blade -> post(def, n.factionId, weapon = n.weaponId, reason = "${n.name} carries ${blade.name}, a blade of this forge.") }
        } }
        update(ctx) { it.copy(offers = offers, nextMissionSerial = serial) }
    }

    private fun drawMaterials(ctx: ResolutionContext, rng: Rng, draw: MaterialDraw?): Map<MaterialId, Int> {
        if (draw == null) return emptyMap()
        val pool = ctx.content.materials.filter { it.tier in draw.minTier..draw.maxTier && (if (draw.scarce) it.category == MaterialCategory.CATALYST || it.dailySupplierStock != null else it.category != MaterialCategory.CATALYST) }
            .ifEmpty { ctx.content.materials.filter { it.category != MaterialCategory.CATALYST } }
        val got = HashMap<MaterialId, Int>()
        repeat(draw.count) { val m = rng.pick(pool); got[m.id] = (got[m.id] ?: 0) + 1 }
        return got.toSortedMap(compareBy { it.value })
    }

    fun generate(ctx: ResolutionContext, def: MissionDef, faction: FactionId, rng: Rng, id: String, hero: HeroId? = null, weapon: WeaponId? = null, reason: String? = null, expires: Int = ctx.day + maxOf(1, def.lifeDays + (GuildOps.catalog(ctx).rank(ctx.guild?.rank ?: 0)?.offerLifeDelta ?: 0)) - 1): MissionOffer {
        val cfg = ctx.config.guild
        val pressure = ctx.factions[faction]?.pressure ?: 0
        val rank = GuildOps.catalog(ctx).rank(ctx.guild?.rank ?: 0)
        val base = cfg.enemyPercentBase + (cfg.enemyPercentPerDay * ctx.day + cfg.enemyPercentPerPressure * pressure).toInt() + (rank?.enemyPercent ?: 0)
        val stages = def.stages.mapIndexed { i, s ->
            val percent = Fight.pct(base, s.percent)
            val gold = if (s.goldMax > 0) (rng.nextInt(s.goldMin, s.goldMax) * (1.0 + cfg.rewardPercentPerDay * ctx.day / 100.0)).toInt() else 0
            val last = i == def.stages.lastIndex
            MissionStage(
                enemies = s.rosters[faction].orEmpty().map { EnemySpec(it, percent) },
                objective = Objective(s.objective, s.rounds, s.protectUnitId?.let { "protected" }), danger = s.danger, seed = rng.nextLong(),
                reward = Reward(gold, drawMaterials(ctx, rng, s.materials), s.suppression, s.integrity, s.militia, s.sabotage, s.relicOffer, weapon.takeIf { last }, hero.takeIf { last }),
                protectUnitId = s.protectUnitId,
            )
        }
        return MissionOffer(id, def.id, faction, ctx.day, if (def.id == GuildContent.WALL_WORK) Int.MAX_VALUE else expires, def.days, def.fee, stages, def.optionalPush, reason, hero, weapon)
    }

    // ---- planning ----

    fun plan(ctx: ResolutionContext, cmd: Command.PlanDeployment): GameError? {
        val g = ctx.guild!!
        if (g.mission != null) return GameError.PartyAway
        val offer = g.offers.firstOrNull { it.id == cmd.offerId } ?: return GameError.MissionNotOffered(cmd.offerId)
        val ids = cmd.heroIds
        if (ids.isEmpty() || ids.size > ctx.config.guild.partyMax || ids.toSet().size != ids.size) return GameError.PartyInvalid("A party is one to ${ctx.config.guild.partyMax} members.")
        for (id in ids) {
            val m = g.member(id) ?: return GameError.NotAMember(id)
            GuildOps.unavailable(ctx, m)?.let { return GameError.MemberUnavailable(id, it) }
            if (id in g.reserved) return GameError.MemberUnavailable(id, "is kept for the wall")
        }
        // The fee of a plan made earlier today comes back before this one's is taken: replacing a plan never costs twice.
        val back = g.planned?.let { p -> g.offers.firstOrNull { it.id == p.offerId }?.fee } ?: 0
        if (ctx.gold + back < offer.fee) return GameError.NotEnoughGold(offer.fee, ctx.gold + back)
        ctx.gold += back - offer.fee
        update(ctx) { it.copy(planned = PlannedDeployment(offer.id, ids, cmd.posture)) }
        return null
    }

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
        update(ctx) { it.copy(mission = instance, offers = it.offers.filter { o -> o.id != offer.id }, members = it.members.map { m -> if (m.heroId in party) m.copy(status = MemberStatus.AWAY, missions = m.missions + 1) else m }) }
        val names = party.map { ctx.hero(it).fullName }
        ctx.emit(EventType.MISSION_DEPARTED, 4, "${names.joinToString(", ")} left for ${title(ctx, offer)}." + (if (offer.fee > 0) " The forge had paid ${offer.fee} gold to send them." else ""), party.map { it.value },
            mapOf("mission" to offer.id, "def" to offer.defId))
    }

    fun title(ctx: ResolutionContext, offer: MissionOffer): String {
        val cat = GuildOps.catalog(ctx)
        val def = cat.mission(offer.defId)
        return if (def.archetype == MissionArchetype.EMERGENCY) def.name.lowercase() else "${def.name.lowercase()} ${cat.route(offer.factionId)?.where ?: "on the road"}"
    }

    /** The fight of [stageIndex] as the party that left would meet it. Pure: builds the setup, resolves nothing. */
    fun setup(ctx: ResolutionContext, m: MissionInstance, stageIndex: Int): FightSetup? {
        val stage = m.offer.stages[stageIndex]
        val combat = ctx.content.combat ?: return null
        if (stage.enemies.isEmpty()) return null
        val law = GuildOps.catalog(ctx).law(ctx.guild?.law?.takeIf { ctx.day in it.fromDay until it.untilDay }?.id)
        fun lawStats(c: Combatant, add: Map<com.tinyblacksmith.core.combat.Stat, Int>) = if (add.isEmpty()) c else c.copy(stats = (c.stats.keys + add.keys).associateWith { (c.stats[it] ?: 0) + (add[it] ?: 0) })
        val party = m.party.mapNotNull { id -> ctx.heroes[id]?.takeIf { it.isAlive } }.map { h -> lawStats(GuildOps.fighter(ctx, h, m.gear[h.id]?.let { ctx.weapons[it] }, Loadout.Field.ROAD, m.party), law?.partyStats.orEmpty()) } +
            listOfNotNull(stage.protectUnitId?.let { Loadout.enemy(combat.unit(it), "protected").copy(side = Side.PARTY) })
        if (party.none { it.key != "protected" }) return null
        val nemesis = ctx.guild?.nemesis?.takeIf { m.offer.subjectWeaponId == it.weaponId }
        val enemies = stage.enemies.mapIndexed { i, e ->
            val unit = combat.unit(e.unitId)
            val foe = Loadout.enemy(unit, "e${i + 1}", e.percent)
            // The one who carries the smith's blade carries what it does, too: the rules of that blade this side of the field can use.
            if (i == 0 && nemesis != null && stageIndex == m.offer.stages.lastIndex) Stories.armed(ctx, foe, nemesis) else foe
        }.map { lawStats(it, law?.enemyStats.orEmpty()) }
        return FightSetup(title(ctx, m.offer).replaceFirstChar { it.uppercase() }, party, enemies, GuildOps.relicRules(ctx, m.relics), stage.objective, m.posture, ctx.config.guild.fight, stage.seed, combat.unitById)
    }

    /** Step 4: the party's day. One stage, or the walk home. */
    fun advance(ctx: ResolutionContext) {
        val g = ctx.guild ?: return
        val m = g.mission ?: return
        if (m.outcome != null || m.departedDay > ctx.day) return
        if (atCheckpoint(m) && m.choice != CheckpointChoice.PUSH) {
            // The smith said "return", or said nothing: the party walks home today with what it secured.
            update(ctx) { it.copy(mission = m.copy(outcome = MissionOutcome.WON, choice = CheckpointChoice.RETURN, returnDay = ctx.day + 1)) }
            ctx.emit(EventType.MISSION_WON, 4, "The party turned for home from ${title(ctx, m.offer)} with what it had secured.", m.party.map { it.value }, mapOf("mission" to m.id))
            return
        }
        val stage = m.offer.stages[m.stage]
        val def = GuildOps.catalog(ctx).mission(m.offer.defId)
        val setup = setup(ctx, m, m.stage)
        val fight = setup?.let { Fight.resolve(it) }
        val alive = m.party.filter { ctx.heroes[it]?.isAlive == true }
        val lines = ArrayList<String>()
        val outcome = when (fight?.outcome) {
            null, FightOutcome.WON -> MissionOutcome.WON
            FightOutcome.RETREATED, FightOutcome.TIMED_OUT -> MissionOutcome.RETREATED
            FightOutcome.LOST -> MissionOutcome.LOST
        }
        if (fight != null) aftermath(ctx, m, fight, setup, outcome, lines)
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
        val continues = outcome == MissionOutcome.WON && m.stage + 1 < m.offer.stages.size
        val after = m.copy(stage = m.stage + 1, secured = m.secured + gained, outcome = if (continues) null else outcome, returnDay = if (continues) m.returnDay else ctx.day + 1)
        update(ctx) { it.copy(mission = after) }
        val where = title(ctx, m.offer)
        val names = alive.map { ctx.heroes[it]?.fullName ?: "?" }.joinToString(", ")
        val head = when {
            continues && atCheckpoint(after) -> "$names won the first stage of $where. Deeper lies more; the smith decides in the morning."
            continues -> "$names won the first day of $where and press on tomorrow."
            outcome == MissionOutcome.WON -> "$names finished $where."
            outcome == MissionOutcome.RETREATED -> "$names pulled back from $where."
            else -> "$names were beaten at $where."
        }
        val told = fight?.highlights?.firstOrNull()?.text?.let { " $it" }.orEmpty()
        ctx.emit(if (outcome == MissionOutcome.WON) EventType.MISSION_WON else EventType.MISSION_FAILED, if (outcome == MissionOutcome.LOST) 8 else 6, head + told, m.party.map { it.value } + m.gear.values.map { it.value },
            mapOf("mission" to m.id, "def" to m.offer.defId, "outcome" to outcome.name, "stage" to (m.stage + 1).toString()))
        if (outcome == MissionOutcome.WON && !continues) {
            update(ctx) { it.copy(completed = it.completed + (def.archetype.name to (it.completed[def.archetype.name] ?: 0) + 1), members = it.members.map { x -> if (x.heroId in m.party) x.copy(missionWins = x.missionWins + 1) else x }) }
        }
        fight?.let { Stories.noteCombos(ctx, m, it) }
        update(ctx) { it.copy(lastMission = MissionReport(m.id, m.offer.defId, where.replaceFirstChar { c -> c.uppercase() }, ctx.day, m.stage, m.party, outcome, continues, fight?.let { compact(it) }, gained, lines)) }
        if (outcome == MissionOutcome.WON && !continues) Stories.afterContract(ctx, m)
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
                wound(ctx, id, WoundKind.INJURED, cfg.injuredDays, "Went down at ${title(ctx, m.offer)}.")
                GuildOps.updateMember(ctx, id) { it.copy(timesDowned = it.timesDowned + 1) }
                lines += "${hero.fullName} went down and was carried out: injured for ${cfg.injuredDays} days."
            } else if (health < cfg.exhaustedBelowHealth) {
                wound(ctx, id, WoundKind.EXHAUSTED, cfg.exhaustedDays, "Came back spent from ${title(ctx, m.offer)}.")
                lines += "${hero.fullName} came through badly hurt: exhausted for ${cfg.exhaustedDays} day."
            }
            m.gear[id]?.let { ctx.weapons[it] }?.let { w ->
                val wear = cfg.missionWear + (if (a.fractured) Fight.pct(cfg.fractureWear, GuildOps.catalog(ctx).law(ctx.guild?.law?.id)?.repairPercent ?: 100) else 0)
                ctx.updateWeapon(w.copy(condition = maxOf(0, w.condition - wear), victories = w.victories + (if (won) 1 else 0), fame = w.fame + (if (won) 1 else 0),
                    kills = w.kills + fight.events.count { it.kind == EventKind.DOWNED && it.source == id.value }))
                if (a.fractured) {
                    ctx.addWeaponHistory(w.id, "CRACKED", "Cracked in ${hero.fullName}'s hands at ${title(ctx, m.offer)} and was brought home to mend.", listOf(id.value))
                    lines += "${w.name} cracked in the fight: it needs the hone (condition ${maxOf(0, w.condition - wear)})."
                } else if (won) ctx.addWeaponHistory(w.id, "VICTORY", "${hero.fullName} carried it on loan through ${title(ctx, m.offer)}.", listOf(id.value))
            }
        }
        if (setup.objective.protect != null && fight.actor("protected")?.downed == true) lines += "What the party was there to protect was lost."
    }

    private fun wound(ctx: ResolutionContext, id: HeroId, kind: WoundKind, days: Int, cause: String) {
        GuildOps.updateMember(ctx, id) { m -> if (m.wound != null && m.wound.untilDay >= ctx.day + 1 + days && m.wound.kind != WoundKind.SCARRED) m else m.copy(wound = Wound(kind, ctx.day + 1 + days, cause)) }
        ctx.emit(EventType.HERO_WOUNDED, 3, "${ctx.hero(id).fullName} is ${kind.name.lowercase()}: $cause", listOf(id.value), mapOf("wound" to kind.name))
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
            lines += "Contract gold: ${r.gold}. The forge's part: $forge."
        }
        if (r.materials.isNotEmpty()) {
            r.materials.forEach { (id, n) -> ctx.materials[id] = (ctx.materials[id] ?: 0) + n }
            lines += "Brought back: " + r.materials.entries.joinToString(", ") { "${it.value} ${ctx.content.material(it.key).name}" } + "."
        }
        if (r.suppression > 0) ctx.factions[m.offer.factionId]?.let { f -> ctx.factions[f.id] = f.copy(pressure = maxOf(0, f.pressure - r.suppression)); lines += "${ctx.content.faction(f.id).name} were set back." }
        if (r.integrity > 0) {
            val max = ctx.config.startingForgeIntegrity + ctx.upgradeTotal(com.tinyblacksmith.core.content.UpgradeEffect.STARTING_INTEGRITY)
            val mended = minOf(r.integrity, max - ctx.town.integrity).coerceAtLeast(0)
            ctx.town = ctx.town.copy(integrity = ctx.town.integrity + mended)
            lines += if (mended > 0) "The wall was mended (+$mended integrity)." else "The wall needed no mending."
        }
        if (r.militia > 0) ctx.town = ctx.town.copy(militia = minOf(ctx.config.militiaMax, ctx.town.militia + r.militia))
        if (r.sabotage) lines += SiegeFight.sabotage(ctx)
        if (r.relicOffer && com.tinyblacksmith.core.engine.Relics.offer(ctx, "The party brought something out of the deep hall: the smith may take a relic.")) lines += "A relic came back with them."
        r.weaponId?.let { id -> ctx.weapons[id]?.let { w ->
            ctx.updateWeapon(w.copy(location = WeaponLocation.Storage))
            ctx.addWeaponHistory(id, "RECOVERED", "Won back by the guild's party and returned to the forge.", m.party.map { it.value })
            ctx.emit(EventType.WEAPON_RECOVERED, 6, "${w.name} is back at the forge: the guild's party took it from the one who carried it.", listOf(id.value) + m.party.map { it.value }, mapOf(WeaponFate.KEY to WeaponFate.RECOVERED.name))
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
            members = it.members.map { m -> if (m.heroId == id) m.copy(status = MemberStatus.AWAY, wound = Wound(WoundKind.SCARRED, ctx.day + 1 + ctx.config.guild.scarredDays, "Held by the enemy for ${ctx.day - c.capturedDay} days.")) else m }) }
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
        if (danger == Danger.SAFE) { lines += "The watch found them on the road and brought them all home."; return }
        val order = fight.events.filter { it.kind == EventKind.DOWNED && it.target != null }.mapNotNull { e -> m.party.firstOrNull { it.value == e.target } }.distinct()
        val standing = order.filter { ctx.heroes[it]?.isAlive == true }
        var taken: HeroId? = standing.lastOrNull()
        if (danger == Danger.LETHAL) standing.firstOrNull()?.let { dead ->
            val hero = ctx.hero(dead)
            seize(ctx, m, dead, GuildOps.LOST_TAKEN)
            Battle.kill(ctx, hero, "fell at ${title(ctx, m.offer)}", weaponRecovered = false, weaponSeized = false)
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
            ctx.emit(EventType.MEMBER_CAPTURED, 8, "${hero.fullName} was taken alive at ${title(ctx, m.offer)}." + (blade?.let { " ${ctx.weapon(it).name} went with them." } ?: ""), listOfNotNull(id.value, blade?.value), mapOf("deadline" to deadline.toString()))
            lines += "${hero.fullName} was taken alive. They can be brought out until day ${deadline - 1}."
        }
        lines += "The rest dragged themselves home."
    }

    /** The loan [id] carried leaves the guild's hands with them. Their own blade is theirs: `Battle.kill` and capture do not touch it here. */
    private fun seize(ctx: ResolutionContext, m: MissionInstance, id: HeroId, reason: String): WeaponId? {
        val w = GuildOps.loanOf(ctx, id) ?: return null
        ctx.updateWeapon(w.copy(location = WeaponLocation.Lost(ctx.day, reason)))
        ctx.addWeaponHistory(w.id, if (reason == GuildOps.LOST_TAKEN) "SEIZED" else "HELD", if (reason == GuildOps.LOST_TAKEN) "Taken by the enemy when ${ctx.hero(id).fullName} fell at ${title(ctx, m.offer)}." else "Taken with ${ctx.hero(id).fullName}, who was captured.", listOf(id.value))
        if (reason == GuildOps.LOST_TAKEN) {
            ctx.emit(EventType.WEAPON_STOLEN, 6, "${w.name}, on loan to ${ctx.hero(id).fullName}, was taken by the enemy.", listOf(w.id.value, id.value), mapOf(WeaponFate.KEY to WeaponFate.SEIZED.name))
            Stories.taken(ctx, w.id, m.offer.factionId)
        }
        return w.id
    }

    /** The record a save keeps of a fight: every line that says something, re-linked past the silent ones. */
    fun compact(r: FightResult): FightResult {
        val byId = r.events.associateBy { it.id }
        fun shownParent(id: Int?): Int? = generateSequence(id?.let(byId::get)) { it.parentId?.let(byId::get) }.firstOrNull { it.text.isNotEmpty() }?.id
        return r.copy(events = r.events.filter { it.text.isNotEmpty() }.map { it.copy(parentId = shownParent(it.parentId)) })
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
