package com.tinyblacksmith.core.heroes

import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.content.Element
import com.tinyblacksmith.core.content.FactionDef
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.rng.Rng
import com.tinyblacksmith.core.rng.RngStream

/** Hero generation and daily autonomous activity selection (GDD 6 PROPOSED utility model). */
object Heroes {

    fun generate(ctx: ResolutionContext, rng: Rng, descendantOf: LineageAnchor? = null): Hero {
        val content = ctx.content
        val cls = descendantOf?.let { content.classById[it.classId] } ?: rng.pick(content.classes)
        val name = rng.pick(content.firstNames)
        val surname = descendantOf?.surname ?: rng.pick(content.surnames)
        val traitCount = rng.nextInt(2, 3)
        val traits = mutableListOf<TraitId>()
        val pool = content.traits.map { it.id }.toMutableList()
        repeat(traitCount) { if (pool.isNotEmpty()) { val t = rng.pick(pool); traits += t; pool.remove(t) } }
        if (descendantOf != null && TraitId("loyal") in content.traitById && TraitId("loyal") !in traits) traits += TraitId("loyal")
        val tastePool = content.materials.mapNotNull { it.element }.distinct()  // only elements a weapon in this catalog can carry
        val taste: Element? = if (rng.chance(0.6)) cls.preferredElement else if (rng.chance(0.5) && tastePool.isNotEmpty()) rng.pick(tastePool) else null
        return Hero(
            id = ctx.newHeroId(), name = name, surname = surname, classId = cls.id,
            level = if (descendantOf != null) 2 else 1, xp = 0,
            gold = rng.nextInt(cls.startingGoldMin, cls.startingGoldMax), health = 100,
            traits = traits, elementTaste = taste, descendantOf = descendantOf?.heroName,
            ambition = rng.pick(Ambition.entries),
        )
    }

    fun resolveActivities(ctx: ResolutionContext) {
        val config = ctx.config
        val content = ctx.content
        val rng = ctx.rng(RngStream.HEROES)
        // Heroes act against the most pressing faction (the one that would besiege next); ties break by ID for determinism.
        val faction = ctx.factions.values.sortedBy { it.id.value }.maxByOrNull { it.pressure } ?: return
        val factionDef = content.faction(faction.id)
        val atHall = mutableListOf<HeroId>()
        for (h in ctx.aliveHeroes()) {
            val hero = ctx.hero(h.id)
            if (!hero.isAlive) continue
            if (hero.lastActivity == HeroActivity.SHOP && ctx.visits.any { it.heroId == hero.id && it.purchasedWeaponId != null }) {
                // A hero who bought a weapon today still acts; shopping is a morning errand.
            }
            if (hero.health < config.heroWoundedThreshold) {
                rest(ctx, hero)
                continue
            }
            val weapon = ctx.equippedWeapon(hero.id)
            when (rng.pickWeighted(activityWeights(ctx, hero, weapon, faction))) {
                HeroActivity.EXPEDITION -> expedition(ctx, hero, weapon, faction.id, factionDef)
                HeroActivity.PATROL -> patrol(ctx, hero, faction.id)
                HeroActivity.GUILD -> { train(ctx, hero); atHall += hero.id }
                HeroActivity.AMBITION -> pursue(ctx, hero, weapon, faction.id, factionDef)
                else -> rest(ctx, hero)
            }
        }
        mentor(ctx, atHall)
        arrivals(ctx, rng)
    }

