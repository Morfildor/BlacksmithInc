package com.example.blacksmithproject

import com.example.blacksmithproject.data.SaveFailure
import com.example.blacksmithproject.ui.loadFailureText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The recovery screen says what Start over really sets aside and what the player really loses (review I1, I5, m5). */
class LoadFailureTextTest {
    private val cause = IllegalStateException("x")

    @Test
    fun aLegacyRowBesideASoundRunCostsNothingAndTheWordsSaySo() {
        for (f in listOf(SaveFailure.Corrupt("legacy", cause, runSound = true), SaveFailure.Newer("legacy", 3, 2, runSound = true))) {
            val text = loadFailureText(f)
            val over = text.startOver!!
            assertTrue(text.safe, "Your current run can still be read" in text.safe)
            assertEquals("Rebuild the legacy from the run", over.button)
            assertTrue(over.does, "The run goes on." in over.does)
            assertFalse("the run is not set aside", "run aside" in over.does || "era 1" in over.does || "era 1" in over.confirm)
        }
    }

    @Test
    fun aLegacyRowWithNoSoundRunNamesThePermanentLoss() {
        for (f in listOf(SaveFailure.Corrupt("legacy", cause), SaveFailure.Newer("legacy", 3, 2))) {
            val over = loadFailureText(f).startOver!!
            assertTrue(over.does, "no points, upgrades or legends" in over.does)
            assertTrue(over.confirm, "your points, upgrades and legends and that run will be gone from the game" in over.confirm)
        }
    }

    @Test
    fun aDamagedFileIsNeverCalledSafeToStartOverFrom() {
        val text = loadFailureText(SaveFailure.FileDamaged(cause))
        assertEquals("Nothing has been deleted or changed.", text.safe)
        assertTrue(text.startOver!!.confirm, "your run and your points, upgrades and legends will be gone from the game" in text.startOver!!.confirm)
        assertTrue("set aside, not deleted" in text.startOver!!.confirm)
    }

    @Test
    fun aRunRowKeepsTheLegacyAndAStorageErrorOffersNoStartOver() {
        val run = loadFailureText(SaveFailure.Corrupt("run", cause))
        assertTrue("is safe" in run.safe && "Your legacy is kept." in run.startOver!!.does)
        assertNull(loadFailureText(SaveFailure.Io(cause)).startOver)
    }

    /** A run refused only because its rules or content are newer is told to update, like a newer save format. */
    @Test
    fun aRunFromNewerRulesSaysUpdateTheGame() {
        val newerRules = loadFailureText(SaveFailure.Incompatible(listOf("Run uses rules 9, newer than this build's 2")))
        assertEquals("This save is from a newer version", newerRules.title)
        assertTrue("Update the game" in newerRules.happened)
        assertTrue(newerRules.startOver!!.confirm.endsWith("even after updating."))
        assertEquals("This version cannot continue the saved run", loadFailureText(SaveFailure.Incompatible(listOf("Negative gold"))).title)
    }
}
