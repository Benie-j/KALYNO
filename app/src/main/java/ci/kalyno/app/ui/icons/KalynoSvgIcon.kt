package ci.kalyno.app.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.caverock.androidsvg.SVG

@Composable
fun KalynoSvgIcon(
    name: String,
    size: Dp = 24.dp,
    tint: Color = Color(0xFF2457C5)
) {
    val context = LocalContext.current
    val svg = remember(name, context) {
        runCatching {
            SVG.getFromAsset(context.assets, "icons/$name.svg")
        }.getOrNull()
    }

    Canvas(Modifier.size(size)) {
        val document = svg ?: return@Canvas
        val canvas = drawContext.canvas.nativeCanvas
        val paint = Paint().apply {
            colorFilter = PorterDuffColorFilter(tint.toArgb(), PorterDuff.Mode.SRC_IN)
        }
        val save = canvas.saveLayer(0f, 0f, size.width, size.height, paint)
        canvas.scale(
            size.width.toPx() / (document.documentWidth.takeIf { it > 0f } ?: 24f),
            size.height.toPx() / (document.documentHeight.takeIf { it > 0f } ?: 24f)
        )
        document.renderToCanvas(canvas)
        canvas.restoreToCount(save)
    }
}
