package com.tradingtail.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Where to reach the remote copy. The anon key is a public client key by design (this is a no-auth,
 * open-RLS single-user project — see CLAUDE.md decision 6), but it still isn't committed: each
 * platform entry point reads it from a gitignored source and passes it in. Absent config = sync off.
 */
data class SyncConfig(val url: String, val anonKey: String)

/** Postgrest-only client. Realtime is deferred (see [com.tradingtail.data.sync.SyncManager]). */
fun createSupabase(config: SyncConfig): SupabaseClient =
    createSupabaseClient(supabaseUrl = config.url, supabaseKey = config.anonKey) {
        install(Postgrest)
    }