    /**
     * GDD 6 utility model: the activities open to a healthy hero today and their weights. Traits weigh on expedition,
     * patrol, rest and the hall; the other inputs are faction pressure (expedition), wounds (rest), money (an unarmed
     * hero short of gold takes the town's patrol pay) and prior history (a hero driven back yesterday lies low or goes
     * to the hall). GUILD is listed only when the hero has a hall to go to ([canTrain]), AMBITION only while the
     * hero's ambition is unfulfilled.
     */
    fun activityWeights(ctx: ResolutionContext, hero: Hero, weapon: Weapon?, faction: FactionState): List<Pair<HeroActivity, Double>> {
        val config = ctx.config
        val traitDefs = hero.traits.map { ctx.content.trait(it) }
        val poor = weapon == null && hero.gold < config.heroLife.poorHeroGold
        val setback = hero.drivenBackOnDay == ctx.day - 1
        val expedition = (1.0 + traitDefs.sumOf { it.expeditionWeight } + faction.pressure / 100.0 * 0.5 + (if (weapon != null) 0.5 else -0.3)).coerceAtLeast(0.05)
        val patrol = (0.8 + traitDefs.sumOf { it.patrolWeight } + (if (ctx.town.integrity < 60) 0.4 else 0.0) + (if (poor) config.heroLife.poorPatrolWeight else 0.0)).coerceAtLeast(0.05)
        val rest = (0.2 + traitDefs.sumOf { it.restWeight } + (100 - hero.health) / 100.0 + (if (setback) config.heroLife.setbackRestWeight else 0.0)).coerceAtLeast(0.02)
        return buildList {
            add(HeroActivity.EXPEDITION to expedition)
            add(HeroActivity.PATROL to patrol)
            add(HeroActivity.REST to rest)
            if (canTrain(ctx, hero)) add(HeroActivity.GUILD to (config.heroLife.guildBaseWeight + traitDefs.sumOf { it.guildWeight } + (if (setback) config.heroLife.setbackGuildWeight else 0.0)).coerceAtLeast(0.02))
            if (hero.ambition != null && !hero.ambitionDone) add(HeroActivity.AMBITION to config.ambitionActivityWeight)
        }
    }

    /** A hall is open to a guild's members, to anyone once a guild stands in town (training there enrols them), and to a hero famous enough to found the town's first. */
    private fun canTrain(ctx: ResolutionContext, hero: Hero): Boolean =
        hero.guildId != null || ctx.town.guilds.isNotEmpty() || hero.fame >= ctx.config.guildFameThreshold

    /** An expedition, plain or a slayer's hunt. A hero who comes back beaten remembers the day ([Hero.drivenBackOnDay]). */
    private fun expedition(ctx: ResolutionContext, hero: Hero, weapon: Weapon?, factionId: FactionId, faction: FactionDef, eliteChanceBonus: Double = 0.0) {
        Battle.resolveExpedition(ctx, hero, weapon, factionId, faction, eliteChanceBonus)
        val after = ctx.hero(hero.id)
        if (after.isAlive && after.expeditionWins == hero.expeditionWins) ctx.updateHero(after.copy(drivenBackOnDay = ctx.day))
    }

    private fun rest(ctx: ResolutionContext, hero: Hero) {
        val healed = minOf(100, hero.health + ctx.config.heroRestHeal)
        ctx.updateHero(hero.copy(health = healed, lastActivity = HeroActivity.REST))
        val e = ctx.emit(EventType.HERO_RESTED, 0, "${hero.fullName} rested and recovered.", listOf(hero.id.value))
        ctx.field += FieldResult(hero.id, hero.fullName, FieldOutcome.RESTED, eventIds = listOf(e.id))
    }

    private fun patrol(ctx: ResolutionContext, hero: Hero, factionId: FactionId) {
        val config = ctx.config
        ctx.patrolsToday += 1
        ctx.town = ctx.town.copy(militia = minOf(config.militiaMax, ctx.town.militia + config.patrolMilitiaGain))
        val f = ctx.factions.getValue(factionId)
        ctx.factions[factionId] = f.copy(suppressionToday = f.suppressionToday + config.patrolSuppression)
        grantXp(ctx, hero.copy(lastActivity = HeroActivity.PATROL, gold = hero.gold + config.patrolGold), config.patrolXp)
        val e = ctx.emit(EventType.HERO_PATROLLED, 1, "${hero.fullName} patrolled the town walls.", listOf(hero.id.value))
        ctx.field += FieldResult(hero.id, hero.fullName, FieldOutcome.PATROLLED, factionId = factionId, gold = config.patrolGold, eventIds = listOf(e.id))
    }

