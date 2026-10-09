package com.tinyblacksmith.core.engine

import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.content.UpgradeEffect
import com.tinyblacksmith.core.crafting.ClueRung
import com.tinyblacksmith.core.crafting.Forge
import com.tinyblacksmith.core.crafting.Journal
import com.tinyblacksmith.core.crafting.SignatureCatalog
import com.tinyblacksmith.core.heroes.Heroes
import com.tinyblacksmith.core.market.Commissions
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.market.QualityBand
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.rng.RngStream
import com.tinyblacksmith.core.model.Journal as JournalModel

/**
 * One scripted systemic world event (GDD 10): eligibility, weight, effects, story template and repetition limits.
 * [apply] performs the effect and returns the variables that fill `{placeholders}` in [story] plus the subject IDs
 * of the resulting Gazette record. Eligibility must never consume RNG.
 */
data class WorldEventDef(
    val id: String,
    val name: String,
    val weight: Double,
    val eligibility: (ResolutionContext) -> Boolean,
    val maxPerRun: Int,
    val cooldownDays: Int,
    val apply: (ResolutionContext) -> WorldEventOutcome,
    val story: String,
)

/** [visit] is set by an event that is a customer at the counter (the collector); [WorldEvents.fire] adds it to the day's visits with the record's ID. */
data class WorldEventOutcome(val vars: Map<String, String> = emptyMap(), val subjects: List<String> = emptyList(), val visit: MarketVisit? = null)

/** Data-driven, seeded world events: at most one per day, chosen by weight on the EVENTS stream. */
object WorldEvents {
    const val UNLIMITED = Int.MAX_VALUE
    const val FLAG_FESTIVAL = "festival"
    const val FLAG_CARAVAN_DELAYED = "caravan_delayed"
    /** Prefix of a day-keyed flag per material: the ore merchant's [ORE_MERCHANT_STOCK] extra units are on sale that morning. */
    const val FLAG_ORE_MERCHANT = "ore_merchant:"
    const val ORE_MERCHANT_STOCK = 2
    /** Key of the run's rumour count and last rumour day in `eventCounters` / `eventLastDay`. Not a pooled event. */
    const val RUMOUR = "rumour"

    /**
     * A rumour (plan 4.6 E3): something that really happened today brings word of one signature not yet found, one rung
     * of its clue ladder ([Journal.nextRung]). [teller] opens the record ("Mira Vance, back from the kill,") and
     * [tellerId] is kept in its data (the record is the forge's news, filed with the journal, not a line of the hero's day). At most
     * `customers.maxRumoursPerRun` a run and none within `customers.rumourCooldownDays` of the last; when one is told it
     * is one pick on the EVENTS stream, as the weapon fragment's is. Returns whether a rumour was told.
     */
    fun rumour(ctx: ResolutionContext, teller: String, tellerId: HeroId? = null): Boolean {
        val cfg = ctx.config.customers
        if ((ctx.eventCounters[RUMOUR] ?: 0) >= cfg.maxRumoursPerRun) return false
        if (ctx.eventLastDay[RUMOUR]?.let { ctx.day - it < cfg.rumourCooldownDays } == true) return false
        val open = forgeableSignatures(ctx).filter { ctx.legacy.journal.state(it.journalKey) != KnowledgeState.SIGNATURE_DISCOVERED && Journal.nextRung(ctx.legacy.journal, it) != null }
        if (open.isEmpty()) return false
        val sig = ctx.rng(RngStream.EVENTS).pick(open)
        val rung = Journal.nextRung(ctx.legacy.journal, sig) ?: return false
        Journal.earn(ctx, sig, rung)
        ctx.eventCounters[RUMOUR] = (ctx.eventCounters[RUMOUR] ?: 0) + 1
        ctx.eventLastDay[RUMOUR] = ctx.day
        ctx.emit(
            EventType.DISCOVERY, 3, "$teller spoke of a ${Journal.subjectName(ctx.content, sig.journalKey)}: ${Journal.clue(sig, rung, ctx.config)}.",
            data = mapOf("key" to sig.journalKey, "rung" to rung.name, "rumour" to "true") + (tellerId?.let { mapOf("hero" to it.value) } ?: emptyMap()),
        )
        return true
    }

