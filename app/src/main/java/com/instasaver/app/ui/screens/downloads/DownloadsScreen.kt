package com.instasaver.app.ui.screens.downloads

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.instasaver.app.InstaSaverApp
import com.instasaver.app.data.local.DownloadEntity
import com.instasaver.app.data.local.DownloadStatus
import com.instasaver.app.data.local.MediaType
import com.instasaver.app.ui.components.AppTopBar
import com.instasaver.app.ui.components.DeleteConfirmationDialog
import com.instasaver.app.ui.components.EmptyStateView
import com.instasaver.app.ui.components.MediaItemCard
import kotlinx.coroutines.launch

@Composable
fun DownloadsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (Long) -> Unit,
    onNavigateToPlayer: (Long) -> Unit,
    onNavigateToHome: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { InstaSaverApp.instance.downloadRepository }

    val allDownloads by repository.getAllDownloads().collectAsState(initial = emptyList())
    var selectedTab by remember { mutableIntStateOf(0) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var itemPendingDelete by remember { mutableStateOf<DownloadEntity?>(null) }

    val tabs = listOf("All", "Active", "Completed", "Failed")

    val filteredList = when (selectedTab) {
        1 -> allDownloads.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING || it.status == DownloadStatus.RESOLVING }
        2 -> allDownloads.filter { it.status == DownloadStatus.COMPLETED }
        3 -> allDownloads.filter { it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED }
        else -> allDownloads
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear Download History?") },
            text = { Text("This will clear records from your history. Saved media files on your device storage will remain intact.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch { repository.clearHistory() }
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            title = "Downloads (${allDownloads.size})",
            canNavigateBack = true,
            onNavigateBack = onNavigateBack,
            actions = {
                if (allDownloads.isNotEmpty()) {
                    IconButton(onClick = { showClearConfirmDialog = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear History")
                    }
                }
            }
        )

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp)
        ) {
            if (filteredList.isEmpty()) {
                item {
                    val msg = when (selectedTab) {
                        1 -> "No active downloads currently in progress."
                        2 -> "No completed downloads found."
                        3 -> "No failed downloads."
                        else -> "Your download history is currently empty."
                    }
                    EmptyStateView(
                        icon = Icons.Default.Download,
                        title = "No Items Found",
                        message = msg,
                        actionLabel = if (selectedTab == 0) "Start Downloading" else null,
                        onActionClick = onNavigateToHome,
                        modifier = Modifier.padding(top = 40.dp)
                    )
                }
            } else {
                items(filteredList, key = { it.id }) { item ->
                    MediaItemCard(
                        item = item,
                        onClick = { onNavigateToDetails(item.id) },
                        onPlayClick = {
                            if (item.mediaType == MediaType.PHOTO) {
                                onNavigateToDetails(item.id)
                            } else {
                                onNavigateToPlayer(item.id)
                            }
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
}

private fun shareMedia(context: Context, item: DownloadEntity) {
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        if (!item.localUriString.isNullOrEmpty()) {
            putExtra(Intent.EXTRA_STREAM, Uri.parse(item.localUriString))
            type = if (item.mediaType == MediaType.PHOTO) "image/*" else "video/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            putExtra(Intent.EXTRA_TEXT, item.instagramUrl)
            type = "text/plain"
        }
    }
    context.startActivity(Intent.createChooser(sendIntent, "Share Media"))
}
