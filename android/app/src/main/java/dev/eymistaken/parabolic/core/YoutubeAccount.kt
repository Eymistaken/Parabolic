package dev.eymistaken.parabolic.core

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * A YouTube sign-in whose cookies yt-dlp uses, the Android counterpart of the
 * desktop Parabolic's "cookies from browser" setting.
 *
 * YouTube answers some requests with "Sign in to confirm you're not a bot";
 * the desktop app (and yt-dlp's own advice in that message) solves it with a
 * signed-in browser's cookies. Android apps cannot read a browser's cookie
 * store, so the user signs in once inside the app instead: a WebView shows
 * Google's sign-in page, and its YouTube cookies are written to a private
 * Netscape cookies.txt that every yt-dlp run gets with `--cookies`.
 *
 * Each run gets its own copy ([cookieFileFor]): yt-dlp writes the jar back
 * when it exits, and concurrent downloads must neither corrupt the file nor
 * replace the signed-in cookies with rotated ones.
 */
object YoutubeAccount {

    private const val TAG = "YoutubeAccount"

    /** Google's sign-in page, continuing to YouTube so it sets youtube.com cookies. */
    const val SIGN_IN_URL =
        "https://accounts.google.com/ServiceLogin?service=youtube&uilel=3&passive=true" +
            "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue" +
            "%26app%3Dm%26hl%3Dtr%26next%3Dhttps%253A%252F%252Fm.youtube.com%252F"

    private const val COOKIE_URL = "https://www.youtube.com"

    /** yt-dlp counts a jar as signed in with LOGIN_INFO plus one of these. */
    private val SAPISID_COOKIES = setOf("SAPISID", "__Secure-1PAPISID", "__Secure-3PAPISID")

    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    private fun file(context: Context) = File(File(context.filesDir, "cookies"), "youtube.txt")

    /** Reads the stored state; call once at start-up. */
    fun load(context: Context) {
        _signedIn.value = file(context).let { it.isFile && it.length() > 0 }
    }

    /**
     * A copy of the cookies for one yt-dlp run, or null when not signed in.
     * The caller deletes it when the run is over.
     */
    fun cookieFileFor(context: Context, runId: String): File? {
        val stored = file(context)
        if (!stored.isFile) return null
        val dir = File(context.cacheDir, "cookies").apply { mkdirs() }
        val copy = File(dir, "${runId.filter { it.isLetterOrDigit() || it == '-' }}.txt")
        return runCatching { stored.copyTo(copy, overwrite = true) }
            .onFailure { Log.w(TAG, "could not copy cookies", it) }
            .getOrNull()
    }

    /** Configures the sign-in WebView: a phone browser as far as Google can tell. */
    @SuppressLint("SetJavaScriptEnabled")
    fun prepare(webView: WebView) {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // Google refuses to sign in "embedded browsers"; the WebView marks
            // itself with "; wv" in the user agent, so present the plain
            // Chrome-on-Android one.
            userAgentString = WebSettings.getDefaultUserAgent(webView.context)
                .replace("; wv", "")
                .replace(Regex("""Version/\S+\s"""), "")
        }
    }

    /** Whether [url] is a YouTube page, where the sign-in ends up. */
    fun isYoutubePage(url: String?): Boolean {
        val host = url?.let { Uri.parse(it).host } ?: return false
        return host == "youtube.com" || host.endsWith(".youtube.com")
    }

    /**
     * Saves the WebView's YouTube cookies if they hold a signed-in session, and
     * reports whether they did. Called as the sign-in pages load; returns false
     * until Google has redirected back to YouTube with the account cookies.
     */
    fun saveFromWebView(context: Context): Boolean {
        val manager = CookieManager.getInstance()
        val header = manager.getCookie(COOKIE_URL) ?: return false
        val cookies = header.split(';')
            .mapNotNull { part ->
                val eq = part.indexOf('=')
                if (eq <= 0) null else part.substring(0, eq).trim() to part.substring(eq + 1).trim()
            }
            .filter { (name, _) -> name.isNotEmpty() }
            .distinctBy { it.first }
        val names = cookies.map { it.first }.toSet()
        if ("LOGIN_INFO" !in names || names.none { it in SAPISID_COOKIES }) return false

        manager.flush()
        // CookieManager hands out names and values only; yt-dlp just needs
        // them on .youtube.com over HTTPS. A far expiry keeps Python's cookie
        // jar from treating them as session cookies.
        val expires = (System.currentTimeMillis() + TimeUnit.DAYS.toMillis(400)) / 1000
        val text = buildString {
            append("# Netscape HTTP Cookie File\n")
            append("# Written by Parabolic from the in-app YouTube sign-in.\n\n")
            for ((name, value) in cookies) {
                append(".youtube.com\tTRUE\t/\tTRUE\t").append(expires)
                    .append('\t').append(name).append('\t').append(value).append('\n')
            }
        }
        val target = file(context)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(text)
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        _signedIn.value = true
        Log.i(TAG, "saved ${cookies.size} YouTube cookies")
        return true
    }

    /** Forgets the account: the saved cookies and the WebView's own session. */
    fun signOut(context: Context) {
        file(context).delete()
        File(context.cacheDir, "cookies").deleteRecursively()
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
        }.onFailure { Log.w(TAG, "could not clear the WebView session", it) }
        _signedIn.value = false
    }
}
