package com.instasaver.app.ui.screens.reels

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
import androidx.compose.material.icons.filled.Movie
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
fun ReelsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (Long) -> Unit,
    onNavigateToPlayer: (Long) -> Unit,
    onNavigateToHome: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { InstaSaverApp.instance.downloadRepository }
    val reels by repository.getDownloadsByType(MediaType.REEL).collectAsState(initial = emptyList())
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
                title = "Instagram Reels (${reels.size})",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        }

        if (reels.isEmpty()) {
            item {
                EmptyStateView(
                    icon = Icons.Default.Movie,
                    title = "No Reels Saved Yet",
                    message = "Copy an Instagram Reel link and paste it on the Home screen to save your favorite reels in original video quality.",
                    actionLabel = "Paste a Reel Link",
                    onActionClick = onNavigateToHome,
                    modifier = Modifier.padding(top = 40.dp)
                )
            }
        } else {
            items(reels, key = { it.id }) { item ->
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
    context.startActivity(Intent.createChooser(sendIntent, "Share Reel"))
}
