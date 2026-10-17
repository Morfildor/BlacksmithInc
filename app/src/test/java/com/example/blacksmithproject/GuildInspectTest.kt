package com.example.blacksmithproject

import com.example.blacksmithproject.ui.ChipTone
import com.example.blacksmithproject.ui.raceClass
import com.example.blacksmithproject.ui.brief
import com.example.blacksmithproject.ui.heroRules
import com.example.blacksmithproject.ui.FactTone
import com.example.blacksmithproject.ui.TipLine
import com.example.blacksmithproject.ui.TipStat
import com.example.blacksmithproject.ui.readsSupport
import com.tinyblacksmith.core.content.CombatContent
import com.example.blacksmithproject.ui.TipTone
import com.example.blacksmithproject.ui.TipUi
import com.example.blacksmithproject.ui.enemyTip
import com.example.blacksmithproject.ui.contractUi
import com.example.blacksmithproject.ui.guildUi
import com.example.blacksmithproject.ui.heroTip
import com.example.blacksmithproject.ui.kitLine
import com.example.blacksmithproject.ui.memberDetail
import com.example.blacksmithproject.ui.offerTip
import com.example.blacksmithproject.ui.statuses
import com.tinyblacksmith.core.combat.Stat
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.crafting.Bench
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.guild.Loadout
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.WeaponId
import com.tinyblacksmith.core.model.WeaponLocation
import com.tinyblacksmith.core.model.Wound
import com.tinyblacksmith.core.model.WoundKind
import com.tinyblacksmith.core.persistence.SaveCodec
import com.tinyblacksmith.core.sim.GuildBot
import com.tinyblacksmith.core.sim.GuildPolicy
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.model.CommandId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** V3 inspect cards of a guild run (`GuildInspect.kt`) against real engine states: heroes, enemies and quests. */
class GuildInspectTest {
    private val engine = GameEngine()
    private val combat = engine.content.combat!!

    private fun guildRun(seed: Long = 7): GameState = engine.guildRun(seed)
    private fun TipUi.lines(): List<TipLine> = sections.flatMap { it.lines }
    private fun TipUi.section(title: String): List<TipLine> = sections.first { it.title == title }.lines

    /** Nothing a card says is a blank heading: its name, its kind, every section title and every ability's name have words. */
    private fun assertNothingEmptyTitled(t: TipUi) {
        assertTrue(t.name.isNotBlank())
        assertTrue(t.kind.isNotBlank())
        t.sections.forEach { s -> s.title?.let { assertTrue(t.name, it.isNotBlank()) } }
        t.lines().forEach { l -> l.title?.let { assertTrue("${t.name}: $l", it.isNotBlank()) } }
        t.stats.forEach { assertTrue("${t.name}: $it", it.label.isNotBlank() && it.value.isNotBlank()) }
    }

    /** A copy of [s] with a hero of [classId] (an existing one of that class, else the first resident made one). */
    private fun withClass(s: GameState, classId: com.tinyblacksmith.core.model.HeroClassId): Pair<GameState, HeroId> {
        // A resident who is not up for signing: a hero of the town, not a candidate.
        val town = s.residents().filter { r -> r.isAlive && s.guild!!.candidates.none { it.heroId == r.id } }
        town.firstOrNull { it.classId == classId }?.let { return s to it.id }
        val h = town.first()
        return s.copy(heroes = s.heroes + (h.id to h.copy(classId = classId))) to h.id
    }

