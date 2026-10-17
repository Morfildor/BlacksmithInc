package com.tinyblacksmith.core

import com.tinyblacksmith.core.text.LegacyProse
import com.tinyblacksmith.core.text.asSentence
import com.tinyblacksmith.core.text.joinSentences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Old stored prose is shown in the new voice without being changed, and new prose passes through untouched. */
class LegacyProseTest {
    private val dash = "—"

    @Test
    fun knownOldJournalLinesBecomeTheNewOnes() {
        assertEquals("Pairing learned. Iron + Ember Resin. Excellent match.", LegacyProse.display("Journal: Iron + Ember Resin is now understood $dash excellent match."))
        assertEquals("First notes on Ember Resin on Spears. Faint harmony.", LegacyProse.display("Journal: Ember Resin on Spears observed $dash faint harmony."))
        assertEquals(
            "Notes on Silver + Stormglass Sword. Hides something more. It wants a hotter fire.",
            LegacyProse.display("Journal: Silver + Stormglass Sword $dash Hides something more; it wants a hotter fire."),
        )
    }

    @Test
    fun aRewrittenLineAlwaysEndsWithAPeriod() {
        assertEquals("First notes on Iron on Axes. Hides something more.", LegacyProse.display("Journal: Iron on Axes observed $dash hides something more"))
    }

    @Test
    fun anyOtherTextKeepsItsFactsAndLosesOnlyItsSeparators() {
        assertEquals(
            "Mira Vance routed Ashclaw scouts using Winterwake. Brought Starsteel back to the forge. Grew stronger (level 2).",
            LegacyProse.display("Mira Vance routed Ashclaw scouts using Winterwake; brought Starsteel back to the forge; grew stronger (level 2)."),
        )
        assertEquals("The raid was thrown back. The town paid 100 gold.", LegacyProse.display("The raid was thrown back $dash the town paid 100 gold"))
    }

    @Test
    fun namesTitlesAndAbbreviationsAreLeftAlone() {
        for (text in listOf(
            "Mr. Hale's Axe, 'Bane of the Raiders', is back in the forge.",
            "Mira Vance, a Ranger, arrived in Emberfall.",
            "Forged Iron Axe. Common. Quality 38.",
            "Their guild covered 30 gold. They paid 30 gold themselves.",
        )) assertEquals(text, LegacyProse.display(text))
    }

    @Test
    fun displayIsIdempotent() {
        for (text in listOf(
            "Journal: A is now understood $dash fine.", "One; two; three", "Plain sentence.", "A $dash B $dash C", "Ends without a period; and a clause",
        )) {
            val once = LegacyProse.display(text)
            assertEquals(once, LegacyProse.display(once), text)
        }
    }

    @Test
    fun theBesiegerIsReadFromTheSiegeRecordsOwnSentence() {
        assertEquals("the Ashclaw horde", LegacyProse.siegeAttacker("Emberfall held against the Ashclaw horde. Mira Vance, Bram Holt defended the walls."))
        assertEquals("the Ashclaw horde", LegacyProse.siegeAttacker("Emberfall repelled the Ashclaw horde! Champions: Mira Vance, Bram Holt."))
        assertEquals("the Hollowbound host", LegacyProse.siegeAttacker("The Hollowbound host overran the defenders (Mira Vance)."))
        assertNull(LegacyProse.siegeAttacker("Something else happened."))
    }

    @Test
    fun aStoredFightLogReadsInTodaysWords() {
        // The engine's own templates as saves keep them (debug scenarios and the v6 fixtures).
        assertEquals("Nixie's weapon (Bronze) deals 4 damage to The Pale Knight.", LegacyProse.fightLine("Nixie's Bronze rings back hits The Pale Knight for 4."))
        assertEquals("Ione's weapon (Storm) deals 2 damage to Gravecaller.", LegacyProse.fightLine("Ione's Arc hits Gravecaller for 2."))
        assertEquals("Ione's Discharge uses up the Mark on Embermaw whelp for 3 extra damage.", LegacyProse.fightLine("Ione's Charge burst hits the Mark on Embermaw whelp for 3 extra damage."))
        assertEquals("Ione's Discharge spends 3 Charge.", LegacyProse.fightLine("Ione's Charge burst spends 3 Charge."))
        assertEquals("Ione gains 3 Charge from the weapon (Silver) (now 4).", LegacyProse.fightLine("Ione gains 3 Charge from Silver holds a charge (now 4)."))
        assertEquals("Ione gains 2 Scrap from the weapon (Obsidian).", LegacyProse.fightLine("Ione gains 2 Scrap from Obsidian cracks."))
        assertEquals("Nixie's weapon (Fire) puts 1 Burn on The Pale Knight.", LegacyProse.fightLine("Nixie's Ember puts 1 Burn on The Pale Knight."))
        assertEquals("Nixie's weapon (Follow-up attack) deals 3 damage to Ashclaw scout.", LegacyProse.fightLine("Nixie's Off-hand cut hits Ashclaw scout for 3."))
        assertEquals("Ione deals 7 damage to Ashclaw brute.", LegacyProse.fightLine("Ione hits Ashclaw brute for 7."))
        assertEquals("Petra uses up the Mark on Embermaw drake for 3 extra damage.", LegacyProse.fightLine("Petra hits the Mark on Embermaw drake for 3 extra damage."))
        assertEquals("Ashclaw scout uses up 1 Chill, so this attack deals 35% less damage.", LegacyProse.fightLine("Ashclaw scout loses 1 Chill: this attack deals 35% less damage."))
        assertEquals("Ashclaw warchief: Heavy swing.", LegacyProse.fightLine("Ashclaw warchief: Cleave."))
        assertEquals("The Pale Knight: Undying blow.", LegacyProse.fightLine("The Pale Knight: Grave weapon."))
        assertEquals("The party is defeated.", LegacyProse.fightLine("The party is beaten."))
        // The status Guard is called Armor now, as a whole word: never "Guardian".
        assertEquals("Ione gains 3 Armor (now 5).", LegacyProse.fightLine("Ione gains 3 Guard (now 5)."))
        assertEquals("Embermaw drake's Armor breaks.", LegacyProse.fightLine("Embermaw drake's Guard breaks."))
        assertEquals("Salvage Bell gives Nixie 3 Armor.", LegacyProse.fightLine("Salvage Bell gives Nixie 3 Guard."))
        // Records older than replays.
        assertEquals("Vesna uses up the Mark on Ashclaw scout.", LegacyProse.fightLine("Vesna strikes the Mark on Ashclaw scout."))
        assertEquals("Ysolde takes 2 damage from Bleed.", LegacyProse.fightLine("Ysolde bleeds for 2."))
        assertEquals("Ysolde's weapon (Grave) puts Bleed on The Pale Knight.", LegacyProse.fightLine("Ysolde's Grave chill puts Bleed on The Pale Knight."))
        assertEquals("Ashclaw scout's Demoralized stores Chill (2).", LegacyProse.fightLine("Ashclaw scout's Rout stores Chill (2)."))
        // A highlight is its sentences, each read the same way.
        assertEquals(
            "Round 2: Ione: Hold the line. Ione deals 6 damage to Embermaw drake. Embermaw drake is down.",
            LegacyProse.fightHighlight("Round 2: Ione: Hold the line. Ione hits Embermaw drake for 6. Embermaw drake is down."),
        )
    }

