package com.tinyblacksmith.core

import com.tinyblacksmith.core.combat.EffectDef
import com.tinyblacksmith.core.combat.Fight
import com.tinyblacksmith.core.content.AffixKind
import com.tinyblacksmith.core.content.CombatCatalog
import com.tinyblacksmith.core.content.CombatContent
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.crafting.Bench
import com.tinyblacksmith.core.crafting.Capabilities
import com.tinyblacksmith.core.crafting.Company
import com.tinyblacksmith.core.crafting.CompositionNote
import com.tinyblacksmith.core.crafting.Fighter
import com.tinyblacksmith.core.crafting.ForgeOutlook
import com.tinyblacksmith.core.crafting.Interactions
import com.tinyblacksmith.core.crafting.Need
import com.tinyblacksmith.core.crafting.Place
import com.tinyblacksmith.core.crafting.Query
import com.tinyblacksmith.core.crafting.Res
import com.tinyblacksmith.core.crafting.SignatureCatalog
import com.tinyblacksmith.core.crafting.Verdict
import com.tinyblacksmith.core.crafting.WeaponRules
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.engine.Technique
import com.tinyblacksmith.core.guild.Loadout
import com.tinyblacksmith.core.legacy.Legacy
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.persistence.SaveCodec
import com.tinyblacksmith.core.sim.CombatSandbox
import com.tinyblacksmith.core.sim.ForgePaths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The deep forging foundation (docs/DEEP_FORGING_LEDGER.md): one composition of a weapon's rules, a rule per metal for
 * weapons forged from now on, typed capabilities checked against real fights, an outlook that promises only what every
 * forge delivers, a bench that touches nothing, and a journal that keeps suggestion and observation apart.
 */
class DeepForgingTest {
    private val engine = TestSupport.engine
    private val content = engine.content
    private val config = engine.config
    private val combat: CombatCatalog = content.combat!!
    private val L = LaunchContent

    private fun made(family: WeaponFamilyId, core: MaterialId, augment: MaterialId, catalyst: MaterialId? = null, buffs: List<AffixId> = emptyList(), flaws: List<AffixId> = emptyList(), signature: String? = null, craft: Int = WeaponRules.CRAFT) =
        Bench.demonstration(family, core, augment, catalyst, content, config).copy(affixes = buffs, flaws = flaws, signatureId = signature, craft = craft)

    private fun rules(w: Weapon, home: Boolean = false) = WeaponRules.of(w, content).rules(home).map { it.id }

    private fun bench(w: Weapon, wielder: HeroClassId, companions: String = "alone", target: String = "attrition", relics: List<String> = emptyList()) = Bench.run(w, false, wielder, companions, target, content, config, relics)

    private fun guildRun(seed: Long = 7): GameState = engine.newRun(LegacyProfile(), seed, charterId = GuildContent.TOWNS_LAST_HOPE).copy(materials = content.materials.associate { it.id to 60 }, energy = 10)

    // ---- composition ----

    /** What `Loadout.weaponEffects` was before the resolver, kept here as the record of it. */
    private fun before(w: Weapon, home: Boolean): List<EffectDef> {
        val situational = if (home) combat.affixHomeEffects else combat.affixRoadEffects
        return (combat.familyById[w.familyId]?.effects.orEmpty() + w.element?.let { combat.elementEffects[it] }.orEmpty() +
            (w.affixes + w.flaws).flatMap { combat.affixEffects[it].orEmpty() + situational[it].orEmpty() } + w.catalystId?.let { combat.catalystEffects[it] }.orEmpty() +
            w.signatureId?.let { combat.signatureEffects[it] }.orEmpty()).distinctBy { it.id }
    }

    @Test fun `a weapon forged before metals had a rule resolves exactly as it always did`() {
        val buffs = content.affixes.filter { it.kind == AffixKind.BENEFICIAL }.map { it.id }
        val flaws = content.affixes.filter { it.kind == AffixKind.FLAW }.map { it.id }
        var checked = 0
        for (f in content.families) for (core in content.materials(MaterialCategory.CORE)) for (aug in content.materials(MaterialCategory.AUGMENT)) for (cat in listOf(null) + content.materials(MaterialCategory.CATALYST).map { it.id }) {
            val i = checked++
            val w = made(f.id, core.id, aug.id, cat, listOf(buffs[i % buffs.size], buffs[(i / 3 + 1) % buffs.size]).distinct(), listOf(flaws[i % flaws.size]), SignatureCatalog.all[i % SignatureCatalog.all.size].id.takeIf { i % 4 == 0 }, craft = 1)
            for (home in listOf(false, true)) {
                assertEquals(before(w, home), Loadout.weaponEffects(w, content, if (home) Loadout.Field.HOME else Loadout.Field.ROAD), "${w.name} home=$home")
            }
            val composed = WeaponRules.of(w, content)
            assertEquals(combat.familyById.getValue(f.id).strikePercent, composed.strikePercent)
            assertEquals(combat.familyById.getValue(f.id).supportPercent, composed.supportPercent)
        }
        assertEquals(1080, checked)
        // The ten fixtures of the interaction engine are such weapons: their fights are what they were.
        CombatSandbox.fixtures.flatMap { it.setup.party }.forEach { c -> assertTrue(c.effects.none { it.id.startsWith("core_") || it.id == CombatContent.DRINKING.id }, c.name) }
    }

    @Test fun `the order is family, metal, element, buffs, flaws, catalyst, signature and a shared rule is listed once`() {
        val w = made(L.DAGGER, L.SILVER, L.STORMGLASS, L.BINDING_SALT, buffs = listOf(L.STORMCHARGED, L.SWIFT), flaws = listOf(L.SENTIENT))
        assertEquals(listOf("dagger_flurry", "core_silver", "element_storm", "affix_stormcharged", "charge_burst", "affix_swift", "affix_sentient_road", "catalyst_capacitor"), rules(w))
        assertEquals(listOf("dagger_flurry", "core_silver", "element_storm", "affix_stormcharged", "charge_burst", "affix_swift", "affix_sentient_home", "catalyst_capacitor"), rules(w, home = true))
        val burst = WeaponRules.of(w, content).rule("charge_burst")!!
        assertEquals(WeaponRules.Source.AFFIX, burst.source)
        assertEquals(listOf("Binding Salt"), burst.alsoFrom)
        assertTrue(CompositionNote.Shared("charge_burst", listOf("Stormcharged", "Binding Salt")) in Capabilities.notes(WeaponRules.of(w, content)))
        // Display names decide nothing: a renamed weapon composes the same.
        assertEquals(rules(w), rules(w.copy(name = "Something else entirely")))
    }

