package ci.kalyno.app.ui.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Liens externes de l'écran Profil. À RENSEIGNER avec tes vraies valeurs.
 * Tant qu'une valeur est vide, le toucher affiche "Bientôt disponible" (rien ne plante).
 */
object ProfileLinks {
    const val SUPPORT_EMAIL = ""   // ex. "support@tondomaine.ci"
    const val HELP_URL = ""        // page Centre d'aide / FAQ
    const val TERMS_URL = ""       // CGU
    const val PRIVACY_URL = ""     // Politique de confidentialité
}

private fun Context.soon() =
    Toast.makeText(this, "Bientôt disponible", Toast.LENGTH_SHORT).show()

/** Ouvre une page web dans le navigateur, ou affiche "Bientôt disponible" si [url] est vide. */
fun Context.openLinkOrSoon(url: String) {
    if (url.isBlank()) return soon()
    val ok = runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
    if (!ok) soon()
}

/** Ouvre l'app mail avec sujet et message pré-remplis (contexte utile pour le support). */
fun Context.emailSupportOrSoon(subject: String, body: String) {
    if (ProfileLinks.SUPPORT_EMAIL.isBlank()) return soon()
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(ProfileLinks.SUPPORT_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val ok = runCatching { startActivity(intent) }.isSuccess
    if (!ok) soon()
}

/** Ex. "KALYNO v1.0 · Online Edition" (lit versionName du build.gradle). */
fun Context.appVersionLabel(): String {
    val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: "1.0"
    return "KALYNO v$version · Online Edition"
}
