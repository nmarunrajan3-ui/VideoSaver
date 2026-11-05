package com.instasaver.app.data.model

import com.instasaver.app.data.local.MediaType

data class CarouselItem(
    val url: String,
    val isVideo: Boolean,
    val thumbnailUrl: String? = null
)

data class MediaItem(
    val shortcode: String,
    val originalUrl: String,
    val mediaType: MediaType,
    val title: String,
    val author: String,
    val downloadUrl: String,
    val thumbnailUrl: String?,
    val carouselItems: List<CarouselItem> = emptyList(),
    val isVideo: Boolean = mediaType == MediaType.REEL || mediaType == MediaType.VIDEO
)

sealed interface ResolutionResult {
    data class Success(val mediaItem: MediaItem) : ResolutionResult
    data class PrivateRestricted(
        val shortcode: String?,
        val message: String = "This Instagram post or account is private. InstaSaver respects privacy and access controls and does not bypass private account restrictions."
    ) : ResolutionResult
    data class StoryAuthRequired(
        val message: String = "Instagram Stories require user account authorization or ownership. InstaSaver only accesses authorized or public media without unauthorized scraping."
    ) : ResolutionResult
    data class ContentUnavailable(
        val message: String = "This media could not be found or has been removed from Instagram."
    ) : ResolutionResult
    data class InvalidUrl(
        val message: String = "The provided URL is not a valid Instagram link. Please paste a valid Reel, Video, Post, or Story link."
    ) : ResolutionResult
    data class Error(
        val message: String
    ) : ResolutionResult
}
