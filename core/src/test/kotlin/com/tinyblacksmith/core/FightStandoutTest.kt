package com.tinyblacksmith.core

import com.tinyblacksmith.core.combat.Contribution
import com.tinyblacksmith.core.combat.Fight
import com.tinyblacksmith.core.combat.FightAccount
import com.tinyblacksmith.core.combat.FightAccounting
import com.tinyblacksmith.core.combat.Share
import com.tinyblacksmith.core.combat.Standout
import com.tinyblacksmith.core.combat.StandoutKind
import com.tinyblacksmith.core.guild.Missions
import com.tinyblacksmith.core.sim.CombatSandbox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The one contribution a siege card may show (`FightAccounting.standout`): a pure choice among the account's own shares,
 * so it is the same every time it is built, never invents a hand for shared work, and never names a blade the defender
 * did not carry.
 */
class FightStandoutTest {
    private fun share(rule: String?, name: String = rule ?: FightAccounting.OWN, weapon: Boolean = false, damage: Int = 0, prevented: Int = 0, healed: Int = 0, applied: Int = 0, times: Int = 0) =
        Share(rule, name, weapon, damage, prevented, healed, applied, times)

    private fun member(key: String, vararg shares: Share, weapon: String? = "$key's blade", downed: Boolean = false) =
        Contribution(key, key.replaceFirstChar { it.uppercase() }, weapon, weapon?.let { "w-$key" }, shares.sumOf { it.damage }, shares.sumOf { it.prevented }, shares.sumOf { it.healed }, shares.sortedByDescending { it.total }, downed)

    private fun account(vararg members: Contribution, shared: Share = share("#shared", "Shared")) =
        FightAccount(members.toList(), shared, members.sumOf { it.damage } + shared.damage, emptyList())

    @Test fun `a blade rule that did the most is the standout and reads as the report's first observation`() {
        val a = account(
            member("ash", share(null, damage = 30), share("t_edge", "Edge", weapon = true, damage = 12, times = 3)),
            member("bo", share("t_ward", "Ward", weapon = true, prevented = 18, times = 2)),
        )
        val s = assertNotNull(FightAccounting.standout(a))
        assertEquals(StandoutKind.WEAPON_RULE, s.kind)
        assertEquals("bo", s.memberKey); assertEquals("bo's blade", s.weapon); assertEquals("w-bo", s.weaponId)
        // Against the whole of that kind: the party's Guard took 18 here, so all of it is Bo's.
        assertEquals("Bo's bo's blade: Ward blocked 18 of the 18 damage the party's Guard took, in 2 activations.", s.text)
    }

    @Test fun `support competes by what the record counted, not by damage alone`() {
        val damage = account(member("ash", share("t_edge", "Edge", weapon = true, damage = 10)), member("bo", share("t_ward", "Ward", weapon = true, prevented = 9)))
        assertEquals("ash", FightAccounting.standout(damage)!!.memberKey)
        val support = account(member("ash", share("t_edge", "Edge", weapon = true, damage = 10)), member("bo", share("t_mend", "Mending", weapon = true, healed = 11)))
        val s = FightAccounting.standout(support)!!
        assertEquals("bo", s.memberKey); assertTrue("healed 11 of the 11 health the party mended" in s.text, s.text)
    }

    @Test fun `a tie goes to the defender listed first, whichever way the shares are ordered`() {
        val a = account(member("ash", share("t_a", "A", weapon = true, damage = 7)), member("bo", share("t_b", "B", weapon = true, prevented = 7)))
        assertEquals("ash", FightAccounting.standout(a)!!.memberKey)
        val b = account(member("bo", share("t_b", "B", weapon = true, prevented = 7)), member("ash", share("t_a", "A", weapon = true, damage = 7)))
        assertEquals("bo", FightAccounting.standout(b)!!.memberKey)
    }

    @Test fun `a blade that only put states on the enemy is the second tier`() {
        val a = account(member("ash", share(null, damage = 20), share("t_chill", "Chill Edge", weapon = true, applied = 4, times = 2)))
        val s = FightAccounting.standout(a)!!
        assertEquals(StandoutKind.WEAPON_STATES, s.kind)
        assertEquals("Ash's ash's blade: Chill Edge left 4 of the 4 stacks put on foes, in 2 activations.", s.text)
    }