    fun resolve(ctx: ResolutionContext) {
        val rng = ctx.rng(RngStream.EVENTS)
        if (!rng.chance(ctx.config.worldEventChancePerDay)) return
        val eligible = all.filter { canFire(ctx, it) }
        if (eligible.isEmpty()) return
        fire(ctx, rng.pickWeighted(eligible.map { it to weight(ctx, it) }))
    }

    /** Today's weight of [def]: Homing Steel (LEGACY_ARTIFACTS) makes a Legend Board blade likelier to return; no other event moves. */
    fun weight(ctx: ResolutionContext, def: WorldEventDef): Double =
        if (def.id == "famous_blade") def.weight + ctx.upgradeTotal(UpgradeEffect.LEGACY_ARTIFACTS) * ctx.config.legacyTracks.legendReturnWeightPerLevel else def.weight

    /** Share of its old quality and power a returned legend keeps: dormant (GDD 7), less so with Homing Steel, never whole. */
    fun returnedLegendFactor(ctx: ResolutionContext): Double {
        val base = ctx.config.returnedLegendQualityFactor
        val tracks = ctx.config.legacyTracks
        return maxOf(base, minOf(tracks.returnedLegendQualityFactorMax, base + ctx.upgradeTotal(UpgradeEffect.LEGACY_ARTIFACTS) * tracks.legendQualityFactorPerLevel))
    }

    fun canFire(ctx: ResolutionContext, def: WorldEventDef): Boolean {
        if ((ctx.eventCounters[def.id] ?: 0) >= def.maxPerRun) return false
        val last = ctx.eventLastDay[def.id]
        if (last != null && ctx.day - last <= def.cooldownDays) return false
        return def.eligibility(ctx)
    }

    fun fire(ctx: ResolutionContext, def: WorldEventDef): EventRecord {
        val outcome = def.apply(ctx)
        ctx.eventCounters[def.id] = (ctx.eventCounters[def.id] ?: 0) + 1
        ctx.eventLastDay[def.id] = ctx.day
        val text = outcome.vars.entries.fold(def.story) { acc, (k, v) -> acc.replace("{$k}", v) }
        val record = ctx.emit(EventType.WORLD_EVENT, 4, text, outcome.subjects, outcome.vars + ("event" to def.id))
        outcome.visit?.let { ctx.visits += it.copy(seq = ctx.visits.size, eventIds = listOf(record.id)) }
        return record
    }

    fun byId(id: String): WorldEventDef = all.first { it.id == id }

    // ---- helpers -------------------------------------------------------------------------------------------

    private fun rareMaterials(ctx: ResolutionContext) = ctx.content.materials.filter { it.dailySupplierStock != null }

    private fun journalKeys(ctx: ResolutionContext): List<String> {
        val c = ctx.content
        val ca = c.materials(MaterialCategory.CORE).flatMap { core -> c.materials(MaterialCategory.AUGMENT).map { JournalModel.coreAugmentKey(core.id, it.id) } }
        val af = c.materials(MaterialCategory.AUGMENT).flatMap { aug -> c.families.map { JournalModel.augmentFamilyKey(aug.id, it.id) } }
        return (ca + af).sorted()
    }

    private fun keysInState(ctx: ResolutionContext, state: KnowledgeState, prefix: String = "") =
        journalKeys(ctx).filter { it.startsWith(prefix) && ctx.legacy.journal.state(it) == state }

    private fun setKnowledge(ctx: ResolutionContext, key: String, state: KnowledgeState) {
        val j = ctx.legacy.journal
        val experiments = if (state == KnowledgeState.UNDERSTOOD) ctx.config.experimentsToUnderstand else maxOf(1, j.experiments[key] ?: 0)
        ctx.legacy = ctx.legacy.copy(journal = j.copy(interactions = j.interactions + (key to state), experiments = j.experiments + (key to experiments)))
    }

    /** Signatures the active catalog can forge (catalog order, so picks are deterministic). */
    private fun forgeableSignatures(ctx: ResolutionContext) = SignatureCatalog.all.filter { s ->
        s.familyId in ctx.content.familyById && s.coreId in ctx.content.materialById && s.augmentId in ctx.content.materialById &&
            (s.catalystId == null || s.catalystId in ctx.content.materialById)
    }

