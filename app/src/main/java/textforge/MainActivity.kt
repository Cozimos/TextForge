package textforge

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import textforge.navigation.AppNavigation

class MainActivity : ComponentActivity() {
    private val incomingUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingUri.value = extractUri(intent)
        setContent {
            AppNavigation(
                incomingUri = incomingUri.value,
                onIncomingUriConsumed = { incomingUri.value = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingUri.value = extractUri(intent)
    }

    @Suppress("DEPRECATION")
    private fun extractUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.let { shared ->
                    val parsed = runCatching { Uri.parse(shared) }.getOrNull()
                    when {
                        parsed?.scheme == "content" || parsed?.scheme == "file" -> parsed
                        File(shared).isAbsolute -> Uri.fromFile(File(shared))
                        else -> null
                    }
                }
            else -> null
        }
    }
}
