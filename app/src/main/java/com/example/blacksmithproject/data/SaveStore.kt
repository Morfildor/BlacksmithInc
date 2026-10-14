package com.example.blacksmithproject.data

import android.content.Context
import android.database.sqlite.SQLiteDatabaseCorruptException
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.tinyblacksmith.core.persistence.SaveCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** A split row whose head or parts do not add up. The store reports it as that row unreadable, never as a shorter save. */
class BrokenSplitRow(message: String) : Exception(message)

/** Authoritative saves live in Room; the payload is the versioned JSON envelope produced by the pure core. */
@Entity(tableName = "saves")
data class SaveEntity(
    @PrimaryKey val key: String,
    val schemaVersion: Int,
    val payload: String,
    val savedAt: Long,
)

/** A row's key and the length of its payload in characters, as SQLite counts it: read without reading the payload. */
data class RowLength(val key: String, val length: Int)

@Dao
abstract class SaveDao {
    @Query("SELECT * FROM saves WHERE `key` = :key")
    abstract suspend fun get(key: String): SaveEntity?

    @Query("SELECT * FROM saves WHERE `key` IN (:keys)")
    abstract suspend fun getAll(keys: List<String>): List<SaveEntity>

    @Upsert
    abstract suspend fun upsert(entity: SaveEntity)

    @Query("DELETE FROM saves WHERE `key` = :key")
    abstract suspend fun delete(key: String)

    /** Quarantine: the row keeps its payload under a backup key that no load reads. */
    @Query("UPDATE saves SET `key` = :to WHERE `key` = :from")
    abstract suspend fun rename(from: String, to: String)

    /** The length of each row's payload, computed by SQLite. A row too big for a cursor window is measured, never read. */
    @Query("SELECT `key` AS `key`, length(payload) AS length FROM saves WHERE `key` IN (:keys)")
    abstract suspend fun lengths(keys: List<String>): List<RowLength>

    /** Copies characters [from] (1-based) to [from] + [count] - 1 of [key]'s payload into the row [part], inside SQLite. */
    @Query("INSERT OR REPLACE INTO saves (`key`, schemaVersion, payload, savedAt) SELECT :part, schemaVersion, substr(payload, :from, :count), savedAt FROM saves WHERE `key` = :key")
    abstract suspend fun copyPart(key: String, part: String, from: Int, count: Int)

    @Query("UPDATE saves SET payload = :payload WHERE `key` = :key")
    abstract suspend fun setPayload(key: String, payload: String)

    /** The part rows of [key] ("key#1", "key#2", ...). */
    @Query("DELETE FROM saves WHERE `key` LIKE :key || '#%'")
    abstract suspend fun deleteParts(key: String)

    /** Quarantine of a split row: its parts follow the head to the backup key. */
    @Query("UPDATE saves SET `key` = :to || substr(`key`, length(:from) + 1) WHERE `key` LIKE :from || '#%'")
    abstract suspend fun renameParts(from: String, to: String)

    /** A row and all its parts move to the backup key together, or none of them do. */
    @Transaction
    open suspend fun quarantine(from: String, to: String) {
        renameParts(from, to)
        rename(from, to)
    }

    /**
     * A row too long for a cursor window, cut into parts inside SQLite: the parts are written, then the head says how
     * many. Nothing of the payload crosses into the app, so a row that cannot be read can still be saved.
     */
    @Transaction
    open suspend fun split(key: String, length: Int, chunk: Int) {
        deleteParts(key)
        val parts = (length + chunk - 1) / chunk
        for (i in 0 until parts) copyPart(key, SaveStore.partKey(key, i + 1), i * chunk + 1, chunk)
        setPayload(key, SaveStore.PARTS + parts)
    }

    /** Run + legacy are written in one transaction so a process death can never split them; each with its parts, if it has any. */
    @Transaction
    open suspend fun saveBoth(run: List<SaveEntity>?, legacy: List<SaveEntity>) {
        deleteParts(SaveStore.KEY_RUN)
        if (run == null) delete(SaveStore.KEY_RUN) else run.forEach { upsert(it) }
        deleteParts(SaveStore.KEY_LEGACY)
        legacy.forEach { upsert(it) }
    }
}

