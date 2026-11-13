
package textforge.integrations

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

        scope.launch {
            val id = withContext(Dispatchers.IO) {
                runCatching { NovelStore.importNovel(context, uri) }.getOrNull()
            }
            if (id != null) {
                onImported(id)
            } else {
                Toast.makeText(context, "Import failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Button(
        onClick = {
            picker.launch(arrayOf("text/*", "text/markdown"))
        }
    ) {
        Text("Import novel")
    }
}
