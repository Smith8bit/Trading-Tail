package com.tradingtail.data.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The last-write-wins policy is the whole correctness story of sync, so it's tested as the pure
 * function it is — no database, no network. `reconcile` is deliberately deleted-agnostic: a tombstone
 * is just a row with a clock, so "a newer deletion propagates" is the same code path as "a newer edit
 * propagates" and needs no separate case here.
 */
class SyncReconcileTest {
    private data class Row(val k: String, val clock: Long)

    private fun plan(local: List<Row>, remote: List<Row>) =
        reconcile(local, remote, key = { it.k }, clock = { it.clock })

    @Test
    fun `a newer remote row is applied locally, not pushed back`() {
        val p = plan(local = listOf(Row("a", 1)), remote = listOf(Row("a", 2)))
        assertEquals(listOf(Row("a", 2)), p.toApplyLocally)
        assertTrue(p.toPush.isEmpty())
    }

    @Test
    fun `a newer local row is pushed, not overwritten by the older remote`() {
        val p = plan(local = listOf(Row("a", 5)), remote = listOf(Row("a", 2)))
        assertEquals(listOf(Row("a", 5)), p.toPush)
        assertTrue(p.toApplyLocally.isEmpty())
    }

    @Test
    fun `equal clocks mean the two sides agree — nothing moves`() {
        val p = plan(local = listOf(Row("a", 3)), remote = listOf(Row("a", 3)))
        assertTrue(p.toApplyLocally.isEmpty())
        assertTrue(p.toPush.isEmpty())
    }

    @Test
    fun `a row present on only one side crosses to the other`() {
        val p = plan(local = listOf(Row("only-local", 1)), remote = listOf(Row("only-remote", 1)))
        assertEquals(listOf(Row("only-remote", 1)), p.toApplyLocally)
        assertEquals(listOf(Row("only-local", 1)), p.toPush)
    }
}
