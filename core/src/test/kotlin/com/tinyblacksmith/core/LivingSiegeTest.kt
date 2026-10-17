package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.guildRun
import com.tinyblacksmith.core.content.ArmyKind
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.guild.Agency
import com.tinyblacksmith.core.guild.SiegeFight
import com.tinyblacksmith.core.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Final design 13.4 and 18.2 fixtures 15 to 18: attacks on the defences, sabotage of the charge, and the siege moments. */
class LivingSiegeTest {
    private val content = engine.content
    private val config = engine.config
    private val banner = FactionId("ashclaw_raiders")

    /** A guild era whose siege is tonight, against [units] at [percent] (strong enough to finish what they prepare). */
    private fun tonight(seed: Long, units: List<String>, armyId: String?, percent: Int = 400, revision: Int = LivingRevision.CURRENT): GameState =
        engine.guildRun(LegacyProfile(), seed, GuildContent.TOWNS_LAST_HOPE, livingRevision = revision).let { s ->
            s.copy(town = s.town.copy(nextSiegeDay = s.day), siege = SiegeScenario(s.day, factionId = banner, plan = units.map { EnemySpec(it, percent) }, armyId = armyId), encounter = null)
        }

    private fun resolve(s: GameState): ResolutionContext = ResolutionContext(s, content, config).also { SiegeFight.resolve(it) }
    private fun ResolutionContext.structural() = newEvents.filter { it.type == EventType.STRUCTURAL_DAMAGE }

    @Test fun `a completed charge costs the forge once, capped at 12, and a held siege with it is Held at a cost`() {
        var seen = 0
        for (seed in 1L..12L) {
            val s = tonight(seed, listOf("ib_hobgoblin_shield", "eng_battering_ram", "ib_goblin_sapper", "ib_ogre"), null)
            val ctx = resolve(s)
            val hit = ctx.structural().singleOrNull() ?: continue
            seen++
            val recorded = hit.data.getValue("recorded").toInt()
            val charged = hit.data.getValue("damage").toInt()
            assertEquals(minOf(config.living.structuralCapPerSiege, recorded), charged, "seed $seed")
            val report = ctx.guild!!.lastSiege!!
            assertTrue(report.forgeDamage >= minOf(charged, config.maxForgeDamagePerSiege), "seed $seed: the charge is part of the night's loss")
            assertEquals(s.town.integrity - report.forgeDamage, ctx.town.integrity, "charged exactly once")
            assertNotEquals(SiegeVerdict.HELD, report.verdict, "seed $seed")
        }
        assertTrue(seen > 0, "some night lets an engine or a sapper finish")
    }

    @Test fun `an era begun before living heroes never pays for a charge`() {
        for (seed in 1L..6L) assertTrue(resolve(tonight(seed, listOf("eng_battering_ram", "ib_goblin_sapper"), null, revision = LivingRevision.LEGACY)).structural().isEmpty())
    }

    @Test fun `sabotage keeps a breacher in the ranks at home, and spoils the charge of one that leads`() {
        val ranks = ResolutionContext(tonight(3, listOf("ib_hobgoblin_shield", "ib_goblin_skirmisher", "ib_goblin_sapper", "ib_goblin_skirmisher"), "army_ib_03"), content, config)
        SiegeFight.sabotage(ranks)
        assertFalse(ranks.siege!!.plan.any { it.unitId == "ib_goblin_sapper" })
        assertEquals(3, ranks.siege!!.plan.size)

        for (seed in 1L..8L) {
            val lead = ResolutionContext(tonight(seed, listOf("ib_ogre", "ib_goblin_skirmisher", "ib_war_drummer"), "army_ib_07"), content, config)
            SiegeFight.sabotage(lead)
            assertTrue(lead.siege!!.chargeSpoiled)
            assertEquals("ib_ogre", lead.siege!!.plan.first().unitId, "the headline still comes as reported")
            SiegeFight.resolve(lead)
            assertTrue(lead.structural().isEmpty(), "seed $seed: a spoiled charge never lands")
        }
    }

