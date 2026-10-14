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
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.persistence.DayCursor
import com.tinyblacksmith.core.persistence.SaveCodec
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
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

    // Decoding without admission is for this test only; the game reads the store through GameSession.
    private suspend fun SaveStore.loadRun(): GameState? = load().run?.let { SaveCodec.decodeRun(it) }
    private suspend fun SaveStore.loadLegacy(): LegacyProfile = load().legacy?.let { SaveCodec.decodeLegacy(it) } ?: LegacyProfile()
    private suspend fun SaveStore.saveAtomically(run: GameState?, legacy: LegacyProfile) = commit(run?.let { SaveCodec.encodeRun(it) }, SaveCodec.encodeLegacy(legacy))

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

    /** The shop-day cursor is its own row: writing it never touches the save, and saving never touches it. */
    @Test
    fun theCursorRowLivesBesideTheSaveAndNeitherTouchesTheOther() = runBlocking {
        val store = store()
        val state = engine.newRun(LegacyProfile(), 42)
        store.saveAtomically(state, state.legacy)
        val saved = store.load()
        assertNull(saved.cursor)

        val cursor = DayCursor("${state.runId.value}:day1", DayCursor.Stage.COUNTER, 2).encode()
        store.saveCursor(cursor)
        assertEquals(saved.copy(cursor = cursor), store.load())
        assertEquals(DayCursor("${state.runId.value}:day1", DayCursor.Stage.COUNTER, 2), DayCursor.decode(store.load().cursor!!))

        val next = engine.handle(state, Command.EndDay(CommandId("${state.runId.value}:day1"))).acceptedOrThrow().state
        store.saveAtomically(next, next.legacy)
        assertEquals("a commit leaves the cursor row alone", cursor, store.load().cursor)
        store.saveCursor(null)
        assertEquals(StoredRows(SaveCodec.encodeRun(next), SaveCodec.encodeLegacy(next.legacy), null), store.load())
    }

    /**
     * Review 2026-10-14, phase 2 item 1: a row past SQLite's 2 MB cursor window. One written by a build before saves were
     * split (raw, through the DAO) is cut into parts inside SQLite on the next load and comes back whole; a new save of
     * that size is written in parts from the start; and a session loads it.
     */
    @Test
    fun aRowPastTheCursorWindowIsSplitInsideSqliteAndLoads() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, SaveDatabase::class.java).build()
        val dao = db.saveDao()
        val store = SaveStore(dao)
        // A real envelope made long, 2.5 MB, by world flags no rule reads: a load of it as one row throws "Row too big".
        val legacy = LegacyProfile(points = 3)
        val base = engine.newRun(legacy, 42)
        val padded = base.copy(worldFlags = base.worldFlags + (1..10_000).associate { "pad_${it}_" + "x".repeat(250) to it })
        val runText = SaveCodec.encodeRun(padded)
        assertTrue("the scenario: the row is past the window (${runText.length})", runText.length > 2_500_000)
        dao.upsert(SaveEntity(SaveStore.KEY_RUN, SaveCodec.SCHEMA_VERSION, runText, 1L))
        dao.upsert(SaveEntity(SaveStore.KEY_LEGACY, SaveCodec.SCHEMA_VERSION, SaveCodec.encodeLegacy(legacy), 1L))

        assertEquals(runText, store.load().run)
        db.openHelper.readableDatabase.query("SELECT max(length(payload)) FROM saves").use { c -> c.moveToFirst(); assertTrue(c.getInt(0) <= SaveStore.CHUNK) }
        assertTrue(GameSession(engine, store).load() is GameSession.Result.Done)

        // Written fresh at that size: parts from the start, read back whole.
        store.commit(runText, SaveCodec.encodeLegacy(legacy))
        assertEquals(runText, store.load().run)
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

    /**
     * Review I5: a database FILE that SQLite calls corrupt is not deleted by opening it. It is reported, left byte for
     * byte, and moved to "<name>.corrupt.<millis>" only by Start over, after which the game has an empty, working save.
     */
    @Test
    fun aCorruptDatabaseFileIsKeptAndOnlyStartOverMovesItAside() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "corrupt_file_test.db"
        val file = context.getDatabasePath(name)
        val dir = file.parentFile!!
        fun leftovers() = dir.listFiles { f -> f.name.startsWith(name) }!!.toList()
        leftovers().forEach { it.delete() }
        dir.mkdirs()
        val garbage = ByteArray(20_000) { (it * 31 + 7).toByte() }
        try {
            file.writeBytes(garbage)
            val session = GameSession(engine, SaveStore.create(context, name))

            val failure = (session.load() as GameSession.Result.Failed).failure
            assertTrue("got $failure: ${failure.cause}", failure is SaveFailure.FileDamaged)
            assertArrayEquals("opening a damaged file leaves it byte for byte", garbage, file.readBytes())
            assertTrue((session.retry() as GameSession.Result.Failed).failure is SaveFailure.FileDamaged)
            assertArrayEquals(garbage, file.readBytes())

            assertTrue(session.startOverKeepingBackup() is GameSession.Result.Done)
            val aside = leftovers().single { it.name.matches(Regex(Regex.escape(name) + "\\.corrupt\\.\\d+")) }
            assertArrayEquals("the damaged file is kept under its backup name", garbage, aside.readBytes())
            assertEquals(GameSession.Snapshot(null, LegacyProfile(), null), session.snapshot.value)

            // The new file is a working save: a run begun now is there for the next process.
            assertTrue(session.run(GameSession.Op.BeginEra(7L, null)) is GameSession.Result.Done)
            val again = GameSession(engine, SaveStore.create(context, name))
            assertTrue(again.load() is GameSession.Result.Done)
            assertEquals(session.snapshot.value?.run, again.snapshot.value?.run)
            assertArrayEquals(garbage, aside.readBytes())
        } finally {
            leftovers().forEach { it.delete() }
        }
    }
}
