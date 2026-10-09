package com.example.blacksmithproject

import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.persistence.SaveCodec
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FakeGameRepositoryTest {
    @Test
    fun aFixedSeedRunSurvivesACommitAndALoadAndClearingTheRunKeepsTheLegacy() = runTest {
        val repo = FakeGameRepository()
        assertEquals(null, repo.load().run)

        val run = GameEngine().newRun(LegacyProfile(), 42L)
        repo.commit(SaveCodec.encodeRun(run), SaveCodec.encodeLegacy(run.legacy))
        assertEquals(run, SaveCodec.decodeRun(repo.load().run!!))
        assertEquals(run.legacy, SaveCodec.decodeLegacy(repo.load().legacy!!))

        val legacy = LegacyProfile(points = 9)
        repo.commit(null, SaveCodec.encodeLegacy(legacy))
        assertNull(repo.load().run)
        assertEquals(legacy, SaveCodec.decodeLegacy(repo.load().legacy!!))
        assertEquals(2, repo.commitCount)
    }
}
