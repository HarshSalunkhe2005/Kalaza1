package com.kalazacare.app.util

import android.util.Log
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Process-wide bus for backend failures that a [safeLaunch] block catches.
 * [com.kalazacare.app.ui.MainActivity] collects [events] once and shows each
 * as a Toast — that's the only consumer; every ViewModel just calls
 * [report] (indirectly, via [safeLaunch]) and moves on.
 */
object AppErrors {
    val events = MutableSharedFlow<String>(extraBufferCapacity = 4)

    fun report(context: String, e: Throwable) {
        Log.e("KalazaCare", "$context failed", e)
        events.tryEmit(humanize(context, e))
    }

    private fun humanize(context: String, e: Throwable): String {
        val detail = when (e) {
            // RLS denial (Postgres 42501) is the one backend error worth naming
            // specifically — everything else ("no network", "timed out", a
            // genuine server error) just reads as a generic failure to the user,
            // there's nothing actionable they can do differently for those.
            is PostgrestRestException -> if (e.code == "42501") {
                "you don't have permission to do that"
            } else {
                "the server rejected that"
            }
            else -> "a connection problem"
        }
        return if (context.isBlank()) "Couldn't complete that action — $detail. Please try again."
        else "Couldn't $context — $detail. Please try again."
    }
}
