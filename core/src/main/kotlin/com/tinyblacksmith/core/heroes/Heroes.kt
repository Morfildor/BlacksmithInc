package com.tinyblacksmith.core.heroes

import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.content.Element
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
        )
    }

    fun resolveActivities(ctx: ResolutionContext) {
        val config = ctx.config
        val content = ctx.content
        val rng = ctx.rng(RngStream.HEROES)
        // Heroes act against the most pressing faction (the one that would besiege next); ties break by ID for determinism.
        val faction = ctx.factions.values.sortedBy { it.id.value }.maxByOrNull { it.pressure } ?: return
        val factionDef = content.faction(faction.id)
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
            val traitDefs = hero.traits.map { content.trait(it) }
            val expedition = (1.0 + traitDefs.sumOf { it.expeditionWeight } + faction.pressure / 100.0 * 0.5 + (if (weapon != null) 0.5 else -0.3)).coerceAtLeast(0.05)
            val patrol = (0.8 + traitDefs.sumOf { it.patrolWeight } + (if (ctx.town.integrity < 60) 0.4 else 0.0)).coerceAtLeast(0.05)
            val rest = (0.2 + traitDefs.sumOf { it.restWeight } + (100 - hero.health) / 100.0).coerceAtLeast(0.02)
            when (rng.pickWeighted(listOf(HeroActivity.EXPEDITION to expedition, HeroActivity.PATROL to patrol, HeroActivity.REST to rest))) {
                HeroActivity.EXPEDITION -> Battle.resolveExpedition(ctx, hero, weapon, faction.id, factionDef)
                HeroActivity.PATROL -> patrol(ctx, hero, faction.id)
                else -> rest(ctx, hero)
            }
        }
        arrivals(ctx, rng)
    }

    private fun rest(ctx: ResolutionContext, hero: Hero) {
        val healed = minOf(100, hero.health + ctx.config.heroRestHeal)
        ctx.updateHero(hero.copy(health = healed, lastActivity = HeroActivity.REST))
        ctx.emit(EventType.HERO_RESTED, 0, "${hero.fullName} rested and recovered.", listOf(hero.id.value))
    }

    private fun patrol(ctx: ResolutionContext, hero: Hero, factionId: FactionId) {
        val config = ctx.config
        ctx.patrolsToday += 1
        ctx.town = ctx.town.copy(militia = minOf(config.militiaMax, ctx.town.militia + config.patrolMilitiaGain))
        val f = ctx.factions.getValue(factionId)
        ctx.factions[factionId] = f.copy(suppressionToday = f.suppressionToday + config.patrolSuppression)
        grantXp(ctx, hero.copy(lastActivity = HeroActivity.PATROL), config.patrolXp)
        ctx.emit(EventType.HERO_PATROLLED, 1, "${hero.fullName} patrolled the town walls.", listOf(hero.id.value))
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
        if (guildId == null && hero.fame >= ctx.config.guildFameThreshold) {
            val guild = Guild(id = "g${ctx.town.guilds.size + 1}", name = "the ${hero.surname} Company", founderId = hero.id, foundedDay = ctx.day)
            ctx.town = ctx.town.copy(guilds = ctx.town.guilds + guild)
            ctx.updateHero(ctx.hero(hero.id).copy(guildId = guild.id))
            guildId = guild.id
            ctx.emit(EventType.GUILD_FOUNDED, 5, "${hero.fullName} founded ${guild.name} in Emberfall.", listOf(hero.id.value), mapOf("guild" to guild.id))
        }
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

    fun describeTraits(hero: Hero, content: ContentCatalog): String = hero.traits.joinToString(", ") { content.trait(it).name }
}
