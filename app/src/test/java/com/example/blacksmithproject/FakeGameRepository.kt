package com.example.blacksmithproject

import com.example.blacksmithproject.data.GameRepository
import com.example.blacksmithproject.data.StoredRows

/** In-memory rows stored as text, exactly like the real store. */
class FakeGameRepository(var run: String? = null, var legacy: String? = null) : GameRepository {
    var cursor: String? = null
    var commitCount = 0

    override suspend fun load(): StoredRows = StoredRows(run, legacy, cursor)

    override suspend fun commit(run: String?, legacy: String) {
        this.run = run
        this.legacy = legacy
        commitCount++
    }
}
