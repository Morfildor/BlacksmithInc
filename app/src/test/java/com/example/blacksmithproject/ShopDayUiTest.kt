package com.example.blacksmithproject

import com.example.blacksmithproject.ui.shopday.Beat
import com.example.blacksmithproject.ui.shopday.ShopDayUiModel
import com.example.blacksmithproject.ui.shopday.isEnding
import com.example.blacksmithproject.ui.shopday.toUi
import com.tinyblacksmith.core.model.VisitKind
import com.tinyblacksmith.core.shopday.Ending
import com.tinyblacksmith.core.shopday.Lines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** `ShopDayScript.toUi` over days the real engine resolved: it rearranges the script into beats and adds nothing. */
class ShopDayUiTest {
    private fun days() = (1L..8L).asSequence().flatMap { ShopDayFixtures.run(it, 15) } +
        (1L..3L).asSequence().flatMap { ShopDayFixtures.run(it, 10, ShopDayFixtures.busy) } +
        (1L..2L).asSequence().flatMap { ShopDayFixtures.run(it, 60, forge = false) }

    private fun ShopDayFixtures.Day.ui(): ShopDayUiModel = script.toUi(state, engine.content, engine.config)

    /** The saved position indexes the screen's cards: the two lists are built apart and must agree card for card, also on a day recorded before the ledger. */
    @Test
    fun theSavedPositionAndTheScreenCountTheSameCards() {
        var checked = 0
        for (d in days()) for (script in listOf(d.script, d.script.copy(ledger = null))) {
            val cards = ShopDayPosition.beats(script)
            val ui = script.toUi(d.state, d.engine.content, d.engine.config)
            assertEquals("seed ${d.seed} day ${script.day}", cards.size, ui.beats.size)
            assertEquals("seed ${d.seed} day ${script.day}", ShopDayPosition(cards, 0).ending, ui.endingIndex)
            cards.zip(ui.beats).forEach { (card, beat) ->
                val same = when (card) {
                    com.example.blacksmithproject.Beat.ShopOpens -> beat is Beat.Open
                    is com.example.blacksmithproject.Beat.Visit -> beat is Beat.Visit
                    com.example.blacksmithproject.Beat.Tally -> beat is Beat.Tally
                    com.example.blacksmithproject.Beat.ShopCloses -> beat is Beat.Close
                    com.example.blacksmithproject.Beat.Quiet -> beat is Beat.Quiet
                    is com.example.blacksmithproject.Beat.Aftermath -> beat is Beat.Aftermath
                    com.example.blacksmithproject.Beat.Fallen -> beat is Beat.Fallen
                    com.example.blacksmithproject.Beat.Blessing -> beat is Beat.Blessing
                    com.example.blacksmithproject.Beat.Tomorrow -> beat is Beat.Tomorrow
                }
                assertTrue("seed ${d.seed} day ${script.day}: $card against $beat", same)
            }
            checked++
        }
        assertTrue(checked > 100)
    }

    @Test
    fun beatsCoverEveryVisitExactlyOnce() {
        var visits = 0; var featured = 0; var quiet = 0
        for (day in days()) {
            val model = day.ui()
            val seqs = model.beats.flatMap { b ->
                when (b) {
                    is Beat.Visit -> listOf(b.visit.seq)
                    is Beat.Tally -> b.groups.flatMap { it.seqs }
                    is Beat.Quiet -> b.seqs
                    else -> emptyList()
                }
            }
            assertEquals("day ${day.resolution.day} of seed ${day.seed}: every visit once", day.resolution.visits.map { it.seq }, seqs.sorted())
            assertEquals("the featured visits are the visit beats, in order", day.script.featured.map { it.seq }, model.beats.filterIsInstance<Beat.Visit>().map { it.visit.seq })
            visits += seqs.size; featured += day.script.featured.size; if (day.script.quiet != null) quiet++
        }
        assertTrue("visits=$visits featured=$featured quiet=$quiet", visits > 200 && featured > 100 && quiet >= 10)
    }

