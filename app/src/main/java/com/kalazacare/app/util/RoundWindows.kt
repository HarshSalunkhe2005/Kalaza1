package com.kalazacare.app.util

import android.content.Context
import com.kalazacare.app.data.model.DoseTag
import com.kalazacare.app.data.remote.SupabaseClients
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalTime

/** When a round's doses can be given: from [start] (inclusive) until [end] (exclusive). */
data class RoundWindow(val start: LocalTime, val end: LocalTime) {
    fun isBeforeWindow(now: LocalTime) = now.isBefore(start)
    fun isAfterWindow(now: LocalTime) = !now.isBefore(end)
}

@Serializable
private data class RoundWindowRow(
    val tag: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
)

/**
 * The Morning/Afternoon/Evening giving windows, edited by Admins in Config → Round Times and stored in
 * the `round_windows` table. Every medicine in a round uses its round's window (not its own scheduled
 * time) for the Scan tab's gate and for when an ungiven dose turns Missed; reminders still follow each
 * medicine's own time. Read synchronously from non-UI code, so the last-known values are kept in memory
 * and in SharedPreferences (cold start / offline), starting from the defaults below. Refreshed at every
 * login/session restore (see SessionManager) and after an edit.
 */
object RoundWindows {
    private val DEFAULTS = mapOf(
        DoseTag.MORNING to RoundWindow(LocalTime.of(8, 0), LocalTime.of(9, 0)),
        DoseTag.AFTERNOON to RoundWindow(LocalTime.of(12, 30), LocalTime.of(13, 30)),
        DoseTag.EVENING to RoundWindow(LocalTime.of(19, 0), LocalTime.of(21, 0)),
    )
    private const val PREFS = "round_windows"
    private const val KEY = "windows"

    private val _windows = MutableStateFlow(DEFAULTS)
    val windows: StateFlow<Map<DoseTag, RoundWindow>> = _windows.asStateFlow()

    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun get(tag: DoseTag): RoundWindow = _windows.value[tag] ?: DEFAULTS.getValue(tag)

    /** Call once from KalazaApp.onCreate: loads the last-known windows saved on this phone. */
    fun init(context: Context) {
        appContext = context.applicationContext
        val saved = appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return
        runCatching {
            val parsed = saved.split(";").associate { part ->
                val (tag, start, end) = part.split(",")
                DoseTag.valueOf(tag) to RoundWindow(LocalTime.parse(start), LocalTime.parse(end))
            }
            _windows.value = DEFAULTS + parsed
        }
    }

    /** Fire-and-forget fetch; failures (offline, signed out) just keep the last-known values. */
    fun refreshAsync() { scope.launch { runCatching { refresh() } } }

    suspend fun refresh() {
        val rows = SupabaseClients.main.postgrest.from("round_windows").select().decodeList<RoundWindowRow>()
        val parsed = rows.mapNotNull { r ->
            runCatching { DoseTag.valueOf(r.tag) to RoundWindow(LocalTime.parse(r.startTime), LocalTime.parse(r.endTime)) }.getOrNull()
        }.toMap()
        if (parsed.isEmpty()) return
        _windows.value = DEFAULTS + parsed
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString(
            KEY, _windows.value.entries.joinToString(";") { (t, w) -> "${t.name},${w.start},${w.end}" }
        )?.apply()
    }

    /** Admin-only (RLS enforces it). Caller has already checked start < end. */
    suspend fun save(tag: DoseTag, start: LocalTime, end: LocalTime) {
        SupabaseClients.main.postgrest.from("round_windows").update(
            mapOf("start_time" to start.toString(), "end_time" to end.toString())
        ) { filter { eq("tag", tag.name) } }
        refresh()
    }
}
