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

/** Stockage local de la session, du choix d'inscription et des repères de synchronisation. */
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

    fun userId(): String? = prefs.getString(KEY_USER_ID, null)

    fun userName(): String? = prefs.getString(KEY_NAME, null)

    fun userEmail(): String? = prefs.getString(KEY_EMAIL, null)

    /** Session complète, ou null si l'élève n'est pas connecté. */
    fun session(): AuthSession? {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val access = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return null
        return AuthSession(
            accessToken = access,
            refreshToken = refresh,
            expiresAtMillis = prefs.getLong(KEY_EXPIRES_AT, 0L),
            userId = userId,
            email = prefs.getString(KEY_EMAIL, null),
            name = prefs.getString(KEY_NAME, null)
        )
    }

    fun saveSession(session: AuthSession) {
        val editor = prefs.edit()
        // Un autre compte se connecte : les repères de synchronisation de l'ancien ne valent plus
        if (prefs.getString(KEY_USER_ID, null) != session.userId) {
            editor.remove(KEY_LAST_PUSH).remove(KEY_LAST_PULL).remove(KEY_PROFILE_SIGNATURE)
        }
        editor
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
            .remove(KEY_LAST_PUSH)
            .remove(KEY_LAST_PULL)
            .remove(KEY_PROFILE_SIGNATURE)
            .apply()
    }

    // ----- Repères de synchronisation cloud -----

    fun lastPushAt(): Long = prefs.getLong(KEY_LAST_PUSH, 0L)

    fun setLastPushAt(value: Long) {
        prefs.edit().putLong(KEY_LAST_PUSH, value).apply()
    }

    fun lastPullAt(): Long = prefs.getLong(KEY_LAST_PULL, 0L)

    fun setLastPullAt(value: Long) {
        prefs.edit().putLong(KEY_LAST_PULL, value).apply()
    }

    fun profileSignature(): String? = prefs.getString(KEY_PROFILE_SIGNATURE, null)

    fun setProfileSignature(value: String) {
        prefs.edit().putString(KEY_PROFILE_SIGNATURE, value).apply()
    }

    private companion object {
        const val KEY_CHOICE_DONE = "registration_choice_done"
        const val KEY_USER_ID = "user_id"
        const val KEY_EMAIL = "email"
        const val KEY_NAME = "name"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_EXPIRES_AT = "expires_at"
        const val KEY_LAST_PUSH = "sync_last_push"
        const val KEY_LAST_PULL = "sync_last_pull"
        const val KEY_PROFILE_SIGNATURE = "sync_profile_signature"
    }
}
