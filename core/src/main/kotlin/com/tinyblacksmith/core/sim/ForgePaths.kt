package com.tinyblacksmith.core.sim

import com.tinyblacksmith.core.combat.Side
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.CombatContent
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.crafting.Bench
import com.tinyblacksmith.core.model.AffixId
import com.tinyblacksmith.core.model.HeroClassId
import com.tinyblacksmith.core.model.MaterialId
import com.tinyblacksmith.core.model.Weapon
import com.tinyblacksmith.core.model.WeaponFamilyId

/**
 * The interaction paths as fights anyone can rerun (`./gradlew :core:combat --args="paths"`, one with
 * `--args="paths steam"`). Each path has a first recipe, a second that reaches it another way, and a case where it
 * stays silent or something else does better. `DeepForgingPathsTest` asserts every [Case.fires] and [Case.silent].
 */
object ForgePaths {
    private val content: ContentCatalog = LaunchContent.catalog
    private val config: BalanceConfig = BalanceConfig.DEFAULT

    /** [fires] and [silent] are rule IDs: weapon rules, or a relic's rule when [relics] bring one. */
    data class Case(
        val label: String, val recipe: String, val weapon: Weapon, val wielder: HeroClassId, val companions: String, val target: String,
        val fires: Set<String> = emptySet(), val silent: Set<String> = emptySet(), val relics: List<String> = emptyList(),
    )

    data class Path(val id: String, val name: String, val relationship: String, val tradeoff: String, val alternative: String, val cases: List<Case>)

    private fun made(family: WeaponFamilyId, core: MaterialId, augment: MaterialId, catalyst: MaterialId? = null, buffs: List<AffixId> = emptyList(), flaws: List<AffixId> = emptyList()): Weapon =
        Bench.demonstration(family, core, augment, catalyst, content, config).let { it.copy(affixes = buffs, flaws = flaws, name = it.name.removeSuffix(" (recipe)")) }

    private val L = LaunchContent

