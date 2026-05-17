package com.travelsouvenirs.main.util

import platform.Foundation.NSHomeDirectory

actual fun localImageModel(path: String): Any {
    // Extract just the filename from whatever form is stored (old absolute path or new filename).
    // This keeps photos visible after reinstalls, which rotate the data-container UUID.
    val filename = path.substringAfterLast("/")
    return "file://${NSHomeDirectory()}/Documents/item_photos/$filename"
}
