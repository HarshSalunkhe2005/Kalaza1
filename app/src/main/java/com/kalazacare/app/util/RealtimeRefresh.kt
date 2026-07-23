package com.kalazacare.app.util

import com.kalazacare.app.data.remote.SupabaseClients
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Subscribes to any INSERT/UPDATE/DELETE on [table] and calls [onChange] every
 * time one happens — a "go refetch everything" reactive refresh rather than
 * granular row-level diffing, since every screen already has a working
 * [onChange] (its own `load()`) and this is far simpler than threading
 * incremental Realtime payloads through the existing one-shot repository
 * calls. Good enough to kill staleness; not a full Realtime data layer.
 *
 * Call once per ViewModel (e.g. from `init {}`), passing `viewModelScope` so
 * the subscription is cancelled automatically when the ViewModel is cleared.
 */
fun subscribeToTableChanges(scope: CoroutineScope, table: String, onChange: () -> Unit) {
    scope.launch {
        val channel = SupabaseClients.main.realtime.channel("changes-$table-${System.nanoTime()}")
        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") { this.table = table }
        scope.launch { changeFlow.collect { onChange() } }
        channel.subscribe()
    }
}
