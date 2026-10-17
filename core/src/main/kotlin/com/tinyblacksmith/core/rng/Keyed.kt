package com.tinyblacksmith.core.rng

/**
 * Event-keyed draws (living heroes, final design 16): a fresh [Rng] seeded from the run seed and a stable text key such as
 * "identity|h17" or "lean|h3|7". Nothing is stored and no gameplay stream moves, so a new name pool, opportunity or army
 * table rerolls nothing else, and a paired run that removes one of the smith's actions changes only what that action touches.
 * The key hash is FNV-1a over UTF-16 code units: fixed arithmetic, never a platform hash.
 */
object Keyed {
    fun rng(runSeed: Long, key: String): Rng = Rng(SplitMix64.mix(runSeed xor fnv(key) xor SplitMix64.GOLDEN))

    fun fnv(key: String): Long {
        var h = -0x340d631b7bdddcdbL   // 0xcbf29ce484222325
        for (c in key) { h = h xor c.code.toLong(); h *= 0x100000001b3L }
        return h
    }
}
