package com.travelsouvenirs.main.sync

/** Reads the file at [path] as a byte array. */
expect fun readLocalFileBytes(path: String): ByteArray

/** Returns true if a file exists at [path]. */
expect fun localFileExists(path: String): Boolean

/** Downloads the resource at [url] and writes it to [localPath]. */
expect suspend fun downloadUrlToFile(url: String, localPath: String)

/** Converts a [ByteArray] to the platform-specific Firebase Storage [Data] type. */
expect fun ByteArray.toFirebaseData(): dev.gitlive.firebase.storage.Data
