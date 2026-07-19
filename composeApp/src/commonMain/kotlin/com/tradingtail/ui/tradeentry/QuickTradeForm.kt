package com.tradingtail.ui.tradeentry

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import com.tradingtail.common.ZERO
import com.tradingtail.common.bigDecimal
import com.tradingtail.common.formatBangkok
import com.tradingtail.common.nowMillis
import com.tradingtail.common.parseBangkok

/**
 * All Quick Trade Entry state + validation, hoisted out of the composable so the rules are plain
 * Kotlin (see QuickTradeFormTest) and the screen is layout only.
 *
 * The raw Strings are the source of truth — parsing happens once, at submit, so a half-typed "15."
 * is a legal intermediate state rather than a parse error. These eight fields are hand-typed money,
 * entered one-handed right after a trade closes; [Saver] carries all of them (and their errors)
 * across an Activity recreation (rotation, a system font change, the process being trimmed while
 * the user checked their broker app).
 */
internal class QuickTradeForm(now: String = formatBangkok(nowMillis())) {
    var symbol by mutableStateOf("")
    var quantity by mutableStateOf("")
    var entryPrice by mutableStateOf("")
    var exitPrice by mutableStateOf("")
    // Both timestamps default to now. The entry field used to default to now − 1h — a guessed hour
    // that flowed straight into Performance By Hour Of Day, avgHoldMillis, and both duration
    // reports, making four report sections quietly wrong. A wrong-but-plausible default is worse
    // than an obvious one: it goes unchallenged. `now` is at least honestly the moment you logged
    // it, and it's one edit away.
    var entryTs by mutableStateOf(now)
    var exitTs by mutableStateOf(now)
    var entryFees by mutableStateOf("")
    var exitFees by mutableStateOf("")

    var symbolErr by mutableStateOf<String?>(null)
    var qtyErr by mutableStateOf<String?>(null)
    var entryErr by mutableStateOf<String?>(null)
    var exitErr by mutableStateOf<String?>(null)
    var entryTsErr by mutableStateOf<String?>(null)
    var exitTsErr by mutableStateOf<String?>(null)
    var entryFeesErr by mutableStateOf<String?>(null)
    var exitFeesErr by mutableStateOf<String?>(null)

    // "Dirty" = anything the user actually typed. The prefilled timestamps don't count: leaving an
    // untouched form must not cost a confirmation.
    val dirty: Boolean
        get() = symbol.isNotBlank() || quantity.isNotBlank() || entryPrice.isNotBlank() ||
            exitPrice.isNotBlank() || entryFees.isNotBlank() || exitFees.isNotBlank()

    /** Re-derive every field error; true when the form is submittable. */
    fun validate(): Boolean {
        symbolErr = if (symbol.isBlank()) "Required" else null
        qtyErr = numError(quantity)
        entryErr = numError(entryPrice)
        exitErr = numError(exitPrice)
        entryTsErr = dateError(entryTs)
        exitTsErr = dateError(exitTs)
        entryFeesErr = feeError(entryFees)
        exitFeesErr = feeError(exitFees)
        // Relational check the per-field validators structurally can't make: each field is fine on
        // its own, and the pair is still nonsense. RecordQuickTrade enforces this too (that's the
        // gate all paths route through) — this only puts the message on the field that's wrong,
        // instead of surfacing raw exception text at the bottom of the form.
        if (entryTsErr == null && exitTsErr == null && parseBangkok(exitTs.trim()) < parseBangkok(entryTs.trim())) {
            exitTsErr = "Exit can't be before entry"
        }
        return listOf(
            symbolErr, qtyErr, entryErr, exitErr,
            entryTsErr, exitTsErr, entryFeesErr, exitFeesErr,
        ).all { it == null }
    }

    companion object {
        /** Fields then errors, as one flat list; restore rebuilds them in the same order. */
        val Saver = listSaver<QuickTradeForm, String?>(
            save = {
                listOf(
                    it.symbol, it.quantity, it.entryPrice, it.exitPrice,
                    it.entryTs, it.exitTs, it.entryFees, it.exitFees,
                    it.symbolErr, it.qtyErr, it.entryErr, it.exitErr,
                    it.entryTsErr, it.exitTsErr, it.entryFeesErr, it.exitFeesErr,
                )
            },
            restore = { l ->
                QuickTradeForm().apply {
                    symbol = l[0]!!; quantity = l[1]!!; entryPrice = l[2]!!; exitPrice = l[3]!!
                    entryTs = l[4]!!; exitTs = l[5]!!; entryFees = l[6]!!; exitFees = l[7]!!
                    symbolErr = l[8]; qtyErr = l[9]; entryErr = l[10]; exitErr = l[11]
                    entryTsErr = l[12]; exitTsErr = l[13]; entryFeesErr = l[14]; exitFeesErr = l[15]
                }
            },
        )
    }
}

internal fun numError(s: String): String? {
    val v = runCatching { bigDecimal(s.trim()) }.getOrNull() ?: return "Enter a number"
    return if (v > ZERO) null else "Must be > 0"
}

// Fees are optional (blank = 0) and, unlike price/quantity, may legitimately be 0 — so ≥ 0, not > 0.
internal fun feeError(s: String): String? {
    if (s.isBlank()) return null
    val v = runCatching { bigDecimal(s.trim()) }.getOrNull() ?: return "Enter a number"
    return if (v >= ZERO) null else "Must be ≥ 0"
}

internal fun dateError(s: String): String? =
    if (runCatching { parseBangkok(s) }.isSuccess) null else "Invalid date/time — use YYYY-MM-DD HH:MM"
