package com.travelsouvenirs.main.image

interface ImageLocationAnalyzer {
    suspend fun analyze(imagePath: String, lat: Double? = null, lng: Double? = null): String?
}
