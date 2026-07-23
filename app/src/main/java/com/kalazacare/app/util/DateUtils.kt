package com.kalazacare.app.util

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object DateUtils {

    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("d/M/yyyy")
    private val DATE_LONG      = DateTimeFormatter.ofPattern("d MMM yyyy")
    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("hh:mm a")

    fun formatDate(date: LocalDate): String     = date.format(DATE_FORMATTER)
    fun formatDateLong(date: LocalDate): String = date.format(DATE_LONG)
    fun formatTime(time: LocalTime): String     = time.format(TIME_FORMATTER)
}
