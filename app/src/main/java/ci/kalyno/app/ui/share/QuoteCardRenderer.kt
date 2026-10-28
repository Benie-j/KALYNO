package ci.kalyno.app.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import ci.kalyno.app.data.dashboard.DailyQuote
import com.caverock.androidsvg.SVG

/**
 * Dessine la carte de partage KALYNO (format vertical 9:16, idéal Stories / statuts).
 *
 * Tout est dessiné au Canvas dans un Bitmap : le rendu est identique sur tous les téléphones,
 * ne dépend pas de la taille d'écran et ne demande aucune vue à l'écran. À appeler hors du
 * thread principal (Dispatchers.Default).
 */
object QuoteCardRenderer {

    const val WIDTH = 1080
    const val HEIGHT = 1920

    private const val MARGIN = 110f

    private val TEAL = Color.parseColor("#0F766E")
    private val TEAL_DEEP = Color.parseColor("#0A3F3B")
    private val AMBER = Color.parseColor("#F59E0B")
    private val AMBER_SOFT = Color.parseColor("#FCD34D")

    // Badges des stores (pied de carte)
    private const val BADGE_WIDTH = 400f
    private const val BADGE_HEIGHT = 88f
    private const val BADGE_RADIUS = 26f
    private const val BADGE_ICON = 52f
    private const val BADGE_ICON_GAP = 18f
    private const val BADGE_CAPTION_GAP = 18f
    private const val STORE_CAPTION = "Bientôt disponible"

    fun render(context: Context, quote: DailyQuote): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = WIDTH.toFloat()
        val h = HEIGHT.toFloat()
        val quoteSvg = runCatching { SVG.getFromAsset(context.assets, "icons/quote.svg") }.getOrNull()
        val appleSvg = runCatching { SVG.getFromAsset(context.assets, "icons/apple.svg") }.getOrNull()
        val playSvg = runCatching { SVG.getFromAsset(context.assets, "icons/google-play.svg") }.getOrNull()

