package com.travelsouvenirs.main.util

import kotlinx.datetime.LocalDate

fun LocalDate.formatDisplay(): String =
    "$dayOfMonth ${month.name.lowercase().replaceFirstChar { it.uppercase() }} $year"

fun String?.formatDisplayDate(noneLabel: String = "none"): String {
    if (this.isNullOrBlank()) return noneLabel
    val parts = this.split("-")
    return when (parts.size) {
        1 -> parts[0] // "2024"
        2 -> { // "2024-05" -> "May 2024"
            val monthVal = parts[1].toIntOrNull() ?: return this
            val monthName = getMonthAbbreviation(monthVal)
            "$monthName ${parts[0]}"
        }
        3 -> { // "2024-05-12" -> "12 May 2024"
            val dayVal = parts[2].toIntOrNull() ?: return this
            val monthVal = parts[1].toIntOrNull() ?: return this
            val monthName = getMonthAbbreviation(monthVal)
            "$dayVal $monthName ${parts[0]}"
        }
        else -> this
    }
}

fun String?.formatTileDisplay(): String {
    if (this.isNullOrBlank()) return ""
    val parts = this.split("-")
    return when (parts.size) {
        1 -> { // "2024" -> "'24"
            val yr = parts[0].toIntOrNull() ?: return this
            "'${(yr % 100).toString().padStart(2, '0')}"
        }
        2, 3 -> { // "2024-05" -> "May '24"
            val yr = parts[0].toIntOrNull() ?: return this
            val monthVal = parts[1].toIntOrNull() ?: return this
            val monthAbbr = getMonthAbbreviation(monthVal)
            "$monthAbbr '${(yr % 100).toString().padStart(2, '0')}"
        }
        else -> this
    }
}

private fun getMonthAbbreviation(month: Int): String {
    return when (month) {
        1 -> "Jan"
        2 -> "Feb"
        3 -> "Mar"
        4 -> "Apr"
        5 -> "May"
        6 -> "Jun"
        7 -> "Jul"
        8 -> "Aug"
        9 -> "Sep"
        10 -> "Oct"
        11 -> "Nov"
        12 -> "Dec"
        else -> ""
    }
}

val DateStringComparatorDescending = Comparator<String?> { da, db ->
    when {
        da == db -> 0
        da == null -> 1  // nulls last
        db == null -> -1 // nulls last
        else -> db.compareTo(da) // descending
    }
}
