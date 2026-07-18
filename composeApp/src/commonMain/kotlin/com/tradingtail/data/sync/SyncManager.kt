package com.tradingtail.data.sync

import com.tradingtail.data.local.entity.ExecutionEntity
import com.tradingtail.data.local.entity.TradeNoteEntity
import com.tradingtail.data.remote.ExecutionRow
import com.tradingtail.data.remote.NoteRow
import com.tradingtail.data.remote.toEntity
import com.tradingtail.data.remote.toRow
import com.tradingtail.common.nowMillis
import com.tradingtail.data.repository.ExecutionRepository
import com.tradingtail.data.repository.TradeNoteRepository
import com.tradingtail.domain.usecase.RebuildTradesForSymbol
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one place local SQLite and the Supabase mirror reconcile. It syncs only the durable, user-owned
 * data — [ExecutionEntity] (keyed by its cross-device `syncId`) and [TradeNoteEntity] (keyed by the
 * round-trip's natural key). Trades are never synced: each device re-derives them from the fills, so
 * after any execution change we just re-run the matcher for the affected symbols.
 *
 * Conflict resolution is last-write-wins on `updatedAt` (project decision 7). Deletes are tombstones,
 * so every change — insert, edit, delete — is a single uniform upsert; nothing is ever hard-deleted
 * remotely, which also means a Realtime DELETE payload is never needed when that layer lands later.
 *
 * ponytail: full snapshot both directions each pass (no `updated_at > lastSync` cursor). Simple and
 * correct for one user's few thousand fills; add an incremental cursor if a table ever gets large.
 */
class SyncManager(
    private val supabase: SupabaseClient,
    private val executionRepo: ExecutionRepository,
    private val noteRepo: TradeNoteRepository,
    private val rebuild: RebuildTradesForSymbol,
) {
    // Serializes the manual Sync button against the 20s poll — two overlapping passes would still be
    // correct (upserts are idempotent), just wasteful and confusing in the UI.
    private val mutex = Mutex()
    private val _state = MutableStateFlow(SyncState())
    /** Observable status for the UI: the Sync button's spinner, and "last synced" / error text. */
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /**
     * One pull+push both ways, then re-derive touched symbols. Serialized, and errors are captured
     * into [state] rather than thrown: sync is best-effort (local-first), so a dropped network is a
     * non-event that both the poll and the button surface the same way.
     */
    suspend fun syncOnce() = mutex.withLock {
        _state.value = _state.value.copy(syncing = true, error = null)
        try {
            syncExecutions()
            syncNotes()
            _state.value = SyncState(syncing = false, lastSyncedAt = nowMillis(), error = null)
        } catch (e: Exception) {
            _state.value = _state.value.copy(syncing = false, error = e.message ?: "Sync failed")
        }
    }

    /**
     * ponytail: poll, not push. A 20s pull+push covers two-way sync for one person hopping between
     * two devices without any websocket lifecycle. Realtime (instant, event-driven) is the upgrade —
     * it slots in beside this without touching the reconcile below.
     */
    suspend fun runPeriodic(intervalMs: Long = 20_000L) {
        while (true) {
            syncOnce()
            delay(intervalMs)
        }
    }

    private suspend fun syncExecutions() {
        val table = supabase.from(EXECUTIONS)
        val remote = table.select().decodeList<ExecutionRow>().map { it.toEntity() }
        val local = executionRepo.allForSync()
        val plan = reconcile(local, remote, key = { it.syncId }, clock = { it.updatedAt })

        val localBySync = local.associateBy { it.syncId }
        val touched = mutableSetOf<String>()
        for (incoming in plan.toApplyLocally) {
            // Re-derive the incoming symbol and, if an edit moved the fill between symbols, the old one too.
            localBySync[incoming.syncId]?.let { touched += it.symbol }
            executionRepo.applyRemote(incoming)
            touched += incoming.symbol
        }
        if (plan.toPush.isNotEmpty()) {
            table.upsert(plan.toPush.map { it.toRow() }) { onConflict = "sync_id" }
        }
        touched.forEach { rebuild(it) }
    }

    private suspend fun syncNotes() {
        val table = supabase.from(NOTES)
        val remote = table.select().decodeList<NoteRow>().map { it.toEntity() }
        val local = noteRepo.allForSync()
        val plan = reconcile(local, remote, key = { noteKey(it) }, clock = { it.updatedAt })
        plan.toApplyLocally.forEach { noteRepo.applyRemote(it) }
        if (plan.toPush.isNotEmpty()) {
            table.upsert(plan.toPush.map { it.toRow() }) { onConflict = "symbol,entry_ts,exit_ts" }
        }
    }

    private companion object {
        const val EXECUTIONS = "executions"
        const val NOTES = "trade_notes"
        fun noteKey(n: TradeNoteEntity) = Triple(n.symbol, n.entryTs, n.exitTs)
    }
}

/** Observable sync status for the UI (manual Sync button + "last synced" caption). */
data class SyncState(val syncing: Boolean = false, val lastSyncedAt: Long? = null, val error: String? = null)

/** What a reconcile pass decided: rows to write into local SQLite, and rows to push to the remote. */
data class Reconciliation<T>(val toApplyLocally: List<T>, val toPush: List<T>)

/**
 * Pure last-write-wins diff of two row sets sharing a key and a clock. A row is applied locally when
 * it's absent locally or the remote copy is strictly newer; pushed when it's absent remotely or the
 * local copy is strictly newer. Equal clocks are left untouched (the two sides already agree). No I/O,
 * so the whole conflict policy is one testable function.
 */
fun <T, K> reconcile(
    local: List<T>,
    remote: List<T>,
    key: (T) -> K,
    clock: (T) -> Long,
): Reconciliation<T> {
    val localByKey = local.associateBy(key)
    val remoteByKey = remote.associateBy(key)
    val toApplyLocally = remote.filter { r ->
        val l = localByKey[key(r)]
        l == null || clock(r) > clock(l)
    }
    val toPush = local.filter { l ->
        val r = remoteByKey[key(l)]
        r == null || clock(l) > clock(r)
    }
    return Reconciliation(toApplyLocally, toPush)
}