@Database(entities = [SaveEntity::class], version = 1, exportSchema = true)
abstract class SaveDatabase : RoomDatabase() {
    abstract fun saveDao(): SaveDao
}

/**
 * Room's stock answer to a database file that SQLite reports as corrupt is to delete it. This callback passes
 * everything else on and leaves a corrupt file exactly where it is: the open fails, the player is told, and only
 * "Start over" moves the file aside (SaveStore.quarantineFile).
 */
private class KeepDamagedFile(private val inner: SupportSQLiteOpenHelper.Callback) : SupportSQLiteOpenHelper.Callback(inner.version) {
    override fun onConfigure(db: SupportSQLiteDatabase) = inner.onConfigure(db)
    override fun onCreate(db: SupportSQLiteDatabase) = inner.onCreate(db)
    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = inner.onUpgrade(db, oldVersion, newVersion)
    override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = inner.onDowngrade(db, oldVersion, newVersion)
    override fun onOpen(db: SupportSQLiteDatabase) = inner.onOpen(db)
    override fun onCorruption(db: SupportSQLiteDatabase) = Unit
}

/** [setAside] moves the damaged database file away and answers the DAO of a new, empty one; null for a store without a file. */
class SaveStore(@Volatile private var dao: SaveDao, private val setAside: (() -> SaveDao)? = null) : GameRepository {
    /**
     * A row longer than [CHUNK] (one written before saves were split, so too big for SQLite's cursor window) is cut into
     * parts inside SQLite first; then each row is read, and a split one is put back together from its parts.
     */
    override suspend fun load(): StoredRows = io {
        val keys = listOf(KEY_RUN, KEY_LEGACY, KEY_CURSOR)
        dao.lengths(keys).filter { it.length > CHUNK }.forEach { dao.split(it.key, it.length, CHUNK) }
        val rows = dao.getAll(keys).associate { it.key to it.payload }
        suspend fun whole(key: String): String? = rows[key]?.let { head -> parts(head)?.let { n -> joined(key, n) } ?: head }
        // A split row whose parts do not add up is reported as that row unreadable, the legacy first as the session reads
        // them. A cursor that cannot be read is no cursor (GameSession.decode).
        suspend fun row(key: String): String? = try { whole(key) } catch (e: BrokenSplitRow) { throw SaveFailure.Corrupt(key, e) }
        val legacy = row(KEY_LEGACY)
        val run = row(KEY_RUN)
        val cursor = try { whole(KEY_CURSOR) } catch (_: BrokenSplitRow) { null }
        StoredRows(run, legacy, cursor)
    }

    /** Every part, in order, or [BrokenSplitRow]: a part that is missing or empty is never read as nothing. */
    private suspend fun joined(key: String, parts: Int): String {
        val keys = (1..parts).map { partKey(key, it) }
        val found = dao.getAll(keys).associate { it.key to it.payload }
        val missing = keys.filter { found[it].isNullOrEmpty() }
        if (missing.isNotEmpty()) throw BrokenSplitRow("'$key' counts $parts parts and ${missing.size} are missing or empty: ${missing.take(3)}")
        return keys.joinToString("") { found.getValue(it) }
    }

    override suspend fun commit(run: String?, legacy: String) = io {
        val now = System.currentTimeMillis()
        dao.saveBoth(run?.let { rows(KEY_RUN, it, now) }, rows(KEY_LEGACY, legacy, now))
    }

    /** [payload] as one row, or as a head that counts its parts and the parts of [CHUNK] characters each. */
    private fun rows(key: String, payload: String, now: Long): List<SaveEntity> {
        if (payload.length <= CHUNK) return listOf(SaveEntity(key, SaveCodec.SCHEMA_VERSION, payload, now))
        val parts = chunks(payload)
        return parts.mapIndexed { i, part -> SaveEntity(partKey(key, i + 1), SaveCodec.SCHEMA_VERSION, part, now) } +
            SaveEntity(key, SaveCodec.SCHEMA_VERSION, PARTS + parts.size, now)
    }

    override suspend fun saveCursor(cursor: String?) = io {
        if (cursor == null) dao.delete(KEY_CURSOR) else dao.upsert(SaveEntity(KEY_CURSOR, SaveCodec.SCHEMA_VERSION, cursor, System.currentTimeMillis()))
    }

    /** By key alone: the row (and its parts) is renamed, never read, so even a row SQLite cannot hand over is set aside. */
    override suspend fun quarantine(key: String) = io { dao.quarantine(key, "$key.bak.${System.currentTimeMillis()}") }

    override suspend fun quarantineFile() = io { setAside?.let { move -> dao = withContext(Dispatchers.IO) { move() } }; Unit }

    /**
     * Everything the database layer throws leaves the repository as a SaveFailure, whatever its type (Room reports a
     * missing migration as IllegalStateException): FileDamaged when SQLite says the file is corrupt, otherwise Io.
     */
    private inline fun <T> io(block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: SaveFailure) {
        throw e
    } catch (e: Exception) {
        throw if (generateSequence<Throwable>(e) { it.cause }.take(8).any { it is SQLiteDatabaseCorruptException }) SaveFailure.FileDamaged(e) else SaveFailure.Io(e)
    }

    companion object {
        const val KEY_RUN = "run"
        const val KEY_LEGACY = "legacy"
        const val KEY_CURSOR = "cursor"

        /**
         * The longest payload kept in one row, in characters. SQLite hands a row to the app through a cursor window of
         * 2 MB; a JSON envelope is mostly one byte a character, so a quarter of that leaves room for any text.
         */
        const val CHUNK = 256 * 1024
        /** The head of a split row: "parts:3" (an envelope always starts with "{", so the two cannot be mistaken). */
        const val PARTS = "parts:"
        /** The most parts a head may count: one query reads them all, and SQLite binds at most 999 values. Far past any save. */
        const val MAX_PARTS = 999
        fun partKey(key: String, n: Int) = "$key#$n"

        /** Null for a payload kept whole; the count of a head; [BrokenSplitRow] for a head that counts nothing ("parts:", "parts:0", "parts:x"). */
        fun parts(head: String): Int? {
            if (!head.startsWith(PARTS)) return null
            val digits = head.substring(PARTS.length)
            val n = digits.takeIf { it.length in 1..3 && it.all { c -> c in '0'..'9' } }?.toInt()
            return n?.takeIf { it in 1..MAX_PARTS } ?: throw BrokenSplitRow("'${head.take(24)}' does not count 1 to $MAX_PARTS parts")
        }

        /**
         * [payload] cut into parts of at most [size] UTF-16 units, never between the two halves of a surrogate pair: a
         * lone half is not text, and SQLite would store it as something else. Joined in order, the parts are [payload].
         */
        fun chunks(payload: String, size: Int = CHUNK): List<String> {
            require(size >= 2)
            val out = ArrayList<String>(payload.length / size + 1)
            var from = 0
            while (from < payload.length) {
                var to = minOf(from + size, payload.length)
                if (to < payload.length && Character.isHighSurrogate(payload[to - 1]) && Character.isLowSurrogate(payload[to])) to--
                out += payload.substring(from, to)
                from = to
            }
            return out
        }

        fun create(context: Context, name: String = "tiny_blacksmith.db"): SaveStore {
            val app = context.applicationContext
            var db = open(app, name)
            return SaveStore(db.saveDao()) {
                runCatching { db.close() }
                moveAside(app.getDatabasePath(name))
                db = open(app, name)
                db.saveDao()
            }
        }

        private fun open(context: Context, name: String): SaveDatabase =
            Room.databaseBuilder(context, SaveDatabase::class.java, name)
                .openHelperFactory { c ->
                    // allowDataLossOnRecovery(false): the helper must never delete a database it failed to open.
                    FrameworkSQLiteOpenHelperFactory().create(
                        SupportSQLiteOpenHelper.Configuration.builder(c.context).name(c.name).callback(KeepDamagedFile(c.callback))
                            .noBackupDirectory(c.useNoBackupDirectory).allowDataLossOnRecovery(false).build(),
                    )
                }
                .build()

        /** The file and its journal files keep their bytes under "<name>.corrupt.<millis>". The journals go first: a new database must not find them. */
        private fun moveAside(file: File) {
            val stamp = System.currentTimeMillis()
            for (suffix in listOf("-wal", "-shm", "-journal", "")) {
                val from = File(file.path + suffix)
                if (from.exists() && !from.renameTo(File("${file.path}.corrupt.$stamp$suffix"))) throw IOException("Could not set ${from.name} aside")
            }
        }
    }
}
