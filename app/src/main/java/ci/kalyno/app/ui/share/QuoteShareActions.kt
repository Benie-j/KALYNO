package ci.kalyno.app.ui.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Telephony
import androidx.core.content.FileProvider
import ci.kalyno.app.data.dashboard.DailyQuote
import java.io.File

/** Toutes les opérations de partage de la carte (aucune dépendance à Compose). */
object QuoteShareActions {

    private const val CAPTION = "Citation du jour sur KALYNO"

    /** Texte brut (uniquement pour "Copier le texte"). */
    fun quoteText(quote: DailyQuote): String = buildString {
        append("« ").append(quote.text.trim()).append(" »")
        if (!quote.author.isNullOrBlank()) append("\n— ").append(quote.author.trim())
        append("\n\n").append(CAPTION).append(" - Révise autrement.")
    }

    /** Écrit le PNG dans le cache de l'appli (dossier "share/", ancien contenu supprimé). */
    fun writeCache(context: Context, bitmap: Bitmap): File {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "kalyno_citation_${System.currentTimeMillis()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    @Suppress("DEPRECATION")
    fun isInstalled(context: Context, vararg packages: String): Boolean =
        packages.any { pkg -> runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess }

    fun defaultSmsPackage(context: Context): String? =
        runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()

    private fun imageIntent(uri: Uri): Intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, CAPTION)
        clipData = ClipData.newRawUri("Carte KALYNO", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** Envoie l'image à la première application installée de [packages]. Faux si aucune n'a répondu. */
    fun shareImage(context: Context, uri: Uri, packages: List<String>): Boolean {
        for (pkg in packages) {
            try {
                context.startActivity(imageIntent(uri).setPackage(pkg))
                return true
            } catch (_: Exception) {
                // application absente ou refus : on essaie la suivante
            }
        }
        return false
    }

    /** Sélecteur Android classique (toutes les applications qui acceptent une image). */
    fun chooser(context: Context, uri: Uri): Boolean = try {
        context.startActivity(Intent.createChooser(imageIntent(uri), "Partager la citation"))
        true
    } catch (_: Exception) {
        false
    }

    fun copyText(context: Context, quote: DailyQuote): Boolean = runCatching {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("Citation KALYNO", quoteText(quote)))
    }.isSuccess

    /** Enregistre dans Pictures/KALYNO (Android 10+ sans permission, avant : WRITE_EXTERNAL_STORAGE). */
    fun saveToGallery(context: Context, bitmap: Bitmap): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "kalyno_citation_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/KALYNO")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            val stream = resolver.openOutputStream(uri) ?: error("flux indisponible")
            stream.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            true
        } catch (_: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }
}
