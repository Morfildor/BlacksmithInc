package com.example.blacksmithproject

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.blacksmithproject.data.SaveDatabase
import com.example.blacksmithproject.data.SaveEntity
import com.example.blacksmithproject.data.SaveFailure
import com.example.blacksmithproject.data.SaveStore
import com.example.blacksmithproject.data.StoredRows
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.acceptedOrThrow
import com.tinyblacksmith.core.model.CommandId
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.persistence.SaveCodec
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Room save/restore: run + legacy persist together and restore byte-equal state (GDD 15.1). */
@RunWith(AndroidJUnit4::class)
class SaveStoreTest {
    private val engine = GameEngine()

    private fun store(): SaveStore {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, SaveDatabase::class.java).build()
        return SaveStore(db.saveDao())
    }

    @Test
    fun savesAndRestoresRunAndLegacyTogether() = runBlocking {
        val store = store()
        assertNull(store.loadRun())
        assertEquals(LegacyProfile(), store.loadLegacy())

        var state = engine.newRun(LegacyProfile(), 42)
        state = engine.handle(state, Command.EndDay(CommandId("${state.runId.value}:day1"))).acceptedOrThrow().state
        store.saveAtomically(state, state.legacy)
        assertEquals(state, store.loadRun())
        assertEquals(state.legacy, store.loadLegacy())

        // Continuing from the restored state yields the same outcome as continuing from memory.
        val restored = store.loadRun()!!
        val a = engine.handle(state, Command.EndDay(CommandId("${state.runId.value}:day2"))).acceptedOrThrow().state
        val b = engine.handle(restored, Command.EndDay(CommandId("${state.runId.value}:day2"))).acceptedOrThrow().state
        assertEquals(a, b)

        // Clearing the run keeps the legacy.
        val legacy = LegacyProfile(points = 9)
        store.saveAtomically(null, legacy)
        assertNull(store.loadRun())
        assertEquals(legacy, store.loadLegacy())
    }

    /** A row that does not decode is reported by the session and left exactly as stored; starting over renames it, never deletes it. */
    @Test
    fun corruptPayloadIsReportedAndTheRowIsUntouched() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, SaveDatabase::class.java).build()
        val dao = db.saveDao()
        val store = SaveStore(dao)
        val legacy = LegacyProfile(points = 9)
        store.saveAtomically(engine.newRun(legacy, 42), legacy)
        val garbage = "this is not a save {{{"
        dao.upsert(SaveEntity(SaveStore.KEY_RUN, 1, garbage, 123L))
        store.saveCursor("stale")

        val session = GameSession(engine, store)
        val failure = (session.load() as GameSession.Result.Failed).failure
        assertTrue(failure is SaveFailure.Corrupt && failure.key == SaveStore.KEY_RUN)
        assertEquals(SaveEntity(SaveStore.KEY_RUN, 1, garbage, 123L), dao.get(SaveStore.KEY_RUN))
        assertNull(session.snapshot.value)

        assertTrue(session.startOverKeepingBackup() is GameSession.Result.Done)
        assertEquals(StoredRows(null, SaveCodec.encodeLegacy(legacy), null), store.load())
        val backup = dao.getAll(listOf(SaveStore.KEY_RUN, SaveStore.KEY_LEGACY, SaveStore.KEY_CURSOR)).map { it.key }
        assertEquals(listOf(SaveStore.KEY_LEGACY), backup)
        db.openHelper.readableDatabase.query("SELECT payload FROM saves WHERE `key` LIKE 'run.bak.%'").use { rows ->
            assertEquals("exactly one backup row", 1, rows.count)
            rows.moveToFirst()
            assertEquals("the unreadable row is still in the table under its backup key", garbage, rows.getString(0))
        }
        assertEquals(legacy, session.snapshot.value?.legacy)
    }
}
