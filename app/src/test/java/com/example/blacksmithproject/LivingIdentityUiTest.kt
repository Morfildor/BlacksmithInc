package com.example.blacksmithproject

import com.example.blacksmithproject.ui.classLine
import com.example.blacksmithproject.ui.detail.heroDetail
import com.example.blacksmithproject.ui.guildUi
import com.example.blacksmithproject.ui.heroTip
import com.example.blacksmithproject.ui.hookSentence
import com.example.blacksmithproject.ui.raceClass
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.Ambition
import com.tinyblacksmith.core.model.FactionId
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.Hero
import com.tinyblacksmith.core.model.HeroFate
import com.tinyblacksmith.core.model.Hook
import com.tinyblacksmith.core.model.HookKind
import com.tinyblacksmith.core.model.HookStatus
import com.tinyblacksmith.core.model.OathKind
import com.tinyblacksmith.core.model.ReasonRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Living heroes in the app's words (final design 6, 7.3, 15): people and class, background, Hook and moments, each said once. */
class LivingIdentityUiTest {
    private val engine = GameEngine()
    private val run: GameState = engine.guildRun(seed = 41)
    private val member: Hero = run.heroes.getValue(run.guild!!.members.first().heroId)
    private val candidate: Hero = run.heroes.getValue(run.guild!!.candidates.first().heroId)

    private fun GameState.with(vararg heroes: Hero) = copy(heroes = this.heroes + heroes.associateBy { it.id })
    private fun Hero.hooked(hook: Hook?) = copy(raceId = "elf", cultureId = "elf", backgroundId = "bg_poacher", hook = hook)

    @Test
    fun peopleAndClassReadAsOneWordPairAndTheClassAloneWithoutAPeople() {
        val duelist = member.copy(classId = LaunchContent.DUELIST, raceId = "elf", level = 3)
        assertEquals("Elf Duelist", engine.raceClass(duelist))
        assertEquals("Elf Duelist level 3", engine.classLine(duelist))
        assertEquals("Duelist", engine.raceClass(duelist.copy(raceId = null)))
        assertEquals("an unknown people is not shown", "Duelist", engine.raceClass(duelist.copy(raceId = "giant")))
        assertEquals("Elf Duelist · level 3", engine.heroDetail(run.with(duelist), duelist.id)!!.kind)
    }

    @Test
    fun everyHookIsOneSentenceFromItsRecord() {
        val kin = candidate
        val creditor = run.heroes.values.first { it.id != member.id && it.id != kin.id && it.isAlive }
        val weapon = run.weapons.values.first()
        fun say(hook: Hook?, state: GameState = run) = engine.hookSentence(state, member.hooked(hook))
        assertEquals("Carries an old grievance against the Ashclaw Raiders.", say(Hook(HookKind.GRUDGE, 1, factionId = FactionId("ashclaw_raiders"), background = true)))
        assertEquals("Holds a grievance against the Hollowbound.", say(Hook(HookKind.GRUDGE, 1, factionId = FactionId("hollowbound"))))
        assertEquals("Owes 40 gold to ${creditor.fullName}, due day 12.", say(Hook(HookKind.DEBT, 1, creditor = creditor.fullName, creditorHeroId = creditor.id, amount = 40, dueDay = 12)))
        assertEquals("Owes 40 gold to a moneylender in Emberfall, due day 9.", say(Hook(HookKind.DEBT, 1, creditor = "a moneylender in Emberfall", amount = 40, dueDay = 9)))
        assertEquals("A family weapon, the ${weapon.name}, was lost before they came to Emberfall.", say(Hook(HookKind.LOST_WEAPON, 1, weaponId = weapon.id, background = true)))
        assertEquals("Will carry only Common or Uncommon weapons.", say(Hook(HookKind.OATH, 1, oath = OathKind.PLAIN_WORK)))
        assertEquals("Will not carry a Grave weapon.", say(Hook(HookKind.OATH, 1, oath = OathKind.NO_GRAVE)))
        assertEquals("Will carry only spears.", say(Hook(HookKind.OATH, 1, oath = OathKind.FAVOURED_FAMILY, familyId = LaunchContent.SPEAR)))
        assertEquals("Will carry only axes.", say(Hook(HookKind.OATH, 1, oath = OathKind.FAVOURED_FAMILY, familyId = LaunchContent.AXE)))
        assertEquals("Kin of ${kin.fullName}.", say(Hook(HookKind.KIN, 1, kinHeroId = kin.id)))
        val fallen = kin.copy(fate = HeroFate.DEAD, diedOnDay = 9)
        assertEquals("Kin of ${kin.fullName}, who fell on day 9.", say(Hook(HookKind.KIN, 1, kinHeroId = kin.id), run.with(fallen)))
        // Settled: only what was recorded about it, never an invented ending.
        assertNull(say(Hook(HookKind.DEBT, 1, status = HookStatus.RESOLVED, amount = 40)))
        assertEquals("Paid in full.", say(Hook(HookKind.DEBT, 1, status = HookStatus.RESOLVED, amount = 40, note = "Paid in full.")))
        assertNull(say(null))
    }