    @Test
    fun aMembersCardHasTheSheetsNumbersTheClassMoveTheTraitAsAGainAndTheShareAsACost() {
        val s = guildRun()
        val before = SaveCodec.encodeRun(s)
        for (m in s.guild!!.members) {
            val h = s.heroes.getValue(m.heroId)
            val tip = engine.heroTip(s, m.heroId)!!
            assertNothingEmptyTitled(tip)
            assertEquals(h.fullName, tip.name)
            assertEquals("${engine.raceClass(h)}, level\u00A0${h.level}", tip.kind)
            assertEquals("Guild member", tip.meta)
            // The same numbers as the hero sheet: one read of the fighter, `fighterView`.
            // Health says the fight's number and the health between fights (a percentage) on one line; Support is shown
            // only where an ability reads it, as what it does.
            val kit = combat.kitByClass.getValue(h.classId)
            assertEquals(engine.memberDetail(s, m.heroId)!!.numbers, tip.stats.map { it.label to it.value })
            assertTrue(tip.stats.first().value, tip.stats.first().label == "Health" && tip.stats.first().value.endsWith("(${h.health}%)"))
            assertEquals(readsSupport(kit.kit), tip.stats.any { it.label == "Support" })
            // What the class does every round leads, under its moves' names, then the class's own rules: on the quick
            // card each said short (its first sentence); the sheet's `heroRules` keeps them whole.
            val fight = tip.section("In a fight")
            assertEquals(kitLine(kit).brief(), fight.first())
            assertEquals(kitLine(kit), engine.heroRules(s, m.heroId).first.first())
            assertEquals(kit.kit.moves.joinToString(" and ") { it.name }, fight.first().title)
            assertEquals(kit.kit.passives.map { it.name }, fight.drop(1).map { it.title })
            // The trait is a gain (a mixed one, which also raises the share, carries no sign); the share is a cost, under Terms.
            val trait = engine.content.guild!!.trait(m.traitId)!!
            assertEquals(if (trait.shareDelta > 0) TipTone.ABILITY else TipTone.BONUS, tip.lines().single { it.title == trait.name }.tone)
            assertEquals(TipTone.DRAWBACK, tip.section("Terms").single { "${m.sharePercent}% of the gold" in it.text }.tone)
            // The card closes and opens the sheet; it is never a command and changes nothing.
            assertTrue(tip.actions.isEmpty())
        }
        assertEquals(before, SaveCodec.encodeRun(s))
    }

    @Test
    fun aClassPassiveIsAnAbilitySplitIntoWhenWhatAndHowOften() {
        val (s, id) = withClass(guildRun(), LaunchContent.GUARDIAN)
        val tip = engine.heroTip(s, id)!!
        // The full rule, as the sheet gives it; the quick card names it and keeps its short trigger and its limit.
        val (rules, words) = engine.heroRules(s, id)
        val riposte = rules.single { it.title == "Riposte" }
        assertEquals(riposte.brief(), tip.section("In a fight").single { it.title == "Riposte" })
        assertEquals(TipTone.ABILITY, riposte.tone)
        // Its typed trigger (Guard broken, by a foe) in a few words, standing for the sentence's own "When ..." clause.
        assertEquals("Enemy breaks Armor", riposte.trigger)
        assertEquals("Once per round", riposte.limit)
        assertEquals("Passive", riposte.source)
        // The class's own line does not say Riposte's counterattack again.
        assertFalse(rules.first().text, "hits back" in rules.first().text)
        assertTrue(words.map { it.name }.toString(), "Armor" in words.map { it.name })
        // The stat Attack is told apart from the action once; Support is said as what it does (Hold the line reads it).
        assertEquals("Attack", words.first().name)
        assertTrue(words.none { it.name == "Support" })
        assertEquals("${engine.fighterView(s, id)!!.support} Armor each round", tip.stats.single { it.label == "Support" }.value)
        assertEquals("The damage of this fighter's attacks.", words.first().meaning)
        // A quick card carries no glossary: the meanings stand beside the full rules.
        assertTrue(tip.keywords.isEmpty())
        // A resident who stands in for the guild is of the town, with no share and no trait.
        assertEquals("Of the town", tip.meta)
        assertTrue(tip.lines().none { it.tone == TipTone.DRAWBACK && "gold" in it.text })
    }

