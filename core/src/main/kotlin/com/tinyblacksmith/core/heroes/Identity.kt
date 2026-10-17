package com.tinyblacksmith.core.heroes

import com.tinyblacksmith.core.content.BackgroundDef
import com.tinyblacksmith.core.content.CultureDef
import com.tinyblacksmith.core.content.RaceDef
import com.tinyblacksmith.core.crafting.Forge
import com.tinyblacksmith.core.crafting.WeaponRules
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.rng.Keyed
import com.tinyblacksmith.core.rng.Rng

/**
 * Who a new hero is in a living-heroes era (final design 6, 7): people, naming culture, background and one Hook. Every draw
 * here is keyed to the hero ("identity|era|h17"), so none moves a gameplay stream: a classic era draws on its streams exactly
 * what a legacy one does, and only the names and the story differ.
 */
object Identity {
    data class Drawn(val race: RaceDef, val culture: CultureDef, val background: BackgroundDef, val hook: HookKind?, val kin: Hero?)

    private fun keyed(ctx: ResolutionContext, what: String, heroId: HeroId): Rng = Keyed.rng(ctx.base.seed, "$what|${ctx.era}|${heroId.value}")

    /** Null when the hero is named the old way: a catalog without cultures, or a descendant of an ancestor whose people is unknown. */
    fun draw(ctx: ResolutionContext, heroId: HeroId, ancestor: LineageAnchor?): Drawn? {
        val content = ctx.content
        if (content.cultures.isEmpty() || content.races.isEmpty() || content.backgrounds.isEmpty()) return null
        if (ancestor != null && (ancestor.cultureId == null || ancestor.cultureId !in content.cultureById)) return null
        val r = keyed(ctx, "identity", heroId)
        var race = ancestor?.raceId?.let { content.raceById[it] } ?: r.pickWeighted(content.races.map { it to it.weight.toDouble() })
        val background = r.pick(content.backgrounds)
        val guild = ctx.guildEra
        // Classic eras carry the two Hooks a classic era can honour: a grievance from before and a relative in town (final design 4.2).
        val kinds = HookKind.entries.filter { guild || it == HookKind.GRUDGE || it == HookKind.KIN }
        val weights = ctx.config.living.hookWeights
        val kinPool = if (ancestor != null) emptyList() else ctx.aliveHeroes().filter { it.id != heroId && it.cultureId != null && it.kinId == null && it.lineageId == null }
        val kind = r.pickWeighted(kinds.filter { it != HookKind.KIN || kinPool.isNotEmpty() }.map { it to (weights[it.name] ?: 0).toDouble() })
        val kin = if (kind == HookKind.KIN) r.pick(kinPool) else null
        if (kin != null) race = content.raceById[kin.raceId] ?: race
        val culture = content.cultureById[kin?.cultureId ?: ancestor?.cultureId ?: race.defaultCultureId] ?: return null
        return Drawn(race, culture, background, kind, kin)
    }