    /**
     * A day at the guild hall: bounded XP and a small heal; no gold, no suppression, no militia. A hero without a guild
     * joins the oldest one in town, or founds the first (only a hero with the fame for it gets here, see [canTrain]).
     */
    private fun train(ctx: ResolutionContext, hero: Hero) {
        val config = ctx.config
        var h = hero
        if (h.guildId == null) {
            val standing = ctx.town.guilds.firstOrNull()
            if (standing == null) foundGuild(ctx, h)
            else {
                ctx.updateHero(h.copy(guildId = standing.id))
                ctx.emit(EventType.GUILD_JOINED, 2, "${h.fullName} joined ${standing.name}.", listOf(h.id.value), mapOf("guild" to standing.id))
            }
            h = ctx.hero(h.id)
        }
        val guild = ctx.town.guilds.first { it.id == h.guildId }
        val e = ctx.emit(EventType.GUILD_TRAINED, 0, "${h.fullName} trained at the hall of ${guild.name}.", listOf(h.id.value), mapOf("guild" to guild.id))
        ctx.field += FieldResult(h.id, h.fullName, FieldOutcome.GUILD_TRAINED, eventIds = listOf(e.id))
        grantXp(ctx, h.copy(health = minOf(100, h.health + config.heroLife.guildHeal), lastActivity = HeroActivity.GUILD), config.heroLife.guildXp)
    }

    /**
     * Mentoring at the hall: each hero who trained today learns from the highest-level guildmate who trained beside them
     * and outranks them (ties by ID). Once a day per hero, no RNG; the first mentor's name stays on the hero's record.
     */
    private fun mentor(ctx: ResolutionContext, atHall: List<HeroId>) {
        val present = atHall.map { ctx.hero(it) }
        for (pupil in present) {
            val mentor = present.filter { it.guildId == pupil.guildId && it.level > pupil.level }.sortedBy { it.id.value }.maxByOrNull { it.level } ?: continue
            val e = ctx.emit(EventType.GUILD_MENTORED, 2, "${pupil.fullName} was taught by ${mentor.fullName} at the guild hall.", listOf(pupil.id.value, mentor.id.value))
            ctx.field += FieldResult(pupil.id, pupil.fullName, FieldOutcome.GUILD_LESSON, withHeroId = mentor.id, eventIds = listOf(e.id))
            ctx.field += FieldResult(mentor.id, mentor.fullName, FieldOutcome.GUILD_TAUGHT, withHeroId = pupil.id, eventIds = listOf(e.id))
            grantXp(ctx, pupil.copy(mentorName = pupil.mentorName ?: mentor.fullName), ctx.config.heroLife.mentorXp)
        }
    }

    /** A day given to the hero's own ambition (GDD 6): one action per ambition, open only while it is unfulfilled. */
    private fun pursue(ctx: ResolutionContext, hero: Hero, weapon: Weapon?, factionId: FactionId, faction: FactionDef) {
        val config = ctx.config
        val ambition = hero.ambition ?: return
        val data = mapOf("ambition" to ambition.name)
        when (ambition) {
            // A slayer goes looking for the strongest foe in the field: an expedition with a raised elite chance.
            Ambition.SLAYER -> {
                ctx.emit(EventType.AMBITION_PURSUED, 2, "${hero.fullName} went hunting for a foe worth the vow.", listOf(hero.id.value), data)
                expedition(ctx, hero, weapon, factionId, faction, config.heroLife.slayerHuntEliteChance)
            }
            // A sworn defender drills the watch: more militia than a patrol raises, but no suppression and no pay.
            Ambition.DEFENDER -> {
                ctx.town = ctx.town.copy(militia = minOf(config.militiaMax, ctx.town.militia + config.heroLife.defenderDrillMilitia))
                val e = ctx.emit(EventType.AMBITION_PURSUED, 2, "${hero.fullName} drilled the town watch.", listOf(hero.id.value), data)
                ctx.field += FieldResult(hero.id, hero.fullName, FieldOutcome.AMBITION_DAY, eventIds = listOf(e.id))
            }
            // A prized weapon and a fortune both take gold: a day of paid work, and nothing else.
            Ambition.COLLECTOR, Ambition.FORTUNE -> {
                ctx.updateHero(hero.copy(gold = hero.gold + config.heroLife.ambitionWorkGold))
                val goal = if (ambition == Ambition.COLLECTOR) "saving for a prized weapon" else "building a fortune"
                val e = ctx.emit(EventType.AMBITION_PURSUED, 2, "${hero.fullName} took guard work for ${config.heroLife.ambitionWorkGold} gold, $goal.", listOf(hero.id.value), data + ("gold" to config.heroLife.ambitionWorkGold.toString()))
                ctx.field += FieldResult(hero.id, hero.fullName, FieldOutcome.AMBITION_DAY, gold = config.heroLife.ambitionWorkGold, eventIds = listOf(e.id))
            }
        }
        ctx.hero(hero.id).let { if (it.isAlive) ctx.updateHero(it.copy(lastActivity = HeroActivity.AMBITION)) }
    }

