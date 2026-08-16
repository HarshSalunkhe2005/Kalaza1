package com.kalazacare.app.util

import com.kalazacare.app.data.remote.SupabaseClients
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Subscribes to any INSERT/UPDATE/DELETE on [table] and calls [onChange] every
 * time one happens — a "go refetch everything" reactive refresh rather than
 * granular row-level diffing, since every screen already has a working
 * [onChange] (its own `load()`) and this is far simpler than threading
 * incremental Realtime payloads through the existing one-shot repository
 * calls. Good enough to kill staleness; not a full Realtime data layer.
 *
 * Call once per ViewModel (e.g. from `init {}`), passing `viewModelScope`.
 *
 * Every subscribing screen's channel used to leak: setup and collection ran
 * in two separate `launch`es, so cancelling the outer one (ViewModel
 * cleared) never actually called `channel.unsubscribe()` — the channel just
 * stayed registered on the client's single shared Realtime socket for the
 * rest of the app's process lifetime. A long session that visits screens
 * repeatedly (Patient Profile, Medicine tab, Approval Queue — 7 call sites
 * across the app) accumulated one live, never-closed channel per visit,
 * each still evaluated against every matching table's change broadcasts —
 * a real, escalating cause of the app slowing down the longer it stays
 * open. Now a single coroutine does setup, subscribe, and collection
 * together, so cancelling it runs `unsubscribe()` in the `finally` block
 * (wrapped in NonCancellable since it's itself a suspend network call).
 */
fun subscribeToTableChanges(scope: CoroutineScope, table: String, onChange: () -> Unit) {
    scope.launch {
        val channel = SupabaseClients.main.realtime.channel("changes-$table-${System.nanoTime()}")
        try {
            val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") { this.table = table }
            channel.subscribe()
            changeFlow.collect { onChange() }
        } finally {
            withContext(NonCancellable) {
                runCatching { channel.unsubscribe() }
            }
        }
    }
}
