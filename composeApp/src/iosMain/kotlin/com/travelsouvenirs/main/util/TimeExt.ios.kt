@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.travelsouvenirs.main.util

import platform.posix.time

actual fun nowEpochMillis(): Long = time(null) * 1000L
