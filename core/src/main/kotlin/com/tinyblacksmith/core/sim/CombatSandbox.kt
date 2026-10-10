package com.tinyblacksmith.core.sim

import com.tinyblacksmith.core.combat.Combatant
import com.tinyblacksmith.core.combat.EffectDef
import com.tinyblacksmith.core.combat.Fight
import com.tinyblacksmith.core.combat.FightResult
import com.tinyblacksmith.core.combat.FightSetup
import com.tinyblacksmith.core.combat.Objective
import com.tinyblacksmith.core.combat.Posture
import com.tinyblacksmith.core.combat.Side
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.CombatContent
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.crafting.Forge
import com.tinyblacksmith.core.guild.Loadout
import com.tinyblacksmith.core.model.*

/**
 * Fixture fights for looking at the interaction engine by itself (plan A07): the same setups the tests assert on,
 * printed as highlights and a timeline. `./gradlew :core:combat` prints all of them, `--args="stormwell"` one.
 * Nothing here touches a run or a save.
 */
object CombatSandbox {
    val content: ContentCatalog = LaunchContent.catalog
    val config: BalanceConfig = BalanceConfig.DEFAULT
    private val combat = content.combat!!

    data class Fixture(val id: String, val title: String, val about: String, val setup: FightSetup)

    fun hero(id: String, name: String, classId: HeroClassId, level: Int = 1, health: Int = 100): Hero =
        Hero(HeroId(id), name, "of Emberfall", classId, level, 0, 0, health, emptyList(), null)

    /** A blade as the forge would have made it from these parts at [quality], with exactly these affixes and flaws. */
    fun blade(
        id: String, family: WeaponFamilyId, core: MaterialId, augment: MaterialId, catalyst: MaterialId? = null, quality: Int = 40,
        affixes: List<AffixId> = emptyList(), flaws: List<AffixId> = emptyList(), signatureId: String? = null, name: String? = null,
    ): Weapon = Weapon(
        id = WeaponId(id), name = name ?: Forge.weaponName(content, family, core, affixes, signatureId), familyId = family, coreId = core, augmentId = augment, catalystId = catalyst,
        mode = if (catalyst != null) ForgeMode.ADVANCED else ForgeMode.QUICK, risk = Risk.BALANCED, quality = quality, rarity = Forge.rarityFor(quality, config),
        power = Forge.powerOf(content, config, family, core, quality, affixes + flaws), element = content.material(augment).element, affixes = affixes, flaws = flaws,
        location = WeaponLocation.Storage, forgedEra = 1, forgedDay = 1, signatureId = signatureId,
    )

    fun fighter(hero: Hero, weapon: Weapon?, field: Loadout.Field = Loadout.Field.ROAD): Combatant = Loadout.combatant(hero, weapon, content, config, field)

    fun enemies(vararg units: String, percent: Int = 100): List<Combatant> = units.mapIndexed { i, id -> Loadout.enemy(combat.unit(id), "e${i + 1}", percent) }

    /** Enemy strikes are pinned to the middle of their range, so a fixture reads the same whatever its seed. */
    fun steady(list: List<Combatant>): List<Combatant> = list.map { val mid = (it.strikeMin + it.strikeMax) / 2; it.copy(strikeMin = mid, strikeMax = mid) }

    fun relic(id: String): List<EffectDef> = combat.relicEffects.getValue(id)

    private fun setup(title: String, party: List<Combatant>, foes: List<Combatant>, relics: List<EffectDef> = emptyList(), posture: Posture = Posture.BALANCED, objective: Objective = Objective(), seed: Long = 7) =
        FightSetup(title, party, foes, relics, objective, posture, config.guild.fight, seed, combat.unitById)

    // The people of the fixtures.
    val brann = hero("h1", "Brann", LaunchContent.GUARDIAN)
    val mira = hero("h2", "Mira", LaunchContent.WARDEN)
    val nessa = hero("h3", "Nessa", LaunchContent.BATTLEMAGE)
    val oda = hero("h4", "Oda", LaunchContent.RANGER)
    val perrin = hero("h5", "Perrin", LaunchContent.DUELIST)

