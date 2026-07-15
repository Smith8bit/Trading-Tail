package com.tradingtail.data.repository

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
    /** Blank clears the note — an empty note is an absent note, not a row holding "". */
    suspend fun save(key: TradeKey, note: String) {
        if (note.isBlank()) dao.delete(key.symbol, key.entryTs, key.exitTs)
        else dao.upsert(TradeNoteEntity(key.symbol, key.entryTs, key.exitTs, note.trim()))
    }

    fun noteFlow(key: TradeKey): Flow<String?> =
        dao.byKeyFlow(key.symbol, key.entryTs, key.exitTs).map { it?.note }

    /** Every noted round-trip, for the calendar's note glyph. */
    fun allFlow(): Flow<List<TradeNoteEntity>> = dao.allFlow()
}
