package ci.kalyno.app.data.dashboard

import android.content.Context
import java.util.UUID

/** Identifiant local anonyme de l'élève (clé de sa progression SRS). */
object StudentId {
    fun get(context: Context): String {
        val prefs = context.applicationContext
            .getSharedPreferences("kalyno_student", Context.MODE_PRIVATE)
        return prefs.getString("id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("id", it).apply()
        }
    }
}
