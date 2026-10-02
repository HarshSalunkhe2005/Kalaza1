package com.kalazacare.app.util

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

fun String.toInitials(): String =
    this.trim().split(" ")
        .filter { it.isNotEmpty() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")

fun LocalDateTime.timeAgo(): String {
    val now = LocalDateTime.now()
    val minutes = ChronoUnit.MINUTES.between(this, now)
    if (minutes < 1) return "Just now"
    if (minutes < 60) return if (minutes == 1L) "1 min ago" else "$minutes mins ago"
    val hours = ChronoUnit.HOURS.between(this, now)
    if (hours < 24) return if (hours == 1L) "1 hour ago" else "$hours hours ago"
    val days = ChronoUnit.DAYS.between(this, now)
    return if (days == 1L) "1 day ago" else "$days days ago"
}
