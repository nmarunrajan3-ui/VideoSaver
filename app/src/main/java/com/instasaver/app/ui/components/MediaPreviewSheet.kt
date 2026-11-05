package com.instasaver.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.instasaver.app.data.local.MediaType
import com.instasaver.app.data.model.CarouselItem
import com.instasaver.app.data.model.MediaItem
import com.instasaver.app.network.InstagramUrlParser
import com.instasaver.app.network.Platform

/**
 * Apple Design Inspired Media Confirmation & Multi-Item Post/Story Preview Sheet.
 * Displays when a media item or album is shared/resolved, allowing the user
 * to review all 3, 4, 10 items in the preview, and download individually or all at once.
 */
@Composable
fun MediaPreviewSheet(
    mediaItem: MediaItem,
    onDownloadClick: (MediaItem) -> Unit,
    onDownloadAllClick: (List<CarouselItem>) -> Unit = {},
    onPreviewClick: (MediaItem) -> Unit,
    onDismiss: () -> Unit
) {
    val parsed = InstagramUrlParser.parse(mediaItem.originalUrl)
    val platform = parsed?.platform ?: Platform.INSTAGRAM

    val platformColor = when (platform) {
        Platform.INSTAGRAM -> Color(0xFFE1306C)
        Platform.YOUTUBE -> Color(0xFFFF0000)
        Platform.FACEBOOK -> Color(0xFF1877F2)
        Platform.X_TWITTER -> Color(0xFF1DA1F2)
    }

    val hasMultipleItems = mediaItem.carouselItems.isNotEmpty()
    var selectedIndex by remember { mutableIntStateOf(0) }

    val activeCarouselItem = if (hasMultipleItems) {
        mediaItem.carouselItems.getOrElse(selectedIndex) { mediaItem.carouselItems[0] }
    } else null

    val currentDownloadUrl = activeCarouselItem?.url ?: mediaItem.downloadUrl
    val currentThumbnailUrl = activeCarouselItem?.thumbnailUrl
        ?: activeCarouselItem?.url
        ?: mediaItem.thumbnailUrl
        ?: mediaItem.downloadUrl
    val currentIsVideo = activeCarouselItem?.isVideo ?: mediaItem.isVideo

    val activeItem = mediaItem.copy(
        shortcode = if (hasMultipleItems) "${mediaItem.shortcode}_${selectedIndex + 1}" else mediaItem.shortcode,
        title = if (hasMultipleItems) "${mediaItem.title} (#${selectedIndex + 1})" else mediaItem.title,
        originalUrl = if (hasMultipleItems) "${mediaItem.originalUrl}#${selectedIndex + 1}" else mediaItem.originalUrl,
        downloadUrl = currentDownloadUrl,
        thumbnailUrl = currentThumbnailUrl,
        isVideo = currentIsVideo,
        mediaType = if (currentIsVideo) MediaType.VIDEO else MediaType.PHOTO,
        carouselItems = emptyList()
    )

    val typeLabel = when {
        mediaItem.mediaType == MediaType.STORY && hasMultipleItems -> "Stories (${mediaItem.carouselItems.size})"
        mediaItem.mediaType == MediaType.STORY -> "Story"
        hasMultipleItems -> "Post Album (${mediaItem.carouselItems.size})"
        mediaItem.mediaType == MediaType.REEL -> "Reel"
        mediaItem.mediaType == MediaType.VIDEO -> "Video"
        else -> "Photo"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) {}
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
                color = Color(0xFF1C1C1E),
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Apple Modal Grabber
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(Color.White.copy(alpha = 0.28f))
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Header Row with Platform Badge & Close Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Platform Tag Pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(platformColor.copy(alpha = 0.16f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(platformColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${platform.displayName} • $typeLabel",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFFA0A0A5),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Media Thumbnail Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF2C2C2E))
                            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
                            .clickable { onPreviewClick(activeItem) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentThumbnailUrl.isNotBlank()) {
                            AsyncImage(
                                model = currentThumbnailUrl,
                                contentDescription = mediaItem.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        // Center Play Button Overlay for Videos
                        if (currentIsVideo) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .border(1.5.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            // Bottom Quality Tag
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(12.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.75f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "1080p Video • Audio Included",
                                    color = Color(0xFF34C759),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            // Bottom Photo Tag
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(12.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.75f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Original High-Resolution Photo",
                                    color = Color(0xFF007AFF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Top-Right Item Counter Badge
                        if (hasMultipleItems) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.75f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${selectedIndex + 1} of ${mediaItem.carouselItems.size}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Left Chevron Arrow for reviewing previous slide
                        if (hasMultipleItems && selectedIndex > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 8.dp)
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .clickable { selectedIndex-- },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = "Previous Slide",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Right Chevron Arrow for reviewing next slide
                        if (hasMultipleItems && selectedIndex < mediaItem.carouselItems.size - 1) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 8.dp)
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .clickable { selectedIndex++ },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Next Slide",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    // Multi-story / Carousel Item Selector Strip
                    if (hasMultipleItems) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(mediaItem.carouselItems) { index, item ->
                                val isSelected = index == selectedIndex
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF2C2C2E))
                                        .border(
                                            width = if (isSelected) 2.dp else 0.5.dp,
                                            color = if (isSelected) Color(0xFF007AFF) else Color.White.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { selectedIndex = index },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val thumb = item.thumbnailUrl ?: item.url
                                    if (thumb.isNotBlank()) {
                                        AsyncImage(
                                            model = thumb,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    if (item.isVideo) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.6f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Title & Author Text
                    Text(
                        text = mediaItem.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (mediaItem.author.isNotBlank()) {
                        Text(
                            text = "@${mediaItem.author}",
                            color = Color(0xFFA0A0A5),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Apple HIG Action Buttons
                    if (hasMultipleItems) {
                        val count = mediaItem.carouselItems.size
                        val allLabel = if (mediaItem.mediaType == MediaType.STORY) "$count Stories" else "$count Photos & Videos"
                        val singleLabel = if (mediaItem.mediaType == MediaType.STORY) "Story" else if (activeItem.isVideo) "Video" else "Photo"

                        // Button 1: Download All (3, 4, 10 items)
                        Button(
                            onClick = { onDownloadAllClick(mediaItem.carouselItems) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF007AFF)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Download All ($allLabel)",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Button 2: Download Selected Item
                        OutlinedButton(
                            onClick = { onDownloadClick(activeItem) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFF2C2C2E)
                            ),
                            border = null
                        ) {
                            Text(
                                text = "Download This $singleLabel (#${selectedIndex + 1})",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Button 3: Watch Preview with Audio / Fullscreen Photo
                        OutlinedButton(
                            onClick = { onPreviewClick(activeItem) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.Transparent
                            ),
                            border = null
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                tint = Color(0xFFA0A0A5),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (activeItem.isVideo) "Watch Preview with Audio" else "Inspect Fullscreen Photo",
                                color = Color(0xFFA0A0A5),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }

                    } else {
                        // Single Item Action Buttons
                        Button(
                            onClick = { onDownloadClick(activeItem) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF007AFF)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (activeItem.isVideo) "Download Full Video (.mp4)" else "Download Photo (.jpg)",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (activeItem.isVideo) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { onPreviewClick(activeItem) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color(0xFF2C2C2E)
                                ),
                                border = null
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Preview Video Before Saving",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
