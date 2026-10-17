package com.example.blacksmithproject

import com.example.blacksmithproject.ui.detail.heroDetail
import com.example.blacksmithproject.ui.guildUi
import com.example.blacksmithproject.ui.loanUi
import com.example.blacksmithproject.ui.memberDetail
import com.example.blacksmithproject.ui.shopday.toUi
import com.tinyblacksmith.core.model.Hook
import com.tinyblacksmith.core.model.HookKind
import com.tinyblacksmith.core.model.LivingRevision
import com.tinyblacksmith.core.model.OathKind
import com.tinyblacksmith.core.model.Rarity
import com.tinyblacksmith.core.text.LivingText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The members' own decisions on the roster and the sheet, in core's words, only in a living era (final design 9, 10, 15). */
class LivingAgencyUiTest {
    private val f = LivingAgencyFixtures.build()
    private val e = LivingAgencyFixtures.engine
    private val s = f.state

    @Test
    fun theRosterCarriesOneShortNoticeAndTheSheetTheWholeSentence() {
        val ui = e.guildUi(s)!!
        val departing = ui.roster.single { it.heroId == f.departing }
        val m = s.guild!!.member(f.departing)!!
        assertEquals(LivingText.marker(m, s, e.content), departing.notice)
        assertTrue(departing.urgent)
        assertEquals(LivingText.marker(s.guild!!.member(f.leaning)!!, s, e.content), ui.roster.single { it.heroId == f.leaning }.notice)
        val sheet = e.heroDetail(s, f.departing)!!
        assertEquals(LivingText.departure(s.heroes.getValue(f.departing), m), sheet.departure)
        assertTrue("the departure is said once, under the head", sheet.story.none { it.value == sheet.departure })
        assertTrue(e.heroDetail(s, f.leaning)!!.story.any { it.value == LivingText.lean(s.heroes.getValue(f.leaning), s.guild!!.member(f.leaning)!!, e.content) })
    }

    @Test
    fun aLivingEraNeverOffersTheSmithASpecialityToChoose() {
        assertTrue(e.memberDetail(s, f.leaning)!!.branches.isEmpty())
        assertFalse(e.guildUi(s)!!.companyNeeds)
        // The same member in a legacy era: the smith chooses, and no notice is shown.
        val legacy = s.copy(livingRevision = LivingRevision.LEGACY, guild = s.guild!!.copy(members = s.guild!!.members.map { it.copy(lean = null, loanReturn = null, concern = null) }))
        assertTrue(e.memberDetail(legacy, f.leaning)!!.branches.isNotEmpty())
        assertTrue(e.guildUi(legacy)!!.roster.all { it.notice == null })
    }

    @Test
    fun anOathThatRefusesALoanSaysSoBeforeTheSmithTries() {
        val id = f.leaning
        val sworn = s.copy(heroes = s.heroes + (id to s.heroes.getValue(id).copy(hook = Hook(HookKind.OATH, 1, oath = OathKind.PLAIN_WORK))))
        val rare = sworn.weapons.values.first().copy(rarity = Rarity.RARE)
        val withRare = sworn.copy(weapons = sworn.weapons + (rare.id to rare.copy(location = com.tinyblacksmith.core.model.WeaponLocation.Storage)))
        val row = e.memberDetail(withRare, id)!!.loanable.firstOrNull { it.weaponId == rare.id }
        assertNotNull(row)
        assertEquals("${s.heroes.getValue(id).name} has sworn to carry only Common or Uncommon weapons.", row!!.refused)
        val target = e.loanUi(withRare, withRare.weapons.getValue(rare.id))!!.targets.single { it.heroId == id }
        assertFalse(target.enabled)
        assertEquals(row.refused, target.reason)
        assertNull(e.memberDetail(withRare, f.departing)!!.loanable.firstOrNull { it.weaponId == rare.id }?.refused)
    }

    @Test
    fun aPersonalOfferNamesWhoAsksForItAndTheOnePartyRule() {
        val offers = e.guildUi(s)!!.offers
        val personal = offers.single { it.sponsor != null }
        assertEquals(s.heroes.getValue(f.leaning).fullName, personal.sponsor!!.name)
        assertEquals(s.guild!!.offers.single { it.sponsorHeroId != null }.reason, personal.sponsor!!.why)
        assertTrue(offers.filter { it !== personal }.all { it.sponsor == null })
        assertTrue(e.guildUi(s.copy(livingRevision = LivingRevision.LEGACY))!!.offers.all { it.sponsor == null })
    }

    @Test
    fun aMembersCounterPurchaseReadsApartFromTheCustomersInTheEnginesWords() {
        val r = s.lastResolution!!
        val model = com.tinyblacksmith.core.shopday.ShopDay.script(r, s, e.content, e.config).toUi(s, e.content, e.config)
        val groups = model.beats.filterIsInstance<com.example.blacksmithproject.ui.shopday.Beat.Tally>().flatMap { it.groups }
        val member = groups.single { it.member }
        val event = s.events.single { it.type == com.tinyblacksmith.core.model.EventType.MEMBER_PURCHASE && it.day == r.day }
        assertEquals(event.text, member.line)
        assertTrue("a guild member", "a member of your guild" in member.line)
        assertTrue(groups.filterNot { it.member }.none { "member" in it.line })
    }

    @Test
    fun theWallLeadsWithTheCommittedArmyAndItsAnswers() {
        val army = e.content.guild!!.army(s.siege!!.armyId)!!
        val ui = e.guildUi(s)!!.wall.army!!
        assertEquals(army.name, ui.name)
        assertNull("a standard army has no kind word", ui.kind)
        assertEquals(army.forecast, ui.forecast)
        assertEquals(army.counters, ui.counters)
        assertEquals("Captain", com.example.blacksmithproject.ui.armyKindWord(com.tinyblacksmith.core.content.ArmyKind.CAPTAIN))
        assertNull(e.guildUi(s.copy(livingRevision = LivingRevision.LEGACY))!!.wall.army)
    }

    @Test
    fun anEnemyCardSaysItsTraitsJobAndAnswerOnce() {
        val combat = e.content.combat!!
        val profiles = e.content.guild!!.profileById
        fun card(id: String) = com.example.blacksmithproject.ui.enemyTip(combat.unit(id), 1, listOf(100), e.config, profile = profiles[id])
        fun lines(id: String) = card(id).sections.flatMap { it.lines }
        val sapper = lines("ib_goblin_sapper")
        assertTrue(sapper.any { it.title == "Attack on the defences" })
        assertTrue(sapper.any { it.text == profiles.getValue("ib_goblin_sapper").job })
        assertEquals(profiles.getValue("ib_goblin_sapper").counter, card("ib_goblin_sapper").sections.single { it.title == "How to answer it" }.lines.single().text)
        assertTrue("its own telegraph keeps the number", sapper.any { "4 damage to the defences" in it.text })
        val bones = lines("pp_skeleton_guard")
        assertTrue(bones.any { it.text == "Poison and Bleed do not affect it." })
        assertTrue(bones.any { it.text == "Axe attacks deal 30% more damage to it." })
        // Each trait is said once: never again in the attack's own line.
        assertEquals(1, bones.count { "do not affect" in it.text || "never affect" in it.text })
        val wolf = lines("tc_werewolf")
        assertTrue(wolf.any { it.title == "Recovers" && "a Silver weapon" in it.text })
        assertTrue(wolf.any { it.text == "Silver weapons deal 30% more damage to it." })
        assertTrue(lines("tc_dire_wolf").any { it.title == "Pack" })
    }
}
