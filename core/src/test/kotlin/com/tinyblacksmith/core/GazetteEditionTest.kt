package com.tinyblacksmith.core

import com.tinyblacksmith.core.gazette.Gazette
import com.tinyblacksmith.core.model.EventRecord
import com.tinyblacksmith.core.model.EventType
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.MarketVisit
import com.tinyblacksmith.core.model.WeaponId
import com.tinyblacksmith.core.sim.Policy
import com.tinyblacksmith.core.sim.SimulationDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Gazette edition: one day's records laid out by section, every line from a real record (GDD 11). */
class GazetteEditionTest {
    private val names = mapOf("h1" to "Cassia Ellery", "h2" to "Sten Dunmore", "h4" to "Thane Kestrel", "h5" to "Sable Stonebrook", "h7" to "Torvald Ferris")
    private var serial = 0
    private fun ev(type: EventType, priority: Int, text: String, subjects: List<String> = emptyList(), data: Map<String, String> = emptyMap()) =
        EventRecord("e${serial++}", 1, 3, type, priority, text, subjects, data)

    private fun section(e: Gazette.Edition, title: String) = e.sections.first { it.title == title }.lines

    @Test
    fun aHeroesSeveralRecordsFoldIntoOneSentence() {
        val e = Gazette.edition(
            listOf(
                ev(EventType.EXPEDITION_WON, 5, "Cassia Ellery routed Ashclaw foragers using Flaming Iron Sword.", listOf("h1", "w13"), mapOf("winProbability" to "0.67")),
                ev(EventType.EXPEDITION_WON, 2, "Cassia Ellery brought Starsteel back to the forge.", listOf("h1"), mapOf("material" to "starsteel")),
                ev(EventType.HERO_LEVELED, 2, "Cassia Ellery grew stronger (level 2).", listOf("h1")),
                ev(EventType.HERO_PATROLLED, 1, "Sten Dunmore patrolled the town walls.", listOf("h2")),
                ev(EventType.EXPEDITION_LOST, 3, "Thane Kestrel was driven back by Ashclaw foragers bare-handed.", listOf("h4")),
                ev(EventType.HERO_WOUNDED, 2, "Thane Kestrel returned wounded.", listOf("h4")),
                ev(EventType.WEAPON_BROKEN, 4, "Iron Axe shattered in Thane Kestrel's hands. Brittle work.", listOf("w9", "h4")),
                ev(EventType.HERO_RESTED, 0, "Sable Stonebrook rested and recovered.", listOf("h5")),
            ),
            names,
        )
        assertEquals(emptyList(), e.lede, "an ordinary expedition is not front-page news")
        assertEquals(
            listOf(
                "Cassia Ellery routed Ashclaw foragers using Flaming Iron Sword; brought Starsteel back to the forge; grew stronger (level 2).",
                "Thane Kestrel was driven back by Ashclaw foragers bare-handed; returned wounded. Iron Axe shattered in Thane Kestrel's hands. Brittle work.",
                "On the walls: Sten Dunmore. Resting: Sable Stonebrook.",
            ),
            section(e, Gazette.HEROES),
        )
        assertEquals(listOf("Expeditions: 1 won, 1 lost"), e.tally)
        assertEquals(listOf(Gazette.HEROES), e.sections.map { it.title })
    }

