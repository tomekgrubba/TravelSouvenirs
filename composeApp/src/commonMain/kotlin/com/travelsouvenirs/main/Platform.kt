package com.travelsouvenirs.main

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform