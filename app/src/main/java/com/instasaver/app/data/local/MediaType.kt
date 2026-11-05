package com.instasaver.app.data.local

enum class MediaType(val displayName: String) {
    REEL("Reel"),
    VIDEO("Video"),
    PHOTO("Photo"),
    CAROUSEL("Carousel"),
    STORY("Story");

    companion object {
        fun fromString(value: String?): MediaType {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: VIDEO
        }
    }
}
