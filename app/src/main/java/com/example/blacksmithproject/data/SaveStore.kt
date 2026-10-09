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

/** Authoritative saves live in Room; the payload is the versioned JSON envelope produced by the pure core. */
@Entity(tableName = "saves")
data class SaveEntity(
    @PrimaryKey val key: String,
    val schemaVersion: Int,
    val payload: String,
    val savedAt: Long,
)

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

    /** Run + legacy are written in one transaction so a process death can never split them. */
    @Transaction
    open suspend fun saveBoth(run: SaveEntity?, legacy: SaveEntity) {
        if (run == null) delete(SaveStore.KEY_RUN) else upsert(run)
        upsert(legacy)
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
    override suspend fun load(): StoredRows = io {
        val rows = dao.getAll(listOf(KEY_RUN, KEY_LEGACY, KEY_CURSOR)).associate { it.key to it.payload }
        StoredRows(rows[KEY_RUN], rows[KEY_LEGACY], rows[KEY_CURSOR])
    }

    override suspend fun commit(run: String?, legacy: String) = io {
        val now = System.currentTimeMillis()
        dao.saveBoth(run?.let { SaveEntity(KEY_RUN, SaveCodec.SCHEMA_VERSION, it, now) }, SaveEntity(KEY_LEGACY, SaveCodec.SCHEMA_VERSION, legacy, now))
    }

    override suspend fun saveCursor(cursor: String?) = io {
        if (cursor == null) dao.delete(KEY_CURSOR) else dao.upsert(SaveEntity(KEY_CURSOR, SaveCodec.SCHEMA_VERSION, cursor, System.currentTimeMillis()))
    }

    override suspend fun quarantine(key: String) = io { dao.rename(key, "$key.bak.${System.currentTimeMillis()}") }

    override suspend fun quarantineFile() = io { setAside?.let { move -> dao = withContext(Dispatchers.IO) { move() } }; Unit }

    /**
     * Everything the database layer throws leaves the repository as a SaveFailure, whatever its type (Room reports a
     * missing migration as IllegalStateException): FileDamaged when SQLite says the file is corrupt, otherwise Io.
     */
    private inline fun <T> io(block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw if (generateSequence<Throwable>(e) { it.cause }.take(8).any { it is SQLiteDatabaseCorruptException }) SaveFailure.FileDamaged(e) else SaveFailure.Io(e)
    }

    companion object {
        const val KEY_RUN = "run"
        const val KEY_LEGACY = "legacy"
        const val KEY_CURSOR = "cursor"

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
