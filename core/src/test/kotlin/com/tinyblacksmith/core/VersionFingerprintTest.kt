package com.tinyblacksmith.core

import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.LaunchContent
import java.lang.reflect.Modifier
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Two guards so a version number cannot be forgotten: the launch catalog and the default balance are hashed and the
 * hash is pinned per version. Changing either without raising its version fails here.
 *
 * After an intended change: raise `LaunchContent.catalog.version` or `BalanceConfig.version`, run the test, and add
 * the hash it prints as a NEW row for the new version (older rows stay as the record). Editing the hash of an
 * existing row is right only when the fingerprint itself changed (this file), never for a changed number.
 *
 * What is hashed: every field of every definition, by field name in sorted order, doubles by their bits, maps by
 * sorted key, lists in their order (the engine picks from them by index). Display text is not content: of the
 * fields in [prose] only presence or count is hashed, so a reworded description needs no version. Name pools are
 * hashed in full, because the names a run generates come from them.
 */
class VersionFingerprintTest {
    private val catalogPins = mapOf(
        2 to "e5e5b96b392777abd63e6f7237fd96f5eee92411942f172035ba0c5c4adb6f9b",
    )
    private val balancePins = mapOf(
        5 to "a9d576db13b2983ebb4e45ed97ff6ddc9ece108aebb4738a14bfd90dc9ae37c1",
        6 to "adb451f9d456482d788d612daefebdbda892de44215fe4a3877152490d6c8e60",
        7 to "66214786ec96489c50453f2dc76ade2bb0d749d18275b30fa14586bc8370129f",
    )

    private val prose = setOf("name", "description", "flavor", "siegeName", "warlordName", "encounterNames", "eliteNames")

    private fun canonical(value: Any?, field: String? = null): String = when (value) {
        null -> "null"
        is String -> if (field in prose) "text" else "\"$value\""
        is Double -> "d${value.toBits()}"
        is Int, is Long, is Boolean -> value.toString()
        is Enum<*> -> value.name
        is Pair<*, *> -> "(${canonical(value.first)},${canonical(value.second)})"
        is Map<*, *> -> value.entries.map { "${canonical(it.key)}=${canonical(it.value)}" }.sorted().joinToString(",", "{", "}")
        is List<*> -> if (field in prose) "texts${value.size}" else value.joinToString(",", "[", "]") { canonical(it) }
        else -> {
            check(value.javaClass.name.startsWith("com.tinyblacksmith.core.")) { "No canonical form for ${value.javaClass.name}" }
            value.javaClass.declaredFields
                .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic && !it.name.endsWith("ById") }  // the catalog's lookup maps repeat its lists
                .sortedBy { it.name }
                .joinToString(",", "${value.javaClass.simpleName}(", ")") { f -> f.isAccessible = true; "${f.name}=${canonical(f.get(value), f.name)}" }
        }
    }

    private fun sha(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    @Test
    fun catalogFingerprintMatchesItsVersion() {
        val catalog = LaunchContent.catalog
        val first = catalog.materials.first()
        assertNotEquals(canonical(catalog), canonical(catalog.copy(materials = listOf(first.copy(price = first.price + 1)) + catalog.materials.drop(1))), "a changed number is seen")
        assertEquals(canonical(catalog), canonical(catalog.copy(materials = listOf(first.copy(flavor = "reworded")) + catalog.materials.drop(1))), "reworded text is not")
        assertEquals(catalogPins[catalog.version], sha(canonical(catalog)),
            "the launch catalog changed: raise its version and pin the new hash as a new row (content version ${catalog.version})")
    }

    @Test
    fun balanceFingerprintMatchesItsVersion() {
        val config = BalanceConfig.DEFAULT
        assertNotEquals(canonical(config), canonical(config.copy(heroLife = config.heroLife.copy(guildXp = config.heroLife.guildXp + 1))), "a number in a nested group is seen")
        assertEquals(balancePins[config.version], sha(canonical(config)),
            "BalanceConfig changed: raise BalanceConfig.version and pin the new hash as a new row (balance version ${config.version})")
    }
}
