package com.tradingtail.data.repository

import com.tradingtail.common.nowMillis
import com.tradingtail.data.local.dao.TradeNoteDao
import com.tradingtail.data.local.entity.TradeKey
import com.tradingtail.data.local.entity.TradeNoteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The only seam UI/usecases touch for trade notes — never the DAO directly.
 *
 * Notes are keyed by [TradeKey] rather than a trade row id on purpose: the matcher truncates and
 * re-derives `trades` on every new fill, so a note anchored to a row id would not survive. Nothing
 * here is ever called by the matcher, which is what makes notes durable.
 */
class TradeNoteRepository(private val dao: TradeNoteDao) {
    /**
     * Blank clears the note. It's written as a *tombstone* (deleted=true), not hard-deleted, so the
     * clearing propagates to the other device instead of the note reappearing on the next pull.
     * ponytail: this can leave an empty tombstone for a trade whose note was only ever blank — bounded
     * by trades whose note field was touched, and filtered from every read, so not worth a guard query.
     */
    suspend fun save(key: TradeKey, note: String) {
        dao.upsert(
            TradeNoteEntity(
                symbol = key.symbol,
                entryTs = key.entryTs,
                exitTs = key.exitTs,
                note = note.trim(),
                updatedAt = nowMillis(),
                deleted = note.isBlank(),
            ),
        )
    }

    fun noteFlow(key: TradeKey): Flow<String?> =
        dao.byKeyFlow(key.symbol, key.entryTs, key.exitTs).map { it?.note }

    /** Every noted round-trip, for the calendar's note glyph. */
    fun allFlow(): Flow<List<TradeNoteEntity>> = dao.allFlow()

    // --- sync ---
    suspend fun allForSync(): List<TradeNoteEntity> = dao.allForSync()

    /** Apply a remote note verbatim. @Upsert resolves it against the local row by the composite key. */
    suspend fun applyRemote(remote: TradeNoteEntity) = dao.upsert(remote)
}