    /**
     * [hero] as generated, with its Hook made real: a grudge names a faction, a debt a creditor and a date after the first
     * siege, a lost weapon is a record in `Lost`, an oath a rule shown before recruitment, kin a living relative who shares the
     * family ID. In a guild era a Hook-linked or guild ambition may take the drawn one's place.
     */
    fun attach(ctx: ResolutionContext, hero: Hero, drawn: Drawn): Hero {
        val r = keyed(ctx, "hook", hero.id)
        val day = ctx.day
        val cfg = ctx.config.living
        val factions = ctx.factions.keys.sortedBy { it.value }
        var h = hero.copy(raceId = drawn.race.id, cultureId = drawn.culture.id, backgroundId = drawn.background.id)
        val hook = when (drawn.hook) {
            null -> null
            HookKind.GRUDGE -> if (factions.isEmpty()) null else Hook(HookKind.GRUDGE, day, factionId = r.pick(factions), background = true)
            HookKind.DEBT -> {
                val creditor = ctx.residents().filter { it.id != hero.id && it.isAlive }.let { if (it.isEmpty()) null else r.pick(it) }
                val firstSiege = if (ctx.town.siegesSurvived + ctx.town.siegesLost == 0) ctx.town.nextSiegeDay else day
                Hook(HookKind.DEBT, day, creditor = creditor?.fullName ?: "a moneylender in Emberfall", creditorHeroId = creditor?.id, amount = cfg.debtAmount,
                    dueDay = maxOf(firstSiege + cfg.debtAfterFirstSiegeDays, day + cfg.debtTermDays))
            }
            HookKind.LOST_WEAPON -> heirloom(ctx, h, r)?.let { Hook(HookKind.LOST_WEAPON, day, weaponId = it, background = true) }
            HookKind.OATH -> when (r.pick(OathKind.entries)) {
                OathKind.PLAIN_WORK -> Hook(HookKind.OATH, day, oath = OathKind.PLAIN_WORK)
                OathKind.NO_GRAVE -> Hook(HookKind.OATH, day, oath = OathKind.NO_GRAVE)
                OathKind.FAVOURED_FAMILY -> Hook(HookKind.OATH, day, oath = OathKind.FAVOURED_FAMILY, familyId = r.pick(ctx.content.heroClass(h.classId).preferredFamilies))
            }
            HookKind.KIN -> drawn.kin?.let { k ->
                val family = "kin-${k.id.value}"
                ctx.updateHero(ctx.hero(k.id).copy(kinId = family))
                h = h.copy(kinId = family)
                Hook(HookKind.KIN, day, kinHeroId = k.id)
            }
        }
        h = h.copy(hook = hook)
        if (ctx.guildEra) {
            val swap = when {
                hook?.kind == HookKind.GRUDGE && r.chance(cfg.hookAmbitionChance) -> Ambition.AVENGER
                hook?.kind == HookKind.LOST_WEAPON && r.chance(cfg.hookAmbitionChance) -> Ambition.HEIRLOOM
                r.chance(cfg.guildAmbitionChance) -> r.pick(listOf(Ambition.CAPTAIN, Ambition.NAME))
                else -> null
            }
            if (swap != null) h = h.copy(ambition = swap)
        }
        return h
    }

    /** A family weapon lost before the hero came to town: ordinary plain metal, never forged here, never the smith's. */
    private fun heirloom(ctx: ResolutionContext, hero: Hero, r: Rng): WeaponId? {
        val content = ctx.content
        val family = r.pick(content.heroClass(hero.classId).preferredFamilies)
        val core = content.materials.filter { it.category == com.tinyblacksmith.core.content.MaterialCategory.CORE && it.tier == 1 }.ifEmpty { return null }.let { r.pick(it) }
        val augment = content.materials.filter { it.category == com.tinyblacksmith.core.content.MaterialCategory.AUGMENT && it.tier == 1 && it.element != null }.ifEmpty { return null }.let { r.pick(it) }
        val quality = ctx.config.living.heirloomQuality
        val id = ctx.newWeaponId()
        val name = Forge.weaponName(content, family, core.id, emptyList())
        ctx.updateWeapon(Weapon(id, name, family, core.id, augment.id, null, ForgeMode.QUICK, Risk.SAFE, quality, Forge.forgedRarity(quality, ctx.config, core.tier),
            Forge.powerOf(content, ctx.config, family, core.id, quality, emptyList()), augment.element, emptyList(), emptyList(),
            WeaponLocation.Lost(ctx.day, "${LOST_FAMILY_PREFIX}${hero.fullName}"), forgedEra = 0, forgedDay = 0, craft = WeaponRules.CRAFT,
            history = listOf(HistoryEntry(ctx.era, ctx.day, "HEIRLOOM", "A family weapon of ${hero.fullName}, lost before they came to Emberfall.", listOf(hero.id.value)))))
        return id
    }

    const val LOST_FAMILY_PREFIX = "lost family weapon of "

    /** Adds a significant moment to a hero's record, newest last, at most `living.reasonsKept`. */
    fun note(ctx: ResolutionContext, heroId: HeroId, kind: String, text: String, refs: List<String> = emptyList()) {
        val h = ctx.heroes[heroId] ?: return
        ctx.updateHero(h.copy(reasons = (h.reasons + ReasonRecord(ctx.day, kind, text, refs)).takeLast(ctx.config.living.reasonsKept)))
    }
}
