package textforge.ui.library

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import textforge.integrations.NovelFilePicker
import textforge.storage.NovelMetadata
import textforge.storage.NovelStore
import textforge.ui.NovelColors

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onOpenNovel: (String) -> Unit,
    incomingUri: Uri? = null,
    onIncomingUriConsumed: () -> Unit = {}
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var novels by remember { mutableStateOf<List<NovelMetadata>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var deleteTarget by remember { mutableStateOf<NovelMetadata?>(null) }

    suspend fun refreshLibrary() {
        novels = withContext(Dispatchers.IO) {
            runCatching { NovelStore.getLibrary(context) }.getOrDefault(emptyList())
        }
        loading = false
    }

    LaunchedEffect(Unit) { refreshLibrary() }

    LaunchedEffect(incomingUri) {
        val uri = incomingUri ?: return@LaunchedEffect
        loading = true
        val imported = withContext(Dispatchers.IO) {
            runCatching { NovelStore.importNovel(context, uri) }
        }
        refreshLibrary()
        onIncomingUriConsumed()
        imported.onSuccess(onOpenNovel)
    }

    Scaffold(containerColor = NovelColors.Background) { scaffoldPadding ->
        Column(
            modifier = Modifier.fillMaxSize().background(NovelColors.Background)
                .padding(scaffoldPadding).padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Book, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("Your Library", style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(Modifier.height(6.dp))
            Text("Your personal reading space.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))

            if (loading) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (novels.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Book, contentDescription = null,
                            modifier = Modifier.size(52.dp),
                            tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.height(16.dp))
                        Text("Your library is waiting", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(6.dp))
                        Text("Add your first novel to start reading.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(22.dp))
                        NovelFilePicker { id -> scope.launch { refreshLibrary(); onOpenNovel(id) } }
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(novels, key = { it.id }) { novel ->
                        Card(
                            modifier = Modifier.fillMaxWidth().combinedClickable(
                                onClick = { onOpenNovel(novel.id) },
                                onLongClick = { deleteTarget = novel }
                            ),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Book, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(novel.title, style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface)
                                    Text("Tap to read · Long-press to delete",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Default.Delete, contentDescription = "Delete book",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                NovelFilePicker { id -> scope.launch { refreshLibrary(); onOpenNovel(id) } }
            }
        }
    }

    deleteTarget?.let { novel ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete book?") },
            text = { Text("“${novel.title}” and its cached chapters and reading progress will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    scope.launch {
                        withContext(Dispatchers.IO) { NovelStore.deleteNovel(context, novel.id) }
                        refreshLibrary()
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}
