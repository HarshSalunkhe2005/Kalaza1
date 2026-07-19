package com.kalazacare.app.util

fun String.toInitials(): String =
    this.trim().split(" ")
        .filter { it.isNotEmpty() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")

fun String.capitalizeWords(): String =
    this.trim().split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercaseChar() } }