    fun grantXp(ctx: ResolutionContext, hero: Hero, xp: Int) {
        var h = hero.copy(xp = hero.xp + xp)
        while (h.xp >= ctx.config.heroLevelXp && h.level < ctx.config.heroMaxLevel) {
            h = h.copy(level = h.level + 1, xp = h.xp - ctx.config.heroLevelXp)
            ctx.emit(EventType.HERO_LEVELED, 2, "${h.fullName} grew stronger (level ${h.level}).", listOf(h.id.value))
            if (h.level >= 5) ctx.milestone("HERO_LEVEL_5", "${h.fullName} reached level 5.")
        }
        ctx.updateHero(h)
    }

    /** Keeps the world alive: when the population thins, newcomers arrive (GDD event 6, simplified). */
    private fun arrivals(ctx: ResolutionContext, rng: Rng) {
        if (ctx.aliveHeroes().size >= ctx.config.minHeroPopulation) return
        val h = generate(ctx, rng)
        ctx.updateHero(h)
        ctx.emit(EventType.HERO_ARRIVED, 3, "A new adventurer, ${h.fullName} the ${ctx.content.heroClass(h.classId).name}, arrived in Emberfall.", listOf(h.id.value))
    }

    /**
     * GDD 6 generational hybrid (step 8): heroes retire at [com.tinyblacksmith.core.config.BalanceConfig.retirementLevel]
     * or, after enough victories, on a seeded check. A retiring famous hero founds a guild; every retiree mentors a
     * newcomer who inherits the weapons. Retired heroes never shop, fight or defend again.
     */
    fun resolveRetirements(ctx: ResolutionContext) {
        if (ctx.phase == Phase.ENDED) return
        val config = ctx.config
        val rng = ctx.rng(RngStream.HEROES)
        for (h in ctx.aliveHeroes()) {
            val hero = ctx.hero(h.id)
            val retires = hero.level >= config.retirementLevel ||
                (hero.victories >= config.retirementVictories && rng.chance(config.retirementChance))
            if (retires) retire(ctx, hero, rng)
        }
    }

    fun retire(ctx: ResolutionContext, hero: Hero, rng: Rng) {
        ctx.updateHero(hero.copy(fate = HeroFate.RETIRED, retiredOnDay = ctx.day, lastActivity = HeroActivity.IDLE))
        ctx.town = ctx.town.copy(championIds = ctx.town.championIds.filter { it != hero.id })
        ctx.emit(EventType.HERO_RETIRED, 6, "${hero.fullName} retired after a storied career (level ${hero.level}, ${hero.victories} victories).", listOf(hero.id.value))
        var guildId = hero.guildId
        if (guildId == null && hero.fame >= ctx.config.guildFameThreshold) guildId = foundGuild(ctx, ctx.hero(hero.id)).id
        val newcomer = generate(ctx, rng)
        val mentee = newcomer.copy(level = newcomer.level + 1, elementTaste = hero.elementTaste, guildId = guildId, mentorName = hero.fullName)
        ctx.updateHero(mentee)
        ctx.emit(EventType.HERO_MENTORED, 4, "${mentee.fullName}, trained by ${hero.fullName}, took up the mentor's calling.", listOf(mentee.id.value, hero.id.value))
        // Every weapon the retiree owned passes to the mentee (one owner per weapon; retired heroes own nothing).
        for (w in ctx.weapons.values.filter { it.ownerId == hero.id }.sortedWith(compareByDescending<Weapon> { it.isEquipped }.thenBy { it.id.value })) {
            ctx.addWeaponHistory(w.id, "INHERITED", "Inherited by ${mentee.fullName} from ${hero.fullName}.", listOf(mentee.id.value, hero.id.value))
            Market.giveAndEquip(ctx, ctx.hero(mentee.id), ctx.weapon(w.id))
            ctx.emit(EventType.WEAPON_INHERITED, 5, "${w.name} passed from ${hero.fullName} to ${mentee.fullName}.", listOf(w.id.value, mentee.id.value, hero.id.value))
        }
    }