    @Test
    fun theSheetOpensWithIdentityThenTheStoryThenMomentsNewestFirst() {
        val oath = member.hooked(Hook(HookKind.OATH, 1, oath = OathKind.NO_GRAVE)).copy(
            ambition = Ambition.NAME,
            reasons = listOf(ReasonRecord(3, "deed", "Held the gate with a borrowed spear."), ReasonRecord(6, "loan", "Took the Iron Sword on loan.")),
        )
        val d = engine.heroDetail(run.with(oath), oath.id)!!
        assertEquals(listOf("Background", "Oath", "Ambition"), d.story.map { it.label })
        assertEquals("Former poacher", d.story.first().value)
        assertEquals("Will not carry a Grave weapon.", d.story[1].value)
        assertEquals(listOf("Day 6: Took the Iron Sword on loan.", "Day 3: Held the gate with a borrowed spear."), d.moments)
        assertTrue("a well member's numbers say their condition", d.condition == null)

        // A hero without the living record (an era before it) shows no new lines at all.
        val legacy = member.copy(raceId = null, cultureId = null, backgroundId = null, hook = null, ambition = null, reasons = emptyList())
        val plain = engine.heroDetail(run.with(legacy), legacy.id)!!
        assertTrue(plain.story.isEmpty())
        assertTrue(plain.moments.isEmpty())
    }

    @Test
    fun kinIsSaidOnBothSheetsAndOpensTheRelative() {
        val elder = candidate.copy(kinId = "kin-x")
        val younger = member.hooked(Hook(HookKind.KIN, 1, kinHeroId = elder.id)).copy(kinId = "kin-x")
        val state = run.with(elder, younger)
        val newcomer = engine.heroDetail(state, younger.id)!!.story.single { it.label == "Family" }
        assertEquals("Kin of ${elder.fullName}." to elder.id, newcomer.value to newcomer.heroId)
        val relative = engine.heroDetail(state, elder.id)!!.story.single { it.label == "Family" }
        assertEquals("Kin of ${younger.fullName}." to younger.id, relative.value to relative.heroId)
    }

    @Test
    fun aRecruitShowsTheirHookBeforeTheFeeAndOnTheirCard() {
        val sworn = candidate.hooked(Hook(HookKind.OATH, 1, oath = OathKind.PLAIN_WORK))
        val state = run.with(sworn)
        val row = engine.guildUi(state)!!.candidates.single { it.heroId == sworn.id }
        assertEquals("Will carry only Common or Uncommon weapons.", row.complication)
        assertTrue(row.sub, row.sub.startsWith("Elf "))
        val tip = engine.heroTip(state, sworn.id)!!
        assertTrue(tip.kind, tip.kind.startsWith("Elf "))
        assertTrue(tip.sections.flatMap { it.lines }.any { it.text == "Will carry only Common or Uncommon weapons." })
        // No Hook, no line.
        assertNull(engine.guildUi(run.with(candidate.copy(hook = null)))!!.candidates.single { it.heroId == candidate.id }.complication)
    }
}