    val paths: List<Path> = listOf(
        Path("charge", "Healing into Charge into a burst", "Anything that stores Charge builds toward one burst at 3. Healing fills a salted weapon, hits fill a Stormcharged one, silver starts two ahead.",
            "The burst waits for the third Charge. A salted weapon stores nothing from healing its wielder does not need.",
            "With no healer and no quench, a plain storm weapon arcs every round for less but never waits.", listOf(
                Case("First recipe", "Iron + Stormglass + Binding Salt sword, a Guardian, with a healer", made(L.SWORD, L.IRON, L.STORMGLASS, L.BINDING_SALT), L.GUARDIAN, "healer", "attrition", fires = setOf("catalyst_capacitor", "charge_burst")),
                Case("Second recipe", "Silver + Stormglass sword, quenched for Stormcharged, a Duelist, alone", made(L.SWORD, L.SILVER, L.STORMGLASS, buffs = listOf(L.STORMCHARGED)), L.DUELIST, "alone", "armoured", fires = setOf("core_silver", "affix_stormcharged", "charge_burst")),
                Case("Without its enabler", "The first recipe with nobody to heal", made(L.SWORD, L.IRON, L.STORMGLASS, L.BINDING_SALT), L.GUARDIAN, "alone", "attrition", silent = setOf("catalyst_capacitor", "charge_burst")),
            )),
        Path("steam", "Heat on cold into Steam", "Burn on a chilled enemy, or Chill on a burning one, cancels both and gives Steam to whoever did it. Dragon Oil spends Steam as a lance through Guard. Void Ink spends it as Guard for all.",
            "The Burn and the Chill are both gone. Nothing burns and the enemy is not slowed.",
            "Against a line with no Guard to pierce, leaving the Chill on to weaken their strikes can be worth more than the lance.", listOf(
                Case("First recipe", "Bronze + Frost Bloom + Dragon Oil sword, a Guardian, behind a fire mage", made(L.SWORD, L.BRONZE, L.FROST_BLOOM, L.DRAGON_OIL), L.GUARDIAN, "fire", "armoured", fires = setOf("catalyst_steam_lance")),
                Case("Second recipe", "Iron + Ember Resin + Void Ink sword, a Duelist, behind a frost mage", made(L.SWORD, L.IRON, L.EMBER_RESIN, L.VOID_INK), L.DUELIST, "frost", "attrition", fires = setOf("catalyst_steam_shield")),
                Case("Without its enabler", "Iron + Ember Resin + Dragon Oil sword, alone: fire with no cold to meet", made(L.SWORD, L.IRON, L.EMBER_RESIN, L.DRAGON_OIL), L.GUARDIAN, "alone", "armoured", fires = setOf("catalyst_spreading_fire"), silent = setOf("catalyst_steam_lance")),
                Case("Without its enabler", "The first recipe alone: cold with no fire to meet, and nothing for the oil to spread", made(L.SWORD, L.BRONZE, L.FROST_BLOOM, L.DRAGON_OIL), L.GUARDIAN, "alone", "armoured", silent = setOf("catalyst_spreading_fire", "catalyst_steam_lance")),
            )),
        Path("burn_guard", "Burn into Guard", "After its wielder acts, Void Ink takes Burn off the enemy the wielder faces and gives it to your weakest ally as Guard.",
            "That Burn no longer deals damage. One Burn is one Guard, so it wants more fire than a single ember.",
            "Against a line that does not hit hard, the same fire with Dragon Oil burns every enemy and ends the fight sooner.", listOf(
                Case("First recipe", "Iron + Ember Resin + Void Ink sword, a Guardian, alone: its own ember becomes its Guard", made(L.SWORD, L.IRON, L.EMBER_RESIN, L.VOID_INK), L.GUARDIAN, "alone", "attrition", fires = setOf("catalyst_drinking", "catalyst_converted_guard")),
                Case("Second recipe", "Bronze + Verdant Sap + Void Ink staff, a Warden, behind a fire mage whose Burn it takes", made(L.STAFF, L.BRONZE, L.VERDANT_SAP, L.VOID_INK), L.WARDEN, "fire", "attrition", fires = setOf("catalyst_drinking", "catalyst_converted_guard")),
                Case("Without its enabler", "The same staff alone: nobody Burns anything", made(L.STAFF, L.BRONZE, L.VERDANT_SAP, L.VOID_INK), L.WARDEN, "alone", "attrition", silent = setOf("catalyst_drinking", "catalyst_converted_guard")),
            )),
        Path("guard_answer", "A broken Guard answers", "Guard that an enemy breaks can be the start of something. Bronze strikes the breaker for 5.",
            "It needs Guard on the wielder and an enemy strong enough to break it. Bronze is a weak metal otherwise.",
            "For a wielder who never has Guard, any other metal does more.", listOf(
                Case("First recipe", "Bronze + Ember Resin sword, a Guardian, alone", made(L.SWORD, L.BRONZE, L.EMBER_RESIN), L.GUARDIAN, "alone", "attrition", fires = setOf("core_bronze")),
                Case("Second recipe", "Bronze + Frost Bloom dagger that came out Reinforced, a Duelist, alone", made(L.DAGGER, L.BRONZE, L.FROST_BLOOM, buffs = listOf(L.REINFORCED)), L.DUELIST, "alone", "attrition", fires = setOf("affix_reinforced", "core_bronze")),
                Case("Without its enabler", "Bronze + Ember Resin sword, a Duelist, alone: no Guard to break", made(L.SWORD, L.BRONZE, L.EMBER_RESIN), L.DUELIST, "alone", "attrition", silent = setOf("core_bronze")),
            )),
        Path("salvage", "A cracked weapon pays", "A weapon that cracks leaves Scrap and wakes the rules that wait for a crack. Obsidian cracks by its nature, so the crack is a recipe and not luck.",
            "A cracked weapon needs a repair after the fight, and the crack itself does nothing unless something answers it.",
            "With no Salvage Bell and no Runestone in the party, obsidian's crack is only a repair bill.", listOf(
                Case("First recipe", "Obsidian + Ember Resin sword, a Guardian, alone, the Salvage Bell with the party", made(L.SWORD, L.OBSIDIAN, L.EMBER_RESIN), L.GUARDIAN, "alone", "attrition", fires = setOf("core_obsidian", "relic_salvage_bell"), relics = listOf(CombatContent.SALVAGE_BELL)),
                Case("Second recipe", "Iron + Ember Resin + Runestone Shard staff, a Battlemage, behind a Guardian whose obsidian sword cracks", made(L.STAFF, L.IRON, L.EMBER_RESIN, L.RUNESTONE_SHARD), L.BATTLEMAGE, "front", "attrition", fires = setOf("catalyst_runed")),
                Case("Without its enabler", "The Runestone staff alone: no weapon cracks", made(L.STAFF, L.IRON, L.EMBER_RESIN, L.RUNESTONE_SHARD), L.BATTLEMAGE, "alone", "attrition", silent = setOf("catalyst_runed")),
            )),
        Path("marks", "Marked for the kill", "The party aims at a marked enemy and the hit that spends the Mark lands 3 harder. Starsteel adds 3 more when the wielder's own action spends it.",
            "One Mark at a time, and whoever strikes next spends it. An ally, a riposte or a second arrow can take it first.",
            "With no Mark in the party, starsteel is only a costly metal. Moonsteel or obsidian does more for the gold.", listOf(
                Case("First recipe", "Starsteel + Sun Ash sword, a Battlemage, alone: the opening sigil Marks, the bolt spends it, and noon light Marks again", made(L.SWORD, L.STARSTEEL, L.SUN_ASH), L.BATTLEMAGE, "alone", "armoured", fires = setOf("element_sun", "core_starsteel")),
                Case("Second recipe", "Starsteel + Frost Bloom sword, a Duelist, with a spotter who Marks for it", made(L.SWORD, L.STARSTEEL, L.FROST_BLOOM), L.DUELIST, "spotter", "armoured", fires = setOf("core_starsteel")),
                Case("Without its enabler", "Starsteel + Frost Bloom sword, a Duelist, alone: nobody Marks anything", made(L.SWORD, L.STARSTEEL, L.FROST_BLOOM), L.DUELIST, "alone", "armoured", silent = setOf("core_starsteel")),
            )),
        Path("shatter", "Cold to break", "Chill on an enemy is something to use. Moonsteel hits a chilled enemy for 3 more. A heavy frost weapon shatters the Chill it has just laid for 6.",
            "The enemy's own strike spends Chill. Moonsteel needs it there before the hit, which takes a second cut or a second source.",
            "Shattering removes the Chill, so the enemy strikes at full strength. Against one hard hitter, leaving it on can be better.", listOf(
                Case("First recipe", "Moonsteel + Frost Bloom sword, a Duelist, alone: the second cut finds the first cut's Chill", made(L.SWORD, L.MOONSTEEL, L.FROST_BLOOM), L.DUELIST, "alone", "attrition", fires = setOf("element_frost", "core_moonsteel")),
                Case("Second recipe", "Bronze + Frost Bloom axe that came out Heavy, a Guardian, alone", made(L.AXE, L.BRONZE, L.FROST_BLOOM, flaws = listOf(L.HEAVY)), L.GUARDIAN, "alone", "attrition", fires = setOf("affix_heavy_shatter")),
                Case("Without its enabler", "Moonsteel + Frost Bloom sword, a Guardian, alone: one hit a round, and the enemy spends the Chill", made(L.SWORD, L.MOONSTEEL, L.FROST_BLOOM), L.GUARDIAN, "alone", "attrition", silent = setOf("core_moonsteel")),
            )),
    )