    @Test
    fun aBattlemagesCardExplainsTheStatusesItsKitNamesOnce() {
        val (s, id) = withClass(guildRun(), LaunchContent.BATTLEMAGE)
        val words = engine.heroRules(s, id).second
        val names = words.map { it.name }
        assertTrue(names.toString(), names.containsAll(listOf("Mark", "Charge", "Burn", "Chill", "Wet")))
        assertEquals(names.distinct(), names)
        assertTrue(words.all { it.meaning.isNotBlank() })
    }

    @Test
    fun aLaidUpMemberWarnsWithTheDayAndAKeptOneIsAConditionNotAWarning() {
        val fresh = guildRun()
        val g = fresh.guild!!
        val m = g.members.first()
        val hurt = fresh.copy(guild = g.copy(members = g.members.map { if (it.heroId == m.heroId) it.copy(wound = Wound(WoundKind.INJURED, fresh.day + 2, "A test.")) else it }))
        val tip = engine.heroTip(hurt, m.heroId)!!
        assertEquals("Injured until day ${fresh.day + 2}", tip.warning)
        val kept = fresh.copy(guild = g.copy(reserved = listOf(m.heroId)))
        val keptTip = engine.heroTip(kept, m.heroId)!!
        assertNull(keptTip.warning)
        assertTrue(keptTip.lines().contains(TipLine("Reserved for the wall", TipTone.CONDITION)))
        assertFalse(engine.heroTip(fresh, m.heroId)!!.lines().any { it.tone == TipTone.CONDITION && it.text == "Reserved for the wall" })
    }

    @Test
    fun theWeaponInHandIsNamedWithItsRarityAndTheNamesOfItsRules() {
        val s = guildRun()
        val m = s.guild!!.members.first()
        val own = s.equippedWeapon(m.heroId)?.id
        val unarmed = s.copy(weapons = s.weapons.filterValues { it.loanedTo != m.heroId && it.id != own })
        assertEquals(listOf(TipLine("Unarmed", TipTone.DRAWBACK)), engine.heroTip(unarmed, m.heroId)!!.section("Weapon"))
        val blade = Bench.demonstration(LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.FROST_BLOOM, null, engine.content, engine.config)
            .copy(id = WeaponId("w_inspect"), name = "Test sword", location = WeaponLocation.Loaned(m.heroId))
        val armed = unarmed.copy(weapons = unarmed.weapons + (blade.id to blade))
        val line = engine.heroTip(armed, m.heroId)!!.section("Weapon").first()
        assertEquals("Test sword", line.title)
        assertTrue(line.source, line.source!!.startsWith(blade.rarity.name.lowercase().replaceFirstChar { it.uppercase() }) && line.source!!.endsWith("on loan"))
        val f = engine.fighterView(armed, m.heroId)!!
        // A metal's or an element's effect is named after its source, so what it does is said beside it.
        val rules = f.effects.filter { it.id in f.weaponRules }.map { CombatContent.label(it.id, it.name) }
        assertEquals(rules.joinToString(", ").ifEmpty { "No effect beyond its normal attack" }, line.text)
        assertTrue(line.text, "Iron · Starts each fight with 3 Armor" in line.text && "Frost · Chills on hit" in line.text)
        // A worn weapon says so as a cost, at the wall's own threshold.
        val worn = armed.copy(weapons = armed.weapons + (blade.id to blade.copy(condition = engine.config.wornConditionThreshold - 1)))
        assertTrue(engine.heroTip(worn, m.heroId)!!.section("Weapon").any { it.tone == TipTone.DRAWBACK && it.text.startsWith("Worn") })
    }

