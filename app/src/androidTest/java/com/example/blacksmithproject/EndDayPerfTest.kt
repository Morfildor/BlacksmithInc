package com.example.blacksmithproject

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tinyblacksmith.core.sim.Policy
import com.tinyblacksmith.core.sim.Simulator
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * GDD 15.3: a nominal day simulation stays under 200 ms at p95 on the device runtime, excluding persistence and
 * rendering. Plays 120 forced-survival days as the active smith (the heaviest policy) and logs the percentiles under
 * the `TinyBlacksmithPerf` tag; the first days include JIT warm-up.
 */
@RunWith(AndroidJUnit4::class)
class EndDayPerfTest {
    @Test
    fun endDayStaysUnderTheBudgetOnDevice() {
        val perf = Simulator.measureEndDay(days = 120, seed = 1, policy = Policy.BALANCED_ACTIVE)
        Log.i("TinyBlacksmithPerf", "END_DAY days=${perf.days} p50=${perf.p50Ms} ms p95=${perf.p95Ms} ms max=${perf.maxMs} ms weapons=${perf.weaponsAtEnd} events=${perf.eventsAtEnd}")
        assertTrue("End Day p95 ${perf.p95Ms} ms over ${perf.days} days", perf.p95Ms < 200.0)
    }
}
