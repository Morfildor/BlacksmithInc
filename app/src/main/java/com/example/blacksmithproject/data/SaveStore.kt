package com.example.blacksmithproject.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.persistence.SaveCodec

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

    @Upsert
    abstract suspend fun upsert(entity: SaveEntity)

    @Query("DELETE FROM saves WHERE `key` = :key")
    abstract suspend fun delete(key: String)

    /** Run + legacy are written in one transaction so a process death can never split them. */
    @Transaction
    open suspend fun saveBoth(run: SaveEntity?, legacy: SaveEntity) {
        if (run == null) delete(SaveStore.KEY_RUN) else upsert(run)
        upsert(legacy)
    }
}

@Database(entities = [SaveEntity::class], version = 1, exportSchema = false)
abstract class SaveDatabase : RoomDatabase() {
    abstract fun saveDao(): SaveDao
}

class SaveStore(private val dao: SaveDao) {
    suspend fun loadRun(): GameState? = dao.get(KEY_RUN)?.let { SaveCodec.decodeRun(it.payload) }

    suspend fun loadLegacy(): LegacyProfile = dao.get(KEY_LEGACY)?.let { SaveCodec.decodeLegacy(it.payload) } ?: LegacyProfile()

    suspend fun saveAtomically(run: GameState?, legacy: LegacyProfile) {
        val now = System.currentTimeMillis()
        dao.saveBoth(
            run?.let { SaveEntity(KEY_RUN, SaveCodec.SCHEMA_VERSION, SaveCodec.encodeRun(it), now) },
            SaveEntity(KEY_LEGACY, SaveCodec.SCHEMA_VERSION, SaveCodec.encodeLegacy(legacy), now),
        )
    }

    companion object {
        const val KEY_RUN = "run"
        const val KEY_LEGACY = "legacy"

        fun create(context: Context): SaveStore =
            SaveStore(Room.databaseBuilder(context.applicationContext, SaveDatabase::class.java, "tiny_blacksmith.db").build().saveDao())
    }
}
