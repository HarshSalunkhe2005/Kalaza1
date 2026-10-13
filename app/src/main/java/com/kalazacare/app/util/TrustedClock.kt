package com.kalazacare.app.util

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.kalazacare.app.data.remote.SupabaseClients
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The giving window is judged against the SERVER's time, not the phone's wall clock (which anyone can
 * change). On each sync (login/session restore, connectivity regained, opening the Scan tab) we ask the
 * database for its time and remember it together with [SystemClock.elapsedRealtime] — a monotonic timer
 * that keeps counting through sleep and that changing the date/time settings cannot move. From then on
 * "now" = server time at sync + elapsed since, which also works offline. A reboot resets that timer, so
 * a baseline from an earlier boot is discarded and the clock is untrusted until the next online sync
 * (the Scan tab then blocks ticking, see ScanScreen). The clinic runs on IST, so all window math is IST.
 */
object TrustedClock {
    val IST: ZoneId = ZoneId.of("Asia/Kolkata")
    private const val PREFS = "trusted_clock"

    private var appContext: Context? = null
    @Volatile private var baseServerMs = 0L
    @Volatile private var baseElapsed = 0L
    @Volatile private var baseBoot = -1
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        appContext = context.applicationContext
        val p = appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        baseServerMs = p.getLong("serverMs", 0L)
        baseElapsed = p.getLong("elapsed", 0L)
        baseBoot = p.getInt("boot", -1)
    }

    private fun bootCount(): Int =
        appContext?.let { Settings.Global.getInt(it.contentResolver, Settings.Global.BOOT_COUNT, -1) } ?: -1

    /** True when a server-anchored baseline from THIS boot exists. */
    fun isTrusted(): Boolean =
        baseServerMs > 0 && baseBoot == bootCount() && SystemClock.elapsedRealtime() >= baseElapsed

    private fun trustedMs(): Long = baseServerMs + (SystemClock.elapsedRealtime() - baseElapsed)

    /** Server-anchored IST time, or null if we have no trusted baseline (never synced this boot). */
    fun nowIstOrNull(): LocalDateTime? =
        if (isTrusted()) LocalDateTime.ofInstant(Instant.ofEpochMilli(trustedMs()), IST) else null

    /** Trusted IST time, falling back to the phone's clock — for display/status only, never to authorize a tick. */
    fun nowIst(): LocalDateTime = nowIstOrNull() ?: LocalDateTime.now(IST)

    /** ISO timestamp with offset for stamping records: trusted time when available, else the phone's. */
    fun nowIso(): String {
        val ms = if (isTrusted()) trustedMs() else System.currentTimeMillis()
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault()).toString()
    }

    fun syncAsync() { scope.launch { runCatching { sync() } } }

    /** Asks the database for its time (RPC `server_time`) and re-anchors. Needs a signed-in session. */
    suspend fun sync() {
        val before = SystemClock.elapsedRealtime()
        val iso = SupabaseClients.main.postgrest.rpc("server_time").decodeAs<String>()
        val after = SystemClock.elapsedRealtime()
        val serverMs = OffsetDateTime.parse(iso).withOffsetSameInstant(ZoneOffset.UTC).toInstant().toEpochMilli()
        // The server read its clock about halfway through the round trip.
        baseServerMs = serverMs
        baseElapsed = (before + after) / 2
        baseBoot = bootCount()
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putLong("serverMs", baseServerMs)?.putLong("elapsed", baseElapsed)?.putInt("boot", baseBoot)?.apply()
    }
}