    @Test fun `conflicts inside one weapon are named - a state two rules take, a weapon that boils itself, a second crack, a rule that feeds another`() {
        fun notes(w: Weapon) = Capabilities.notes(WeaponRules.of(w, content))
        // Heavy's shatter and Winterwake both take Chill off the target: the flaw is listed first, so it is asked first.
        val contest = notes(made(L.SWORD, L.BRONZE, L.FROST_BLOOM, flaws = listOf(L.HEAVY), signature = "winterwake"))
        assertTrue(CompositionNote.Contest(Res.CHILL, listOf("affix_heavy_shatter", "sig_winterwake")) in contest, contest.toString())
        assertTrue(CompositionNote.BoilsItself in notes(made(L.SWORD, L.BRONZE, L.FROST_BLOOM, L.DRAGON_OIL)))
        assertFalse(CompositionNote.BoilsItself in notes(made(L.SWORD, L.BRONZE, L.EMBER_RESIN, L.DRAGON_OIL)))
        assertTrue(CompositionNote.CracksOnce(listOf("core_obsidian", "affix_brittle")) in notes(made(L.SWORD, L.OBSIDIAN, L.EMBER_RESIN, flaws = listOf(L.BRITTLE))))
        assertTrue(CompositionNote.Feeds(Res.BURN, "element_fire", "catalyst_drinking") in notes(made(L.SWORD, L.IRON, L.EMBER_RESIN, L.VOID_INK)))
        // Each note has a sentence, and none of them is empty.
        val w = made(L.SWORD, L.OBSIDIAN, L.FROST_BLOOM, L.DRAGON_OIL, flaws = listOf(L.BRITTLE))
        notes(w).forEach { assertTrue(Capabilities.text(it, WeaponRules.of(w, content)).length > 20) }
        // A cracked weapon cracks once in the fight too, whatever asked for it.
        val cracked = bench(made(L.SWORD, L.OBSIDIAN, L.EMBER_RESIN, flaws = listOf(L.BRITTLE)), L.GUARDIAN)
        assertEquals(1, cracked.result.events.count { it.kind == com.tinyblacksmith.core.combat.EventKind.FRACTURED })
    }

    // ---- metals ----

    @Test fun `every metal has a rule that a real fight shows, and an older weapon of the same metal does not have it`() {
        // Metal, the hands and company it is shown in, and the rule.
        val shown = listOf(
            Triple(made(L.SWORD, L.IRON, L.EMBER_RESIN), L.DUELIST to "alone", "core_iron"),
            Triple(made(L.SWORD, L.BRONZE, L.EMBER_RESIN), L.GUARDIAN to "alone", "core_bronze"),
            Triple(made(L.SWORD, L.SILVER, L.STORMGLASS, buffs = listOf(L.STORMCHARGED)), L.DUELIST to "alone", "core_silver"),
            Triple(made(L.SWORD, L.OBSIDIAN, L.EMBER_RESIN), L.GUARDIAN to "alone", "core_obsidian"),
            Triple(made(L.SWORD, L.STARSTEEL, L.FROST_BLOOM), L.DUELIST to "spotter", "core_starsteel"),
            Triple(made(L.SWORD, L.MOONSTEEL, L.FROST_BLOOM), L.DUELIST to "alone", "core_moonsteel"),
        )
        assertEquals(content.materials(MaterialCategory.CORE).map { it.id }.toSet(), shown.map { it.first.coreId }.toSet())
        assertEquals(combat.cores.map { it.coreId }.toSet(), shown.map { it.first.coreId }.toSet())
        for ((w, hands, rule) in shown) {
            val target = if (rule == "core_starsteel") "armoured" else "attrition"
            assertTrue(bench(w, hands.first, hands.second, target).fired.any { it.rule.id == rule }, "$rule fires")
            val old = w.copy(craft = 1)
            assertFalse(rule in rules(old), "$rule is not on a weapon forged before")
            assertTrue(bench(old, hands.first, hands.second, target).result.events.none { it.effectId == rule })
        }
        // Obsidian strikes a tenth harder, and only as a new weapon.
        val brann = CombatSandbox.hero("h", "Brann", L.GUARDIAN)
        val glass = made(L.SWORD, L.OBSIDIAN, L.EMBER_RESIN)
        assertEquals(110, WeaponRules.of(glass, content).strikePercent)
        assertTrue(Loadout.combatant(brann, glass, content, config).strikeMin > Loadout.combatant(brann, glass.copy(craft = 1), content, config).strikeMin)
        // Silver's two Charge are a head start, not a burst: nothing on the weapon spends them by itself.
        val silver = bench(made(L.SWORD, L.SILVER, L.EMBER_RESIN), L.DUELIST)
        assertTrue(silver.result.events.none { it.effectId == "charge_burst" })
    }

    @Test fun `a rewritten rule is the new one on a new weapon and the old one, under its old id, on an older weapon`() {
        val ink = made(L.SWORD, L.IRON, L.EMBER_RESIN, L.VOID_INK)
        assertTrue("catalyst_drinking" in rules(ink) && "catalyst_converting" !in rules(ink))
        assertTrue("catalyst_converting" in rules(ink.copy(craft = 1)) && "catalyst_drinking" !in rules(ink.copy(craft = 1)))
        assertEquals(emptyList(), content.validate())
    }

    // ---- capabilities ----

    private fun verdict(q: Query, w: Weapon, wielder: HeroClassId? = null, companions: String = "alone", target: String = "attrition"): Verdict =
        Capabilities.evaluate(q, WeaponRules.of(w, content), if (wielder == null) Company() else Bench.company(wielder, companions, target, content, config))

    @Test fun `can it generate Charge - yes with what it waits for, conditional with the enabler named, no with a reason`() {
        val salted = made(L.SWORD, L.IRON, L.STORMGLASS, L.BINDING_SALT)
        // Alone it has the rule and lacks the healer: the missing enabler is the healing, a typed need.
        val alone = assertIs<Verdict.Needs>(verdict(Query.Generates(Res.CHARGE), salted, L.GUARDIAN))
        assertEquals(listOf<Need>(Need.Present(setOf(Res.HEALING), Place.WIELDER)), alone.missing)
        assertEquals("Somebody or something that heals the wielder.", Capabilities.text(alone.missing.single()))
        assertIs<Verdict.Available>(verdict(Query.Generates(Res.CHARGE), salted, L.GUARDIAN, "healer"))
        // The fights agree with both verdicts.
        assertTrue(bench(salted, L.GUARDIAN, "healer").fired.any { it.rule.id == "catalyst_capacitor" })
        assertTrue(bench(salted, L.GUARDIAN, "alone").silent.any { it.rule.id == "catalyst_capacitor" })
        // "Answers healing" is not "provides healing": the salted sword requires it and makes none.
        assertIs<Verdict.Available>(verdict(Query.Requires(Res.HEALING), salted))
        val none = assertIs<Verdict.CannotProvide>(verdict(Query.Generates(Res.HEALING), salted, L.GUARDIAN, "healer"))
        assertEquals("Nothing on it makes healing.", none.reason)
        assertIs<Verdict.CannotProvide>(verdict(Query.Generates(Res.CHARGE), made(L.SWORD, L.IRON, L.EMBER_RESIN), L.GUARDIAN, "healer"))
    }

