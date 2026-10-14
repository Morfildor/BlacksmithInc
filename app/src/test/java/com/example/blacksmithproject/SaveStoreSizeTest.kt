package com.example.blacksmithproject

import com.example.blacksmithproject.data.RowLength
import com.example.blacksmithproject.data.SaveDao
import com.example.blacksmithproject.data.SaveEntity
import com.example.blacksmithproject.data.SaveStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A save past SQLite's 2 MB cursor window (review 2026-10-14, phase 2 item 1): new saves are split into rows that fit,
 * a row written too big before is split inside SQLite before anything reads it, and setting a row aside never reads it.
 * The SQL itself runs on a device (`SaveStoreTest`); here an in-memory table plays SQLite's rules, the window included.
 */
class SaveStoreSizeTest {
    /** SQLite's rules as the store meets them: `getAll` refuses a row past the cursor window; the other queries never read a payload into the app. */
    private class MemoryDao : SaveDao() {
        val rows = linkedMapOf<String, SaveEntity>()
        var reads = 0
        override suspend fun get(key: String): SaveEntity? = getAll(listOf(key)).firstOrNull()
        override suspend fun getAll(keys: List<String>): List<SaveEntity> = keys.mapNotNull { rows[it] }.onEach {
            reads++
            check(it.payload.length <= WINDOW) { "Row too big to fit into CursorWindow: ${it.key}" }
        }
        override suspend fun upsert(entity: SaveEntity) { rows[entity.key] = entity }
        override suspend fun delete(key: String) { rows.remove(key) }
        override suspend fun rename(from: String, to: String) { rows.remove(from)?.let { rows[to] = it.copy(key = to) } }
        override suspend fun lengths(keys: List<String>) = keys.mapNotNull { k -> rows[k]?.let { RowLength(k, it.payload.length) } }
        override suspend fun copyPart(key: String, part: String, from: Int, count: Int) {
            val row = rows[key] ?: return
            rows[part] = row.copy(key = part, payload = row.payload.drop(from - 1).take(count))   // substr is 1-based
        }
        override suspend fun setPayload(key: String, payload: String) { rows[key]?.let { rows[key] = it.copy(payload = payload) } }
        override suspend fun deleteParts(key: String) { rows.keys.removeAll { it.startsWith("$key#") } }
        override suspend fun renameParts(from: String, to: String) {
            rows.keys.filter { it.startsWith("$from#") }.forEach { k -> rows.remove(k)?.let { rows[to + k.removePrefix(from)] = it.copy(key = to + k.removePrefix(from)) } }
        }
    }

    private companion object {
        /** About the 2 MB window for one-byte characters. */
        const val WINDOW = 2 * 1024 * 1024 - 1
        fun payload(length: Int) = buildString { append('{'); while (this.length < length - 1) append("\"day\":${this.length % 997},"); setLength(length - 1); append('}') }
    }

    @Test
    fun aSaveLongerThanTheWindowIsWrittenInPartsAndReadBackWhole() = runTest {
        val dao = MemoryDao()
        val store = SaveStore(dao)
        val run = payload(2_600_000)
        val legacy = payload(1_000)
        store.commit(run, legacy)
        assertTrue("every row fits the window", dao.rows.values.all { it.payload.length <= SaveStore.CHUNK })
        assertEquals("parts:${(run.length + SaveStore.CHUNK - 1) / SaveStore.CHUNK}", dao.rows.getValue(SaveStore.KEY_RUN).payload)
        val rows = store.load()
        assertEquals(run, rows.run)
        assertEquals(legacy, rows.legacy)
        // A shorter save afterwards leaves no stale part behind.
        store.commit("{\"short\":1}", legacy)
        assertEquals(setOf(SaveStore.KEY_RUN, SaveStore.KEY_LEGACY), dao.rows.keys)
        assertEquals("{\"short\":1}", store.load().run)
    }

    @Test
    fun aRowWrittenTooBigBeforeIsSplitWithoutBeingReadAndThenLoads() = runTest {
        val dao = MemoryDao()
        val run = payload(2_500_000)
        // As a build before the split wrote it: one row, past the window.
        dao.upsert(SaveEntity(SaveStore.KEY_RUN, 7, run, 1L))
        dao.upsert(SaveEntity(SaveStore.KEY_LEGACY, 7, payload(500), 1L))
        val rows = SaveStore(dao).load()
        assertEquals("the row comes back whole", run, rows.run)
        assertTrue("and stays split", dao.rows.values.all { it.payload.length <= SaveStore.CHUNK })
        assertEquals(7, dao.rows.getValue(SaveStore.KEY_RUN + "#1").schemaVersion)
    }

    @Test
    fun settingARowAsideNeverReadsItAndTakesItsPartsAlong() = runTest {
        val dao = MemoryDao()
        val store = SaveStore(dao)
        store.commit(payload(700_000), payload(300))
        dao.reads = 0
        store.quarantine(SaveStore.KEY_RUN)
        assertEquals("nothing was read", 0, dao.reads)
        assertNull(dao.rows[SaveStore.KEY_RUN])
        assertTrue(dao.rows.keys.none { it.startsWith(SaveStore.KEY_RUN + "#") })
        val backup = dao.rows.keys.single { it.startsWith("run.bak.") && '#' !in it }
        assertEquals(3, dao.rows.keys.count { it.startsWith("$backup#") })
        assertNull(store.load().run)
    }
}
