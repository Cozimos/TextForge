
package textforge.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import textforge.integrations.NovelFilePicker
import textforge.storage.NovelMetadata
import textforge.storage.NovelStore
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun LibraryScreen(
    onOpenNovel: (String) -> Unit
) {
    val context = LocalContext.current
    var novels by remember { mutableStateOf(emptyList<NovelMetadata>()) }

    fun refresh() {
        novels = NovelStore.getLibrary(context)
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        NovelFilePicker {
            refresh()
        }

        LazyColumn {
            items(novels) { novel ->
                Text(
                    text = novel.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenNovel(novel.id) }
                        .padding(16.dp)
                )
            }
        }
    }
}

data class NovelItem(
    val title: String,
    val content: String,
    val progress: String = "Not started"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    novels: List<NovelItem>,
    onAddNovel: () -> Unit,
    onOpenNovel: (NovelItem) -> Unit
) {
    Scaffold(containerColor = NovelColors.Background) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
        ) {

            Text(
                "Your Library",
                style = MaterialTheme.typography.headlineLarge
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Your personal reading space.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(32.dp))

            if (novels.isEmpty()) {

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        Text("📚", fontSize = 56.sp)

                        Spacer(Modifier.height(16.dp))

                        Text(
                            "Your library is waiting",
                            style = MaterialTheme.typography.titleLarge
                        )

                        Text(
                            "Add your first novel to start reading.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(24.dp))

                        Button(onClick = onAddNovel) {
                            Text("Add Novel")
                        }
                    }
                }

            } else {

                novels.forEach { novel ->

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clickable { onOpenNovel(novel) },
                        shape = RoundedCornerShape(16.dp)
                    ) {

                        Column(Modifier.padding(18.dp)) {

                            Text(
                                novel.title,
                                style = MaterialTheme.typography.titleMedium
                            )

                            Text(
                                novel.progress,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onAddNovel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("+ Add Novel")
                }
            }
        }
    }
}
