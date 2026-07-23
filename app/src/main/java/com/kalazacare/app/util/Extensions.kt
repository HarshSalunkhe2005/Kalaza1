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
    if (minutes < 60) return "$minutes mins ago"
    val hours = ChronoUnit.HOURS.between(this, now)
    if (hours < 24) return "$hours hours ago"
    val days = ChronoUnit.DAYS.between(this, now)
    return "$days days ago"
}
