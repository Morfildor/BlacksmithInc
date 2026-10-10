package com.example.blacksmithproject

import com.example.blacksmithproject.data.ShopDaySpeed
import com.example.blacksmithproject.ui.shopday.Beat
import com.example.blacksmithproject.ui.shopday.ReceiptRow
import com.example.blacksmithproject.ui.shopday.ShopDayUiModel
import com.example.blacksmithproject.ui.shopday.isEnding
import com.example.blacksmithproject.ui.shopday.toUi
import com.tinyblacksmith.core.model.FieldOutcome
import com.tinyblacksmith.core.model.IncomeKind
import com.tinyblacksmith.core.model.VisitKind
import com.tinyblacksmith.core.shopday.AftermathKind
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

    /**
     * At 1x with no taps the screen waits exactly `beat.millis` on each card before the day's last one (the auto-advance
     * in `ShopDayScreen` is `delay(millis / divisor)`), so a day's length is that sum. Budget (plan 6.5): median at
     * most 25 s and p90 at most 30 s, over the first ten recorded days with 6 to 10 visitors at the town's real size.
     */
    @Test
    fun dayLengthAtOneSpeedOverTheFixtureDays() {
        val days = (1L..12L).asSequence().flatMap { ShopDayFixtures.run(it, 25) }.filter { it.script.visits.size in 6..10 }.take(10).toList()
        assertEquals("ten recorded days of 6 to 10 visitors", 10, days.size)
        val seconds = days.map { d -> d.ui().let { m -> m.beats.take(m.endingIndex).sumOf { it.millis } } / 1000.0 }.sorted()
        val median = (seconds[4] + seconds[5]) / 2
        val p90 = seconds[8]
        println("day length at 1x: ${seconds.joinToString { "%.1f".format(it) }} s; median %.1f, p90 %.1f".format(median, p90))
        assertTrue("median $median s", median <= 25.0)
        assertTrue("p90 $p90 s", p90 <= 30.0)
        // One tap still skips any day: the ending is a card of its own, after every timed card.
        days.forEach { d -> d.ui().let { m -> assertTrue(m.endingIndex <= m.beats.lastIndex && m.beats[m.endingIndex].millis == 0) } }
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
            // Before the ending only a siege waits for the player (it decides the run); every other card moves on by itself.
            assertTrue(model.beats.take(model.endingIndex).none { it.isEnding || (it.millis == 0 && (it as? Beat.Aftermath)?.card?.siege == null) })
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
                // A request is paid its reward, whatever tag the blade wore: its receipt has no listed price to add up from.
                assertEquals(sale.listedPrice?.takeIf { v.kind != VisitKind.COMMISSION }?.let { "$it gold" }, ui.receipt.firstOrNull { it.label == "Listed price" }?.value)
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
                assertEquals(Lines.headline(v, day.script).replaceFirstChar { it.uppercase() }, ui.outcome)
                ui.decision?.let { assertEquals(Lines.decision(v, day.script, day.engine.content), it) }
                if (v.kind == VisitKind.BROWSE) assertEquals(v.considered.map { Lines.weighed(it) }, ui.looked.map { it.factors })
            }
        }
    }

    private fun gold(text: String) = text.removeSuffix(" gold").replace("−", "-").toInt()

    /**
     * "Earned today" is arithmetic over the day's Sale records and nothing else: it rises on each sale card by that
     * receipt's total, takes in the tally's sales at once, and with what the day earned away from the counter it is the
     * till's total, which is the ledger's. A request payment and a collector count like any sale.
     */
    @Test
    fun earnedTodayIsTheSaleRecordsAndMeetsTheTill() {
        var sales = 0; var tallied = 0; var requests = 0; var tills = 0
        for (day in days() + listOfNotNull(ShopDayFixtures.find("commission"), ShopDayFixtures.find("siege"))) {
            val model = day.ui()
            val ledger = day.resolution.ledger ?: continue
            if (day.script.quiet != null) continue
            var earned = 0
            for (beat in model.beats) when (beat) {
                is Beat.Visit -> {
                    val v = day.script.featured.single { it.seq == beat.visit.seq }
                    val coin = v.sale?.let { it.cashPaid + it.saleBonus + it.stipend } ?: 0
                    assertEquals("the card counts from where the last one stopped", earned, beat.visit.earnedBefore)
                    assertEquals(coin, beat.visit.coin)
                    assertEquals(earned + coin, beat.visit.earnedAfter)
                    if (v.sale == null) assertEquals("only a sale moves the number", beat.visit.earnedBefore, beat.visit.earnedAfter)
                    if (v.sale != null) { sales++; assertEquals("the receipt's total is the rise", coin, gold(beat.visit.receipt.last().value)) }
                    if (v.kind == VisitKind.COMMISSION) {
                        requests++
                        assertEquals("Request paid", beat.visit.banner)
                        assertEquals(listOf("Request payment" to "${v.sale!!.cashPaid} gold", "Coin to the till" to "$coin gold"), beat.visit.receipt.map { it.label to it.value })
                    }
                    earned = beat.visit.earnedAfter
                }
                is Beat.Tally -> {
                    val rest = day.script.tally.sumOf { g -> g.visits.sumOf { v -> v.sale?.let { it.cashPaid + it.saleBonus + it.stipend } ?: 0 } }
                    assertEquals(earned to earned + rest, beat.earnedBefore to beat.earnedAfter)
                    if (rest > 0) tallied++
                    earned = beat.earnedAfter
                }
                is Beat.Close -> {
                    tills++
                    val total = ledger.goldAtClose - ledger.goldAtOpen
                    // Everything the counter took is on the cards before the till; what is left came from beyond it (a tribute).
                    val atCounter = ledger.income.filterKeys { it != IncomeKind.TRIBUTE }.values.sum()
                    assertEquals("seed ${day.seed} day ${day.resolution.day}: the cards add up to the counter's income", atCounter, earned)
                    assertEquals(total, earned + (ledger.income[IncomeKind.TRIBUTE] ?: 0))
                    assertEquals(ReceiptRow("Earned today", "$total gold", total = true), beat.rows.last())
                    assertEquals("the rows by kind add up to the total", total, beat.rows.dropLast(1).sumOf { gold(it.value) })
                    assertEquals(ledger.income.filterValues { it != 0 }.size, beat.rows.size - 1)
                    assertEquals(ledger.income[IncomeKind.COMMISSION]?.takeIf { it != 0 }?.let { "$it gold" }, beat.rows.firstOrNull { it.label == "Requests" }?.value)
                    assertEquals(ledger.goldAtClose, beat.purse)
                }
                else -> {}
            }
            // The purse the next morning starts from is the saved one: nothing the cards showed was added to it.
            assertEquals(day.state.gold, (model.beats.last() as? Beat.Tomorrow)?.gold ?: day.state.gold)
        }
        assertTrue("sales=$sales tallied=$tallied requests=$requests tills=$tills", sales > 40 && tallied > 5 && requests > 0 && tills > 60)
    }

    /** A receipt reads top to bottom as a sum: the price, less a trade-in, plus a blessing, is the coin to the till. A guild's share is part of the price. */
    @Test
    fun aSaleReceiptAddsUpToItsTotal() {
        var sales = 0; var tradeIns = 0
        for (day in days() + listOfNotNull(ShopDayFixtures.find("tradein"))) for (beat in day.ui().beats.filterIsInstance<Beat.Visit>()) {
            val rows = beat.visit.receipt
            if (rows.isEmpty()) continue
            sales++
            if (rows.any { it.label.startsWith("Trade-in") }) tradeIns++
            val parts = rows.dropLast(1).filterNot { it.label.startsWith("Of that") }.sumOf { gold(it.value.removePrefix("+")) }
            assertEquals("${rows.map { it.label to it.value }}", gold(rows.last().value), parts)
            rows.firstOrNull { it.label.startsWith("Of that") }?.let { assertTrue(gold(it.value) <= gold(rows.first().value)) }
        }
        assertTrue("sales=$sales tradeIns=$tradeIns", sales > 40 && tradeIns > 0)
    }

    /** A sale and a refusal are told apart by more than a word, and a refusal leads with what the record holds. */
    @Test
    fun aRefusalLeadsWithItsRecordedNumbersAndASaleWithItsCoin() {
        var short = 0; var refusals = 0
        for (day in days()) for (beat in day.ui().beats.filterIsInstance<Beat.Visit>()) {
            val ui = beat.visit
            val v = day.script.featured.single { it.seq == ui.seq }
            assertEquals(ui.sold, ui.banner != "No sale")
            assertEquals(ui.sold, ui.receipt.isNotEmpty())
            if (ui.sold) continue
            refusals++
            assertEquals(0, ui.coin)
            if (v.reason == com.tinyblacksmith.core.model.VisitReason.TOO_EXPENSIVE) {
                // The gap is to the cheapest blade still on the shelf when they came, by what the visit says they could pay.
                val funds = v.considered.first { it.shortBy != null }.let { it.price - it.shortBy!! }
                val gone = day.script.visits.filter { it.seq < v.seq }.mapNotNull { it.purchasedWeaponId }
                val cheapest = day.script.prices.filterKeys { it !in gone }.values.min()
                assertEquals("${cheapest - funds} gold short of the cheapest blade", ui.outcome)
                assertEquals("Could pay up to $funds gold; the cheapest blade is $cheapest gold.", ui.decision)
                short++
            }
            for (looked in ui.looked) {
                assertTrue(looked.factors, looked.factors.isEmpty() || looked.factors.startsWith("For it: ") || looked.factors.startsWith("Against it: "))
                assertTrue(looked.title, looked.title.endsWith(" gold"))
            }
            // What was missing, never what a different price would have done.
            val said = listOfNotNull(ui.outcome, ui.decision) + ui.looked.map { it.factors }
            assertTrue("$said", said.none { Regex("would|lower|cheaper|if you|%|chance", RegexOption.IGNORE_CASE).containsMatchIn(it) })
        }
        assertTrue("short=$short refusals=$refusals", short > 5 && refusals > 30)
    }

    /** The opening card counts everyone and says how many have a card of their own; each of those is numbered among the shown only. */
    @Test
    fun theOpeningCardTellsVisitorsFromThoseShownAtTheCounter() {
        var some = 0
        for (day in days()) {
            val model = day.ui()
            val open = model.beats.first() as? Beat.Open ?: continue
            assertEquals(day.resolution.visits.size to day.script.featured.size, open.visitors to open.shown)
            val shown = model.beats.filterIsInstance<Beat.Visit>()
            assertEquals(shown.indices.map { "Counter · ${it + 1} of ${open.shown}" }, shown.map { it.progress })
            model.beats.filterIsInstance<Beat.Tally>().singleOrNull()?.let { assertEquals(open.visitors - open.shown, it.count) }
            if (open.shown in 1 until open.visitors) some++
        }
        assertTrue("days with a tally after the featured: $some", some > 10)
    }

    /** A day when heroes fought and nothing else beyond the door earned a card: one summary card, in the place the saved position expects. */
    @Test
    fun aQuietDayWithFightsShowsOneSummaryBeyondTheDoor() {
        val day = ShopDayFixtures.run(42, 1, forge = false).single()
        val fights = day.resolution.field.count { it.outcome == FieldOutcome.DRIVEN_BACK || it.outcome == FieldOutcome.WON || it.outcome == FieldOutcome.DIED }
        assertTrue("seed 42, day 1, nothing forged: heroes still went out ($fights)", fights > 0)
        val model = day.ui()
        assertEquals(listOf("Quiet", "Aftermath", "Tomorrow"), model.beats.map { it::class.simpleName })
        val card = (model.beats[1] as Beat.Aftermath).card
        assertEquals(AftermathKind.FIELD_SUMMARY, card.kind)
        assertEquals("Out in the field", card.title)
        assertEquals(Lines.field(day.script.aftermath.single().tally!!), card.text)
        assertTrue(card.text, card.text.startsWith("$fights hero"))
        assertTrue("no face, blade or fight to open: the Gazette has the names", card.hero == null && card.blade == null && card.replay == null && card.champions.isEmpty())
        assertEquals(
            listOf(com.example.blacksmithproject.Beat.Quiet, com.example.blacksmithproject.Beat.Aftermath(0), com.example.blacksmithproject.Beat.Tomorrow),
            ShopDayPosition.beats(day.script),
        )
        // A day with a card of its own beyond the door has no summary.
        for (d in days()) if (d.script.aftermath.size > 1) assertTrue(d.script.aftermath.none { it.kind == AftermathKind.FIELD_SUMMARY })
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
