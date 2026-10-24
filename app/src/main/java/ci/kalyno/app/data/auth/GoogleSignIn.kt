package ci.kalyno.app.data.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest

/** Remonte jusqu'à l'Activity (nécessaire pour afficher la fenêtre de choix du compte Google). */
fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** Empreinte SHA-256 en hexadécimal : Google reçoit l'empreinte, Supabase vérifie avec la valeur brute. */
fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

/** Affiche la fenêtre Google et renvoie le jeton d'identité de l'élève. */
suspend fun requestGoogleIdToken(activity: Activity, hashedNonce: String): String {
    if (GoogleAuthConfig.WEB_CLIENT_ID.isBlank()) {
        throw AuthException("La connexion Google n'est pas encore configurée.")
    }

    val option = GetGoogleIdOption.Builder()
        .setServerClientId(GoogleAuthConfig.WEB_CLIENT_ID)
        .setFilterByAuthorizedAccounts(false) // propose tous les comptes (inscription possible)
        .setNonce(hashedNonce)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(option)
        .build()

    val result = CredentialManager.create(activity).getCredential(activity, request)
    val credential = result.credential
    if (credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
    throw AuthException("Type d'identifiant Google inattendu.")
}