    @Test
    fun theDayIsOpenThenTheCounterThenBeyondTheDoorThenOneEnding() {
        val endings = HashSet<Ending>()
        for (day in days()) {
            val model = day.ui()
            val kinds = model.beats.map { it::class.simpleName!! }
            val quiet = day.script.quiet != null
            assertEquals(if (quiet) "Quiet" else "Open", kinds.first())
            if (quiet) assertEquals("a quiet day is one card before what follows", 1, kinds.count { it in setOf("Quiet", "Open", "Visit", "Tally", "Close") })
            assertEquals(day.script.aftermath.size, kinds.count { it == "Aftermath" })
            // Skip day lands on the first of the cards that wait for a choice, and nothing before it waits.
            assertTrue(model.beats[model.endingIndex].isEnding)
            assertTrue(model.beats.take(model.endingIndex).none { it.isEnding || it.millis == 0 })
            assertTrue(model.beats.drop(model.endingIndex).all { it.isEnding && it.millis == 0 })
            assertEquals(
                when (day.script.ending) { Ending.FALLEN -> listOf("Fallen"); Ending.BLESSING -> listOf("Blessing", "Tomorrow"); Ending.TOMORROW -> listOf("Tomorrow") },
                kinds.drop(model.endingIndex),
            )
            assertEquals(kinds.sortedBy { listOf("Open", "Quiet", "Visit", "Tally", "Close", "Aftermath", "Blessing", "Tomorrow", "Fallen").indexOf(it) }, kinds)
            endings += day.script.ending
        }
        assertEquals("the fixture days reach every ending", Ending.entries.toSet(), endings)
    }

    @Test
    fun toUiIsPure() {
        for (day in days().take(60)) {
            val before = day.state
            assertEquals(day.ui(), day.ui())
            assertTrue("the state is not replaced or changed", before === day.state)
        }
    }

    @Test
    fun receiptRowsAreTheSaleRecord() {
        var sales = 0
        for (day in days()) {
            val model = day.ui()
            for (v in day.script.featured) {
                val ui = model.beats.filterIsInstance<Beat.Visit>().single { it.visit.seq == v.seq }.visit
                val sale = v.sale
                if (sale == null) { assertTrue(ui.receipt.isEmpty()); assertFalse(ui.sold); continue }
                sales++
                assertEquals("${sale.cashPaid + sale.saleBonus + sale.stipend} gold", ui.receipt.last().value)
                assertTrue(ui.receipt.last().total)
                assertEquals(sale.listedPrice?.let { "$it gold" }, ui.receipt.firstOrNull { it.label == "Listed price" }?.value)
                assertEquals(sale.tradeInWeaponId != null, ui.receipt.any { it.label.startsWith("Trade-in") })
                if (sale.tradeInWeaponId != null) assertEquals("−${sale.tradeInCredit} gold", ui.receipt.first { it.label.startsWith("Trade-in") }.value)
                assertEquals("label and value rows only: no row is a sentence", 0, ui.receipt.count { "." in it.label || "." in it.value })
            }
        }
        assertTrue("sales=$sales", sales > 40)
    }

    @Test
    fun everySentenceOfAVisitIsALinesTemplate() {
        for (day in days().take(80)) {
            val model = day.ui()
            for (v in day.script.featured) {
                val ui = model.beats.filterIsInstance<Beat.Visit>().single { it.visit.seq == v.seq }.visit
                val customer = Lines.customer(v, day.engine.content)
                assertTrue("'${ui.face.name}' + '${ui.detail}' is '$customer'", customer.startsWith(ui.face.name) && customer.endsWith(ui.detail))
                assertEquals(Lines.reason(v.reason).replaceFirstChar { it.uppercase() }, ui.outcome)
                ui.decision?.let { assertEquals(Lines.decision(v, day.script, day.engine.content), it) }
                if (v.kind == VisitKind.BROWSE) assertEquals(v.considered.map { Lines.considered(it, day.script) }, ui.looked.map { it.title + if (it.factors.isEmpty()) "" else ": ${it.factors}" })
            }
        }
    }
}

/** Plan 6.5: no composable is handed a `Random`; the screens only replay records. */
class NoRandomInUiTest {
    @Test
    fun noSourceUnderUiMentionsRandom() {
        val sources = File("src/main/java/com/example/blacksmithproject/ui").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("found ${sources.size} sources", sources.size > 15)
        val offenders = sources.filter { f -> f.readLines().any { "Random" in it || "currentTimeMillis" in it || "nanoTime" in it } }
        assertEquals(emptyList<File>(), offenders)
    }
}
