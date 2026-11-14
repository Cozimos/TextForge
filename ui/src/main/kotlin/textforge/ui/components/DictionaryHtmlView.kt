package textforge.ui.components

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun DictionaryHtmlView(
    html: String,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = false
) {
    val background = if (isDarkTheme) "#202020" else "#FFFFFF"
    val foreground = if (isDarkTheme) "#EEEEEE" else "#222222"
    val quoteBackground =
        if (isDarkTheme) "#303030" else "#F3F3F3"
    val quoteBorder =
        if (isDarkTheme) "#888888" else "#777777"

    val document = remember(html, background, foreground, quoteBackground, quoteBorder) { """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport"
                content="width=device-width, initial-scale=1">
          <style>
            body {
              margin: 12px;
              padding: 0;
              background: $background;
              color: $foreground;
              font-family: sans-serif;
              font-size: 16px;
              line-height: 1.6;
              overflow-wrap: anywhere;
            }

            blockquote {
              margin: 12px 0;
              padding: 8px 12px;
              border-left: 3px solid $quoteBorder;
              background: $quoteBackground;
            }

            img {
              max-width: 100%;
              height: auto;
            }

            table {
              max-width: 100%;
              border-collapse: collapse;
            }

            a {
              color: #4A90E2;
            }
          </style>
        </head>
        <body>
          $html
        </body>
        </html>
    """.trimIndent() }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = false

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        // Keep links from opening external pages.
                        return true
                    }
                }
            }
        },
        update = { webView ->
            if (webView.tag != document.hashCode()) {
                webView.tag = document.hashCode()
                webView.loadDataWithBaseURL(
                    "https://textforge.invalid/",
                    document,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        }
    )
}
