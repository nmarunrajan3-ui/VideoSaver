package com.instasaver.app.ui.screens.stories

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.instasaver.app.InstaSaverApp
import com.instasaver.app.data.local.DownloadEntity
import com.instasaver.app.data.local.MediaType
import com.instasaver.app.ui.components.AppTopBar
import com.instasaver.app.ui.components.DeleteConfirmationDialog
import com.instasaver.app.ui.components.EmptyStateView
import com.instasaver.app.ui.components.MediaItemCard
import com.instasaver.app.ui.theme.InstaOrange
import com.instasaver.app.ui.theme.InstaPink
import kotlinx.coroutines.launch

@Composable
fun StoriesScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (Long) -> Unit,
    onNavigateToPlayer: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { InstaSaverApp.instance.downloadRepository }
    val mediaImporter = remember { InstaSaverApp.instance.mediaImporter }

    val stories by repository.getDownloadsByType(MediaType.STORY).collectAsState(initial = emptyList())
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

    val importStoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val result = mediaImporter.importMedia(uri, assignedType = MediaType.STORY, customTitle = "User Story Archive")
                if (result.isSuccess) {
                    Toast.makeText(context, "Story archived to InstaSaver", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Import failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
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
                title = "Stories (${stories.size})",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        }

        // Compliance & Authorized Access Banner
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = InstaPink,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Authorized Access & Ownership",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Instagram Stories require authorized access or direct content ownership. InstaSaver strictly adheres to Instagram access policies and never intercepts private account tokens or passwords.\n\n" +
                                "To archive your own stories or stories you have authorized access to, use the Manual Media Import below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { importStoryLauncher.launch("*/*") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Archive / Import My Story")
                    }
                }
            }
        }

        if (stories.isEmpty()) {
            item {
                EmptyStateView(
                    icon = Icons.Default.PlayCircle,
                    title = "No Stories Archived",
                    message = "Import and archive your authorized Instagram Stories to preserve them locally before they expire in 24 hours.",
                    modifier = Modifier.padding(top = 20.dp)
                )
            }
        } else {
            items(stories, key = { it.id }) { item ->
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
    context.startActivity(Intent.createChooser(sendIntent, "Share Story"))
}
