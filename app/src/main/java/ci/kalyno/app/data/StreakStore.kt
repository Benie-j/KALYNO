package ci.kalyno.app.data

import android.content.Context

class StreakStore(context: Context) {
    private val prefs = context.getSharedPreferences("kalyno_streak", Context.MODE_PRIVATE)

    /** Enregistre un défi terminé aujourd'hui et retourne la série en cours (sans effet si déjà fait aujourd'hui). */
    fun recordCompletion(): Int {
        val today = System.currentTimeMillis() / DAY_MS
        val last = prefs.getLong(KEY_LAST_DAY, -1L)
        val current = prefs.getInt(KEY_STREAK, 0)

        val updated = when {
            last == today -> maxOf(current, 1)
            last == today - 1 -> current + 1
            else -> 1
        }
        prefs.edit().putLong(KEY_LAST_DAY, today).putInt(KEY_STREAK, updated).apply()
        return updated
    }

    /** Série en cours, sans rien modifier (0 si elle est interrompue). */
    fun current(): Int {
        val today = System.currentTimeMillis() / DAY_MS
        val last = prefs.getLong(KEY_LAST_DAY, -1L)
        return if (last >= today - 1) prefs.getInt(KEY_STREAK, 0) else 0
    }

    private companion object {
        const val DAY_MS = 86_400_000L
        const val KEY_LAST_DAY = "last_day"
        const val KEY_STREAK = "streak"
    }
}