    @Test
    fun theShopTellsWhoBoughtAndWhoLeftAndWhy() {
        val events = listOf(
            ev(EventType.WEAPON_SOLD, 4, "Cassia Ellery bought Flaming Iron Sword for 108 gold. Bronze Dagger came back to the shop in part payment (35 gold).", listOf("h1", "w13"), mapOf("price" to "108", "tradeIn" to "35")),
            ev(EventType.WEAPON_EQUIPPED, 2, "Cassia Ellery now wields Flaming Iron Sword.", listOf("h1", "w13")),
            ev(EventType.COMMISSION_OFFERED, 3, "Torvald Ferris asks for a fine verdant Spear by day 8, offering 222 gold.", listOf("h7", "c1")),
        )
        val visits = listOf(
            MarketVisit(HeroId("h1"), "Cassia Ellery", WeaponId("w13"), "GREAT_FIT"),
            MarketVisit(HeroId("h2"), "Sten Dunmore", null, "TOO_EXPENSIVE"),
            MarketVisit(HeroId("h4"), "Thane Kestrel", null, "NOT_BETTER"),
            MarketVisit(HeroId("h5"), "Sable Stonebrook", null, "TOO_EXPENSIVE"),
        )
        val e = Gazette.edition(events, names, visits)
        assertEquals(
            listOf(
                events[0].text,
                events[2].text,
                "Sten Dunmore and Sable Stonebrook could afford nothing on the shelf.",
                "Thane Kestrel found nothing better than the weapon in hand.",
            ),
            section(e, Gazette.SHOP),
        )
        assertEquals(listOf("Shop took 73 gold", "1 of 4 visitors bought"), e.tally)
        assertTrue(e.sections.flatMap { it.lines }.none { "now wields" in it }, "equipping is implied by the sale")
        // Without the visits (the archive) the tally still counts the sale.
        assertEquals(listOf("Shop took 73 gold", "1 sold"), Gazette.edition(events, names).tally)
    }

    @Test
    fun bigNewsLeadsAndImpliedMilestonesFoldAway() {
        val events = listOf(
            ev(EventType.ELITE_SLAIN, 7, "Torvald Ferris slew an Ashclaw warchief using Iron Sword and returned with 132 gold in spoils.", listOf("h7", "w7"), mapOf("winProbability" to "0.31")),
            ev(EventType.MILESTONE, 4, "An elite foe fell to a hero of Emberfall: an Ashclaw warchief.", data = mapOf("milestone" to "ELITE_SLAIN")),
            ev(EventType.EXPEDITION_WON, 2, "Torvald Ferris brought Stormglass back to the forge.", listOf("h7"), mapOf("material" to "stormglass")),
            ev(EventType.SIEGE_WARNING, 6, "Hollowbound gather for the day 5 invasion (restless).", data = mapOf("day" to "5")),
            ev(EventType.FORGE_DAMAGED, 7, "The forge took 11 damage in the siege.", data = mapOf("damage" to "11")),
            ev(EventType.SIEGE_WON, 9, "Emberfall repelled the Ashclaw horde! Champions: Cassia Ellery, Sten Dunmore, Torvald Ferris.", listOf("h1", "h2", "h7")),
            ev(EventType.MILESTONE, 4, "Emberfall survived its first siege.", data = mapOf("milestone" to "SIEGE_SURVIVED")),
            ev(EventType.MILESTONE, 4, "Cassia Ellery reached level 5.", data = mapOf("milestone" to "HERO_LEVEL_5")),
            ev(EventType.MILESTONE, 6, "Warlord Krag was thrown back from the walls. The town paid the smith 120 gold in thanks.", data = mapOf("tribute" to "120")),
            ev(EventType.BLESSING_OFFERED, 3, "The grateful town offers the smith a blessing."),
        )
        val e = Gazette.edition(events, names)
        assertEquals(listOf(events[5].text, events[0].text), e.lede)
        val all = e.lede + e.sections.flatMap { it.lines }
        assertTrue(all.none { "An elite foe fell" in it || "survived its first siege" in it }, "milestones the news already tells are folded")
        assertEquals(listOf("Torvald Ferris brought Stormglass back to the forge."), section(e, Gazette.HEROES))
        assertEquals(listOf(events[4].text, events[7].text, events[8].text, events[9].text, events[3].text), section(e, Gazette.TOWN), "the siege warning stands last")
        assertEquals(listOf("Shop took 120 gold", "Expeditions: 1 won, 0 lost"), e.tally)
    }

