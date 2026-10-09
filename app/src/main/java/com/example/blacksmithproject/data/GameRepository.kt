package com.example.blacksmithproject.data

/** The rows exactly as stored. The repository never decodes: decoding, migration and admission happen above it. */
data class StoredRows(val run: String?, val legacy: String?, val cursor: String?)

/** Room implementation: SaveStore. Test implementation: FakeGameRepository. The repository owns its own dispatcher. */
interface GameRepository {
    suspend fun load(): StoredRows                         // one read; never writes or deletes; throws SaveFailure.Io or FileDamaged
    suspend fun commit(run: String?, legacy: String)       // one transaction; null run clears the run; throws SaveFailure.Io
    suspend fun saveCursor(cursor: String?)                // its own small row; never inside GameState
    suspend fun quarantine(key: String)                    // moves a row to "<key>.bak.<millis>"; never deletes
    suspend fun quarantineFile() {}                        // moves the whole store to "<name>.corrupt.<millis>" and opens an empty one; never deletes. A store without a file has nothing to move
}

sealed class SaveFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** [runSound]: the legacy row is the unreadable one and the run beside it loads, so starting over rebuilds the legacy from the run's copy. */
    class Corrupt(val key: String, cause: Throwable, val runSound: Boolean = false) : SaveFailure("Save '$key' cannot be read", cause)
    class Newer(val key: String, val found: Int, val supported: Int, val runSound: Boolean = false) : SaveFailure("Save '$key' is from a newer version")
    class Incompatible(val problems: List<String>) : SaveFailure("Save cannot continue: $problems")
    class Io(cause: Throwable) : SaveFailure("Storage failed", cause)
    /** The database file itself is damaged, so no row can be read. The file is left exactly as it is. */
    class FileDamaged(cause: Throwable) : SaveFailure("The save file is damaged", cause)
}
