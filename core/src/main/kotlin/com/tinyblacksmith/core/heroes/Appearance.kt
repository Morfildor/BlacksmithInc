package com.tinyblacksmith.core.heroes

import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.model.Hero
import com.tinyblacksmith.core.model.HeroClassId
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.LineageAnchor

/**
 * The face a hero wears (plan 6.6). A key is the asset ID of a portrait ("portrait_hero_07"), listed per class in
 * `HeroClassDef.appearances` and stored on the hero when they are created; it is never an index, so adding or
 * reordering faces cannot change a saved hero. Nothing here draws from an RNG stream.
 */
object Appearance {
    /**
     * FROZEN. Before faces were stored, a hero's face was slot `floorMod(id.hashCode(), 5)` of their class, and the app
     * drew these twenty portraits for the five slots. Never edit a row: it is how a hero saved before the field existed,
     * and a key a past shop day recorded ("portrait_ranger_4"), keep the face the player knows. New faces go in the content.
     */
    private val legacyFaces: Map<String, List<String>> = mapOf(
        "guardian" to listOf("01", "06", "09", "11", "16"),
        "ranger" to listOf("02", "07", "12", "17", "02"),
        "duelist" to listOf("03", "10", "13", "18", "03"),
        "battlemage" to listOf("04", "08", "14", "19", "04"),
        "warden" to listOf("05", "15", "20", "05", "15"),
    ).mapValues { (_, faces) -> faces.map { "portrait_hero_$it" } }

    const val LEGACY_SLOTS = 5

    /** The slot 0..4 a hero ID had before faces were stored. */
    fun legacySlot(heroId: String): Int = Math.floorMod(heroId.hashCode(), LEGACY_SLOTS)

    /** The face [slot] of [classId] showed before faces were stored; null for a class or slot that had none. */
    fun legacyFace(classId: HeroClassId, slot: Int): String? = legacyFaces[classId.value]?.getOrNull(slot)

    /** The key of a hero saved without one: the face they have always shown (for a class outside the table, the old slot key). */
    fun legacyKey(classId: HeroClassId, heroId: String): String =
        legacySlot(heroId).let { legacyFace(classId, it) ?: "portrait_${classId.value}_$it" }

    fun legacyKey(hero: Hero): String = legacyKey(hero.classId, hero.id.value)

    fun keyOf(hero: Hero): String = hero.appearance ?: legacyKey(hero)

    /**
     * The face for a new hero: the key of [classKeys] that the fewest living heroes of the class wear, so no two share
     * one while faces remain. [wornByLiving] holds one RESOLVED key ([keyOf]) per living hero of the class, so a hero
     * saved before the field existed still counts for the face the player sees on them. Ties go by a hash of the run
     * seed, the hero ID and the key, which spreads a class evenly over its faces without touching gameplay RNG.
     */
    fun assign(runSeed: Long, heroId: HeroId, classKeys: List<String>, wornByLiving: Collection<String>): String {
        val worn = wornByLiving.groupingBy { it }.eachCount()
        return classKeys.minWith(compareBy<String> { worn[it] ?: 0 }.thenBy { mix(runSeed, heroId.value, it) }.thenBy { it })
    }

    /** [hero] as just generated, with a face. A descendant takes the ancestor's when the lineage stores one and nobody living wears it. */
    fun stamp(ctx: ResolutionContext, hero: Hero, ancestor: LineageAnchor? = null): Hero {
        val keys = ctx.content.heroClass(hero.classId).appearances
        val worn = ctx.heroes.values.filter { it.isAlive && it.classId == hero.classId && it.id != hero.id }.map { keyOf(it) }
        val inherited = ancestor?.appearance?.takeIf { it in keys && it !in worn }
        return hero.copy(appearance = inherited ?: assign(ctx.base.seed, hero.id, keys, worn))
    }

    /** SplitMix64's finalizer over the three inputs: a fixed arithmetic hash, not a stream. */
    private fun mix(seed: Long, heroId: String, key: String): Long {
        var z = seed xor (heroId.hashCode().toLong() shl 32) xor (key.hashCode().toLong() and 0xFFFFFFFFL)
        z += -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }
}
