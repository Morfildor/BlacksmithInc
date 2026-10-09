package com.tinyblacksmith.core

import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.persistence.SaveCodec
import com.tinyblacksmith.core.sim.Policy
import com.tinyblacksmith.core.sim.SimulationDriver
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** F11: nothing the engine stores depends on the device language (a formatted decimal reads "0.31" in English and "0,31" in German or Turkish). */
class LocaleDeterminismTest {
    private fun thirtyDays(locale: Locale): String {
        Locale.setDefault(locale)
        val (_, state) = SimulationDriver(maxDays = 30).playRun(LegacyProfile(), 12, Policy.BALANCED_ACTIVE)
        assertTrue(state.day > 20, "the run should last: day ${state.day}")
        val decimal = Regex("\\d[.,]\\d")
        val formatted = state.events.flatMap { e -> e.data.entries.filter { decimal.containsMatchIn(it.value) }.map { "${e.type} ${it.key}=${it.value}" } }
        assertEquals(emptyList(), formatted, "no formatted decimal in any event payload ($locale)")
        return SaveCodec.encodeRun(state)
    }

    @Test
    fun encodedStateIsEqualUnderEnglishGermanAndTurkishLocales() {
        val before = Locale.getDefault()
        try {
            val english = thirtyDays(Locale.US)
            assertEquals(english, thirtyDays(Locale.GERMANY), "German")
            assertEquals(english, thirtyDays(Locale.forLanguageTag("tr-TR")), "Turkish")
        } finally {
            Locale.setDefault(before)
        }
    }
}