        // Fond : dégradé Vert Pétrole -> teal profond
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, w * 0.55f, h, TEAL, TEAL_DEEP, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, h, background)

        // Cercles décoratifs discrets
        val soft = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(22, 255, 255, 255) }
        canvas.drawCircle(w * 0.95f, h * 0.07f, 400f, soft)
        canvas.drawCircle(w * 0.02f, h * 0.94f, 470f, soft)

        // Grand guillemet en filigrane (retourné, en bas à droite)
        drawSvg(canvas, quoteSvg, x = w - 600f, y = h - 1000f, size = 560f, color = Color.argb(26, 255, 255, 255), rotate = true)

        // En-tête : point orange + nom de l'application
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AMBER }
        canvas.drawCircle(MARGIN + 14f, 150f, 14f, dot)
        val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 60f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.2f
        }
        canvas.drawText("KALYNO", MARGIN + 52f, 170f, brand)

        // Guillemet ouvrant orange
        drawSvg(canvas, quoteSvg, x = MARGIN - 8f, y = 400f, size = 170f, color = AMBER)

        // Citation : serif italique, taille ajustée pour tenir dans la zone
        val textWidth = (w - 2 * MARGIN).toInt()
        val regionTop = 620f
        val regionBottom = 1480f
        val authorText = quote.author?.trim()?.takeIf { it.isNotEmpty() }

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }
        val authorPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AMBER_SOFT
            textSize = 46f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val authorLayout = authorText?.let { buildLayout(it, authorPaint, textWidth, 2) }
        val authorBlock = if (authorLayout != null) authorLayout.height + 56f + 6f else 0f
        val maxTextHeight = regionBottom - regionTop - authorBlock

        var size = 84f
        var layout: StaticLayout
        while (true) {
            textPaint.textSize = size
            layout = buildLayout(quote.text.trim(), textPaint, textWidth)
            if (layout.height <= maxTextHeight || size <= 44f) break
            size -= 4f
        }
        if (layout.height > maxTextHeight) {
            val oneLine = buildLayout("Ag", textPaint, textWidth, 1)
            val lines = (maxTextHeight / oneLine.height).toInt().coerceAtLeast(1)
            layout = buildLayout(quote.text.trim(), textPaint, textWidth, lines)
        }

        val blockHeight = layout.height + authorBlock
        var y = regionTop + (regionBottom - regionTop - blockHeight) / 2f
        canvas.save()
        canvas.translate(MARGIN, y)
        layout.draw(canvas)
        canvas.restore()
        y += layout.height

        if (authorLayout != null) {
            y += 56f
            val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AMBER }
            canvas.drawRoundRect(RectF(MARGIN, y, MARGIN + 90f, y + 6f), 3f, 3f, bar)
            y += 6f + 30f
            canvas.save()
            canvas.translate(MARGIN, y - 30f)
            authorLayout.draw(canvas)
            canvas.restore()
        }

        // Pied de carte : signature + pastille "Citation du jour", puis badges des stores
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 255, 255, 255)
            strokeWidth = 2f
        }
        canvas.drawLine(MARGIN, 1520f, w - MARGIN, 1520f, line)

        val footerBrand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 46f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.16f
        }
        canvas.drawText("KALYNO", MARGIN, 1608f, footerBrand)
        val tagline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(205, 255, 255, 255)
            textSize = 38f
        }
        canvas.drawText("Révise autrement.", MARGIN, 1668f, tagline)

        val pillText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AMBER_SOFT
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val label = "Citation du jour"
        val pillWidth = pillText.measureText(label) + 72f
        val pillRect = RectF(w - MARGIN - pillWidth, 1572f, w - MARGIN, 1656f)
        val pillStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AMBER
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(pillRect, 42f, 42f, pillStroke)
        canvas.drawText(label, pillRect.left + 36f, pillRect.top + 54f, pillText)

        // Badges App Store / Google Play, alignés sur les marges (400 + 60 + 400 = 860 px)
        val badgeTop = 1722f
        val badgeGap = (w - 2 * MARGIN) - 2 * BADGE_WIDTH
        drawStoreBadge(canvas, appleSvg, Color.WHITE, "App Store", MARGIN, badgeTop)
        drawStoreBadge(canvas, playSvg, null, "Google Play", MARGIN + BADGE_WIDTH + badgeGap, badgeTop)

        return bitmap
    }

    /**
     * Badge de store : capsule vitrée (fond sombre translucide + liseré clair), logo vectoriel
     * et nom du store centrés, puis la mention "Bientôt disponible" centrée juste dessous.
     * [iconTint] = null conserve les couleurs d'origine du logo.
     */
    private fun drawStoreBadge(
        canvas: Canvas,
        icon: SVG?,
        iconTint: Int?,
        storeName: String,
        left: Float,
        top: Float
    ) {
        val rect = RectF(left, top, left + BADGE_WIDTH, top + BADGE_HEIGHT)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(78, 0, 0, 0) }
        canvas.drawRoundRect(rect, BADGE_RADIUS, BADGE_RADIUS, fill)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(95, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val inset = stroke.strokeWidth / 2f
        canvas.drawRoundRect(
            RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset),
            BADGE_RADIUS, BADGE_RADIUS, stroke
        )

        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val iconSpace = if (icon != null) BADGE_ICON + BADGE_ICON_GAP else 0f
        val groupWidth = iconSpace + namePaint.measureText(storeName)
        val startX = rect.centerX() - groupWidth / 2f
        drawSvg(canvas, icon, x = startX, y = rect.centerY() - BADGE_ICON / 2f, size = BADGE_ICON, color = iconTint)
        val metrics = namePaint.fontMetrics
        val baseline = rect.centerY() - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(storeName, startX + iconSpace, baseline, namePaint)

        val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(205, 255, 255, 255)
            textSize = 28f
            letterSpacing = 0.03f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(STORE_CAPTION, rect.centerX(), rect.bottom + BADGE_CAPTION_GAP + 26f, caption)
    }

    private fun buildLayout(
        text: CharSequence,
        paint: TextPaint,
        width: Int,
        maxLines: Int = Int.MAX_VALUE
    ): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.22f)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

    /** Dessine une icône SVG (viewBox 24) dans un carré de [size] px, teintée si [color] != null. */
    private fun drawSvg(
        canvas: Canvas,
        svg: SVG?,
        x: Float,
        y: Float,
        size: Float,
        color: Int?,
        rotate: Boolean = false
    ) {
        svg ?: return
        val paint = Paint().apply {
            if (color != null) colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        }
        val save = canvas.saveLayer(x, y, x + size, y + size, paint)
        canvas.translate(x, y)
        canvas.scale(size / 24f, size / 24f)
        if (rotate) canvas.rotate(180f, 12f, 12f)
        svg.renderToCanvas(canvas, RectF(0f, 0f, 24f, 24f))
        canvas.restoreToCount(save)
    }
}
