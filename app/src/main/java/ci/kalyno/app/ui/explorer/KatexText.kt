package ci.kalyno.app.ui.explorer

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.Locale

/**
 * Moteur de formules KaTeX embarqué (assets/katex) : même moteur et même code que l'aperçu de l'admin.
 * false = retour immédiat à l'ancien moteur JLaTeXMath.
 */
internal const val KATEX_ENABLED = true

private const val KATEX_BASE = "file:///android_asset/katex/"

private object KatexCache {
    val heights = HashMap<String, Float>()
    var template: String? = null
}

internal class HeightBridge(private val onHeight: (Int) -> Unit) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun h(px: Int) {
        main.post { onHeight(px) }
    }
}

/** Contenu non interactif : les gestes passent à la liste qui défile. */
private class StaticWebView(context: Context) : WebView(context) {
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean = false
}

@SuppressLint("NewApi")
private fun isKatexUsable(context: Context): Boolean = try {
    val webView = android.os.Build.VERSION.SDK_INT < 26 || WebView.getCurrentWebViewPackage() != null
    val assets = context.assets.list("katex")?.contains("katex.min.js") == true
    webView && assets
} catch (e: Throwable) {
    false
}

/** Faux si le WebView ou les fichiers KaTeX manquent : RichText retombe alors sur JLaTeXMath. */
@Composable
internal fun katexAvailable(): Boolean {
    val context = LocalContext.current
    return remember { isKatexUsable(context) }
}

private fun buildPage(context: Context, html: String, css: String): String {
    val template = KatexCache.template
        ?: context.assets.open("katex/lesson.html").bufferedReader().use { it.readText() }
            .also { KatexCache.template = it }
    val safe = html.replace(Regex("(?i)</template"), "&lt;/template")
    return template.replace("/*__CSS__*/", css).replace("<!--__CONTENT__-->", safe)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun KatexText(
    html: String,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight,
    fontStyle: FontStyle
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val fontPx = with(density) { fontSize.toDp().value }
    val lhPx = if (lineHeight == TextUnit.Unspecified) fontPx * 1.6f else with(density) { lineHeight.toDp().value }
    val css = String.format(
        Locale.US,
        "body{color:#%06X;font-size:%.2fpx;line-height:%.2fpx;font-weight:%d;font-style:%s}",
        color.toArgb() and 0xFFFFFF, fontPx, lhPx, fontWeight.weight,
        if (fontStyle == FontStyle.Italic) "italic" else "normal"
    )
    val key = css + "|" + html.hashCode()
    var heightDp by remember(key) { mutableStateOf(KatexCache.heights[key] ?: lhPx) }
    val onHeight = rememberUpdatedState { px: Int ->
        heightDp = px.toFloat()
        KatexCache.heights[key] = px.toFloat()
    }

    AndroidView(
        modifier = Modifier.fillMaxWidth().height(heightDp.dp),
        factory = { ctx ->
            StaticWebView(ctx).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                isLongClickable = false
                settings.javaScriptEnabled = true
                settings.blockNetworkLoads = true
                settings.allowContentAccess = false
                settings.textZoom = 100
                settings.setSupportZoom(false)
                addJavascriptInterface(HeightBridge { px -> onHeight.value(px) }, "K")
            }
        },
        update = { view ->
            if (view.tag != key) {
                view.tag = key
                view.loadDataWithBaseURL(KATEX_BASE, buildPage(context, html, css), "text/html", "UTF-8", null)
            }
        },
        onRelease = { it.destroy() }
    )
}
