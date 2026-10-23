package ci.kalyno.app.data

import android.content.Context

class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("kalyno_app", Context.MODE_PRIVATE)

    /** Classe choisie par l'élève (null tant qu'il n'a pas choisi). */
    var level: String?
        get() = prefs.getString(KEY_LEVEL, null)
        set(value) {
            val editor = prefs.edit()
            if (value == null) editor.remove(KEY_LEVEL) else editor.putString(KEY_LEVEL, value)
            editor.apply()
        }

    /** Pseudo choisi à l'onboarding (null tant qu'il n'est pas saisi). */
    var nickname: String?
        get() = prefs.getString(KEY_NICKNAME, null)
        set(value) {
            val editor = prefs.edit()
            if (value == null) editor.remove(KEY_NICKNAME) else editor.putString(KEY_NICKNAME, value)
            editor.apply()
        }

    /** Vrai une fois la démo (défi de découverte) terminée ou quittée. */
    var demoDone: Boolean
        get() = prefs.getBoolean(KEY_DEMO_DONE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DEMO_DONE, value).apply()
        }

    private companion object {
        const val KEY_LEVEL = "level"
        const val KEY_NICKNAME = "nickname"
        const val KEY_DEMO_DONE = "demo_done"
    }
}
