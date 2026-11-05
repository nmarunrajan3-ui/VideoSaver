package com.instasaver.app.ui.screens.home

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.instasaver.app.InstaSaverApp
import com.instasaver.app.data.local.DownloadEntity
import com.instasaver.app.data.local.MediaType
import com.instasaver.app.data.model.ResolutionResult
import com.instasaver.app.ui.components.AppTopBar
import com.instasaver.app.ui.components.DeleteConfirmationDialog
import com.instasaver.app.ui.components.DuplicateWarningDialog
import com.instasaver.app.ui.components.LimitationNoticeDialog
import com.instasaver.app.ui.components.MediaItemCard
import com.instasaver.app.ui.components.MediaPreviewSheet
import com.instasaver.app.ui.components.PreviewPlayerDialog
import com.instasaver.app.ui.components.WebResolverDialog
import com.instasaver.app.ui.navigation.Screen
import com.instasaver.app.ui.theme.InstaBlue
import com.instasaver.app.ui.theme.InstaOrange
import com.instasaver.app.ui.theme.InstaPink
import com.instasaver.app.ui.theme.InstaPurple
import com.instasaver.app.ui.theme.InstaRed
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onNavigateToCategory: (String) -> Unit,
    onNavigateToDetails: (Long) -> Unit,
    onNavigateToPlayer: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit,
    initialSharedUrl: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { InstaSaverApp.instance.downloadRepository }
    val mediaImporter = remember { InstaSaverApp.instance.mediaImporter }

    val recentDownloads by repository.getRecentDownloads(5).collectAsState(initial = emptyList())
    val completedCount by repository.getCompletedCount().collectAsState(initial = 0)
    val totalBytes by repository.getTotalDownloadedBytes().collectAsState(initial = 0L)

    val reelsCount by repository.getDownloadsByType(MediaType.REEL).collectAsState(initial = emptyList())
    val videosCount by repository.getDownloadsByType(MediaType.VIDEO).collectAsState(initial = emptyList())
    val photosCount by repository.getDownloadsByType(MediaType.PHOTO).collectAsState(initial = emptyList())
    val storiesCount by repository.getDownloadsByType(MediaType.STORY).collectAsState(initial = emptyList())

    var inputUrl by remember { mutableStateOf(initialSharedUrl ?: "") }
    var isResolving by remember { mutableStateOf(false) }

    // Dialog & Preview States
    var previewMediaItem by remember { mutableStateOf<com.instasaver.app.data.model.MediaItem?>(null) }
    var activePlayerMediaItem by remember { mutableStateOf<com.instasaver.app.data.model.MediaItem?>(null) }
    var limitationDialogMessage by remember { mutableStateOf<String?>(null) }
    var duplicateEntity by remember { mutableStateOf<DownloadEntity?>(null) }
    var showWebResolverUrl by remember { mutableStateOf<String?>(null) }
    var itemPendingDelete by remember { mutableStateOf<DownloadEntity?>(null) }

    // Manual Media Import Picker
    val importPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val result = mediaImporter.importMedia(uri)
                if (result.isSuccess) {
                    Toast.makeText(context, "Media imported to InstaSaver library", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Import failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun startResolution(url: String) {
        if (url.isBlank()) {
            Toast.makeText(context, "Please paste a supported link", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            isResolving = true

            // Check Duplicate
            val duplicate = repository.checkDuplicate(url)
            if (duplicate != null) {
                duplicateEntity = duplicate
                isResolving = false
                return@launch
            }

            when (val res = repository.resolveUrl(url)) {
                is ResolutionResult.Success -> {
                    // Apple Design Flow: Present rich preview sheet to confirm download or watch preview
                    previewMediaItem = res.mediaItem
                    inputUrl = ""
                }
                is ResolutionResult.PrivateRestricted -> {
                    limitationDialogMessage = res.message
                }
                is ResolutionResult.StoryAuthRequired -> {
                    limitationDialogMessage = res.message
                }
                is ResolutionResult.ContentUnavailable -> {
                    limitationDialogMessage = res.message
                }
                is ResolutionResult.InvalidUrl -> {
                    Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                }
                is ResolutionResult.Error -> {
                    // Automatically fallback to In-App Video Sniffer
                    Toast.makeText(context, "Activating in-app media sniffer...", Toast.LENGTH_SHORT).show()
                    showWebResolverUrl = url
                }
            }
            isResolving = false
        }
    }

    // Auto-resolve if shared via intent initially
    LaunchedEffect(initialSharedUrl) {
        if (!initialSharedUrl.isNullOrBlank()) {
            startResolution(initialSharedUrl)
        }
    }

    // Apple Design Media Confirmation & Preview Sheet
    previewMediaItem?.let { item ->
        MediaPreviewSheet(
            mediaItem = item,
            onDownloadClick = { targetItem ->
                scope.launch {
                    repository.startDownload(targetItem)
                    val typeLabel = if (targetItem.isVideo) "Full Video (.mp4 with audio)" else "Photo"
                    Toast.makeText(context, "Download started: $typeLabel", Toast.LENGTH_SHORT).show()
                }
                previewMediaItem = null
            },
            onDownloadAllClick = { carouselItems ->
                scope.launch {
                    for ((index, cItem) in carouselItems.withIndex()) {
                        val label = if (cItem.isVideo) "Video" else "Photo"
                        val typePrefix = if (item.mediaType == MediaType.STORY) "Story" else label
                        val subItem = item.copy(
                            shortcode = "${item.shortcode}_${index + 1}",
                            originalUrl = "${item.originalUrl}#${index + 1}",
                            title = "${item.title} ($typePrefix ${index + 1})",
                            downloadUrl = cItem.url,
                            thumbnailUrl = cItem.thumbnailUrl ?: cItem.url,
                            isVideo = cItem.isVideo,
                            mediaType = if (cItem.isVideo) MediaType.VIDEO else MediaType.PHOTO,
                            carouselItems = emptyList()
                        )
                        repository.startDownload(subItem)
                    }
                    val label = if (item.mediaType == MediaType.STORY) "stories" else "items"
                    Toast.makeText(context, "Started downloading ${carouselItems.size} $label", Toast.LENGTH_SHORT).show()
                }
                previewMediaItem = null
            },
            onPreviewClick = { targetItem ->
                activePlayerMediaItem = targetItem
            },
            onDismiss = { previewMediaItem = null }
        )
    }

    // Full-Screen Preview Player / Photo Inspector
    activePlayerMediaItem?.let { item ->
        PreviewPlayerDialog(
            mediaItem = item,
            onDownloadClick = {
                scope.launch {
                    repository.startDownload(item)
                    val typeLabel = if (item.isVideo) "Full Video (.mp4 with audio)" else "Photo"
                    Toast.makeText(context, "Download started: $typeLabel", Toast.LENGTH_SHORT).show()
                }
                previewMediaItem = null
                activePlayerMediaItem = null
            },
            onDismiss = { activePlayerMediaItem = null }
        )
    }

    // Limitation Dialog with Force Web Download Option
    limitationDialogMessage?.let { msg ->
        LimitationNoticeDialog(
            message = msg,
            onForceWebDownload = {
                val target = inputUrl.ifBlank { initialSharedUrl ?: "" }
                limitationDialogMessage = null
                if (target.isNotBlank()) {
                    showWebResolverUrl = target
                }
            },
            onDismiss = { limitationDialogMessage = null }
        )
    }

    // Force Web Resolver Dialog
    showWebResolverUrl?.let { webUrl ->
        WebResolverDialog(
            targetUrl = webUrl,
            onMediaCaptured = { item ->
                showWebResolverUrl = null
                previewMediaItem = item
            },
            onDismiss = { showWebResolverUrl = null }
        )
    }

    // Duplicate Warning Dialog
    duplicateEntity?.let { dup ->
        DuplicateWarningDialog(
            existingItem = dup,
            onViewExisting = {
                duplicateEntity = null
                onNavigateToDetails(dup.id)
            },
            onDownloadAnyway = {
                duplicateEntity = null
                scope.launch {
                    isResolving = true
                    when (val res = repository.resolveUrl(dup.instagramUrl)) {
                        is ResolutionResult.Success -> {
                            previewMediaItem = res.mediaItem
                            inputUrl = ""
                        }
                        else -> {
                            Toast.makeText(context, "Unable to refresh media", Toast.LENGTH_SHORT).show()
                        }
                    }
                    isResolving = false
                }
            },
            onDismiss = { duplicateEntity = null }
        )
    }

    // Apple HIG Delete Confirmation Dialog
    itemPendingDelete?.let { item ->
        DeleteConfirmationDialog(
            itemTitle = item.title,
            onConfirmDelete = {
                scope.launch {
                    repository.deleteDownload(item.id, deleteFile = true)
                    Toast.makeText(context, "Download removed", Toast.LENGTH_SHORT).show()
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
                title = "InstaSaver",
                actions = {
                    IconButton(onClick = onNavigateToAbout) {
                        Icon(Icons.Default.Info, contentDescription = "About")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }

        // URL Input Section
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Paste Instagram Link",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Reels, Video Posts, Photos, Carousels & Stories",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Paste link (Instagram, YouTube, FB, X)...", fontSize = 14.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { startResolution(inputUrl) }
                        ),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (inputUrl.isNotEmpty()) {
                                    IconButton(onClick = { inputUrl = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = clipboard.primaryClip
                                    if (clip != null && clip.itemCount > 0) {
                                        inputUrl = clip.getItemAt(0).text?.toString() ?: ""
                                    } else {
                                        Toast.makeText(context, "Clipboard empty", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "Paste",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Apple Primary Action Button
                    Button(
                        onClick = { startResolution(inputUrl) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = !isResolving,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isResolving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Resolving Media...", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        } else {
                            Icon(Icons.Default.Download, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Download Original Media", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Apple Secondary Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { importPickerLauncher.launch("*/*") },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Import", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import File", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }

                        Button(
                            onClick = {
                                val target = inputUrl.ifBlank { "https://www.instagram.com" }
                                showWebResolverUrl = target
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Web Resolver", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // Stats Banner
        item {
            val totalMb = (totalBytes ?: 0L) / (1024f * 1024f)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$completedCount",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Saved Items",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(Color.White.copy(alpha = 0.1f))
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = String.format("%.1f MB", totalMb),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Storage Managed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Four Categories Section
        item {
            Text(
                text = "Media Categories",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CategoryGridCard(
                    title = "Reels",
                    count = reelsCount.size,
                    icon = Icons.Default.Movie,
                    gradientColors = listOf(InstaPurple, InstaPink),
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToCategory(Screen.Reels.route) }
                )
                CategoryGridCard(
                    title = "Video Posts",
                    count = videosCount.size,
                    icon = Icons.Default.VideoLibrary,
                    gradientColors = listOf(InstaPink, InstaRed),
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToCategory(Screen.Videos.route) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CategoryGridCard(
                    title = "Photos & Carousels",
                    count = photosCount.size,
                    icon = Icons.Default.Image,
                    gradientColors = listOf(InstaRed, InstaOrange),
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToCategory(Screen.Photos.route) }
                )
                CategoryGridCard(
                    title = "Stories",
                    count = storiesCount.size,
                    icon = Icons.Default.PlayCircle,
                    gradientColors = listOf(InstaOrange, InstaBlue),
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToCategory(Screen.Stories.route) }
                )
            }
        }

        // Recent Downloads Section Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Downloads",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "View All",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onNavigateToCategory(Screen.Downloads.route) }
                )
            }
        }

        if (recentDownloads.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No downloads yet",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Paste an Instagram Reel, Video, or Photo link above to start saving.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        } else {
            items(recentDownloads, key = { it.id }) { item ->
                MediaItemCard(
                    item = item,
                    onClick = { onNavigateToDetails(item.id) },
                    onPlayClick = {
                        if (item.mediaType == MediaType.REEL || item.mediaType == MediaType.VIDEO || item.mediaType == MediaType.STORY) {
                            onNavigateToPlayer(item.id)
                        } else {
                            onNavigateToDetails(item.id)
                        }
                    },
                    onShareClick = {
                        shareMedia(context, item)
                    },
                    onDeleteClick = {
                        itemPendingDelete = item
                    },
                    onRetryClick = {
                        scope.launch { repository.retryDownload(item.id) }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun CategoryGridCard(
    title: String,
    count: Int,
    icon: ImageVector,
    gradientColors: List<Color>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(92.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Brush.linearGradient(gradientColors)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$count saved",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
            type = if (item.mediaType == MediaType.PHOTO) "image/*" else "video/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            putExtra(Intent.EXTRA_TEXT, item.instagramUrl)
            type = "text/plain"
        }
    }
    context.startActivity(Intent.createChooser(sendIntent, "Share Media"))
}