    /** Founds a guild in [hero]'s name and enrols the founder: a retiring famous hero, or the first famous hero to spend a day on it. */
    private fun foundGuild(ctx: ResolutionContext, hero: Hero): Guild {
        val guild = Guild(id = "g${ctx.town.guilds.size + 1}", name = "the ${hero.surname} Company", founderId = hero.id, foundedDay = ctx.day)
        ctx.town = ctx.town.copy(guilds = ctx.town.guilds + guild)
        ctx.updateHero(hero.copy(guildId = guild.id))
        ctx.emit(EventType.GUILD_FOUNDED, 5, "${hero.fullName} founded ${guild.name} in Emberfall.", listOf(hero.id.value), mapOf("guild" to guild.id))
        return guild
    }

    /** GDD 6 ambitions: checked once a day (step 8); DEFENDER is fulfilled directly by a won siege. */
    fun resolveAmbitions(ctx: ResolutionContext) {
        val config = ctx.config
        for (h in ctx.aliveHeroes()) {
            val met = when (h.ambition) {
                Ambition.SLAYER -> h.expeditionWins >= config.ambitionSlayerWins
                Ambition.COLLECTOR -> (ctx.equippedWeapon(h.id)?.quality ?: 0) >= config.ambitionCollectorQuality
                Ambition.FORTUNE -> h.gold >= config.ambitionFortuneGold
                Ambition.DEFENDER, null -> false
            }
            if (met) fulfilAmbition(ctx, h.id, h.ambition!!)
        }
    }

    fun fulfilAmbition(ctx: ResolutionContext, heroId: HeroId, ambition: Ambition) {
        val hero = ctx.hero(heroId)
        if (!hero.isAlive || hero.ambition != ambition || hero.ambitionDone) return
        val config = ctx.config
        ctx.updateHero(hero.copy(ambitionDone = true, fame = hero.fame + config.ambitionFame, loyalty = hero.loyalty + config.ambitionLoyalty))
        ctx.reputation += config.ambitionReputation
        val weapon = ctx.equippedWeapon(heroId)
        val text = when (ambition) {
            Ambition.SLAYER -> "${hero.fullName} kept a vow: ${config.ambitionSlayerWins} foes routed in the field."
            Ambition.DEFENDER -> "${hero.fullName} swore to hold the walls of Emberfall, and held them."
            Ambition.COLLECTOR -> "${hero.fullName} at last carries a weapon worth boasting of: ${weapon?.name ?: "a fine blade"}."
            Ambition.FORTUNE -> "${hero.fullName} has made a fortune on the roads."
        }
        ctx.emit(EventType.AMBITION_FULFILLED, 6, text, listOfNotNull(hero.id.value, weapon?.id?.value), mapOf("ambition" to ambition.name))
        ctx.milestone("AMBITION_FULFILLED", "A hero of Emberfall fulfilled a life's ambition.")
    }

    /** Player-facing line for the Town panel; progress is shown as plain counts, never weights. */
    fun describeAmbition(hero: Hero, weapon: Weapon?, config: com.tinyblacksmith.core.config.BalanceConfig): String? {
        val a = hero.ambition ?: return null
        if (!hero.isAlive && !hero.ambitionDone) return null
        if (hero.ambitionDone) return when (a) {
            Ambition.SLAYER -> "Kept a slayer's vow"
            Ambition.DEFENDER -> "Held the walls as sworn"
            Ambition.COLLECTOR -> "Carries a prized weapon"
            Ambition.FORTUNE -> "Made a fortune"
        }
        return when (a) {
            Ambition.SLAYER -> "Vows to rout ${config.ambitionSlayerWins} foes (${minOf(hero.expeditionWins, config.ambitionSlayerWins)}/${config.ambitionSlayerWins})"
            Ambition.DEFENDER -> "Sworn to defend the walls in a siege"
            Ambition.COLLECTOR -> "Wants a weapon of quality ${config.ambitionCollectorQuality}+ (has ${weapon?.quality ?: 0})"
            Ambition.FORTUNE -> "Saving a fortune (${minOf(hero.gold, config.ambitionFortuneGold)}/${config.ambitionFortuneGold} gold)"
        }
    }

    fun describeTraits(hero: Hero, content: ContentCatalog): String = hero.traits.joinToString(", ") { content.trait(it).name }
}
