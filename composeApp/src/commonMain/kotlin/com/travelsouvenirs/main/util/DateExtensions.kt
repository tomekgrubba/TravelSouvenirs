package com.travelsouvenirs.main.util

import kotlinx.datetime.LocalDate

fun LocalDate.formatDisplay(): String =
    "$dayOfMonth ${month.name.lowercase().replaceFirstChar { it.uppercase() }} $year"