    @Test fun `can it spend Charge - only with enough of it, and silver brings enough for one burst`() {
        val salted = made(L.SWORD, L.IRON, L.STORMGLASS, L.BINDING_SALT)
        assertIs<Verdict.Needs>(verdict(Query.Spends(Res.CHARGE), salted, L.GUARDIAN))
        assertIs<Verdict.Available>(verdict(Query.Spends(Res.CHARGE), salted, L.GUARDIAN, "healer"))
        // Silver's three Charge are a gift given once, and three is one burst: the salt needs nobody else for the first.
        val silver = made(L.SWORD, L.SILVER, L.STORMGLASS, L.BINDING_SALT)
        assertIs<Verdict.Available>(verdict(Query.Spends(Res.CHARGE), silver, L.GUARDIAN))
        assertTrue(bench(silver, L.GUARDIAN, "alone").fired.any { it.rule.id == "charge_burst" })
        // The same weapon forged before silver held a charge still waits for a healer.
        val old = silver.copy(craft = 1)
        assertIs<Verdict.Needs>(verdict(Query.Spends(Res.CHARGE), old, L.GUARDIAN))
        assertTrue(bench(old, L.GUARDIAN, "alone").silent.any { it.rule.id == "charge_burst" })
        assertIs<Verdict.CannotProvide>(verdict(Query.Spends(Res.CHARGE), made(L.SWORD, L.SILVER, L.EMBER_RESIN), L.GUARDIAN, "healer"))
    }

    @Test fun `can it protect another ally - the weakest may be the wielder, everybody is everybody`() {
        // Void Ink shields whoever is weakest: no company can promise that is somebody else.
        val ink = assertIs<Verdict.Needs>(verdict(Query.ProtectsAnother, made(L.SWORD, L.IRON, L.EMBER_RESIN, L.VOID_INK), L.GUARDIAN, "healer"))
        assertTrue(ink.missing.any { it is Need.Circumstance })
        // Dawnbrand shields every ally with the Burn it takes, and its own fire supplies the Burn.
        val dawn = made(L.SWORD, L.IRON, L.EMBER_RESIN, L.BINDING_SALT, buffs = listOf(L.FLAMING, L.KEEN), signature = "dawnbrand")
        assertIs<Verdict.Available>(verdict(Query.ProtectsAnother, dawn, L.GUARDIAN, "healer"))
        assertIs<Verdict.CannotProvide>(verdict(Query.ProtectsAnother, made(L.SWORD, L.IRON, L.EMBER_RESIN), L.GUARDIAN, "healer"))
        // Iron's Guard is the wielder's own: it protects nobody else.
        assertTrue(Capabilities.facets(WeaponRules.of(made(L.SWORD, L.IRON, L.EMBER_RESIN), content)).none { it.res == Res.GUARD && it.on.others })
    }

    @Test fun `does it convert Burn into Guard - through the two rules of one weapon, and only where there is Burn to take`() {
        val fireInk = made(L.SWORD, L.IRON, L.EMBER_RESIN, L.VOID_INK)
        assertIs<Verdict.Available>(verdict(Query.Converts(Res.BURN, Res.GUARD), fireInk, L.GUARDIAN))
        assertTrue(bench(fireInk, L.GUARDIAN).fired.map { it.rule.id }.containsAll(listOf("catalyst_drinking", "catalyst_converted_guard")))
        // On a frost weapon the rule is there and the Burn is not: what is missing is the Burn, not the rule.
        val frostInk = made(L.SWORD, L.IRON, L.FROST_BLOOM, L.VOID_INK)
        val cold = assertIs<Verdict.Needs>(verdict(Query.Converts(Res.BURN, Res.GUARD), frostInk, L.GUARDIAN))
        assertEquals(listOf<Need>(Need.Present(setOf(Res.BURN), Place.FOE)), cold.missing)
        assertTrue(bench(frostInk, L.GUARDIAN).silent.any { it.rule.id == "catalyst_converted_guard" })
        assertIs<Verdict.Available>(verdict(Query.Converts(Res.BURN, Res.GUARD), frostInk, L.GUARDIAN, "fire"))
        assertIs<Verdict.CannotProvide>(verdict(Query.Converts(Res.BURN, Res.GUARD), made(L.SWORD, L.IRON, L.EMBER_RESIN, L.DRAGON_OIL), L.GUARDIAN))
        // Steam is a conversion too, and it goes two ways. It takes a fire and a cold from two different hands:
        // a frost weapon with Dragon Oil no longer lights its own fire, and one forged before that change still does.
        val frostOil = made(L.SWORD, L.BRONZE, L.FROST_BLOOM, L.DRAGON_OIL)
        val lone = assertIs<Verdict.Needs>(verdict(Query.Converts(Res.STEAM, Res.DAMAGE), frostOil, L.GUARDIAN))
        assertEquals(listOf<Need>(Need.Present(setOf(Res.BURN), Place.FOE)), lone.missing)
        assertIs<Verdict.Available>(verdict(Query.Converts(Res.STEAM, Res.DAMAGE), frostOil, L.GUARDIAN, "fire"))
        assertIs<Verdict.Available>(verdict(Query.Converts(Res.STEAM, Res.DAMAGE), frostOil.copy(craft = 1), L.GUARDIAN))
        assertTrue("catalyst_spreading" in rules(frostOil.copy(craft = 1)) && "catalyst_spreading_fire" in rules(frostOil))
        assertIs<Verdict.Needs>(verdict(Query.Converts(Res.STEAM, Res.GUARD), made(L.SWORD, L.IRON, L.EMBER_RESIN, L.VOID_INK), L.GUARDIAN))
        assertIs<Verdict.Available>(verdict(Query.Converts(Res.STEAM, Res.GUARD), made(L.SWORD, L.IRON, L.EMBER_RESIN, L.VOID_INK), L.DUELIST, "frost"))
    }

    @Test fun `can it reach the backline - a bow and a storm weapon do, a plain sword does not`() {
        assertIs<Verdict.Available>(verdict(Query.ReachesBackline, made(L.BOW, L.IRON, L.EMBER_RESIN), L.RANGER))
        assertIs<Verdict.Available>(verdict(Query.ReachesBackline, made(L.SWORD, L.IRON, L.STORMGLASS), L.GUARDIAN))
        val sword = made(L.SWORD, L.IRON, L.EMBER_RESIN)
        assertIs<Verdict.CannotProvide>(verdict(Query.ReachesBackline, sword, L.GUARDIAN))
        // The fight agrees: the caster at the back of the line is hit by the bow's rule and by nothing of the sword's.
        fun reaches(w: Weapon, cls: HeroClassId): Boolean { val r = bench(w, cls, target = "backline"); return r.result.events.any { it.effectId in rules(w) && it.target == r.setup.enemies.last().key && it.kind == com.tinyblacksmith.core.combat.EventKind.DAMAGE && it.round == 1 } }
        assertTrue(reaches(made(L.BOW, L.IRON, L.EMBER_RESIN), L.RANGER))
        assertFalse(reaches(sword, L.GUARDIAN))
    }