    /** Those of them whose journal entry is still blank. */
    private fun unknownSignatures(ctx: ResolutionContext) = forgeableSignatures(ctx).filter { ctx.legacy.journal.state(it.journalKey) == KnowledgeState.UNKNOWN }

    /** Legend Board blades that can still come back this era: one that already has is in the run and is not picked again. */
    private fun returnable(ctx: ResolutionContext): List<LegendEntry> {
        val here = ctx.weapons.values.mapNotNull { it.legendKey }.toSet()
        return ctx.legacy.legendBoard.filter { it.key !in here }
    }

    private fun hasFaction(id: String): (ResolutionContext) -> Boolean = { ctx -> ctx.factions.containsKey(FactionId(id)) }

    private fun pressureEvent(id: String, name: String, factionId: String, weight: Double, story: String) = WorldEventDef(
        id = id, name = name, weight = weight, eligibility = hasFaction(factionId), maxPerRun = UNLIMITED, cooldownDays = 1,
        apply = { ctx ->
            val f = ctx.factions.getValue(FactionId(factionId))
            ctx.factions[f.id] = f.copy(pressure = (f.pressure + ctx.config.encampmentPressure).coerceIn(0, 100))
            WorldEventOutcome(mapOf("faction" to ctx.content.faction(f.id).name))
        },
        story = story,
    )

    private fun fallenOwnerName(ctx: ResolutionContext, w: Weapon): String =
        w.history.lastOrNull { it.kind == "LOST" || it.kind == "SEIZED" }?.subjectIds?.firstOrNull()?.let { ctx.heroes[HeroId(it)]?.fullName } ?: "a fallen hero"

    private fun lostWithHero(w: Weapon): Boolean {
        val loc = w.location
        return loc is WeaponLocation.Lost && (loc.reason == "seized" || loc.reason.startsWith("lost with"))
    }

    // ---- the catalogue (GDD 10 numbering in comments) ------------------------------------------------------

