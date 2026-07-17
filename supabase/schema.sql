-- Trading Tail — Supabase sync schema
-- =====================================
-- Run this once in your Supabase project: Dashboard → SQL Editor → paste → Run.
--
-- Only two tables sync. `executions` and `trade_notes` are the durable, user-owned data; `trades`
-- are DERIVED and never leave the device — each client re-runs the FIFO matcher locally. See
-- CLAUDE.md (decisions 1, 5, 7) and data/sync/SyncManager.kt.
--
-- Money and quantity are `text`, not `numeric`: they hold the exact BigDecimal string the client
-- produces, so a fill can't pick up floating-point drift crossing the wire.
--
-- RLS is intentionally left DISABLED. This is a private, single-user, no-auth project (CLAUDE.md
-- decision 6) — the anon key is the only key and it needs full access. Supabase will flag these
-- tables as "unrestricted"; that is expected here, not a mistake. Do NOT enable RLS unless the
-- project ever becomes multi-user.

create table if not exists public.executions (
    sync_id          text        primary key,   -- stable cross-device id (the local Room id never leaves)
    symbol           text        not null,
    side             text        not null,       -- BUY / SELL
    price            text        not null,       -- BigDecimal as exact string — never a JSON number
    quantity         text        not null,
    "timestamp"      bigint      not null,       -- epoch millis, UTC
    fees             text        not null,
    instrument_type  text        not null,       -- STOCK / OPTION / FUTURES / FOREX
    source           text        not null,       -- MANUAL / CSV / PDF
    updated_at       bigint      not null,       -- last-write-wins clock (epoch millis)
    deleted          boolean     not null default false  -- tombstone; clients filter these from reads
);

create table if not exists public.trade_notes (
    symbol      text    not null,
    entry_ts    bigint  not null,
    exit_ts     bigint  not null,
    note        text    not null,
    updated_at  bigint  not null,
    deleted     boolean not null default false,
    primary key (symbol, entry_ts, exit_ts)      -- the round-trip's natural key, already stable across devices
);

-- The anon key CRUDs both tables (RLS is off). Explicit so the script doesn't depend on project
-- default-privilege settings.
grant all on public.executions to anon;
grant all on public.trade_notes to anon;

-- Realtime is deferred: v1 sync polls every 20s, which covers one person moving between two devices.
-- To upgrade to instant push later, add the tables to the realtime publication:
--   alter publication supabase_realtime add table public.executions, public.trade_notes;
