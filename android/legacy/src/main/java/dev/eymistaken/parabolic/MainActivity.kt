package dev.eymistaken.parabolic

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.eymistaken.parabolic.legacy.LegacyApplication
import dev.eymistaken.parabolic.legacy.ui.LegacyRoot
import dev.eymistaken.parabolic.legacy.ui.LegacyTheme
import dev.eymistaken.parabolic.legacy.ui.extractUrl

/**
 * Lives in the root package because the shared notification code opens
 * dev.eymistaken.parabolic.MainActivity; in this APK that is the plain UI.
 */
class MainActivity : ComponentActivity() {

    private var sharedUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consume(intent)

        val app = application as LegacyApplication
        setContent {
            LegacyTheme(prefs = app.prefs) {
                LegacyRoot(
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

    private fun consume(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        extractUrl(intent.getStringExtra(Intent.EXTRA_TEXT))?.let { sharedUrl = it }
    }
}
