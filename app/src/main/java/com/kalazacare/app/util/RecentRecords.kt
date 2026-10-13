package com.kalazacare.app.util

import java.time.LocalDate

/**
 * The app only asks the database for the last [DAYS] days of records (plus whatever date range a report
 * explicitly requests) instead of whole tables — lists stay fast as history grows, and PostgREST's silent
 * 1000-row cap can no longer truncate an unordered full-table read. A patient's profile has a "Show all"
 * button that flips [showAll] for the open profile; opening a profile resets it.
 */
object RecentRecords {
    const val DAYS = 30L

    @Volatile var showAll = false

    /** Start of the default window (IST, server-anchored), for per-patient lists that honour [showAll]. */
    fun since(): LocalDate? = if (showAll) null else last30()

    /** Start of the fixed 30-day window, for lists that have their own "load older" control. */
    fun last30(): LocalDate = TrustedClock.nowIst().toLocalDate().minusDays(DAYS)
}