    @Test fun `the committed army is announced with its answers and only fields what the scouts report`() {
        val ctx = ResolutionContext(tonight(5, emptyList(), null).let { it.copy(town = it.town.copy(nextSiegeDay = it.day + 3), siege = SiegeScenario(it.day + 3, factionId = banner)) }, content, config)
        SiegeFight.plan(ctx)
        val army = GuildContentLookup.army(ctx.siege!!.armyId)
        assertEquals(army.members, ctx.siege!!.plan.map { it.unitId })
        val said = ctx.newEvents.single { it.type == EventType.SIEGE_PREPARATION }.text
        assertTrue(army.counters.all { it in said }, said)
    }

    @Test fun `a deserter at home describes the captain the scouts report`() {
        val s = tonight(7, emptyList(), null)
        val ctx = ResolutionContext(s, content, config)
        val id = s.guild!!.members.first().heroId
        ctx.updateHero(ctx.hero(id).copy(backgroundId = "bg_deserter"))
        val army = content.guild!!.armies.first { it.kind == ArmyKind.CAPTAIN }
        val line = assertNotNull(Agency.drill(ctx, army))
        val captain = army.members.map { content.combat!!.unit(it) }.first { com.tinyblacksmith.core.combat.Tags.CAPTAIN in it.tags }
        assertTrue(captain.name in line && ctx.hero(id).fullName in line, line)
        assertTrue(ctx.hero(id).reasons.any { it.kind == "drill" })
    }

    @Test fun `a member who watched a troll mend wants what stops it, and Silver stops a werewolf`() {
        val s = tonight(9, listOf("ib_troll", "ib_hobgoblin_shield", "ib_deserter"), "army_ib_08").let { it.copy(town = it.town.copy(nextSiegeDay = it.day + 2), siege = it.siege!!.copy(siegeDay = it.day + 2)) }
        val ctx = ResolutionContext(s, content, config)
        val m = s.guild!!.members.first()
        val hero = ctx.hero(m.heroId)
        ctx.updateHero(hero.copy(reasons = hero.reasons + ReasonRecord(s.day, "mend_seen", "saw it", listOf("ib_troll"))))
        val troll = content.combat!!.unit("ib_troll")
        val family = content.heroClass(hero.classId).preferredFamilies.first()
        val q = 50
        val frost = Weapon(WeaponId("w${s.nextWeaponSerial}"), "Test frost", family, LaunchContent.IRON, LaunchContent.FROST_BLOOM, null, ForgeMode.QUICK, Risk.BALANCED, q,
            com.tinyblacksmith.core.crafting.Forge.forgedRarity(q, config, content.material(LaunchContent.IRON).tier), com.tinyblacksmith.core.crafting.Forge.powerOf(content, config, family, LaunchContent.IRON, q, emptyList()),
            content.material(LaunchContent.FROST_BLOOM).element, emptyList(), emptyList(), WeaponLocation.Loaned(m.heroId), s.era, s.day, craft = com.tinyblacksmith.core.crafting.WeaponRules.CRAFT)
        ctx.updateWeapon(frost)
        assertFalse(Agency.stopsMending(frost, troll))
        assertEquals("mending", Agency.need(ctx, ctx.guild!!.member(m.heroId)!!))
        assertEquals(troll.id, Agency.mendingFoe(ctx, ctx.hero(m.heroId), frost)?.id)
        val wolf = content.combat!!.unit("tc_werewolf")
        val silver = frost.copy(coreId = LaunchContent.SILVER, element = null)
        assertTrue(Agency.stopsMending(silver, wolf))
        assertFalse(Agency.stopsMending(silver, troll), "only Fire stops a troll")
    }
}

private object GuildContentLookup {
    fun army(id: String?) = TestSupport.engine.content.guild!!.army(id)!!
}
