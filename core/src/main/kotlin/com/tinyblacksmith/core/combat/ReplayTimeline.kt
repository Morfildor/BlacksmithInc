package com.tinyblacksmith.core.combat

/**
 * The order a [Replay] is played in: a round's banner, then for each step the actor stepping up (who moves and what
 * fires), then its pops one at a time in the order recorded, then the end. A frame says how many of its step's pops
 * have landed, so the bars, the states and the labels on screen are always those of the same moment; nothing is ever
 * shown ahead of the pop that causes it.
 *
 * The short version plays only the steps of [Replay.highlights]; each starts from its own [Step.before], which is what
 * the full fight had at that point, so the two never disagree.
 */
enum class FrameKind { ROUND, INTRO, POP, END }

data class Frame(val step: Int, val kind: FrameKind, val applied: Int, val pop: Pop?)

object ReplayTimeline {
    /**
     * How long a frame stays at 1x and at 2x, in milliseconds (PROPOSED). 2x is not half: a pop keeps a floor long
     * enough to read, and a label stays on its portrait until the step ends, so it is on screen longer than its frame.
     */
    fun millis(kind: FrameKind, fast: Boolean): Int = when (kind) {
        FrameKind.ROUND -> if (fast) 400 else 700
        FrameKind.INTRO -> if (fast) 320 else 550
        FrameKind.POP -> if (fast) 520 else 900
        FrameKind.END -> if (fast) 900 else 1400
    }

    fun frames(replay: Replay, short: Boolean): List<Frame> {
        val wanted = if (short) replay.highlights.toSet() else null
        val out = ArrayList<Frame>()
        for (s in replay.steps) {
            if (wanted != null && s.index !in wanted) continue
            when (s.kind) {
                StepKind.END -> { out += Frame(s.index, FrameKind.END, s.pops.size, null); continue }
                StepKind.ROUND -> if (wanted == null) out += Frame(s.index, FrameKind.ROUND, 0, null)
                else -> out += Frame(s.index, FrameKind.INTRO, 0, null)
            }
            s.pops.forEachIndexed { i, p -> if (p.shown) out += Frame(s.index, FrameKind.POP, i + 1, p) }
        }
        return out
    }

    /** Everybody as frame [at] shows them. */
    fun state(replay: Replay, frame: Frame): Map<String, ActorState> = replay.steps[frame.step].stateAfter(frame.applied)

    /** The label each portrait carries at [frame]: the latest pop of this step on it, up to the one landing now. One per portrait, never two. */
    fun labels(replay: Replay, frame: Frame): Map<String, Pop> {
        val s = replay.steps[frame.step]
        return s.pops.take(frame.applied).filter { it.shown }.associateBy { it.target }
    }

    /** A step in one line for the recent-action strip: who did what, and what it did to whom ("Brann · Cleave: scout −9, Brann Guard +4"). */
    fun summary(replay: Replay, step: Step, max: Int = 3): String {
        val shown = step.shownPops
        val parts = shown.take(max).map { p -> "${replay.actor(p.target)?.name ?: "?"} ${p.text}${p.note?.let { n -> " ($n)" }.orEmpty()}" }
        val more = if (shown.size > max) ", ${shown.size - max} more" else ""
        return if (parts.isEmpty()) step.title else "${step.title}: ${parts.joinToString(", ")}$more"
    }

    /** The frame where [step] starts, or the first frame after it in the short version. */
    fun frameOf(frames: List<Frame>, step: Int): Int = frames.indexOfFirst { it.step >= step }.takeIf { it >= 0 } ?: frames.lastIndex
}
