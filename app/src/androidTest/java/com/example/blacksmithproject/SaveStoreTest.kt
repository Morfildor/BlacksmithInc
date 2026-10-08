package com.example.blacksmithproject

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.blacksmithproject.data.SaveDatabase
import com.example.blacksmithproject.data.SaveStore
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.acceptedOrThrow
import com.tinyblacksmith.core.model.CommandId
import com.tinyblacksmith.core.model.LegacyProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