    // The blades of the fixtures. A capacitor is any blade forged with Binding Salt; the stormglass one is the one the spec names.
    val capacitor = blade("w1", LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.STORMGLASS, LaunchContent.BINDING_SALT, name = "Stormglass Capacitor")
    val plainSword = blade("w2", LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.STORMGLASS, name = "Stormglass Iron Sword")
    val healingStaff = blade("w3", LaunchContent.STAFF, LaunchContent.IRON, LaunchContent.VERDANT_SAP, name = "Sapwood Staff")
    val sigilStaff = blade("w4", LaunchContent.STAFF, LaunchContent.IRON, LaunchContent.EMBER_RESIN, name = "Ember Staff")
    val bellblade = blade("w5", LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.EMBER_RESIN, flaws = listOf(LaunchContent.BRITTLE), quality = 30, name = "Brittle Bellblade")
    val soundSword = blade("w6", LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.EMBER_RESIN, quality = 30, name = "Ember Iron Sword")
    val scrapTuned = blade("w7", LaunchContent.STAFF, LaunchContent.IRON, LaunchContent.EMBER_RESIN, LaunchContent.RUNESTONE_SHARD, name = "Scrap-Tuned Staff")
    val frostBow = blade("w8", LaunchContent.BOW, LaunchContent.BRONZE, LaunchContent.FROST_BLOOM, quality = 55, affixes = listOf(LaunchContent.FROSTBOUND), name = "Frostbound Bow")
    val heavyAxe = blade("w9", LaunchContent.AXE, LaunchContent.BRONZE, LaunchContent.EMBER_RESIN, flaws = listOf(LaunchContent.HEAVY), name = "Heavy Bronze Axe")
    val bloodDagger = blade("w10", LaunchContent.DAGGER, LaunchContent.BRONZE, LaunchContent.GRAVE_DUST, flaws = listOf(LaunchContent.BLOODBOUND), name = "Bloodbound Dagger")

    private val raiders get() = steady(enemies("ashclaw_warchief", "ashclaw_brute", "ashclaw_scout", percent = 150))
    private val brutes get() = steady(enemies("ashclaw_brute", "ashclaw_brute", "ashclaw_thief", percent = 150))

    val fixtures: List<Fixture> by lazy {
        listOf(
            Fixture("stormwell", "Stormwell", "Healing fills the capacitor; three Charge become a burst on the marked warchief.",
                setup("Stormwell against an Ashclaw warband", listOf(fighter(brann, capacitor), fighter(mira, healingStaff), fighter(nessa, sigilStaff)), raiders, relic(CombatContent.OVERFLOW_BASIN))),
            Fixture("stormwell-no-capacitor", "Stormwell without the capacitor", "The same party and foes; Brann carries the same sword forged without Binding Salt. No Charge, no burst.",
                setup("The same fight with a plain sword", listOf(fighter(brann, plainSword), fighter(mira, healingStaff), fighter(nessa, sigilStaff)), raiders, relic(CombatContent.OVERFLOW_BASIN))),
            Fixture("stormwell-no-healer", "Stormwell without the Warden", "The capacitor has only the Battlemage's opening Charge; nobody heals, so it never fills.",
                setup("Stormwell with a Ranger for the Warden", listOf(fighter(brann, capacitor), fighter(oda, null), fighter(nessa, sigilStaff)), raiders, relic(CombatContent.OVERFLOW_BASIN))),
            Fixture("overheal", "Overheal without Charge", "Nobody is hurt in the first round: the Warden's heal is all overheal. The Basin turns it into Guard; the capacitor stores nothing from it.",
                setup("A full-health party against one brute", listOf(fighter(brann, capacitor), fighter(mira, healingStaff)), steady(enemies("ashclaw_brute", percent = 150)), relic(CombatContent.OVERFLOW_BASIN))),
            Fixture("scrap-choir", "Scrap Choir", "The Bellblade cracks when Brann's Guard breaks; the Salvage Bell turns its Scrap into Guard for everyone; the rune answers when that Guard breaks.",
                setup("Scrap Choir against Ashclaw brutes", listOf(fighter(brann, bellblade), fighter(mira, healingStaff), fighter(nessa, scrapTuned)), brutes, relic(CombatContent.SALVAGE_BELL))),
            Fixture("scrap-choir-sound-blade", "Scrap Choir with a sound blade", "The same party; Brann's sword is not brittle. Nothing cracks, the Bell stays silent, the rune never answers.",
                setup("The same fight with a sound sword", listOf(fighter(brann, soundSword), fighter(mira, healingStaff), fighter(nessa, scrapTuned)), brutes, relic(CombatContent.SALVAGE_BELL))),
            Fixture("scrap-choir-no-bell", "Scrap Choir without the Bell", "The Bellblade cracks and its Scrap lies there: no relic to spend it. The flaw is only a repair bill.",
                setup("The same fight without the Salvage Bell", listOf(fighter(brann, bellblade), fighter(mira, healingStaff), fighter(nessa, scrapTuned)), brutes)),
            Fixture("icebreaker", "Icebreaker", "The Ranger's frost bow chills the drake; the heavy axe shatters the Chill. A chilled drake also breathes weaker.",
                setup("Icebreaker against an Embermaw drake", listOf(fighter(oda, frostBow), fighter(brann, heavyAxe), fighter(mira, healingStaff)), steady(enemies("ember_drake", "ember_whelp", percent = 220)))),
            Fixture("blood-bank", "Blood Bank", "The Duelist pays health to the dagger for a harder cut; the Warden heals what was paid. Paying is not damage.",
                setup("Blood Bank against the dead", listOf(fighter(perrin, bloodDagger), fighter(mira, healingStaff), fighter(brann, soundSword)), steady(enemies("bone_warden", "hollow_shambler", "gravecaller", percent = 170)), relic(CombatContent.BLOOD_RECEIPT))),
            Fixture("retreat", "A cautious retreat", "Two unarmed novices meet a warchief under a cautious posture and leave when it turns.",
                setup("Out of their depth", listOf(fighter(oda, null), fighter(perrin, null)), steady(enemies("ashclaw_warchief", "ashclaw_brute", percent = 250)), posture = Posture.CAUTIOUS)),
        )
    }

