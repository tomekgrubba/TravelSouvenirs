package com.travelsouvenirs.main.util

import platform.Foundation.NSDate

actual fun nowEpochMillis(): Long = (NSDate.date().timeIntervalSince1970() * 1000.0).toLong()
