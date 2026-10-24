package ci.kalyno.app.data.auth

import android.content.Context

/** Session Supabase obtenue après la connexion Google. */
data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
    val userId: String,
    val email: String?,
    val name: String?
)

/** Stockage local de la session et du choix d'inscription (invité ou compte). */
class AuthStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("kalyno_auth", Context.MODE_PRIVATE)

    /** Vrai une fois que l'élève a choisi : compte Google ou mode invité. */
    var registrationChoiceDone: Boolean
        get() = prefs.getBoolean(KEY_CHOICE_DONE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_CHOICE_DONE, value).apply()
        }

    fun isSignedIn(): Boolean =
        prefs.getString(KEY_USER_ID, null) != null && prefs.getString(KEY_REFRESH_TOKEN, null) != null

    fun userName(): String? = prefs.getString(KEY_NAME, null)

    fun userEmail(): String? = prefs.getString(KEY_EMAIL, null)

    fun saveSession(session: AuthSession) {
        prefs.edit()
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_EMAIL, session.email)
            .putString(KEY_NAME, session.name)
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .putString(KEY_REFRESH_TOKEN, session.refreshToken)
            .putLong(KEY_EXPIRES_AT, session.expiresAtMillis)
            .putBoolean(KEY_CHOICE_DONE, true)
            .apply()
    }

    /** Déconnexion : efface la session, garde le choix d'inscription déjà fait. */
    fun signOut() {
        prefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_EMAIL)
            .remove(KEY_NAME)
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .apply()
    }

    private companion object {
        const val KEY_CHOICE_DONE = "registration_choice_done"
        const val KEY_USER_ID = "user_id"
        const val KEY_EMAIL = "email"
        const val KEY_NAME = "name"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_EXPIRES_AT = "expires_at"
    }
}
