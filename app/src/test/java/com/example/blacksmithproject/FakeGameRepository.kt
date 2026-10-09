package com.example.blacksmithproject

import com.example.blacksmithproject.data.GameRepository
import com.example.blacksmithproject.data.SaveFailure
import com.example.blacksmithproject.data.StoredRows
import kotlinx.coroutines.CompletableDeferred

/** In-memory rows stored as text, exactly like the real store. Can hold each commit or load until released and can fail on demand. */
class FakeGameRepository(var run: String? = null, var legacy: String? = null) : GameRepository {
    var cursor: String? = null
    var hold = false
    val gates = ArrayDeque<CompletableDeferred<Unit>>()
    /** A held load has already read its rows: released late, it returns what was stored when it started. */
    var holdLoad = false
    val loadGates = ArrayDeque<CompletableDeferred<Unit>>()
    var failNextCommit: Throwable? = null
    /** The next commit is stored and then reported as failed (the transaction committed, the error surfaced afterwards). */
    var failNextCommitAfterWriting: Throwable? = null
    var failNextCursor: Throwable? = null
    var failNextLoad: Throwable? = null
    var commitCount = 0
    var loadCount = 0
    val quarantined = mutableMapOf<String, String>()
    /** The whole store is unreadable until it is moved aside. */
    var fileDamaged = false
    var fileQuarantines = 0

    /** Stops holding and lets everything held through. A failing test must call it: the session commits under NonCancellable. */
    fun releaseAll() {
        hold = false; holdLoad = false
        gates.forEach { it.complete(Unit) }; gates.clear()
        loadGates.forEach { it.complete(Unit) }; loadGates.clear()
    }

    override suspend fun load(): StoredRows {
        loadCount++
        failNextLoad?.let { failNextLoad = null; throw SaveFailure.Io(it) }
        if (fileDamaged) throw SaveFailure.FileDamaged(IllegalStateException("file is not a database"))
        val rows = StoredRows(run, legacy, cursor)
        if (holdLoad) CompletableDeferred<Unit>().also { loadGates += it }.await()
        return rows
    }

    override suspend fun commit(run: String?, legacy: String) {
        if (hold) CompletableDeferred<Unit>().also { gates += it }.await()
        failNextCommit?.let { failNextCommit = null; throw SaveFailure.Io(it) }
        this.run = run; this.legacy = legacy; commitCount++
        failNextCommitAfterWriting?.let { failNextCommitAfterWriting = null; throw SaveFailure.Io(it) }
    }

    override suspend fun saveCursor(cursor: String?) {
        failNextCursor?.let { failNextCursor = null; throw SaveFailure.Io(it) }
        this.cursor = cursor
    }

    override suspend fun quarantine(key: String) {
        if (key == "run") { run?.let { quarantined["run.bak"] = it }; run = null }
        if (key == "legacy") { legacy?.let { quarantined["legacy.bak"] = it }; legacy = null }
    }

    override suspend fun quarantineFile() {
        fileQuarantines++; fileDamaged = false
        run = null; legacy = null; cursor = null
    }
}