    @Test fun `a defender's own part is named for the defender and never for a blade`() {
        val a = account(member("ash", share(null, damage = 24)), member("bo", share("kit_wall", "Shield Wall", prevented = 9)))
        val s = FightAccounting.standout(a)!!
        assertEquals(StandoutKind.HERO_PART, s.kind)
        assertEquals("ash", s.memberKey); assertNull(s.weapon); assertNull(s.weaponId)
        assertEquals("Ash's own moves dealt 24 of the 24 damage the enemy took.", s.text)
        // A kit rule of a defender who does carry a blade is still the defender's, not the blade's.
        val kit = FightAccounting.standout(account(member("bo", share("kit_wall", "Shield Wall", prevented = 9), weapon = "Oak Staff")))!!
        assertEquals(StandoutKind.HERO_PART, kit.kind); assertNull(kit.weapon)
        assertEquals("Bo: Shield Wall blocked 9 of the 9 damage the party's Guard took.", kit.text)
        // A small part says so: against the damage the whole party and the shared work made, not alone.
        val small = account(member("ash", share("t_edge", "Edge", weapon = true, damage = 16)), member("bo", share(null, damage = 108)), shared = share("#shared", "Shared", damage = 69))
        assertEquals("Ash's ash's blade: Edge dealt 16 of the 193 damage the enemy took.", FightAccounting.standout(small)!!.text)
    }

    @Test fun `work the record cannot give to one hand is never a standout`() {
        val a = account(member("ash"), shared = share("#shared", "Shared", damage = 40, prevented = 12, healed = 5))
        assertNull(FightAccounting.standout(a))
        // A blade that fired with nothing counted is not a contribution either.
        assertNull(FightAccounting.standout(account(member("ash", share("t_edge", "Edge", weapon = true, times = 3)))))
        assertNull(FightAccounting.standout(account()))
    }

    @Test fun `selection reads only the account, so the same account always gives the same standout`() {
        val a = account(member("ash", share("t_edge", "Edge", weapon = true, damage = 12)), member("bo", share("t_ward", "Ward", weapon = true, prevented = 12)))
        val first = FightAccounting.standout(a)
        repeat(5) { assertEquals(first, FightAccounting.standout(a)) }
    }

    @Test fun `over every fixture fight the standout is a share the account holds, and its first tier is the report's first blade`() {
        val tiers = mutableSetOf<StandoutKind>()
        for (f in CombatSandbox.fixtures) {
            val r = Missions.compact(Fight.resolve(f.setup))
            val account = FightAccounting.of(r)!!
            val s: Standout? = FightAccounting.standout(account)
            assertEquals(s, FightAccounting.standout(FightAccounting.of(r)!!), f.id)
            if (s == null) { assertTrue(account.members.all { m -> m.shares.none { it.total > 0 || it.applied > 0 } }, "${f.id}: nothing counted, so nothing to pick"); continue }
            tiers += s.kind
            val m = account.members.single { it.key == s.memberKey }
            assertTrue(s.share in m.shares, f.id)
            assertEquals(m.name, s.memberName, f.id)
            // The blade is named only when the share is the blade's rule, and then it is the blade the record put in their hands.
            if (s.kind == StandoutKind.HERO_PART) assertNull(s.weapon, f.id) else { assertTrue(s.share.weapon, f.id); assertEquals(m.weapon, s.weapon, f.id); assertEquals(m.weaponId, s.weaponId, f.id) }
            // The blade rule is the one the report's first observation names, and the line says how much of the fight it was.
            if (s.kind == StandoutKind.WEAPON_RULE) {
                assertTrue(account.observations.first().startsWith("${s.memberName}'s ${s.weapon ?: "weapon"}: ${s.share.name} "), f.id)
                assertTrue(" of the " in s.text, "${f.id}: ${s.text}")
            }
            // Never more than the account counted for that defender.
            assertTrue(s.share.total <= m.damage + m.prevented + m.healed, f.id)
        }
        assertTrue(StandoutKind.WEAPON_RULE in tiers, "the fixtures reach a blade rule: $tiers")
    }
}
