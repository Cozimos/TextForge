
package textforge.integrations

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import textforge.storage.NovelStore

@Composable
fun NovelFilePicker(
    onImported: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult

        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        importing = true
        scope.launch {
            val id = withContext(Dispatchers.IO) {
                runCatching { NovelStore.importNovel(context, uri) }
            }
            importing = false
            id.onSuccess { onImported(it) }
            id.onFailure {
                Toast.makeText(context, "Import failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    if (importing) {
        Text("Importing… keep the app open")
    } else {
        Button(onClick = { picker.launch(arrayOf("text/*", "text/markdown")) }) {
            Text("Import novel")
        }
    }
}
