package ci.kalyno.app.data

import android.content.Context

class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("kalyno_app", Context.MODE_PRIVATE)

    /** Classe choisie par l'élève (null tant qu'il n'a pas choisi). */
    var level: String?
        get() = prefs.getString(KEY_LEVEL, null)?.takeUnless { it in LEGACY_LEVELS }
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

    /** Dernière leçon ouverte dans l'Explorer, pour "Reprendre l'aventure" sur l'Accueil. */
    fun saveLastLesson(subjectName: String, subjectEmoji: String, chapterTitle: String, progress: Float) {
        prefs.edit()
            .putString(KEY_LAST_LESSON_SUBJECT, subjectName)
            .putString(KEY_LAST_LESSON_EMOJI, subjectEmoji)
            .putString(KEY_LAST_LESSON_CHAPTER, chapterTitle)
            .putFloat(KEY_LAST_LESSON_PROGRESS, progress)
            .apply()
    }

    val lastLessonSubjectName: String? get() = prefs.getString(KEY_LAST_LESSON_SUBJECT, null)
    val lastLessonSubjectEmoji: String? get() = prefs.getString(KEY_LAST_LESSON_EMOJI, null)
    val lastLessonChapterTitle: String? get() = prefs.getString(KEY_LAST_LESSON_CHAPTER, null)
    val lastLessonProgress: Float get() = prefs.getFloat(KEY_LAST_LESSON_PROGRESS, 0f)

    /** Enregistre qu'une leçon a été ouverte (pour le badge "Rat de Bibliothèque 2.0"). */
    fun markLessonOpened(lessonId: String) {
        val current = prefs.getStringSet(KEY_OPENED_LESSONS, emptySet()) ?: emptySet()
        if (lessonId !in current) {
            prefs.edit().putStringSet(KEY_OPENED_LESSONS, current + lessonId).apply()
        }
    }

    val openedLessonsCount: Int
        get() = (prefs.getStringSet(KEY_OPENED_LESSONS, emptySet()) ?: emptySet()).size

    /** Premier partage (citation ou score) réussi, pour le badge "C'est Carré". */
    fun markFirstShare() {
        if (!prefs.getBoolean(KEY_FIRST_SHARE, false)) {
            prefs.edit().putBoolean(KEY_FIRST_SHARE, true).apply()
        }
    }

    val hasSharedOnce: Boolean get() = prefs.getBoolean(KEY_FIRST_SHARE, false)

    private companion object {
        /**
         * Anciennes classes supprimées : "TLE_A" a été scindée en Tle A1 et Tle A2 (programmes différents).
         * L'élève qui l'avait choisie repasse une seule fois par l'écran de choix pour trancher.
         */
        val LEGACY_LEVELS = setOf("TLE_A")

        const val KEY_LEVEL = "level"
        const val KEY_NICKNAME = "nickname"
        const val KEY_DEMO_DONE = "demo_done"
        const val KEY_LAST_LESSON_SUBJECT = "last_lesson_subject"
        const val KEY_LAST_LESSON_EMOJI = "last_lesson_emoji"
        const val KEY_LAST_LESSON_CHAPTER = "last_lesson_chapter"
        const val KEY_LAST_LESSON_PROGRESS = "last_lesson_progress"
        const val KEY_OPENED_LESSONS = "opened_lessons"
        const val KEY_FIRST_SHARE = "first_share_done"
    }
}
