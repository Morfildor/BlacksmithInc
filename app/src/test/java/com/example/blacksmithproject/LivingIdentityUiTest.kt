package com.example.blacksmithproject

import com.example.blacksmithproject.ui.classLine
import com.example.blacksmithproject.ui.detail.heroDetail
import com.example.blacksmithproject.ui.guildUi
import com.example.blacksmithproject.ui.heroTip
import com.example.blacksmithproject.ui.hookSentence
import com.example.blacksmithproject.ui.raceClass
import com.example.blacksmithproject.ui.nameParts
import com.tinyblacksmith.core.text.LivingText
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
        assertEquals("Elf Duelist level\u00A03", engine.classLine(duelist))
        assertEquals("Duelist", engine.raceClass(duelist.copy(raceId = null)))
        assertEquals("an unknown people is not shown", "Duelist", engine.raceClass(duelist.copy(raceId = "giant")))
        assertEquals("Elf Duelist, level\u00A03", engine.heroDetail(run.with(duelist), duelist.id)!!.kind)
    }

    /** Every Hook in core's own words (`LivingText.hook`): screens compose nothing of their own about a hero's situation. */
    @Test
    fun everyHookIsCoresSentenceFromItsRecord() {
        val kin = candidate
        val weapon = run.weapons.values.first()
        val hooks = listOf(
            Hook(HookKind.GRUDGE, 1, factionId = FactionId("ashclaw_raiders"), background = true),
            Hook(HookKind.DEBT, 1, creditor = "a moneylender in Emberfall", amount = 40, dueDay = 9),
            Hook(HookKind.LOST_WEAPON, 1, weaponId = weapon.id, background = true),
            Hook(HookKind.OATH, 1, oath = OathKind.PLAIN_WORK), Hook(HookKind.OATH, 1, oath = OathKind.NO_GRAVE),
            Hook(HookKind.OATH, 1, oath = OathKind.FAVOURED_FAMILY, familyId = LaunchContent.SPEAR),
            Hook(HookKind.KIN, 1, kinHeroId = kin.id),
        )
        for (hook in hooks) {
            val hero = member.hooked(hook)
            val said = engine.hookSentence(run, hero)
            assertEquals(LivingText.hook(hero, run, engine.content), said)
            assertTrue("$said is one sentence", said!!.endsWith(".") && " ;" !in said)
        }
        assertEquals("Kin of ${kin.fullName}.", engine.hookSentence(run, member.hooked(Hook(HookKind.KIN, 1, kinHeroId = kin.id))))
        // Settled: only what was recorded about it, never an invented ending.
        assertEquals("Paid in full.", engine.hookSentence(run, member.hooked(Hook(HookKind.DEBT, 1, status = HookStatus.RESOLVED, amount = 40, note = "Paid in full."))))
        assertNull(engine.hookSentence(run, member.hooked(null)))
    }

    @Test
    fun theSheetOpensWithIdentityThenTheStoryThenMomentsNewestFirst() {
        val oath = member.hooked(Hook(HookKind.OATH, 1, oath = OathKind.NO_GRAVE)).copy(
            ambition = Ambition.NAME,
            reasons = listOf(ReasonRecord(3, "deed", "Held the gate with a borrowed spear."), ReasonRecord(6, "loan", "Took the Iron Sword on loan.")),
        )
        val d = engine.heroDetail(run.with(oath), oath.id)!!
        // The pressing line first, the background last.
        assertEquals(listOf("Oath", "Ambition", "Background"), d.story.map { it.label })
        assertEquals("Former poacher", d.story.last().value)
        assertEquals(LivingText.hook(oath, run, engine.content), d.story.first().value)
        assertEquals("what they carry is in the head", run.equippedWeapon(oath.id)?.let { "Carries ${it.name}" } ?: "Unarmed", d.equipment)
        assertEquals(listOf("Day 6: Took the Iron Sword on loan.", "Day 3: Held the gate with a borrowed spear."), d.moments)
        assertTrue("a well member's numbers say their condition", d.condition == null)

        // A hero without the living record (an era before it) shows no new lines at all.
        val legacy = member.copy(raceId = null, cultureId = null, backgroundId = null, hook = null, ambition = null, reasons = emptyList())
        val plain = engine.heroDetail(run.with(legacy), legacy.id)!!
        assertTrue(plain.story.isEmpty())
        assertTrue(plain.moments.isEmpty())
        // A newcomer's lifetime numbers are all zero and say nothing; any that are not are said alone.
        assertNull(engine.heroDetail(run.with(legacy.copy(fame = 0, victories = 0, kills = 0)), legacy.id)!!.deeds)
        assertEquals("2 victories", engine.heroDetail(run.with(legacy.copy(fame = 0, victories = 2, kills = 0)), legacy.id)!!.deeds)
    }

    @Test
    fun theCaptainsAmbitionIsSaidPlainlyWithTheMembersCount() {
        val captain = member.copy(ambition = Ambition.CAPTAIN, ambitionDone = false)
        val led = run.copy(guild = run.guild!!.copy(members = run.guild!!.members.map { if (it.heroId == captain.id) it.copy(ledWins = 1) else it }))
        val d = engine.heroDetail(led.with(captain), captain.id)!!
        assertEquals(LivingText.captain(captain, led.guild!!.member(captain.id)), d.story.single { it.label == "Ambition" }.value)
    }

    @Test
    fun aBynameOfResidenceIsSplitOffAndNoOtherSurname() {
        val by = engine.content.bynames.first()
        assertEquals("Tansy Saffronfield" to by, engine.nameParts("Tansy Saffronfield $by"))
        assertEquals("Tansy" to by, engine.nameParts("Tansy $by"))
        assertEquals("Keshra Obsidianhorn" to null, engine.nameParts("Keshra Obsidianhorn"))
        assertEquals("a name that is only a byname stays whole", by to null, engine.nameParts(by))
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
        assertEquals(LivingText.hook(sworn, state, engine.content), row.complication)
        assertTrue(row.sub, row.sub.startsWith("Elf "))
        val tip = engine.heroTip(state, sworn.id)!!
        assertTrue(tip.kind, tip.kind.startsWith("Elf "))
        assertTrue(tip.sections.flatMap { it.lines }.any { it.text == row.complication })
        // No Hook, no line.
        assertNull(engine.guildUi(run.with(candidate.copy(hook = null)))!!.candidates.single { it.heroId == candidate.id }.complication)
    }
}