    @Test fun `does its payoff wait for a fracture - the runestone does, and a cracked weapon in the party is what it lacks`() {
        val rune = made(L.STAFF, L.IRON, L.EMBER_RESIN, L.RUNESTONE_SHARD)
        assertIs<Verdict.Available>(verdict(Query.Requires(Res.FRACTURE), rune))
        assertIs<Verdict.CannotProvide>(verdict(Query.Requires(Res.FRACTURE), made(L.STAFF, L.IRON, L.EMBER_RESIN)))
        val alone = Capabilities.missing(WeaponRules.of(rune, content), Bench.company(L.BATTLEMAGE, "alone", "attrition", content, config)).getValue("catalyst_runed")
        assertTrue(Need.Present(setOf(Res.FRACTURE), Place.PARTY) in alone, alone.toString())
        // Behind a Guardian whose obsidian cracks, nothing is missing, and the rune answers.
        assertFalse("catalyst_runed" in Capabilities.missing(WeaponRules.of(rune, content), Bench.company(L.BATTLEMAGE, "front", "attrition", content, config)))
        assertTrue(bench(rune, L.BATTLEMAGE, "front").fired.any { it.rule.id == "catalyst_runed" })
        // The Guardian's sword cracks where the Guardian stands: an ally's rule is judged with the ally's own class, not the wielder's.
        val guardian = Fighter(combat.kitByClass.getValue(L.GUARDIAN).kit, WeaponRules.of(made(L.SWORD, L.OBSIDIAN, L.EMBER_RESIN), content))
        assertFalse("catalyst_runed" in Capabilities.missing(WeaponRules.of(rune, content), Company(combat.kitByClass.getValue(L.BATTLEMAGE).kit, listOf(guardian))))
        val duelist = guardian.copy(kit = combat.kitByClass.getValue(L.DUELIST).kit)
        assertTrue("catalyst_runed" in Capabilities.missing(WeaponRules.of(rune, content), Company(combat.kitByClass.getValue(L.BATTLEMAGE).kit, listOf(duelist))))
    }

    @Test fun `a metal's rule does not see what its own element lays with the same hit, a flaw's rule does`() {
        // Moonsteel is asked before the element: one hit a round leaves it waiting. A Duelist's second cut finds the Chill.
        val moon = made(L.SWORD, L.MOONSTEEL, L.FROST_BLOOM)
        assertTrue("core_moonsteel" in Capabilities.missing(WeaponRules.of(moon, content), Bench.company(L.GUARDIAN, "alone", "attrition", content, config)))
        assertFalse("core_moonsteel" in Capabilities.missing(WeaponRules.of(moon, content), Bench.company(L.DUELIST, "alone", "attrition", content, config)))
        assertTrue(bench(moon, L.GUARDIAN).silent.any { it.rule.id == "core_moonsteel" })
        assertTrue(bench(moon, L.DUELIST).fired.any { it.rule.id == "core_moonsteel" })
        // Heavy's shatter is asked after the element and breaks the Chill the same hit laid.
        val heavy = made(L.AXE, L.BRONZE, L.FROST_BLOOM, flaws = listOf(L.HEAVY))
        assertFalse("affix_heavy_shatter" in Capabilities.missing(WeaponRules.of(heavy, content), Bench.company(L.GUARDIAN, "alone", "attrition", content, config)))
        assertTrue(bench(heavy, L.GUARDIAN).fired.any { it.rule.id == "affix_heavy_shatter" })
        // The same flaw on a fire axe has no Chill to break: a flaw that pays in one build is only a flaw in another.
        val wrong = made(L.AXE, L.BRONZE, L.EMBER_RESIN, flaws = listOf(L.HEAVY))
        assertTrue("affix_heavy_shatter" in Capabilities.missing(WeaponRules.of(wrong, content), Bench.company(L.GUARDIAN, "alone", "attrition", content, config)))
        assertTrue(bench(wrong, L.GUARDIAN).silent.any { it.rule.id == "affix_heavy_shatter" })
    }

    @Test fun `every need has words and every rule of the catalog has facts`() {
        val all = combat.allEffects.distinctBy { it.id }
        for (e in all) {
            val facets = Capabilities.facets(e)
            assertTrue(facets.isNotEmpty(), "${e.id} has no facts")
            facets.flatMap { it.needs }.forEach { assertTrue(Capabilities.text(it).endsWith("."), "${e.id}: ${Capabilities.text(it)}") }
        }
        assertEquals(emptyList(), content.validate())
    }

    // ---- the outlook ----

