package com.example.blacksmithproject

import com.example.blacksmithproject.data.SaveDao
import com.example.blacksmithproject.data.SaveEntity
import com.example.blacksmithproject.data.SaveFailure
import com.example.blacksmithproject.data.SaveStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Whatever the database layer throws leaves the store as a SaveFailure, so the session can show it instead of crashing. */
class SaveStoreFailureTest {
    private class ThrowingDao(private val error: Exception) : SaveDao() {
        override suspend fun get(key: String): SaveEntity? = throw error
        override suspend fun getAll(keys: List<String>): List<SaveEntity> = throw error
        override suspend fun upsert(entity: SaveEntity) = throw error
        override suspend fun delete(key: String) = throw error
        override suspend fun rename(from: String, to: String) = throw error
    }

    private suspend fun everyCall(store: SaveStore): List<Throwable?> = listOf<suspend () -> Unit>(
        { store.load() }, { store.commit("run", "legacy") }, { store.commit(null, "legacy") }, { store.saveCursor("cursor") }, { store.saveCursor(null) }, { store.quarantine("run") },
    ).map { call -> runCatching { call() }.exceptionOrNull() }

    /** Room reports a missing migration or a failed identity check as IllegalStateException, not as an SQLException. */
    @Test
    fun anyExceptionFromTheDatabaseBecomesAStorageFailure() = runTest {
        for (error in listOf(IllegalStateException("A migration from 1 to 2 was required but not found."), RuntimeException("anything else"))) {
            for (thrown in everyCall(SaveStore(ThrowingDao(error)))) {
                assertTrue("got $thrown", thrown is SaveFailure.Io)
                assertSame(error, thrown!!.cause)
            }
        }
    }

    @Test
    fun cancellationIsNeverTurnedIntoAFailure() = runTest {
        val cancelled = CancellationException("the scope went away")
        for (thrown in everyCall(SaveStore(ThrowingDao(cancelled)))) {
            if (thrown !== cancelled) fail("got $thrown")
        }
    }
}
