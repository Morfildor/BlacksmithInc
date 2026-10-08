package com.example.blacksmithproject

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.blacksmithproject.ui.AffinityHint
import com.tinyblacksmith.core.content.SliceContent
import com.tinyblacksmith.core.model.Journal
import com.tinyblacksmith.core.model.KnowledgeState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The live forge hint shows the journal's descriptive text plus a knowledge-state icon and label (GDD 4.6, 12). */
@RunWith(AndroidJUnit4::class)
class ForgeHintTest {
    @get:Rule
    val compose = createComposeRule()

    private val key = Journal.coreAugmentKey(SliceContent.IRON, SliceContent.EMBER_RESIN)

    @Test
    fun unknownPairingShowsQuestionMarkAndUnknown() {
        compose.setContent { AffinityHint(Journal(), SliceContent.catalog, key) }
        compose.onNodeWithText("?").assertIsDisplayed()
        compose.onNodeWithText("Iron + Ember Resin: Unknown").assertIsDisplayed()
        compose.onNodeWithContentDescription("Iron + Ember Resin, unknown: Unknown").assertIsDisplayed()
    }

    @Test
    fun understoodPairingShowsFilledDotAndDescription() {
        val journal = Journal(interactions = mapOf(key to KnowledgeState.UNDERSTOOD))
        compose.setContent { AffinityHint(journal, SliceContent.catalog, key) }
        compose.onNodeWithText("●").assertIsDisplayed()
        compose.onNodeWithText("understood").assertIsDisplayed()
    }
}
