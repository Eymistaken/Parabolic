package dev.eymistaken.parabolic

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.eymistaken.parabolic.ui.ParabolicRoot
import dev.eymistaken.parabolic.ui.extractUrl
import dev.eymistaken.parabolic.ui.theme.ParabolicTheme

class MainActivity : ComponentActivity() {

    private var sharedUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consume(intent)

        val app = application as ParabolicApplication
        setContent {
            ParabolicTheme(prefs = app.prefs) {
                ParabolicRoot(
                    prefs = app.prefs,
                    repo = app.downloads,
                    sharedUrl = sharedUrl,
                    onSharedUrlConsumed = { sharedUrl = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consume(intent)
    }

    /** "Share to Parabolic" hands us a caption plus a link; keep the link. */
    private fun consume(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        extractUrl(text)?.let { sharedUrl = it }
    }
}
