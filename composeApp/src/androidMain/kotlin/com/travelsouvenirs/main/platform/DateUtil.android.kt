package com.travelsouvenirs.main.platform

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

actual fun todayLocalDate(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
