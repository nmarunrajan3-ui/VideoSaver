package com.instasaver.app.ui.screens.videos

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.instasaver.app.InstaSaverApp
import com.instasaver.app.data.local.DownloadEntity
import com.instasaver.app.data.local.MediaType
import com.instasaver.app.ui.components.AppTopBar
import com.instasaver.app.ui.components.DeleteConfirmationDialog
import com.instasaver.app.ui.components.EmptyStateView
import com.instasaver.app.ui.components.MediaItemCard
import kotlinx.coroutines.launch

@Composable
fun VideosScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (Long) -> Unit,
    onNavigateToPlayer: (Long) -> Unit,
    onNavigateToHome: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { InstaSaverApp.instance.downloadRepository }
    val videos by repository.getDownloadsByType(MediaType.VIDEO).collectAsState(initial = emptyList())
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            AppTopBar(
                title = "Video Posts (${videos.size})",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        }

        if (videos.isEmpty()) {
            item {
                EmptyStateView(
                    icon = Icons.Default.VideoLibrary,
                    title = "No Video Posts Saved",
                    message = "Save your favorite Instagram video posts and IGTV content directly to your Movies/InstaSaver folder.",
                    actionLabel = "Add Video Link",
                    onActionClick = onNavigateToHome,
                    modifier = Modifier.padding(top = 40.dp)
                )
            }
        } else {
            items(videos, key = { it.id }) { item ->
                MediaItemCard(
                    item = item,
                    onClick = { onNavigateToDetails(item.id) },
                    onPlayClick = { onNavigateToPlayer(item.id) },
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
            type = "video/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            putExtra(Intent.EXTRA_TEXT, item.instagramUrl)
            type = "text/plain"
        }
    }
    context.startActivity(Intent.createChooser(sendIntent, "Share Video"))
}
