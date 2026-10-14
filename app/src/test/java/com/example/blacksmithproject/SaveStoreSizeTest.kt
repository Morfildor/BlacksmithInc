package com.example.blacksmithproject

import com.example.blacksmithproject.data.BrokenSplitRow
import com.example.blacksmithproject.data.RowLength
import com.example.blacksmithproject.data.SaveDao
import com.example.blacksmithproject.data.SaveEntity
import com.example.blacksmithproject.data.SaveFailure
import com.example.blacksmithproject.data.SaveStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        /** The key is the primary key: renaming onto one that exists fails, as SQLite's UNIQUE constraint does. */
        override suspend fun rename(from: String, to: String) {
            check(to !in rows) { "UNIQUE constraint failed: saves.key ($to)" }
            rows.remove(from)?.let { rows[to] = it.copy(key = to) }
        }
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
        /** What Room's generated @Transaction gives the DAO's own body: all of it, or the table as it was. */
        override suspend fun quarantine(from: String, to: String) {
            val before = LinkedHashMap(rows)
            try { super.quarantine(from, to) } catch (e: Exception) { rows.clear(); rows.putAll(before); throw e }
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

    private suspend fun corruptKey(store: SaveStore): String? = try { store.load(); null } catch (e: SaveFailure.Corrupt) {
        assertTrue("the cause names the broken split row: ${e.cause}", e.cause is BrokenSplitRow)
        e.key
    }

    /** A part that is missing or empty makes the row unreadable: the save is never put back together around the gap. */
    @Test
    fun aMissingOrEmptyPartIsReportedNotReadAsNothing() = runTest {
        val dao = MemoryDao()
        val store = SaveStore(dao)
        val legacy = payload(400)
        store.commit(payload(700_000), legacy)
        val second = dao.rows.remove(SaveStore.partKey(SaveStore.KEY_RUN, 2))!!
        assertEquals(SaveStore.KEY_RUN, corruptKey(store))
        dao.upsert(second.copy(payload = ""))
        assertEquals("an empty part is no part", SaveStore.KEY_RUN, corruptKey(store))
        dao.upsert(second)
        assertEquals(legacy, store.load().legacy)

        // The legacy is reported before the run, as the session reads them; a cursor that cannot be read is no cursor.
        store.commit(payload(700_000), payload(600_000))
        dao.rows.remove(SaveStore.partKey(SaveStore.KEY_RUN, 1)); dao.rows.remove(SaveStore.partKey(SaveStore.KEY_LEGACY, 1))
        assertEquals(SaveStore.KEY_LEGACY, corruptKey(store))
        val fresh = MemoryDao().also { it.upsert(SaveEntity(SaveStore.KEY_CURSOR, 7, "parts:2", 1L)) }
        assertNull(SaveStore(fresh).load().cursor)
    }

    @Test
    fun aHeadThatCountsNoPartsIsReported() = runTest {
        for (head in listOf("parts:", "parts:0", "parts:-1", "parts:+2", "parts:x", "parts:2x", "parts: 2", "parts:1000", "parts:99999999999")) {
            val dao = MemoryDao()
            dao.upsert(SaveEntity(SaveStore.KEY_RUN, 7, head, 1L))
            dao.upsert(SaveEntity(SaveStore.partKey(SaveStore.KEY_RUN, 1), 7, "{}", 1L))
            assertEquals(head, SaveStore.KEY_RUN, corruptKey(SaveStore(dao)))
        }
        assertNull("a payload kept whole has no count", SaveStore.parts("{\"parts:3\":1}"))
        assertEquals(3, SaveStore.parts("parts:3"))
        assertEquals(SaveStore.MAX_PARTS, SaveStore.parts("parts:${SaveStore.MAX_PARTS}"))
    }

    /**
     * Parts are cut between characters, never inside a surrogate pair: a supplementary character that would straddle
     * the boundary goes whole into the next part, and every part is well-formed text. Joined, the parts are the save.
     */
    @Test
    fun unicodeSurvivesThePartBoundariesExactly() = runTest {
        val emoji = "\uD83D\uDD25"   // U+1F525, two UTF-16 units
        fun wellFormed(t: String) = t.indices.all { i ->
            val c = t[i]
            when {
                Character.isHighSurrogate(c) -> i + 1 < t.length && Character.isLowSurrogate(t[i + 1])
                Character.isLowSurrogate(c) -> i > 0 && Character.isHighSurrogate(t[i - 1])
                else -> true
            }
        }
        // The pair starts on the last unit of the first part.
        val run = "{" + "é".repeat(SaveStore.CHUNK - 2) + emoji + "ß中\\n\"}".repeat(1_000) + emoji
        assertTrue(Character.isHighSurrogate(run[SaveStore.CHUNK - 1]))
        val dao = MemoryDao()
        val store = SaveStore(dao)
        store.commit(run, "{\"legacy\":\"$emoji\"}")
        val parts = dao.rows.filterKeys { it.startsWith(SaveStore.KEY_RUN + "#") }.values.map { it.payload }
        assertTrue(parts.all { it.length <= SaveStore.CHUNK && wellFormed(it) })
        assertEquals(SaveStore.CHUNK - 1, parts.first().length)
        assertEquals(run, store.load().run)
        assertEquals("{\"legacy\":\"$emoji\"}", store.load().legacy)

        // The cut itself, at small sizes, on every alignment of text full of pairs.
        val mixed = (0 until 300).joinToString("") { if (it % 3 == 0) emoji else if (it % 3 == 1) "a" else "ж" }
        for (size in 2..9) {
            val cut = SaveStore.chunks(mixed, size)
            assertEquals(mixed, cut.joinToString(""))
            assertTrue("size $size", cut.all { it.isNotEmpty() && it.length <= size && wellFormed(it) })
        }
    }

    /** Setting a row aside renames the head and every part in one transaction: when the head cannot move, no part has. */
    @Test
    fun aQuarantineThatFailsLeavesEveryPartWhereItWas() = runTest {
        val dao = MemoryDao()
        val store = SaveStore(dao)
        val runText = payload(700_000)
        store.commit(runText, payload(300))
        dao.upsert(SaveEntity("run.bak.1", 7, "an older backup", 1L))
        val before = LinkedHashMap(dao.rows)
        assertTrue(runCatching { dao.quarantine(SaveStore.KEY_RUN, "run.bak.1") }.isFailure)
        assertEquals("rolled back: the parts did not move without their head", before, dao.rows)
        assertFalse(dao.rows.keys.any { it.startsWith("run.bak.1#") })
        assertEquals(runText, store.load().run)
    }
}