    @Test fun `the outlook promises only what every forge of the recipe delivers, and nothing it calls impossible ever appears`() {
        val base = guildRun()
        val neutral = content.affixes.filter { it.kind == AffixKind.BENEFICIAL && it.element == null }.map { it.id }.toSet()
        val recipes = listOf(
            Command.Forge(ForgeMode.QUICK, L.SWORD, L.IRON, L.EMBER_RESIN, null, Risk.SAFE),
            Command.Forge(ForgeMode.QUICK, L.DAGGER, L.BRONZE, L.FROST_BLOOM, null, Risk.BALANCED),
            Command.Forge(ForgeMode.QUICK, L.STAFF, L.MOONSTEEL, L.FROST_BLOOM, null, Risk.RECKLESS),
            Command.Forge(ForgeMode.ADVANCED, L.SWORD, L.IRON, L.STORMGLASS, L.BINDING_SALT, Risk.BALANCED),
            Command.Forge(ForgeMode.ADVANCED, L.SWORD, L.SILVER, L.STORMGLASS, null, Risk.SAFE, Technique.QUENCH),
            Command.Forge(ForgeMode.ADVANCED, L.AXE, L.OBSIDIAN, L.EMBER_RESIN, L.VOID_INK, Risk.BALANCED, Technique.ETCH),
            Command.Forge(ForgeMode.ADVANCED, L.SPEAR, L.STARSTEEL, L.SUN_ASH, L.DRAGON_OIL, Risk.SAFE, Technique.TEMPER),
        )
        for (cmd in recipes) {
            val outlook = assertNotNull(ForgeOutlook.of(base, content, config, cmd))
            val certain = outlook.certain.rules.map { it.id }.toSet()
            val elementBuff = content.affixes.first { it.kind == AffixKind.BENEFICIAL && it.element == content.material(cmd.augmentId).element }.id
            val may = outlook.possible.map { it.kind }.toSet()
            var seenElementBuff = 0
            var seenFlaw = 0
            repeat(120) { seed ->
                val run = engine.newRun(LegacyProfile(), 1000L + seed, charterId = GuildContent.TOWNS_LAST_HOPE).copy(materials = base.materials, energy = 10)
                val forged = (engine.handle(run, cmd) as CommandOutcome.Accepted).let { out -> out.state.weapons.values.single { it.id !in run.weapons } }
                assertEquals(WeaponRules.CRAFT, forged.craft)
                assertTrue(rules(forged).toSet().containsAll(certain.filter { r -> outlook.certain.rule(r)!!.where != WeaponRules.Where.HOME }.toSet()), "$cmd seed $seed: ${certain - rules(forged).toSet()} was promised")
                if (elementBuff in forged.affixes) seenElementBuff++ else assertFalse(elementBuff in outlook.certain.parts.affixes, "$cmd seed $seed: the element's buff was promised")
                if (elementBuff in forged.affixes) assertTrue(elementBuff in outlook.certain.parts.affixes || ForgeOutlook.Kind.ELEMENT_BUFF in may, "$cmd: an element buff the outlook ruled out")
                if (forged.affixes.any { it in neutral } && forged.signatureId == null) assertTrue(ForgeOutlook.Kind.OTHER_BUFF in may, "$cmd: another buff the outlook ruled out")
                if (forged.flaws.isNotEmpty()) { seenFlaw++; assertTrue(ForgeOutlook.Kind.FLAW in may, "$cmd: a flaw the outlook ruled out") }
                assertTrue(forged.quality in 1..100)
            }
            // Quench forces the element's buff: said as certain, and it is.
            if (cmd.technique == Technique.QUENCH) { assertTrue(elementBuff in outlook.certain.parts.affixes); assertEquals(120, seenElementBuff) }
            // What is only possible is never listed among the certain rules.
            if (ForgeOutlook.Kind.ELEMENT_BUFF in may) assertFalse(elementBuff in outlook.certain.parts.affixes, "$cmd: the element's buff is both certain and possible")
            assertTrue(outlook.parts.size >= 3 && outlook.role.isNotBlank() && outlook.tradeoff != null)
        }
    }

    @Test fun `the outlook names missing enablers, the trade-off and a known signature, and never a signature the journal does not hold`() {
        val base = guildRun()
        val salted = Command.Forge(ForgeMode.ADVANCED, L.SWORD, L.IRON, L.STORMGLASS, L.BINDING_SALT, Risk.BALANCED)
        val alone = ForgeOutlook.of(base, content, config, salted)!!
        assertEquals(listOf<Need>(Need.Present(setOf(Res.HEALING), Place.WIELDER)), alone.missing.getValue("catalyst_capacitor"))
        assertEquals("It needs a healer, and healing a wielder at full health stores nothing.", alone.tradeoff)
        assertEquals(listOf("Sword", "Iron", "Stormglass", "Binding Salt"), alone.parts.map { it.name })
        val healed = ForgeOutlook.of(base, content, config, salted, Bench.company(L.GUARDIAN, "healer", "attrition", content, config))!!
        assertFalse("catalyst_capacitor" in healed.missing)
        // Dawnbrand's recipe: nothing of it is said until the journal holds the signature.
        val dawn = Command.Forge(ForgeMode.ADVANCED, L.SWORD, L.IRON, L.EMBER_RESIN, L.BINDING_SALT, Risk.BALANCED)
        assertTrue(ForgeOutlook.of(base, content, config, dawn)!!.possible.none { it.kind == ForgeOutlook.Kind.SIGNATURE })
        val knowing = base.copy(legacy = base.legacy.copy(journal = base.legacy.journal.copy(interactions = mapOf("sig:dawnbrand" to KnowledgeState.SIGNATURE_DISCOVERED))))
        val known = ForgeOutlook.of(knowing, content, config, dawn)!!.possible.single { it.kind == ForgeOutlook.Kind.SIGNATURE }
        assertEquals("Dawnbrand", known.name)
        assertTrue(known.rules.map { it.id }.containsAll(listOf("sig_dawnbrand", "sig_dawnbrand_guard")))
        // Asking changes nothing.
        assertEquals(SaveCodec.encodeRun(base), SaveCodec.encodeRun(base.also { ForgeOutlook.of(it, content, config, salted) }))
    }

    // ---- the bench ----

    @Test fun `the bench runs the production fight, reads no run and leaves a save byte for byte as it was`() {
        val (state, weapon) = (engine.handle(guildRun(), Command.Forge(ForgeMode.ADVANCED, L.SWORD, L.IRON, L.STORMGLASS, L.BINDING_SALT, Risk.BALANCED)) as CommandOutcome.Accepted).let { it.state to it.state.weapons.values.maxBy { w -> w.forgedDay * 1000 + w.id.value.drop(1).toInt() } }
        val before = SaveCodec.encodeRun(state)
        val legacyBefore = SaveCodec.encodeLegacy(state.legacy)
        val reports = Bench.targets.flatMap { t -> Bench.companions.map { c -> Bench.run(weapon, false, L.GUARDIAN, c.id, t.id, content, config) } }
        assertEquals(before, SaveCodec.encodeRun(state))
        assertEquals(legacyBefore, SaveCodec.encodeLegacy(state.legacy))
        // The same engine: resolving the report's own setup again gives the same timeline, whatever the run's seed.
        for (r in reports) {
            assertEquals(r.result.events, Fight.resolve(r.setup).events)
            assertEquals(Bench.SEED, r.setup.seed)
            assertTrue(r.synthetic.any { "made-up" in it } && r.synthetic.any { "Nothing here costs or earns gold, adds to your notebook or goes into the records." == it })
            // Every rule the weapon has on the road is accounted for: it fired, or the report says why not.
            assertEquals(rules(weapon).toSet(), (r.fired.map { it.rule.id } + r.silent.map { it.rule.id }).toSet())
            r.silent.forEach { assertTrue(it.why.endsWith(".") && it.why.length > 10) }
            r.fired.forEach { assertTrue(it.chain.isNotEmpty() && it.times >= 1) }
        }
        // A recipe that is not a weapon yet is said to be one, and carries nothing a roll decides.
        val demo = Bench.demonstration(L.SWORD, L.IRON, L.STORMGLASS, L.BINDING_SALT, content, config)
        assertTrue(demo.affixes.isEmpty() && demo.flaws.isEmpty() && demo.signatureId == null)
        assertTrue(Bench.run(demo, true, L.GUARDIAN, "healer", "attrition", content, config).synthetic.any { it.startsWith("This is a recipe, not a weapon you own") })
        // A rule the weapon has only on the wall is named as not tested here.
        val sentient = made(L.SWORD, L.IRON, L.EMBER_RESIN, flaws = listOf(L.SENTIENT))
        assertEquals(listOf("affix_sentient_home"), bench(sentient, L.GUARDIAN).notTested.map { it.id })
    }

