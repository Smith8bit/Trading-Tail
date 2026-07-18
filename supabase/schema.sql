-- Trading Tail — Supabase sync schema
-- =====================================
-- Sync is OPT-IN and per-user: each person runs this once in THEIR OWN Supabase project, then pastes
-- that project's URL + publishable key into the app's Settings. The app ships with no credentials and
-- syncs nothing until you do this — so no two users ever share a database.
-- Run it: Dashboard → SQL Editor → paste → Run.
--
-- Only two tables sync. `executions` and `trade_notes` are the durable, user-owned data; `trades`
-- are DERIVED and never leave the device — each client re-runs the FIFO matcher locally. See
-- CLAUDE.md (decisions 1, 5, 7) and data/sync/SyncManager.kt.
--
-- Money and quantity are `text`, not `numeric`: they hold the exact BigDecimal string the client
-- produces, so a fill can't pick up floating-point drift crossing the wire.
--
-- Access is fully OPEN — this is a private, single-user, no-auth project (CLAUDE.md decision 6);
-- the publishable key is the only key and it needs full CRUD. New Supabase projects enable RLS by
-- default and keep it sticky (a plain `disable row level security` did NOT take here), so instead of
-- fighting RLS we grant blanket-permissive policies below: with RLS on they open everything, and if
-- RLS is off they're simply ignored. Net result either way is a fully-open table, which is the intent.
-- Do NOT narrow these policies unless the project ever becomes multi-user.

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

-- Blanket-permissive policies = full open access regardless of the project's RLS state (see header).
-- No role clause → applies to `public` (covers the anon/publishable key).
drop policy if exists tt_open on public.executions;
drop policy if exists tt_open on public.trade_notes;
create policy tt_open on public.executions for all using (true) with check (true);
create policy tt_open on public.trade_notes for all using (true) with check (true);

-- Explicit grants so the script doesn't depend on project default-privilege settings.
grant all on public.executions to anon;
grant all on public.trade_notes to anon;

-- Realtime is deferred: v1 sync polls every 20s, which covers one person moving between two devices.
-- To upgrade to instant push later, add the tables to the realtime publication:
--   alter publication supabase_realtime add table public.executions, public.trade_notes;
