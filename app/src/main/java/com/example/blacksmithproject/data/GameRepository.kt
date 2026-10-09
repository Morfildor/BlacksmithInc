package com.example.blacksmithproject.data

/** The rows exactly as stored. The repository never decodes: decoding, migration and admission happen above it. */
data class StoredRows(val run: String?, val legacy: String?, val cursor: String?)

/** Room implementation: SaveStore. Test implementation: FakeGameRepository. */
interface GameRepository {
    suspend fun load(): StoredRows                         // one read; never writes or deletes
    suspend fun commit(run: String?, legacy: String)       // one transaction; null run clears the run
}