    @Test fun `the bench says why a rule stayed silent, in the fight's own terms`() {
        fun why(w: Weapon, cls: HeroClassId, rule: String, companions: String = "alone", target: String = "attrition") = bench(w, cls, companions, target).silent.single { it.rule.id == rule }.why
        assertEquals("Nobody healed Bench Guardian.", why(made(L.SWORD, L.IRON, L.STORMGLASS, L.BINDING_SALT), L.GUARDIAN, "catalyst_capacitor"))
        assertEquals("Bench Duelist never had Armor for an enemy to break.", why(made(L.SWORD, L.BRONZE, L.EMBER_RESIN), L.DUELIST, "core_bronze"))
        assertEquals("No weapon in the party cracked.", why(made(L.STAFF, L.IRON, L.EMBER_RESIN, L.RUNESTONE_SHARD), L.BATTLEMAGE, "catalyst_runed"))
        assertEquals("There was no Mark for Bench Duelist's hit to use up.", why(made(L.SWORD, L.STARSTEEL, L.FROST_BLOOM), L.DUELIST, "core_starsteel", target = "armoured"))
        assertEquals("Bench Guardian never got Steam.", why(made(L.SWORD, L.IRON, L.EMBER_RESIN, L.DRAGON_OIL), L.GUARDIAN, "catalyst_steam_lance", target = "armoured"))
        assertEquals("None of these enemies is an Elite.", why(made(L.SWORD, L.IRON, L.EMBER_RESIN, buffs = listOf(L.GIANT_SLAYER)), L.GUARDIAN, "affix_giant_slayer", target = "armoured"))
    }

    // ---- the journal ----

    @Test fun `forging in a guild run learns the weapon's rules, and a classic run learns none`() {
        val cmd = Command.Forge(ForgeMode.ADVANCED, L.SWORD, L.IRON, L.STORMGLASS, L.BINDING_SALT, Risk.BALANCED)
        val guild = (engine.handle(guildRun(), cmd) as CommandOutcome.Accepted).state
        val learned = guild.legacy.journal.learnedRules
        assertTrue(learned.containsAll(listOf("core_iron", "element_storm", "catalyst_capacitor", "charge_burst")), learned.toString())
        assertEquals(emptySet(), guild.legacy.journal.witnessed)
        val classic = (engine.handle(engine.newRun(LegacyProfile(), 7).copy(materials = content.materials.associate { it.id to 60 }, energy = 10), cmd) as CommandOutcome.Accepted).state
        assertEquals(emptySet(), classic.legacy.journal.learnedRules)
        // The page: the rules as learned properties, the path they take part in as a suggestion, the rest still hidden.
        val page = Interactions.page(guild.legacy.journal, combat)
        assertTrue(page.learned.map { it.id }.containsAll(listOf("catalyst_capacitor", "charge_burst")))
        val charge = page.entries.single()
        assertEquals("charge", charge.def.id)
        assertEquals(Interactions.Evidence.SUGGESTED, charge.evidence)
        assertEquals(listOf("catalyst_capacitor"), charge.known.map { it.id })
        assertEquals(combat.interactions.size - 1, page.hidden)
    }

    @Test fun `a path is witnessed only by a real fight handed to the journal - never by an outlook, the bench or forging`() {
        val cmd = Command.Forge(ForgeMode.ADVANCED, L.SWORD, L.IRON, L.STORMGLASS, L.BINDING_SALT, Risk.BALANCED)
        var state = (engine.handle(guildRun(), cmd) as CommandOutcome.Accepted).state
        val weapon = state.weapons.values.first { it.catalystId == L.BINDING_SALT }
        // Everything that predicts or rehearses it: the outlook, the evaluator, the bench with a healer, where the burst does fire.
        ForgeOutlook.of(state, content, config, cmd, Bench.company(L.GUARDIAN, "healer", "attrition", content, config))
        val rehearsal = Bench.run(weapon, false, L.GUARDIAN, "healer", "attrition", content, config)
        assertTrue("charge" in Interactions.shownIn(rehearsal.result, combat))
        assertEquals(emptySet(), state.legacy.journal.witnessed)
        assertEquals(Interactions.Evidence.SUGGESTED, Interactions.page(state.legacy.journal, combat).entries.single().evidence)

        // The engine's hook, with the record of a fight that showed it: written once, told once.
        val ctx = ResolutionContext(state, content, config)
        Interactions.witness(ctx, rehearsal.result)
        assertEquals(setOf("charge"), ctx.legacy.journal.witnessed)
        val told = ctx.newEvents.filter { it.type == EventType.DISCOVERY && it.data["interaction"] == "charge" }
        assertEquals(1, told.size)
        assertTrue(told.single().text.startsWith("Seen in a real fight. Charge and Discharge."))
        Interactions.witness(ctx, rehearsal.result)
        assertEquals(1, ctx.newEvents.count { it.data["interaction"] == "charge" })
        state = state.copy(legacy = ctx.legacy)
        assertEquals(Interactions.Evidence.WITNESSED, Interactions.page(state.legacy.journal, combat).entries.single { it.def.id == "charge" }.evidence)

        // A fight that did not show it writes nothing.
        val quiet = ResolutionContext(guildRun(), content, config)
        Interactions.witness(quiet, Bench.run(weapon, false, L.GUARDIAN, "alone", "attrition", content, config).result)
        assertEquals(emptySet(), quiet.legacy.journal.witnessed)
        assertTrue(quiet.newEvents.isEmpty())
        // An enemy's rule firing is not the party's doing: only party-side events count.
        assertTrue(Interactions.shownIn(CombatSandbox.run("retreat"), combat).isEmpty())
    }

    @Test fun `the two real fight sites hand their result to the journal`() {
        // The hooks are single lines beside each production `Fight.resolve`: their presence is what this pins.
        fun source(name: String) = java.io.File("src/main/kotlin/com/tinyblacksmith/core/guild/$name").readText()
        for (file in listOf("Missions.kt", "SiegeFight.kt")) {
            val text = source(file)
            assertEquals(1, Regex("""Fight\.resolve\(""").findAll(text).count(), "$file resolves one fight")
            assertEquals(1, Regex("""Interactions\.witness\(ctx, """).findAll(text).count(), "$file hands it over")
        }
    }

    @Test fun `what the journal learned and saw survives the end of a run and a save`() {
        val a = Journal(learnedRules = setOf("core_iron"), witnessed = setOf("charge"))
        val text = SaveCodec.encodeLegacy(LegacyProfile(journal = a))
        assertEquals(a, SaveCodec.decodeLegacy(text).journal)
        // A profile written before the fields existed reads as having learned and seen nothing.
        val older = text.replace(Regex(""",\\"learnedRules\\":\[[^\]]*\],\\"witnessed\\":\[[^\]]*\]"""), "")
        assertFalse("learnedRules" in older)
        assertEquals(Journal(), SaveCodec.decodeLegacy(older).journal)
        // The merge at a run's end is a union.
        val merge = Legacy::class.java.getDeclaredMethod("mergeJournal", Journal::class.java, Journal::class.java).apply { isAccessible = true }
        val merged = merge.invoke(Legacy, a, Journal(learnedRules = setOf("core_bronze"), witnessed = setOf("steam"))) as Journal
        assertEquals(setOf("core_iron", "core_bronze"), merged.learnedRules)
        assertEquals(setOf("charge", "steam"), merged.witnessed)
    }