    @Test
    fun aCandidatesCardSaysTheFeeAndShareAsCostsAndThatTheyWouldSign() {
        val s = guildRun()
        val c = s.guild!!.candidates.first()
        val tip = engine.heroTip(s, c.heroId)!!
        assertNothingEmptyTitled(tip)
        assertEquals("For hire", tip.meta)
        val terms = tip.section("Terms")
        assertEquals(TipLine("Recruitment fee ${c.fee} gold", TipTone.DRAWBACK), terms.first())
        assertTrue(terms.toString(), terms.single { "${c.sharePercent}% of the gold" in it.text }.tone == TipTone.DRAWBACK)
        val trait = engine.content.guild!!.trait(c.traitId)!!
        assertEquals(if (trait.shareDelta > 0) TipTone.ABILITY else TipTone.BONUS, tip.lines().single { it.title == trait.name }.tone)
    }

    @Test
    fun anEnemyCardIsTheCatalogsUnitAtTheFieldsScale() {
        val drake = combat.unit("ember_drake")
        val tip = enemyTip(drake, 2, listOf(150, 150), engine.config)
        assertNothingEmptyTitled(tip)
        assertEquals("2 on the field", tip.meta)
        assertEquals("Enemy · Elite", tip.kind)
        assertEquals("${Loadout.enemy(drake, "x", 150).maxHealth}", tip.stats.first { it.label == "Health" }.value)
        assertEquals(FactTone.GOOD, tip.stats.first { it.label == "Weak to" }.tone)
        assertEquals("Frost", tip.stats.first { it.label == "Weak to" }.value)
        assertEquals(FactTone.WARN, tip.stats.first { it.label == "Resists" }.tone)
        // Each move by name, with what the board says of it where the catalog says something.
        val breath = tip.section("Abilities").single { it.title == "Fire breath" }
        assertTrue(breath.text, breath.text.contains("130%") && breath.text.contains("no Burn"))
        assertEquals(drake.kit.moves.map { it.name }, tip.section("Abilities").map { it.title })
        assertTrue(tip.lines().contains(TipLine("Takes 50% less damage from Burn.", TipTone.PLAIN)))
        assertTrue(tip.keywords.map { it.name }.containsAll(listOf("Attack", "Armor", "Burn")))
        // The drake's abilities read no Support, so none is shown; the Gravecaller's healing does.
        assertTrue(tip.stats.none { it.label == "Support" })
        assertTrue(enemyTip(combat.unit("gravecaller"), 1, listOf(100), engine.config).stats.any { it.label == "Support" })
        // Units of different strength on one field: a range, never one invented number.
        val mixed = enemyTip(drake, 2, listOf(100, 150), engine.config)
        assertEquals("${Loadout.enemy(drake, "x", 100).maxHealth} to ${Loadout.enemy(drake, "x", 150).maxHealth}", mixed.stats.first { it.label == "Health" }.value)
        // A leader is said in words, as the weapon rules that answer it say it.
        assertEquals("Enemy · Leader · Elite", enemyTip(combat.unit("ashclaw_warchief"), 1, listOf(100), engine.config).kind)
        // A summoner is the first priority target, so the card says so.
        assertEquals("Enemy · Summoner · Leader · Elite", enemyTip(combat.unit("hollow_king"), 1, listOf(100), engine.config).kind)
        assertEquals(listOf(Stat.GUARD), drake.kit.moves.first { it.name == "Bite" }.statuses())
    }

    @Test
    fun theBoardsEnemiesCarryTheirCardAndTheirScaledHealth() {
        val s = guildRun()
        val offer = s.guild!!.offers.first { it.stages.first().enemies.isNotEmpty() }
        val sheet = engine.contractUi(s, offer.id, emptyList(), com.tinyblacksmith.core.combat.Posture.BALANCED)!!
        for ((i, stage) in offer.stages.withIndex()) {
            for (e in sheet.offer.stages[i].enemies) {
                val specs = stage.enemies.filter { it.unitId == e.unitId }
                assertEquals(specs.size, e.count)
                assertEquals(e.name, e.tip!!.name)
                val healths = specs.map { Loadout.enemy(combat.unit(it.unitId), "x", it.percent).maxHealth }
                assertEquals(if (healths.min() == healths.max()) "${healths.min()}" else "${healths.min()} to ${healths.max()}", e.health)
            }
        }
    }