    @Test
    fun weaponNamesAndNewLinesAreNeverTouched() {
        for (text in listOf(
            "Ione's Frostbound Obsidian Axe cracks.", "Mira's Ember Sword cracks.", "Bram's Guard Spear cracks.",
            "Ione's weapon (Bronze) deals 5 damage to Ashclaw scout.", "Mira gains 3 Armor from the weapon (Iron).", "Round 3.", "Spectral helper joins the party (Bone Music Box).",
            "Brann: Hold the line.", "Mira's Guardian's Iron Sword cracks.",
        )) assertEquals(text, LegacyProse.fightLine(text))
        for (text in listOf("Ione hits Ashclaw brute for 7.", "Nixie's Arc hits Gravecaller for 2.", "Ashclaw warchief: Cleave.")) {
            val once = LegacyProse.fightLine(text)
            assertEquals(once, LegacyProse.fightLine(once), text)
        }
        // The general prose adapter does not take part: a weapon name with an old word in it stays as stored.
        assertEquals("Forged Ember Arc Sword. Rare.", LegacyProse.display("Forged Ember Arc Sword. Rare."))
    }

    @Test
    fun aStoredRuleNameChangesOnlyForThatVeryRule() {
        assertEquals("Iron", LegacyProse.ruleName("core_iron", "Iron holds"))
        assertEquals("Discharge", LegacyProse.ruleName("charge_burst", "Charge burst"))
        assertEquals("Armor penetration", LegacyProse.ruleName("spear_reach", "Reach"))
        // One display name, two rules: only the old forging's ID reads as the old forging.
        assertEquals("Dragon oil (old forging)", LegacyProse.ruleName("catalyst_spreading", "Dragon oil"))
        assertEquals("Dragon oil", LegacyProse.ruleName("catalyst_spreading_fire", "Dragon oil"))
        assertEquals("Void ink (Armor)", LegacyProse.ruleName("catalyst_converted_guard", "Void ink (Guard)"))
        assertEquals("Avenger", LegacyProse.ruleName("trait_loyal", "Loyal"))
        // An old name under another ID is somebody's own name, not the old rule.
        assertEquals("Ember", LegacyProse.ruleName("some_trait", "Ember"))
        assertEquals("Riposte", LegacyProse.ruleName("guardian_riposte", "Riposte"))
        assertEquals(mapOf("element_frost" to "Frost", "affix_swift" to "Swift"), LegacyProse.ruleNames(mapOf("element_frost" to "Rime", "affix_swift" to "Swift")))
    }

    @Test
    fun aStoredSiegeReplayNoteReadsInTodaysWords() {
        assertEquals("attacks with Flaming Keen Starsteel Spear", LegacyProse.roundNote("strikes with Flaming Keen Starsteel Spear"))
        assertEquals("deals 5 damage to Ashclaw scout", LegacyProse.roundNote("hits Ashclaw scout for 5"))
        assertEquals("weapon (Bronze) deals 5 damage to The Pale Knight", LegacyProse.roundNote("Bronze rings back hits The Pale Knight for 5"))
        for (note in listOf("attacks with Ember Arc Sword", "holds the gate", "fights bare-handed", "met the Pale Knight with Bronze Bow")) assertEquals(note, LegacyProse.roundNote(note))
    }

    @Test
    fun clausesJoinAsSentencesWithOneCapitalAndOnePeriod() {
        assertEquals("Iron Axe.", "iron Axe..".asSentence())
        assertEquals("", "  ".asSentence())
        assertEquals("Suits their class. They need a weapon.", listOf("suits their class", "They need a weapon.", "").joinSentences())
        assertEquals("", emptyList<String>().joinSentences())
    }
}