    // ---- old weapons and saves ----

    @Test fun `weapons in every older save decode as the older composition and fight without a metal's rule`() {
        val saves = listOf("v1_forced_seed4242_day61.json", "v2_forced_seed4242_day61.json", "v3_forced_seed4242_day61.json", "v5_relic_full_workshop.json", "v5_visitor_merchant.json")
        var weapons = 0
        for (name in saves) {
            val state = SaveCodec.decodeRun(DeepForgingTest::class.java.getResource("/saves/$name")!!.readText())
            for (w in state.weapons.values) {
                weapons++
                assertEquals(1, w.craft, "$name ${w.name}")
                assertTrue(rules(w).none { it.startsWith("core_") || it == "catalyst_drinking" }, "$name ${w.name}")
                assertEquals(before(w, false), Loadout.weaponEffects(w, content))
            }
        }
        assertTrue(weapons > 20, "the fixtures hold weapons ($weapons)")
        // The field survives a save, and a new weapon stays new.
        val forged = (engine.handle(guildRun(), TestSupport.quickSword()) as CommandOutcome.Accepted).state
        val again = SaveCodec.decodeRun(SaveCodec.encodeRun(forged))
        assertEquals(forged.weapons, again.weapons)
        assertTrue(again.weapons.values.any { it.craft == WeaponRules.CRAFT && "core_iron" in rules(it) })
    }

    @Test fun `an older weapon is the same weapon after the hone, the shelf, a loan, a contract and a save`() {
        var s = guildRun()
        s.guild!!.openingOffer.firstOrNull()?.let { s = (engine.handle(s, Command.ChooseOpening(it.defId)) as CommandOutcome.Accepted).state }
        // Two old weapons, one for each catalyst whose rule has been rewritten since, and silver, which holds a charge only when forged since.
        val oil = made(L.SWORD, L.BRONZE, L.FROST_BLOOM, L.DRAGON_OIL, craft = 1).copy(id = WeaponId("old-oil"), name = "Old Bronze Sword", condition = 60)
        val ink = made(L.SWORD, L.SILVER, L.EMBER_RESIN, L.VOID_INK, craft = 1).copy(id = WeaponId("old-ink"), name = "Old Silver Sword", condition = 60)
        val expected = mapOf(oil.id to rules(oil), ink.id to rules(ink))
        assertTrue("catalyst_spreading" in rules(oil) && "catalyst_converting" in rules(ink))
        assertTrue(expected.values.flatten().none { it.startsWith("core_") || it == "catalyst_drinking" || it == "catalyst_spreading_fire" })
        s = s.copy(weapons = s.weapons + (oil.id to oil) + (ink.id to ink))
        fun same(step: String) = expected.forEach { (id, was) -> val w = s.weapons.getValue(id); assertEquals(1, w.craft, "$step ${w.name}"); assertEquals(was, rules(w), "$step ${w.name}") }
        fun send(step: String, c: Command) { s = (engine.handle(s, c) as? CommandOutcome.Accepted)?.state ?: error("$step was refused: ${engine.handle(s, c)}"); same(step) }
        send("honed", Command.Hone(oil.id))
        assertTrue(s.weapons.getValue(oil.id).honed)
        send("listed", Command.ToggleShelf(ink.id, true, 40))
        send("priced", Command.SetPrice(ink.id, 55))
        send("unlisted", Command.ToggleShelf(ink.id, false))
        val members = s.guild!!.members.map { it.heroId }
        send("loaned", Command.LoanWeapon(members[0], oil.id))
        send("loaned", Command.LoanWeapon(members[1], ink.id))
        send("recalled", Command.RecallLoan(ink.id))
        send("loaned again", Command.LoanWeapon(members[1], ink.id))
        s = SaveCodec.decodeRun(SaveCodec.encodeRun(s)); same("saved and loaded")
        // A real contract (the supplier's road is fought too): the fight the old weapons are carried into runs their old rules and none of the new ones.
        val offer = s.guild!!.offers.first { o -> content.guild!!.mission(o.defId).archetype == com.tinyblacksmith.core.content.MissionArchetype.SUPPLY }
        send("planned", Command.PlanDeployment(offer.id, members.take(2), com.tinyblacksmith.core.combat.Posture.BALANCED))
        send("fought", Command.EndDay(CommandId("old-weapons")))
        val fight = assertNotNull(s.guild!!.lastMission?.fight, "the contract was fought")
        assertTrue(fight.events.mapNotNull { it.effectId }.none { it.startsWith("core_") || it == "catalyst_drinking" || it == "catalyst_spreading_fire" }, "no rule from after their forging ran")
        s = SaveCodec.decodeRun(SaveCodec.encodeRun(s)); same("saved after the fight")
    }

    @Test fun `a legend comes back as the composition it was forged under`() {
        fun entry(craft: Int) = LegendEntry(1, "Old Iron", "Wallkeeper", 3, 9, listOf("Mira"), familyId = L.SWORD, coreId = L.IRON, augmentId = L.EMBER_RESIN, quality = 60, power = 20, weaponKey = "era1-w1", craft = craft)
        assertEquals(1, LegendEntry(1, "Old Iron", "Wallkeeper", 3, 9, listOf("Mira")).craft)
        assertEquals(2, SaveCodec.decodeLegacy(SaveCodec.encodeLegacy(LegacyProfile(legendBoard = listOf(entry(2))))).legendBoard.single().craft)
        // The two lines that carry it: the board remembers the weapon's composition, and the return gives it back.
        val legacy = java.io.File("src/main/kotlin/com/tinyblacksmith/core/legacy/Legacy.kt").readText()
        val world = java.io.File("src/main/kotlin/com/tinyblacksmith/core/engine/WorldEvents.kt").readText()
        assertTrue("craft = w.craft" in legacy)
        assertTrue("craft = legend.craft" in world)
    }

    // ---- the paths ----