    fun run(c: Case): Bench.Report = Bench.run(c.weapon, false, c.wielder, c.companions, c.target, content, config, c.relics)

    /** Every rule ID that fired for the party in [report], weapon rules and relic rules alike. */
    fun fired(report: Bench.Report): Set<String> {
        val party = report.setup.party.map { it.key }.toSet()
        return report.result.events.filter { it.effectId != null && it.source in party }.mapNotNull { it.effectId }.toSet()
    }

    fun print(only: Set<String>, out: Appendable = System.out) {
        for (p in paths.filter { (only - "timeline").isEmpty() || it.id in only }) {
            out.appendLine("=".repeat(96))
            out.appendLine("${p.name}  [${p.id}]")
            out.appendLine("Relationship: ${p.relationship}")
            out.appendLine("Trade-off: ${p.tradeoff}")
            out.appendLine("Another approach: ${p.alternative}")
            for (c in p.cases) {
                val r = run(c)
                val foes = r.result.actors.filter { it.side == Side.ENEMY && !it.summoned }
                val party = r.result.actors.filter { it.side == Side.PARTY && !it.summoned }
                out.appendLine()
                out.appendLine("  ${c.label}: ${c.recipe}")
                out.appendLine("    Against ${Bench.targets.first { it.id == c.target }.name.lowercase()}. ${r.result.outcome} after ${r.result.rounds} rounds, enemy health left ${foes.sumOf { it.health }}/${foes.sumOf { it.maxHealth }}, party health left ${party.sumOf { it.health }}/${party.sumOf { it.maxHealth }}.")
                for (f in r.fired) out.appendLine("    FIRED ${f.rule.effect.name} x${f.times}: ${f.chain.joinToString(" > ")}")
                for (id in c.relics) fired(r).intersect(content.combat!!.relicEffects.getValue(id).map { it.id }.toSet()).forEach { out.appendLine("    FIRED (relic) $it") }
                for (s in r.silent) out.appendLine("    SILENT ${s.rule.effect.name}: ${s.why}")
                if ("timeline" in only) r.result.events.filter { it.text.isNotEmpty() }.forEach { out.appendLine("      ${it.id.toString().padStart(3)}${it.parentId?.let { p -> " <${p.toString().padStart(3)}" } ?: "     "}  ${it.text}") }
            }
            out.appendLine()
        }
    }
}
