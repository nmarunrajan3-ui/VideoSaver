package com.instasaver.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.instasaver.app.data.local.DownloadEntity
import com.instasaver.app.data.local.DownloadStatus
import com.instasaver.app.data.local.MediaType
import com.instasaver.app.ui.theme.AppleBlue
import com.instasaver.app.ui.theme.AppleGreen
import com.instasaver.app.ui.theme.AppleIndigo
import com.instasaver.app.ui.theme.AppleOrange
import com.instasaver.app.ui.theme.ApplePink
import com.instasaver.app.ui.theme.AppleRed

@Composable
fun MediaItemCard(
    item: DownloadEntity,
    onClick: () -> Unit,
    onPlayClick: () -> Unit = onClick,
    onShareClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    onRetryClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Media Thumbnail Preview with format badge
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!item.thumbnailUrl.isNullOrEmpty() || !item.localUriString.isNullOrEmpty()) {
                    AsyncImage(
                        model = item.localUriString ?: item.thumbnailUrl,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                val isVideoItem = item.localFilePath?.endsWith(".mp4") == true ||
                        item.downloadUrl.contains(".mp4") ||
                        item.downloadUrl.contains("video") ||
                        item.downloadUrl.contains(".m3u8") ||
                        item.mediaType == MediaType.REEL ||
                        item.mediaType == MediaType.VIDEO

                if (isVideoItem) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable(onClick = onPlayClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Format Badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "MP4",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    // Photo Format Badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "JPG",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Details Column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val badgeColor = when (item.mediaType) {
                        MediaType.REEL -> AppleBlue
                        MediaType.VIDEO -> AppleIndigo
                        MediaType.PHOTO, MediaType.CAROUSEL -> AppleOrange
                        MediaType.STORY -> ApplePink
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = item.mediaType.displayName.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    val statusText = when (item.status) {
                        DownloadStatus.COMPLETED -> "Saved"
                        DownloadStatus.DOWNLOADING -> "Downloading ${item.downloadProgress}%"
                        DownloadStatus.FAILED -> "Failed"
                        DownloadStatus.PENDING -> "Queued"
                        DownloadStatus.RESOLVING -> "Resolving"
                        DownloadStatus.CANCELLED -> "Cancelled"
                    }

                    val statusColor = when (item.status) {
                        DownloadStatus.COMPLETED -> AppleGreen
                        DownloadStatus.DOWNLOADING -> AppleOrange
                        DownloadStatus.FAILED -> AppleRed
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Text(
                        text = "• $statusText",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = statusColor
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = item.title.ifEmpty { "Instagram ${item.mediaType.displayName}" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (item.author.isNotEmpty()) {
                    Text(
                        text = "@${item.author}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (item.status == DownloadStatus.DOWNLOADING) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinearProgressIndicator(
                            progress = { item.downloadProgress / 100f },
                            modifier = Modifier
                                .weight(1f)
                                .height(5.dp)
                                .clip(RoundedCornerShape(2.5.dp)),
                            color = AppleBlue,
                            trackColor = Color.White.copy(alpha = 0.12f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${item.downloadProgress}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppleBlue
                        )
                    }
                } else if (item.fileSizeBytes > 0) {
                    val mb = item.fileSizeBytes / (1024f * 1024f)
                    Text(
                        text = String.format("%.2f MB", mb),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Apple Style Actions
            Row(horizontalArrangement = Arrangement.End) {
                if (item.status == DownloadStatus.FAILED || item.status == DownloadStatus.CANCELLED) {
                    IconButton(onClick = onRetryClick) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry",
                            tint = AppleBlue
                        )
                    }
                } else {
                    IconButton(
                        onClick = onShareClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