    @Test fun `every interaction path fires in its two recipes and stays silent without its enabler`() {
        assertTrue(ForgePaths.paths.size >= 6)
        for (p in ForgePaths.paths) {
            assertTrue(p.cases.size >= 3, p.id)
            // Two recipes that differ in what they are made of or who carries them.
            val (first, second) = p.cases
            assertTrue(first.weapon.coreId != second.weapon.coreId || first.weapon.augmentId != second.weapon.augmentId || first.weapon.catalystId != second.weapon.catalystId || first.wielder != second.wielder, p.id)
            for (c in p.cases) {
                val report = ForgePaths.run(c)
                val fired = ForgePaths.fired(report)
                assertTrue(fired.containsAll(c.fires), "${p.id} / ${c.label}: expected ${c.fires - fired} to fire")
                assertTrue(fired.none { it in c.silent }, "${p.id} / ${c.label}: ${fired.intersect(c.silent)} fired")
                // The catalog's own path definition agrees with the fight that is meant to show it.
                val def = combat.interactions.firstOrNull { it.id == p.id } ?: continue
                if (c.silent.isEmpty()) assertTrue(p.id in Interactions.shownIn(report.result, combat), "${p.id} / ${c.label} shows the path")
            }
            assertTrue(p.cases.any { it.silent.isNotEmpty() }, "${p.id} has a case where it stays silent")
        }
        assertEquals(combat.interactions.map { it.id }.toSet(), ForgePaths.paths.map { it.id }.toSet())
    }

    // ---- what "available" promises, and what it does not ----

    @Test fun `an answer that hangs on a second step is said to be unchecked, not available`() {
        fun rule(id: String, trigger: com.tinyblacksmith.core.combat.Trigger, action: com.tinyblacksmith.core.combat.Action) =
            EffectDef(id, id, trigger, listOf(action), com.tinyblacksmith.core.combat.Limit(perRound = 1), "", party = true)
        val foe = com.tinyblacksmith.core.combat.Aim.Foe
        // The Chill a Moonsteel weapon waits for comes only after a Mark, and the Mark only after healing: two steps.
        val chillAfterMark = rule("t_chill", com.tinyblacksmith.core.combat.Trigger(com.tinyblacksmith.core.combat.EventKind.STAT_GAINED, com.tinyblacksmith.core.combat.Who.FOE_TARGET, com.tinyblacksmith.core.combat.Stat.MARK), com.tinyblacksmith.core.combat.Action.Give(com.tinyblacksmith.core.combat.Stat.CHILL, foe))
        val markAfterHealing = rule("t_mark", com.tinyblacksmith.core.combat.Trigger(com.tinyblacksmith.core.combat.EventKind.HEALED, com.tinyblacksmith.core.combat.Who.ALLY_TARGET), com.tinyblacksmith.core.combat.Action.Give(com.tinyblacksmith.core.combat.Stat.MARK, foe))
        val plainMark = rule("t_plain", com.tinyblacksmith.core.combat.Trigger(com.tinyblacksmith.core.combat.EventKind.ROUND_START, com.tinyblacksmith.core.combat.Who.ANY), com.tinyblacksmith.core.combat.Action.Give(com.tinyblacksmith.core.combat.Stat.MARK, foe))
        val moon = WeaponRules.of(made(L.SWORD, L.MOONSTEEL, L.VERDANT_SAP), content)
        val guardian = combat.kitByClass.getValue(L.GUARDIAN).kit
        fun open(vararg relics: EffectDef) = Capabilities.missing(moon, Company(guardian, relics = relics.toList()))["core_moonsteel"]
        // Nothing brings Chill: a plain need.
        assertTrue(open()!!.none { it is Need.Unverified })
        // Chill after a Mark, and a Mark that waits for nothing: one step, known.
        assertEquals(null, open(chillAfterMark, plainMark))
        // Chill after a Mark that itself waits for healing: not followed, and said so.
        val far = open(chillAfterMark, markAfterHealing)!!
        assertIs<Need.Unverified>(far.single())
        assertTrue("not checked here" in Capabilities.text(far.single()))
    }

    @Test fun `a verdict reads as available when, needs, or cannot provide`() {
        val salted = made(L.SWORD, L.IRON, L.STORMGLASS, L.BINDING_SALT)
        val c = WeaponRules.of(salted, content)
        fun say(q: Query, companions: String) = Capabilities.text(Capabilities.evaluate(q, c, Bench.company(L.GUARDIAN, companions, "attrition", content, config)), c)
        assertTrue(say(Query.Generates(Res.CHARGE), "healer").startsWith("Available when its trigger happens: "))
        assertTrue(say(Query.Generates(Res.CHARGE), "alone").startsWith("Needs: "))
        assertTrue(say(Query.Generates(Res.HEALING), "healer").startsWith("Cannot provide. "))
    }

    @Test fun `Wet is left out of ordinary guidance because only a Storm front brings it`() {
        // True of the catalog: no weapon rule, class, relic or enemy puts Wet on anybody. When one does, this wording has to change.
        val everything = combat.allEffects.flatMap { Capabilities.facets(it) } + combat.kits.flatMap { Capabilities.facets(it.kit) } + combat.units.flatMap { Capabilities.facets(it) }
        // (A Battlemage echoes Wet that is already there. That is not a source.)
        assertTrue(everything.none { f -> f.res == Res.WET && f.verb == com.tinyblacksmith.core.crafting.Verb.PROVIDES && f.needs.none { it is Need.Present && Res.WET in it.any } })
        assertTrue(GuildContent.catalog.laws.any { com.tinyblacksmith.core.combat.Stat.WET in it.enemyStats })
        assertEquals("Chill on an enemy.", Capabilities.text(Need.Present(setOf(Res.CHILL, Res.WET), Place.FOE)))
        assertEquals("Wet on an enemy. Only a Storm front brings it.", Capabilities.text(Need.Present(setOf(Res.WET), Place.FOE)))
        // The recipe guidance of a fire weapon names Chill for its Steam and never Wet.
        val outlook = ForgeOutlook.of(guildRun(), content, config, Command.Forge(ForgeMode.ADVANCED, L.SWORD, L.IRON, L.EMBER_RESIN, L.DRAGON_OIL, Risk.BALANCED))!!
        val words = outlook.missing.values.flatten().map(Capabilities::text)
        assertTrue(words.isNotEmpty() && words.none { "Wet" in it }, "$words")
    }

    @Test fun `the bench can stand on the town's wall, where home rules count and road rules do not`() {
        val sentient = made(L.SWORD, L.IRON, L.EMBER_RESIN, flaws = listOf(L.SENTIENT))
        val road = bench(sentient, L.GUARDIAN)
        val wall = bench(sentient, L.GUARDIAN, target = "wall")
        assertFalse(road.home)
        assertTrue(wall.home)
        assertEquals(listOf("affix_sentient_home"), road.notTested.map { it.id })
        assertEquals(listOf("affix_sentient_road"), wall.notTested.map { it.id })
        assertTrue("affix_sentient_home" in wall.fired.map { it.rule.id }, "the home rule is fought, not only named")
        assertTrue("affix_sentient_road" in (road.fired + road.silent.map { Bench.Fired(it.rule, 0, emptyList()) }).map { it.rule.id })
        assertTrue(wall.synthetic.any { "wall here is made up" in it })
        assertTrue(Bench.targets.single { it.home }.name == "The town's wall")
    }
}