    fun fixture(id: String): Fixture = fixtures.first { it.id == id }

    fun run(id: String): FightResult = Fight.resolve(fixture(id).setup)

    fun print(f: Fixture, out: Appendable = System.out) {
        val r = Fight.resolve(f.setup)
        out.appendLine("=".repeat(96))
        out.appendLine("${f.title}  [${f.id}]")
        out.appendLine(f.about)
        out.appendLine()
        for (c in f.setup.party + f.setup.enemies) {
            out.appendLine("  ${if (c.side == Side.PARTY) "+" else "-"} ${c.name}${c.weaponName?.let { " with $it" } ?: ""}: health ${c.health}/${c.maxHealth}, strike ${c.strikeMin}${if (c.strikeMax > c.strikeMin) "-${c.strikeMax}" else ""}${if (c.support > 0) ", support ${c.support}" else ""}")
            for (e in c.kit.passives + c.effects) out.appendLine("      ${e.name}: ${e.description}")
            c.kit.moves.mapNotNull { it.telegraph }.forEach { out.appendLine("      Known: $it") }
        }
        for (e in f.setup.partyEffects) out.appendLine("  * ${e.name}: ${e.description}")
        out.appendLine()
        out.appendLine("RESULT: ${r.outcome} after ${r.rounds} rounds. " + r.actors.filter { it.side == Side.PARTY }.joinToString(", ") { "${it.name} ${if (it.downed) "down" else "${it.health}/${it.maxHealth}"}${if (it.fractured) " (blade cracked)" else ""}" })
        out.appendLine()
        out.appendLine("HIGHLIGHTS")
        r.highlights.forEach { out.appendLine("  > ${it.text}") }
        out.appendLine()
        out.appendLine("TIMELINE")
        r.events.filter { it.text.isNotEmpty() }.forEach { out.appendLine("  ${it.id.toString().padStart(3)}${it.parentId?.let { p -> " <${p.toString().padStart(3)}" } ?: "     "}  ${it.text}") }
        out.appendLine()
    }
}

fun main(args: Array<String>) {
    val wanted = args.toSet()
    CombatSandbox.fixtures.filter { wanted.isEmpty() || it.id in wanted }.forEach { CombatSandbox.print(it) }
}