    val all: List<WorldEventDef> = listOf(
        // 1
        WorldEventDef(
            id = "ore_merchant", name = "Traveling Ore Merchant", weight = 3.0, eligibility = { rareMaterials(it).isNotEmpty() }, maxPerRun = UNLIMITED, cooldownDays = 1,
            apply = { ctx ->
                val m = ctx.rng(RngStream.EVENTS).pick(rareMaterials(ctx))
                ctx.materials[m.id] = (ctx.materials[m.id] ?: 0) + 1
                ctx.worldFlags[FLAG_ORE_MERCHANT + m.id.value] = ctx.day + 1  // the morning's restock would overwrite stock added tonight
                WorldEventOutcome(mapOf("material" to m.name, "materialId" to m.id.value))
            },
            story = "A traveling ore merchant arrived with {material}.",
        ),
        // 2
        WorldEventDef(
            id = "caravan_delayed", name = "Trade Caravan Delayed", weight = 2.0, eligibility = { rareMaterials(it).isNotEmpty() }, maxPerRun = 4, cooldownDays = 3,
            apply = { ctx -> ctx.worldFlags[FLAG_CARAVAN_DELAYED] = ctx.day + 1; WorldEventOutcome() },
            story = "The trade caravan was delayed on the mountain road; no rare ores will reach the supplier tomorrow.",
        ),
        // 3
        WorldEventDef(
            id = "merchant_festival", name = "Merchant Festival", weight = 2.0, eligibility = { true }, maxPerRun = 4, cooldownDays = 4,
            apply = { ctx -> ctx.worldFlags[FLAG_FESTIVAL] = ctx.day + 1; WorldEventOutcome() },
            story = "Emberfall declared a merchant festival: adventurers will crowd the shops tomorrow.",
        ),
        // 4
        WorldEventDef(
            id = "abandoned_mine", name = "Abandoned Mine Rediscovered", weight = 2.0, eligibility = { true }, maxPerRun = 3, cooldownDays = 5,
            apply = { ctx ->
                val m = ctx.rng(RngStream.EVENTS).pick(ctx.content.materials(MaterialCategory.CORE))
                val n = ctx.config.abandonedMineMaterials
                ctx.materials[m.id] = (ctx.materials[m.id] ?: 0) + n
                WorldEventOutcome(mapOf("material" to m.name, "materialId" to m.id.value, "amount" to n.toString()))
            },
            story = "Prospectors rediscovered an abandoned mine and sent {amount} {material} to the forge.",
        ),
        // 5
        WorldEventDef(
            id = "noble_commission", name = "Noble Commission", weight = 2.0,
            eligibility = { ctx -> Market.openCommissions(ctx).let { open -> open.size < ctx.config.customers.maxOpenCommissions && ctx.aliveHeroes().any { h -> open.none { it.buyerId == h.id || it.recipientId == h.id } } } },
            maxPerRun = 3, cooldownDays = 5,
            apply = { ctx ->
                val rng = ctx.rng(RngStream.EVENTS)
                val config = ctx.config
                val buyer = rng.pick(Market.openCommissions(ctx).let { open -> ctx.aliveHeroes().filter { h -> open.none { it.buyerId == h.id || it.recipientId == h.id } } })
                val family = rng.pick(ctx.content.heroClass(buyer.classId).preferredFamilies)
                val minQuality = QualityBand.SUPERB.floor(config)
                val reward = (config.commissionRewardBase + minQuality * config.commissionRewardPerQuality) * config.nobleCommissionRewardMultiplier
                val id = ctx.newCommissionId()
                val c = Commission(id, buyer.id, family, minQuality, reward, ctx.day, ctx.day + config.commissionDeadlineDays + 2, CommissionStatus.OFFERED, kind = CommissionKind.NOBLE)
                ctx.commissions[id] = c
                WorldEventOutcome(mapOf("hero" to buyer.fullName, "family" to ctx.content.family(family).name, "request" to Commissions.describe(c, ctx.content, config), "reward" to reward.toString(), "day" to c.deadlineDay.toString()), listOf(buyer.id.value, id.value))
            },
            story = "A noble patron, speaking through {hero}, commissions a {request} by day {day} for {reward} gold.",
        ),
        // 6
        WorldEventDef(
            id = "new_adventurers", name = "New Adventurers Arrive", weight = 2.0,
            eligibility = { ctx -> ctx.aliveHeroes().size + ctx.config.newAdventurerCount <= ctx.config.customers.maxHeroPopulation }, maxPerRun = 3, cooldownDays = 4,
            apply = { ctx ->
                val rng = ctx.rng(RngStream.EVENTS)
                val arrived = (1..ctx.config.newAdventurerCount).map { Heroes.generate(ctx, rng).also { h ->
                    ctx.updateHero(h)
                    ctx.emit(EventType.HERO_ARRIVED, 2, "${h.fullName} the ${ctx.content.heroClass(h.classId).name} arrived in Emberfall.", listOf(h.id.value))
                } }
                WorldEventOutcome(mapOf("names" to arrived.joinToString(" and ") { it.fullName }), arrived.map { it.id.value })
            },
            story = "A band of new adventurers reached Emberfall: {names} are looking for work and weapons.",
        ),
        // 7
        WorldEventDef(
            id = "veteran_returns", name = "Veteran Returns", weight = 1.5, eligibility = { ctx -> ctx.aliveHeroes().size < ctx.config.customers.maxHeroPopulation }, maxPerRun = 2, cooldownDays = 6,
            apply = { ctx ->
                val base = Heroes.generate(ctx, ctx.rng(RngStream.EVENTS))
                val h = base.copy(level = maxOf(base.level, ctx.config.veteranLevel), gold = base.gold + ctx.config.veteranGold, fame = 2)
                ctx.updateHero(h)
                ctx.emit(EventType.HERO_ARRIVED, 2, "${h.fullName} the ${ctx.content.heroClass(h.classId).name} arrived in Emberfall.", listOf(h.id.value))
                WorldEventOutcome(mapOf("hero" to h.fullName, "class" to ctx.content.heroClass(h.classId).name), listOf(h.id.value))
            },
            story = "{hero}, a veteran {class} of distant wars, returned to Emberfall with a heavy purse.",
        ),
        // 8 Guild Founded and 9 Champion Retirement are deterministic generational rules (Heroes.resolveRetirements).
        // 10
        WorldEventDef(
            id = "heroic_inheritance", name = "Heroic Inheritance", weight = 2.0,
            eligibility = { ctx -> ctx.aliveHeroes().isNotEmpty() && ctx.weapons.values.any(::lostWithHero) }, maxPerRun = 3, cooldownDays = 3,
            apply = { ctx ->
                val rng = ctx.rng(RngStream.EVENTS)
                val w = rng.pick(ctx.weapons.values.filter(::lostWithHero).sortedWith(compareBy(IdOrder.numeric) { it.id.value }))
                val heir = rng.pick(ctx.aliveHeroes())
                val fallen = fallenOwnerName(ctx, w)
                ctx.addWeaponHistory(w.id, "INHERITED", "Carried home after ${fallen}'s death and passed to ${heir.fullName}.", listOf(heir.id.value))
                Market.giveAndEquip(ctx, ctx.hero(heir.id), ctx.weapon(w.id))
                ctx.emit(EventType.WEAPON_INHERITED, 5, "${w.name} passed to ${heir.fullName}.", listOf(w.id.value, heir.id.value))
                WorldEventOutcome(mapOf("weapon" to w.name, "fallen" to fallen, "hero" to heir.fullName), listOf(w.id.value, heir.id.value))
            },
            story = "{weapon}, lost with {fallen}, was carried home by comrades and passed to {hero}.",
        ),
        // 11-13 faction pressure events; the slice has only the Ashclaw faction, the other two become eligible with their content.
        pressureEvent("raider_encampment", "Raider Encampment", "ashclaw_raiders", 3.0, "Scouts report a new {faction} encampment near Emberfall."),
        pressureEvent("restless_graves", "Restless Graves", "hollowbound", 3.0, "The graves beyond the river stir; {faction} grow bolder."),
        pressureEvent("volcanic_tremors", "Volcanic Tremors", "embermaw_brood", 3.0, "Tremors shake the ash slopes and the {faction} pour out of the vents."),
        // 14
        WorldEventDef(
            id = "successful_patrol", name = "Successful Patrol", weight = 2.0,
            eligibility = { ctx -> ctx.aliveHeroes().isNotEmpty() && ctx.factions.values.any { it.pressure > 0 } }, maxPerRun = UNLIMITED, cooldownDays = 2,
            apply = { ctx ->
                val hero = ctx.rng(RngStream.EVENTS).pick(ctx.aliveHeroes())
                val f = ctx.factions.values.filter { it.pressure > 0 }.maxWith(compareBy<FactionState> { it.pressure }.thenBy { it.id.value })
                ctx.factions[f.id] = f.copy(pressure = maxOf(0, f.pressure - ctx.config.successfulPatrolPressureDrop))
                ctx.town = ctx.town.copy(militia = minOf(ctx.config.militiaMax, ctx.town.militia + ctx.config.successfulPatrolMilitia))
                WorldEventOutcome(mapOf("hero" to hero.fullName, "faction" to ctx.content.faction(f.id).name), listOf(hero.id.value))
            },
            story = "{hero} led a patrol that scattered {faction} scouts; the town breathes easier.",
        ),
        // 15
        WorldEventDef(
            id = "border_ambush", name = "Border Ambush", weight = 2.0,
            eligibility = { ctx -> ctx.aliveHeroes().any { it.health >= ctx.config.heroWoundedThreshold } }, maxPerRun = UNLIMITED, cooldownDays = 2,
            apply = { ctx ->
                val hero = ctx.rng(RngStream.EVENTS).pick(ctx.aliveHeroes().filter { it.health >= ctx.config.heroWoundedThreshold })
                val health = maxOf(1, hero.health - ctx.config.ambushDamage)
                ctx.updateHero(hero.copy(health = health))
                if (health < ctx.config.heroWoundedThreshold) ctx.emit(EventType.HERO_WOUNDED, 2, "${hero.fullName} returned wounded.", listOf(hero.id.value))
                WorldEventOutcome(mapOf("hero" to hero.fullName), listOf(hero.id.value))
            },
            story = "{hero} was ambushed on the border road and limped home bleeding.",
        ),
        // 16
        WorldEventDef(
            id = "ancient_notes", name = "Ancient Smithing Notes", weight = 1.5, eligibility = { keysInState(it, KnowledgeState.UNKNOWN).isNotEmpty() }, maxPerRun = 3, cooldownDays = 4,
            apply = { ctx ->
                val key = ctx.rng(RngStream.EVENTS).pick(keysInState(ctx, KnowledgeState.UNKNOWN))
                setKnowledge(ctx, key, KnowledgeState.OBSERVED)
                val subject = Journal.subjectName(ctx.content, key)
                ctx.emit(EventType.DISCOVERY, 1, "Journal: $subject observed — ${Journal.describeAffinity(Journal.affinityFor(ctx.content, key))}.", data = mapOf("key" to key))
                WorldEventOutcome(mapOf("subject" to subject, "key" to key))
            },
            story = "Ancient smithing notes turned up in the market; they describe {subject}.",
        ),
        // 17
        WorldEventDef(
            id = "mysterious_alloy", name = "Mysterious Alloy", weight = 1.5, eligibility = { keysInState(it, KnowledgeState.UNKNOWN, "ca:").isNotEmpty() }, maxPerRun = 2, cooldownDays = 5,
            apply = { ctx ->
                val key = ctx.rng(RngStream.EVENTS).pick(keysInState(ctx, KnowledgeState.UNKNOWN, "ca:"))
                setKnowledge(ctx, key, KnowledgeState.OBSERVED)
                key.substring(3).split("|").forEach { id -> ctx.materials[MaterialId(id)] = (ctx.materials[MaterialId(id)] ?: 0) + 1 }
                val subject = Journal.subjectName(ctx.content, key)
                ctx.emit(EventType.DISCOVERY, 1, "Journal: $subject observed — ${Journal.describeAffinity(Journal.affinityFor(ctx.content, key))}.", data = mapOf("key" to key))
                WorldEventOutcome(mapOf("subject" to subject, "key" to key))
            },
            story = "A mysterious alloy was traded to the forge; studying it taught something of {subject}.",
        ),
        // 18
        WorldEventDef(
            id = "forgotten_shrine", name = "Forgotten Shrine", weight = 1.5, eligibility = { it.content.materials(MaterialCategory.CATALYST).isNotEmpty() }, maxPerRun = 3, cooldownDays = 5,
            apply = { ctx ->
                val m = ctx.rng(RngStream.EVENTS).pick(ctx.content.materials(MaterialCategory.CATALYST))
                ctx.materials[m.id] = (ctx.materials[m.id] ?: 0) + 2
                WorldEventOutcome(mapOf("material" to m.name, "materialId" to m.id.value, "amount" to "2"))
            },
            story = "Pilgrims found a forgotten shrine and left {amount} {material} at the forge door.",
        ),
        // 19
        WorldEventDef(
            id = "wandering_master", name = "Wandering Master Smith", weight = 1.0, eligibility = { keysInState(it, KnowledgeState.OBSERVED).isNotEmpty() }, maxPerRun = 2, cooldownDays = 6,
            apply = { ctx ->
                val key = ctx.rng(RngStream.EVENTS).pick(keysInState(ctx, KnowledgeState.OBSERVED))
                setKnowledge(ctx, key, KnowledgeState.UNDERSTOOD)
                ctx.discoveriesThisRun += 1
                val subject = Journal.subjectName(ctx.content, key)
                val label = Journal.describeAffinity(Journal.affinityFor(ctx.content, key))
                ctx.emit(EventType.DISCOVERY, 3, "Journal: $subject is now understood — $label.", data = mapOf("key" to key))
                WorldEventOutcome(mapOf("subject" to subject, "label" to label, "key" to key))
            },
            story = "A wandering master smith shared a trade secret: {subject} is {label}.",
        ),
        // 20
        WorldEventDef(
            id = "weapon_fragment", name = "Strange Weapon Fragment", weight = 1.5, eligibility = { unknownSignatures(it).isNotEmpty() }, maxPerRun = 2, cooldownDays = 5,
            apply = { ctx ->
                val sig = ctx.rng(RngStream.EVENTS).pick(unknownSignatures(ctx))
                Journal.earn(ctx, sig, ClueRung.RECIPE)   // the first rung of its ladder: the base recipe hides something more
                for (id in listOf(sig.coreId, sig.augmentId)) ctx.materials[id] = (ctx.materials[id] ?: 0) + 1
                val subject = Journal.subjectName(ctx.content, sig.journalKey)
                ctx.emit(EventType.DISCOVERY, 3, "Journal: $subject — ${Journal.hint(ctx.legacy.journal, ctx.content, sig.journalKey)}.", data = mapOf("key" to sig.journalKey))
                WorldEventOutcome(mapOf("subject" to subject, "key" to sig.journalKey))
            },
            story = "A strange weapon fragment was dug from the river mud; melted down it proved to be a {subject}, worked by hands that knew something more.",
        ),
        // 21
        WorldEventDef(
            id = "famous_blade", name = "A Famous Blade Returns", weight = 1.0, eligibility = { returnable(it).isNotEmpty() }, maxPerRun = 1, cooldownDays = 0,
            apply = { ctx ->
                val rng = ctx.rng(RngStream.EVENTS)
                val c = ctx.content
                val legend = rng.pick(returnable(ctx))
                val family = legend.familyId?.let { c.familyById[it] } ?: rng.pick(c.families)
                val core = legend.coreId?.let { c.materialById[it] } ?: rng.pick(c.materials(MaterialCategory.CORE))
                val augment = legend.augmentId?.let { c.materialById[it] } ?: rng.pick(c.materials(MaterialCategory.AUGMENT))
                // It comes back as what it was (G09): signature, flaws and catalyst kept; its beneficial affixes asleep until the smith hones it.
                val signature = legend.signatureId?.takeIf { SignatureCatalog.byId[it]?.let { s -> s.familyId == family.id && s.coreId == core.id && s.augmentId == augment.id } == true }
                val dormant = legend.affixes.filter { it in c.affixById && c.affix(it).kind == com.tinyblacksmith.core.content.AffixKind.BENEFICIAL }
                val flaws = legend.flaws.filter { it in c.affixById }
                val factor = returnedLegendFactor(ctx)
                val quality = maxOf(1, ((if (legend.quality > 0) legend.quality else 60) * factor).toInt())
                val fullPower = if (legend.power > 0) legend.power else family.basePower + core.tier * ctx.config.powerPerCoreTier + quality / ctx.config.powerPerQualityDivisor
                // The power its sleeping affixes carried sleeps with them, and comes back whole when they wake (GameEngine.hone).
                val power = maxOf(1, ((fullPower - dormant.sumOf { c.affix(it).power }) * factor).toInt())
                // The name promises nothing the blade lacks: it keeps the name it had unless that is a signature's name without the
                // signature, or carries an affix the blade does not have (awake, asleep or as a flaw); then it is called by what is known of it.
                val words = " ${legend.weaponName} "
                val promisesTooMuch = SignatureCatalog.all.any { it.name == legend.weaponName && it.id != signature } ||
                    c.affixes.any { a -> a.id !in dormant && a.id !in flaws && " ${a.name} " in words }
                val name = if (promisesTooMuch) Forge.weaponName(c, family.id, core.id, emptyList(), signature) else legend.weaponName
                val w = Weapon(
                    id = ctx.newWeaponId(), name = name, familyId = family.id, coreId = core.id, augmentId = augment.id, catalystId = legend.catalystId?.takeIf { it in c.materialById },
                    mode = ForgeMode.ADVANCED, risk = Risk.BALANCED, quality = quality, rarity = Forge.rarityFor(quality, ctx.config), power = power,
                    element = legend.element ?: augment.element, affixes = emptyList(), flaws = flaws, location = WeaponLocation.Storage,
                    forgedEra = legend.era, forgedDay = 1, kills = legend.kills, fame = legend.fame, title = legend.title,
                    history = legend.ownerLine + HistoryEntry(ctx.era, ctx.day, "RETURNED", "Returned to Emberfall in Era ${ctx.era}, worn and dormant; once carried by ${legend.owners.joinToString(", ").ifEmpty { "forgotten hands" }}."),
                    signatureId = signature, dormantAffixes = dormant, legendKey = legend.key,
                )
                ctx.updateWeapon(w)
                ctx.emit(EventType.ARTIFACT_RETURNED, 6, "${w.name}, ${legend.title}, has returned to the forge.", listOf(w.id.value), mapOf("era" to legend.era.toString(), "legend" to legend.key, "power" to power.toString()))
                WorldEventOutcome(mapOf("weapon" to w.name, "title" to legend.title, "era" to legend.era.toString()), listOf(w.id.value))
            },
            story = "{weapon}, {title} of Era {era}, found its way back to the forge, dented and dormant.",
        ),
        // 22
        WorldEventDef(
            id = "descendant", name = "Descendant of a Champion", weight = 1.0,
            eligibility = { ctx -> ctx.aliveHeroes().size < ctx.config.customers.maxHeroPopulation && ctx.legacy.lineages.any { l -> ctx.heroes.values.none { it.lineageId == l.id } } },
            maxPerRun = 2, cooldownDays = 5,
            apply = { ctx ->
                val rng = ctx.rng(RngStream.EVENTS)
                val anchor = rng.pick(ctx.legacy.lineages.filter { l -> ctx.heroes.values.none { it.lineageId == l.id } })
                val h = Heroes.generate(ctx, rng, anchor)
                ctx.updateHero(h)
                ctx.emit(EventType.HERO_ARRIVED, 2, "${h.fullName} the ${ctx.content.heroClass(h.classId).name} arrived in Emberfall.", listOf(h.id.value))
                WorldEventOutcome(mapOf("hero" to h.fullName, "ancestor" to anchor.heroName, "deed" to anchor.deed), listOf(h.id.value))
            },
            story = "{hero}, descendant of {ancestor} who {deed}, has come to Emberfall to honour the name.",
        ),
        // 23
        WorldEventDef(
            id = "guild_banner", name = "Forgotten Guild Banner", weight = 1.0, eligibility = { it.legacy.eras.isNotEmpty() || it.town.guilds.isNotEmpty() }, maxPerRun = 1, cooldownDays = 0,
            apply = { ctx ->
                ctx.reputation += 2
                ctx.town = ctx.town.copy(militia = minOf(ctx.config.militiaMax, ctx.town.militia + 3))
                val era = ctx.legacy.eras.lastOrNull()?.era ?: ctx.era
                WorldEventOutcome(mapOf("era" to era.toString()))
            },
            story = "A forgotten guild banner from Era {era} was raised over the walls; the militia takes heart.",
        ),
        // 24
        WorldEventDef(
            id = "collector", name = "The Collector Arrives", weight = 1.0,
            eligibility = { ctx -> ctx.weapons.values.any { it.isListed && (it.fame > 0 || it.rarity >= Rarity.EPIC) } }, maxPerRun = 2, cooldownDays = 6,
            apply = { ctx ->
                val w = ctx.weapons.values.filter { it.isListed && (it.fame > 0 || it.rarity >= Rarity.EPIC) }
                    .maxWith(compareBy<Weapon> { it.fame }.thenBy { it.quality }.thenBy(IdOrder.numeric) { it.id.value })
                // The collector pays over the shelf price, but never above the going rate times the multiplier: an absurd price mints no gold.
                val price = (minOf(w.listedPrice ?: 0, Market.askingPrice(w, ctx.config)) * ctx.config.collectorPriceMultiplier).toInt()
                ctx.earn(IncomeKind.COLLECTOR, price)
                ctx.reputation += 1
                ctx.updateWeapon(w.copy(location = WeaponLocation.Lost(ctx.day, "sold to a collector")))
                ctx.addWeaponHistory(w.id, "COLLECTED", "Bought by a collector for $price gold and taken to a distant vault.")
                WorldEventOutcome(
                    mapOf("weapon" to w.name, "price" to price.toString()), listOf(w.id.value),
                    MarketVisit(
                        null, "A collector", w.id, VisitReason.COLLECTOR_PURCHASE, kind = VisitKind.COLLECTOR,
                        considered = listOf(Considered(w.id, w.listedPrice ?: 0, listOf(VisitFactor.COLLECTOR_PRIZE))), sale = Sale(listedPrice = w.listedPrice, cashPaid = price),
                    ),
                )
            },
            story = "A collector paid {price} gold for {weapon} and carried it off to a distant vault.",
        ),
        // 25
        WorldEventDef(
            id = "ballad", name = "Ballad of the Blacksmith", weight = 1.5, eligibility = { "FIRST_SALE" in it.milestones }, maxPerRun = 3, cooldownDays = 5,
            apply = { ctx -> ctx.reputation += ctx.config.balladReputation; WorldEventOutcome() },
            story = "A bard's ballad of the Emberfall blacksmith spread through the taverns; customers arrive curious.",
        ),
    )
}
