package com.example.blacksmithproject

import com.example.blacksmithproject.ui.fightSide
import com.example.blacksmithproject.ui.fightTimeline
import com.example.blacksmithproject.ui.sandboxFighter
import com.tinyblacksmith.core.combat.Fight
import com.tinyblacksmith.core.combat.Side
import com.tinyblacksmith.core.sim.CombatSandbox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The debug build's combat sandbox: every fixture has a setup to show and a fight to report, and no other build knows of it. */
class CombatSandboxTest {
    @Test
    fun everyFixtureShowsItsFightersWithTheirRulesAndAFight() {
        assertTrue(CombatSandbox.fixtures.size >= 10)
        assertEquals("IDs are test tags", CombatSandbox.fixtures.size, CombatSandbox.fixtures.map { it.id }.toSet().size)
        for (f in CombatSandbox.fixtures) {
            for (c in f.setup.party + f.setup.enemies) {
                val (head, rules) = sandboxFighter(c)
                assertTrue(head, head.startsWith(c.name) && "health ${c.health}/${c.maxHealth}" in head && "strike ${c.strikeMin}" in head)
                assertEquals(head, c.weaponName != null, " with " in head)
                assertEquals(head, c.support > 0, "support ${c.support}" in head)
                // Each rule is the definition's own name and description, never retyped.
                for (e in c.kit.passives + c.effects) assertTrue(e.id, "${e.name}: ${e.description}" in rules)
            }
            val result = Fight.resolve(f.setup)
            assertEquals(f.id, result, Fight.resolve(f.setup))
            assertTrue(f.id, fightTimeline(result).first().header)
            assertEquals(f.id, f.setup.party.size, fightSide(result, Side.PARTY).size)
        }
    }

    @Test
    fun onlyTheDebugSourceSetNamesTheSandbox() {
        val elsewhere = listOf("src/main", "src/release").flatMap { File(it).walkTopDown().filter { f -> f.extension == "kt" }.toList() }
        assertTrue(elsewhere.size > 20)
        assertEquals(emptyList<File>(), elsewhere.filter { f -> f.readText().let { "CombatSandbox" in it || "sandbox_open" in it } })
        assertTrue("CombatSandbox" in File("src/debug/java/com/example/blacksmithproject/ui/CombatSandboxScreen.kt").readText())
        assertTrue("sandbox_open" in File("src/debug/java/com/example/blacksmithproject/ui/ScenarioMenu.kt").readText())
    }
}
