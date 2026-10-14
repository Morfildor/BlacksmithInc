package com.tinyblacksmith.core

import com.tinyblacksmith.core.content.LaunchContent
import java.io.File
import java.net.URLClassLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Review 2026-10-14, decision 10: the content objects read each other while they initialise, and `Depth` touched first
 * once found `LaunchContent` half built (`Depth` -> `CombatContent` -> `LaunchContent` -> `Depth.relics`, still null).
 * Each entry point is touched first in a class loader of its own, so no earlier test has already initialised anything,
 * and the whole launch catalog it then builds must validate and match the one this JVM built in the usual order.
 */
class ContentInitOrderTest {
    private val pkg = "com.tinyblacksmith.core.content"

    /** Each content object (`Class.forName` runs its whole initialiser) and what it hands out. */
    private val entryPoints = listOf(
        "Depth" to listOf("getEncounters", "getRelics", "getSiegeTraits"),
        "CombatContent" to listOf("getRelicEffects", "getCatalog"),
        "GuildContent" to listOf("getEncounters", "getCatalog"),
        "LaunchContent" to listOf("getCatalog"),
        "SliceContent" to listOf("getCatalog"),
    )

    private fun isolated(): URLClassLoader {
        val urls = System.getProperty("java.class.path").split(File.pathSeparator).filter { it.isNotEmpty() }.map { File(it).toURI().toURL() }
        // The platform loader as parent: nothing of this project or of Kotlin is shared with the loader running this test.
        return URLClassLoader(urls.toTypedArray(), ClassLoader.getPlatformClassLoader())
    }

    @Test
    fun everyContentObjectCanBeTouchedFirst() {
        val expected = CatalogFingerprint.launch()
        assertTrue(expected.startsWith("valid\n"), expected.lineSequence().first())
        for ((name, getters) in entryPoints) isolated().use { loader ->
            val first = Class.forName("$pkg.$name", true, loader)
            assertTrue(first.classLoader === loader, "$name came from the isolated loader")
            val instance = first.getField("INSTANCE").get(null)
            for (getter in getters) {
                val value = runCatching { first.getMethod(getter).invoke(instance) }.getOrElse { throw AssertionError("$name.$getter touched first", it) }
                assertTrue(value != null, "$name.$getter is null when $name is touched first")
            }
            val got = Class.forName(CatalogFingerprint::class.java.name, true, loader).getMethod("launch").invoke(null) as String
            assertEquals(expected, got, "the launch catalog built after touching $name first")
        }
    }
}

/** The launch catalog as text: whether it validates, then every list in order. Lambda identities are left out. */
object CatalogFingerprint {
    @JvmStatic
    fun launch(): String {
        val catalog = LaunchContent.catalog
        val problems = catalog.validate()
        val head = if (problems.isEmpty()) "valid" else "invalid: $problems"
        return head + "\n" + catalog.toString().replace(Regex("[$][$]Lambda[^@]*@[0-9a-f]+"), "Lambda").replace(Regex("@[0-9a-f]{4,}"), "@")
    }
}
