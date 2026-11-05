package com.instasaver.app.ui.screens.photos

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.instasaver.app.InstaSaverApp
import com.instasaver.app.data.local.DownloadEntity
import com.instasaver.app.data.local.MediaType
import com.instasaver.app.ui.components.AppTopBar
import com.instasaver.app.ui.components.DeleteConfirmationDialog
import com.instasaver.app.ui.components.EmptyStateView
import com.instasaver.app.ui.components.MediaItemCard
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@Composable
fun PhotosScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (Long) -> Unit,
    onNavigateToHome: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { InstaSaverApp.instance.downloadRepository }

    val photosFlow = remember {
        combine(
            repository.getDownloadsByType(MediaType.PHOTO),
            repository.getDownloadsByType(MediaType.CAROUSEL)
        ) { photos, carousels ->
            (photos + carousels).sortedByDescending { it.createdAt }
        }
    }
    val photoItems by photosFlow.collectAsState(initial = emptyList())

    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var itemPendingDelete by remember { mutableStateOf<DownloadEntity?>(null) }

    // Apple HIG Delete Confirmation Dialog
    itemPendingDelete?.let { item ->
        DeleteConfirmationDialog(
            itemTitle = item.title,
            onConfirmDelete = {
                scope.launch {
                    repository.deleteDownload(item.id, deleteFile = true)
                }
                itemPendingDelete = null
            },
            onDismiss = { itemPendingDelete = null }
        )
    }

    // Fullscreen Photo Dialog
    previewImageUrl?.let { imageUrl ->
        Dialog(
            onDismissRequest = { previewImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { previewImageUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 40.dp, end = 16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            AppTopBar(
                title = "Photos & Carousels (${photoItems.size})",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        }

        if (photoItems.isEmpty()) {
            item {
                EmptyStateView(
                    icon = Icons.Default.Image,
                    title = "No Photos Saved",
                    message = "Download high-resolution Instagram photos and album carousels directly to your Pictures/InstaSaver folder.",
                    actionLabel = "Add Photo Link",
                    onActionClick = onNavigateToHome,
                    modifier = Modifier.padding(top = 40.dp)
                )
            }
        } else {
            items(photoItems, key = { it.id }) { item ->
                MediaItemCard(
                    item = item,
                    onClick = {
                        val url = item.localUriString ?: item.downloadUrl
                        if (url.isNotEmpty()) {
                            previewImageUrl = url
                        } else {
                            onNavigateToDetails(item.id)
                        }
                    },
                    onPlayClick = {
                        val url = item.localUriString ?: item.downloadUrl
                        previewImageUrl = url
                    },
                    onShareClick = { shareMedia(context, item) },
                    onDeleteClick = {
                        itemPendingDelete = item
                    },
                    onRetryClick = {
                        scope.launch { repository.retryDownload(item.id) }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }
}

private fun shareMedia(context: Context, item: DownloadEntity) {
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        if (!item.localUriString.isNullOrEmpty()) {
            putExtra(Intent.EXTRA_STREAM, Uri.parse(item.localUriString))
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            putExtra(Intent.EXTRA_TEXT, item.instagramUrl)
            type = "text/plain"
        }
    }
    context.startActivity(Intent.createChooser(sendIntent, "Share Photo"))
}
