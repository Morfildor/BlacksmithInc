package com.tinyblacksmith.core.rng

import kotlinx.serialization.Serializable

/**
 * Game-owned SplitMix64 generator (GDD 4.4). Never depends on platform Random.
 * Each gameplay subsystem owns an independent stream so that UI/animation code
 * can never perturb gameplay outcomes.
 */
object SplitMix64 {
    const val GOLDEN: Long = -7046029254386353131L // 0x9E3779B97F4A7C15

    fun mix(z0: Long): Long {
        var z = z0
        z = (z xor (z ushr 30)) * -4658895280553007687L // 0xBF58476D1CE4E5B9
        z = (z xor (z ushr 27)) * -7723592293110705685L // 0x94D049BB133111EB
        return z xor (z ushr 31)
    }

    /** Returns (nextState, output). */
    fun step(state: Long): Pair<Long, Long> {
        val next = state + GOLDEN
        return next to mix(next)
    }
}

/** ENCOUNTERS (schema 5) and GUILD (schema 6) are appended: a stream is seeded by its ordinal, so the older ones keep the seeds they always had. */
enum class RngStream { CRAFTING, HEROES, PURCHASES, COMBAT, FACTIONS, EVENTS, LEGACY, WORLD, ENCOUNTERS, GUILD }

/** Serializable snapshot of every gameplay stream. Saved with the run. */
@Serializable
data class RngState(val version: Int, val streams: Map<RngStream, Long>) {
    fun with(stream: RngStream, newState: Long): RngState = copy(streams = streams + (stream to newState))

    fun stateOf(stream: RngStream): Long = streams[stream] ?: error("Missing RNG stream $stream")

    companion object {
        const val CURRENT_VERSION = 1

        fun seeded(seed: Long, rulesVersion: Int): RngState = RngState(
            version = CURRENT_VERSION,
            streams = RngStream.entries.associateWith { s ->
                SplitMix64.mix(seed + (s.ordinal + 1) * SplitMix64.GOLDEN + rulesVersion.toLong() * 7919L)
            },
        )
    }
}

/** Mutable cursor over one stream, used inside a single resolver step and then written back. */
class Rng(initialState: Long) {
    var state: Long = initialState
        private set

    fun nextLong(): Long {
        val (s, out) = SplitMix64.step(state)
        state = s
        return out
    }

    /** Uniform in [0, bound). */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive" }
        return ((nextLong() ushr 1) % bound).toInt()
    }

    /** Uniform in [from, to] inclusive. */
    fun nextInt(from: Int, to: Int): Int {
        require(to >= from)
        return from + nextInt(to - from + 1)
    }

    /** Uniform in [0.0, 1.0). */
    fun nextDouble(): Double = (nextLong() ushr 11) * (1.0 / (1L shl 53))

    fun chance(probability: Double): Boolean = nextDouble() < probability

    fun <T> pick(items: List<T>): T {
        require(items.isNotEmpty())
        return items[nextInt(items.size)]
    }

    fun <T> pickWeighted(items: List<Pair<T, Double>>): T {
        require(items.isNotEmpty())
        val total = items.sumOf { maxOf(0.0, it.second) }
        if (total <= 0.0) return items.first().first
        var r = nextDouble() * total
        for ((item, w) in items) {
            r -= maxOf(0.0, w)
            if (r < 0) return item
        }
        return items.last().first
    }
}