    @Test
    fun theSmithsOwnWorkIsOneLineAndDiscoveriesFollow() {
        val events = listOf(
            ev(EventType.TOOL_BOUGHT, 2, "The forge gained a new tool: Display Case.", data = mapOf("tool" to "display_case", "name" to "Display Case", "level" to "1")),
            ev(EventType.WEAPON_FORGED, 1, "The smith forged Moonsteel Spear (legendary, quality 98).", listOf("w11"), mapOf("rarity" to "LEGENDARY")),
            ev(EventType.MILESTONE, 4, "A legendary weapon, Moonsteel Spear, left the anvil.", data = mapOf("milestone" to "LEGENDARY_FORGED")),
            ev(EventType.DISCOVERY, 1, "Journal: Ember Resin on Spears observed — faint harmony.", data = mapOf("key" to "af:ember_resin|spear")),
            ev(EventType.WEAPON_FORGED, 1, "The smith forged Starsteel Bow (epic, quality 73).", listOf("w12"), mapOf("rarity" to "EPIC")),
            ev(EventType.WEAPON_FORGED, 1, "The smith forged Iron Sword (rare, quality 50).", listOf("w13"), mapOf("rarity" to "RARE")),
            ev(EventType.WEAPON_LISTED, 0, "Moonsteel Spear was placed on the shelf for 240 gold.", listOf("w11")),
            ev(EventType.WEAPON_LISTED, 0, "Starsteel Bow was placed on the shelf for 176 gold.", listOf("w12")),
            ev(EventType.WEAPON_HONED, 2, "The smith honed Starsteel Bow to quality 79.", listOf("w12"), mapOf("quality" to "79")),
            ev(EventType.WEAPON_DONATED, 3, "The smith armed the town watch with Iron Sword.", listOf("w13"), mapOf("armory" to "4")),
            ev(EventType.WEAPON_SALVAGED, 0, "The smith melted Iron Dagger down for its Iron.", listOf("w14")),
        )
        val e = Gazette.edition(events, names)
        assertEquals(
            listOf(
                "Forged 3 weapons (1 legendary, 1 epic), listed 2, honed 1, armed the watch with 1, melted down 1, bought Display Case.",
                events[3].text,
            ),
            section(e, Gazette.FORGE),
        )
        assertEquals(listOf(Gazette.FORGE), e.sections.map { it.title })
        assertEquals(emptyList(), e.tally)
    }

    @Test
    fun aSimulatedDayIsFullyAccountedForAndDeterministic() {
        val (_, state) = SimulationDriver(maxDays = 5).playRun(LegacyProfile(), 3, Policy.BALANCED_ACTIVE)
        val heroNames = state.heroes.values.associate { it.id.value to it.fullName }
        for (day in 1..5) {
            val events = state.eventsForDay(day)
            val visits = state.lastResolution?.takeIf { it.day == day }?.visits ?: emptyList()
            val e = Gazette.edition(events, heroNames, visits)
            assertEquals(e, Gazette.edition(events.shuffled(java.util.Random(day.toLong())), heroNames, visits), "day $day: order of records does not matter")
            val lines = e.lede + e.sections.flatMap { it.lines }
            assertEquals(lines.distinct(), lines, "day $day: no line twice")
            for (ev in events) {
                if (ev.type == EventType.WEAPON_EQUIPPED || ev.type == EventType.MILESTONE || ev.type == EventType.HERO_ARRIVED) continue
                val hero = ev.subjectIds.firstOrNull { it.startsWith("h") }?.let { heroNames[it] }
                val needle = (if (hero != null) ev.text.removePrefix("$hero ") else ev.text).trimEnd('.')
                val folded = ev.type in setOf(
                    EventType.WEAPON_FORGED, EventType.WEAPON_LISTED, EventType.WEAPON_HONED, EventType.WEAPON_DONATED, EventType.WEAPON_SALVAGED,
                    EventType.TOOL_BOUGHT, EventType.HERO_PATROLLED, EventType.HERO_RESTED,
                ) || (ev.type == EventType.SIEGE_WARNING)
                assertTrue(folded || lines.any { needle in it }, "day $day: record not in the paper: ${ev.type} ${ev.text}")
                if (ev.type == EventType.HERO_PATROLLED || ev.type == EventType.HERO_RESTED) assertTrue(lines.any { hero!! in it }, "day $day: $hero missing from the quiet line")
            }
        }
    }
}