    @Test
    fun aQuestCardLeadsWithTheDecidingFactsAndKeepsTheCostsAndTheMissedSiege() {
        val fresh = guildRun()
        val s = fresh.copy(town = fresh.town.copy(nextSiegeDay = fresh.day))
        val o = engine.guildUi(s)!!.offers.first()
        var opened = 0
        val tip = offerTip(o, onOpen = { opened++ })
        assertNothingEmptyTitled(tip)
        assertEquals(o.title, tip.name)
        assertEquals(listOf("Reward", "Danger", "Away", "Back", "Fee"), tip.stats.map { it.label }.take(5))
        assertEquals("day ${o.returnDay}", tip.stats.first { it.label == "Back" }.value)
        assertEquals(o.stages.size, tip.section("Stages").size)
        // The siege tonight is missed by any party sent today: the card says it too, as a cost.
        assertNotNull(o.missesSiege)
        assertTrue(tip.lines().contains(TipLine(o.missesSiege!!, TipTone.DRAWBACK)))
        assertTrue(o.failure.isNotBlank())
        assertTrue(tip.lines().contains(TipLine("If it fails: ${o.failure}", TipTone.DRAWBACK)))
        // One way on, to the party sheet: planning is a step of its own.
        assertEquals(listOf("Choose a party"), tip.actions.map { it.label })
        tip.actions.single().onClick()
        assertEquals(1, opened)
        assertTrue(offerTip(o).actions.isEmpty())
    }

    @Test
    fun eachDefendersChipIsTheWallsOwnReadinessCheck() {
        val bot = GuildBot(engine, GuildPolicy.ADAPTIVE)
        var s = guildRun(9)
        var checked = 0
        while (!s.isEnded && s.day <= 12) {
            val ui = engine.guildUi(s)!!
            for (d in ui.wall.defenders) {
                val w = engine.wallForecast(s)!!.defenders.first { it.first.id == d.heroId }.second
                val expected = when {
                    w == null -> "Unarmed" to ChipTone.OUT
                    w.condition < engine.config.wornConditionThreshold -> "Worn weapon" to ChipTone.HURT
                    else -> "Armed" to ChipTone.OK
                }
                assertEquals(expected, d.chip to d.chipTone)
                assertEquals(w == null, d.weaponArt == null)
                checked++
            }
            s = (engine.handle(bot.planDay(s), Command.EndDay(CommandId("${s.runId.value}:day${s.day}"))) as CommandOutcome.Accepted).state
        }
        assertTrue(checked > 0)
    }

    @Test
    fun theMembersSheetReadsLikeTheirCard() {
        val s = guildRun()
        val id = s.guild!!.members.first().heroId
        val d = engine.memberDetail(s, id)!!
        val h = s.heroes.getValue(id)
        val kit = combat.kitByClass.getValue(h.classId)
        assertEquals(kitLine(kit), d.abilities.first())
        val trait = engine.content.guild!!.trait(s.guild!!.member(id)!!.traitId)!!
        assertEquals(if (trait.shareDelta > 0) TipTone.ABILITY else TipTone.BONUS, d.gains.single { it.title == trait.name }.tone)
        assertEquals(TipTone.DRAWBACK, d.costs.single().tone)
        // The trait's own rules are its + line: the fight list does not say them twice.
        assertTrue(d.abilities.none { a -> trait.effects.any { it.name == a.title } })
        // Every rule the old list had is still read on the sheet, as an ability.
        val f = engine.fighterView(s, id)!!
        (f.kit.passives + f.effects).filter { e -> trait.effects.none { it.id == e.id } }.forEach { e -> assertTrue(e.name, d.abilities.any { it.title == e.name }) }
        assertTrue(d.since, d.since.startsWith("Signed on day") || d.since.startsWith("With the guild since day"))
        assertNull(d.status)
    }
}
