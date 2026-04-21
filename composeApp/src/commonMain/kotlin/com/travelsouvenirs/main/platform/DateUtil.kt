package com.travelsouvenirs.main.platform

import kotlinx.datetime.LocalDate

/** Returns today's [LocalDate] in the device's current time zone. */
expect fun todayLocalDate(): LocalDate
