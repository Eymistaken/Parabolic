package dev.eymistaken.parabolic.ui.components

import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.eymistaken.parabolic.core.YoutubeAccount

/**
 * Google's sign-in page in a full-screen WebView. As soon as it lands back on
 * YouTube with a signed-in session, the cookies are saved for yt-dlp and
 * [onSignedIn] runs; the back gesture steps back through the pages first.
 */
@Composable
fun YoutubeSignInDialog(onDismiss: () -> Unit, onSignedIn: () -> Unit) {
    val signedIn by rememberUpdatedState(onSignedIn)
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableFloatStateOf(0f) }
    var done by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = {
            val view = webView
            if (view != null && view.canGoBack()) view.goBack() else onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column {
                TopAppBar(
                    title = { Text("YouTube'a giriş", style = MaterialTheme.typography.titleLargeEmphasized) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Kapat") }
                    },
                    windowInsets = WindowInsets(0),
                )
                Box {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            WebView(context).apply {
                                YoutubeAccount.prepare(this)
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                        loading = 0.05f
                                    }

                                    override fun onPageFinished(view: WebView, url: String?) {
                                        loading = 1f
                                        if (!done && YoutubeAccount.isYoutubePage(url) &&
                                            YoutubeAccount.saveFromWebView(context)
                                        ) {
                                            done = true
                                            signedIn()
                                        }
                                    }
                                }
                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                                        loading = newProgress / 100f
                                    }
                                }
                                loadUrl(YoutubeAccount.SIGN_IN_URL)
                                webView = this
                            }
                        },
                        onRelease = { it.destroy() },
                    )
                    if (loading < 1f) {
                        LinearWavyProgressIndicator(progress = { loading }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
