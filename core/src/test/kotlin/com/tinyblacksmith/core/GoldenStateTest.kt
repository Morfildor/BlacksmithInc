package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.CommandId
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.WeaponLocation
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Gameplay for fixed seeds and a fixed command script, as an explicit projection: adding a field to the save does not
 * change it, changing an outcome or moving an RNG stream does. A task that claims to alter no outcome leaves it equal.
 */
class GoldenStateTest {
    private val engine = TestSupport.engine
    private val resource = "golden/state_rules${GameEngine.RULES_VERSION}.txt"

    /** Records that describe a day without being gameplay. A task that adds such a type names it here on purpose. */
    private val recordOnly = setOf("MATERIAL_BOUGHT")          // T1.6 adds "MATERIAL_BOUGHT"; T2.1 adds "SHOP_DAY"

    private fun sha(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun location(l: WeaponLocation): String = when (l) {
        WeaponLocation.Storage -> "storage"
        is WeaponLocation.Shelf -> "shelf:${l.price}"
        is WeaponLocation.Owned -> "owned:${l.heroId.value}:${l.equipped}"
        is WeaponLocation.Lost -> "lost:${l.day}:${l.reason}"
        is WeaponLocation.Destroyed -> "destroyed:${l.day}"
    }

    private fun streams(s: GameState): String = s.rng.streams.toSortedMap().entries.joinToString(",") { "${it.key}:${it.value}" }

    /** Adding a line here is a deliberate edit; nothing enters the projection because a model class grew a field. */
    private fun project(s: GameState): String = buildString {
        appendLine("day=${s.day} phase=${s.phase} gold=${s.gold} energy=${s.energy} overwork=${s.overworkToday} reputation=${s.reputation}")
        appendLine("materials=" + s.materials.entries.sortedBy { it.key.value }.joinToString(",") { "${it.key.value}:${it.value}" })
        appendLine("supplier=" + s.supplierStock.entries.sortedBy { it.key.value }.joinToString(",") { "${it.key.value}:${it.value}" })
        appendLine("tools=${s.tools.toSortedMap()} flags=${s.worldFlags.toSortedMap()} milestones=${s.milestones.sorted()}")
        appendLine("town=${s.town.integrity}/${s.town.militia}/${s.town.armory}/${s.town.nextSiegeDay}/${s.town.siegesSurvived}/${s.town.siegesLost}" +
            " champions=${s.town.championIds.map { it.value }} guilds=${s.town.guilds.size}")
        appendLine("factions=" + s.factions.values.sortedBy { it.id.value }.joinToString(",") { "${it.id.value}:${it.pressure}" })
        appendLine("blessings=" + s.blessings.joinToString(",") { "${it.id.value}:${it.expiresDay}" } + " offer=${s.pendingBlessingOffer.map { it.value }}")
        appendLine("commissions=" + s.commissions.values.sortedBy { it.id.value }.joinToString(",") { "${it.id.value}:${it.status}:${it.deliveredWeaponId?.value}" })
        for (h in s.heroes.values.sortedBy { it.id.value })
            appendLine("hero ${h.id.value} ${h.classId.value} level=${h.level} xp=${h.xp} gold=${h.gold} health=${h.health} ${h.fate}" +
                " loyalty=${h.loyalty} fame=${h.fame} kills=${h.kills} victories=${h.victories} guild=${h.guildId} ambitionDone=${h.ambitionDone}")
        for (w in s.weapons.values.sortedBy { it.id.value })
            appendLine("weapon ${w.id.value} ${location(w.location)} quality=${w.quality} power=${w.power} condition=${w.condition}" +
                " kills=${w.kills} fame=${w.fame} title=${w.title}")
        appendLine("journal=" + s.legacy.journal.interactions.toSortedMap())
        for (e in s.events) if (e.type.name !in recordOnly) appendLine("event ${e.day} ${e.type} ${e.subjectIds}")
    }

    private fun GameState.tryRun(command: Command): GameState = (engine.handle(this, command) as? CommandOutcome.Accepted)?.state ?: this

    /** Each morning: restock iron and ember resin when out, forge up to three quick swords, list every stored blade, end the day. */
    private fun play(seed: Long, days: Int): List<String> {
        var s = engine.newRun(LegacyProfile(), seed)
        val out = mutableListOf<String>()
        for (n in 1..days) {
            if (s.isEnded) { out += "$seed:$n:ended:ended"; continue }
            repeat(3) {
                if ((s.materials[LaunchContent.IRON] ?: 0) == 0) s = s.tryRun(Command.BuyMaterial(LaunchContent.IRON))
                if ((s.materials[LaunchContent.EMBER_RESIN] ?: 0) == 0) s = s.tryRun(Command.BuyMaterial(LaunchContent.EMBER_RESIN))
                s = s.tryRun(quickSword())
            }
            for (w in s.storedWeapons()) s = s.tryRun(Command.ToggleShelf(w.id, listed = true))
            s = (engine.handle(s, Command.EndDay(CommandId("${s.runId.value}:day${s.day}"))) as CommandOutcome.Accepted).state
            out += "$seed:$n:${sha(streams(s))}:${sha(project(s))}"
        }
        return out
    }

    @Test
    fun rngStreamsAndGameplayMatchTheRecordedProjection() {
        val actual = (1L..20L).flatMap { play(it, 15) }
        if (System.getProperty("golden.record") == "true") {
            File("src/test/resources/$resource").apply { parentFile.mkdirs() }.writeText(actual.joinToString("\n"))
            return   // the classpath copy is stale in the recording run; the next run compares
        }
        val expected = javaClass.classLoader.getResource(resource)?.readText()?.lines()?.filter { it.isNotBlank() }
            ?: error("No golden file $resource: run with -Dgolden.record=true once and commit it")
        fun rngOnly(lines: List<String>) = lines.map { it.split(":").take(3).joinToString(":") }
        assertEquals(rngOnly(expected), rngOnly(actual), "an RNG stream moved: this change draws, or stops drawing, gameplay randomness")
        assertEquals(expected, actual, "RNG streams are equal but an outcome changed")
    }
}
