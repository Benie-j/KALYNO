package ci.kalyno.app.data

import android.content.Context
import ci.kalyno.app.data.auth.AuthStore

/**
 * Série du jour, conservée sur le téléphone et isolée par compte :
 * chaque compte Supabase (et le mode invité) a ses propres valeurs, jamais mélangées.
 *
 * Migration sans perte : les anciennes valeurs globales ("last_day" / "streak") restent en place ;
 * le premier propriétaire qui ouvre l'appli après la mise à jour les reprend. Une série acquise en
 * mode invité est reprise par le premier compte qui se connecte, puis plus jamais par un autre.
 */
class StreakStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("kalyno_streak", Context.MODE_PRIVATE)
    private val auth = AuthStore(appContext)

    private fun owner(): String = auth.userId() ?: GUEST
    private fun k(owner: String, name: String) = "$owner.$name"

    private fun read(owner: String): StreakState = StreakState(
        streak = prefs.getInt(k(owner, KEY_STREAK), 0),
        lastDay = prefs.getLong(k(owner, KEY_LAST_DAY), -1L)
    )

    private fun write(owner: String, state: StreakState) {
        prefs.edit()
            .putLong(k(owner, KEY_LAST_DAY), state.lastDay)
            .putInt(k(owner, KEY_STREAK), state.streak)
            .apply()
    }

    /** À appeler (sous verrou) avant toute lecture : reprend l'ancien format et la série invité. */
    private fun migrate(owner: String) {
        if (!prefs.getBoolean(KEY_LEGACY_DONE, false)) {
            val legacyDay = prefs.getLong(LEGACY_LAST_DAY, -1L)
            val legacy = StreakState(prefs.getInt(LEGACY_STREAK, 0), legacyDay)
            if (!legacy.isEmpty && read(owner).isEmpty) {
                write(owner, legacy)
                if (owner != GUEST) setPending(owner, true)
            }
            prefs.edit().putBoolean(KEY_LEGACY_DONE, true).apply()
        }
        if (owner != GUEST && prefs.getString(KEY_GUEST_CLAIMED_BY, null) == null) {
            val guest = read(GUEST)
            if (!guest.isEmpty) {
                val merged = StreakLogic.merge(read(owner), guest).state
                if (!merged.isEmpty) {
                    write(owner, merged)
                    setPending(owner, true)
                }
            }
            if (!guest.isEmpty || prefs.contains(k(GUEST, KEY_LAST_DAY))) {
                prefs.edit().putString(KEY_GUEST_CLAIMED_BY, owner).apply()
            }
        }
    }

    private fun setPending(owner: String, value: Boolean) {
        prefs.edit().putBoolean(k(owner, KEY_PENDING), value).apply()
    }

    /** Enregistre une session terminée aujourd'hui et retourne la série en cours (sans effet si déjà fait aujourd'hui). */
    fun recordCompletion(): Int = synchronized(LOCK) {
        val owner = owner()
        migrate(owner)
        val updated = StreakLogic.recordCompletion(read(owner), StreakLogic.dayOf(System.currentTimeMillis()))
        write(owner, updated)
        if (owner != GUEST) setPending(owner, true)
        updated.streak
    }

    /** Série en cours, sans rien modifier (0 si elle est interrompue). */
    fun current(): Int = synchronized(LOCK) {
        val owner = owner()
        migrate(owner)
        StreakLogic.current(read(owner), StreakLogic.dayOf(System.currentTimeMillis()))
    }

    /** Vrai si une session a déjà été terminée aujourd'hui (sans rien modifier). */
    fun doneToday(): Boolean = synchronized(LOCK) {
        val owner = owner()
        migrate(owner)
        val state = read(owner)
        !state.isEmpty && state.lastDay == StreakLogic.dayOf(System.currentTimeMillis())
    }

    /** Série telle qu'elle sera une fois la session du jour enregistrée, sans rien modifier (bilan de fin de session). */
    fun projectedAfterCompletion(): Int = synchronized(LOCK) {
        val owner = owner()
        migrate(owner)
        StreakLogic.recordCompletion(read(owner), StreakLogic.dayOf(System.currentTimeMillis())).streak
    }

    // ---- Synchronisation cloud (propriétaire explicite : pas de mélange si le compte change en cours de route)

    internal fun stateFor(userId: String): StreakState = synchronized(LOCK) {
        migrate(userId)
        read(userId)
    }

    internal fun hasPending(userId: String): Boolean = prefs.getBoolean(k(userId, KEY_PENDING), false)

    /** Restaure sur ce téléphone une série plus récente venue du cloud. */
    internal fun restore(userId: String, state: StreakState) = synchronized(LOCK) {
        if (!state.isEmpty) write(userId, state)
    }

    /** Marque la série comme envoyée, seulement si elle n'a pas changé pendant l'envoi. */
    internal fun markSynced(userId: String, sent: StreakState) = synchronized(LOCK) {
        if (read(userId) == sent) setPending(userId, false)
    }

    private companion object {
        val LOCK = Any()
        const val GUEST = "guest"
        const val KEY_LAST_DAY = "last_day"
        const val KEY_STREAK = "streak"
        const val KEY_PENDING = "pending"
        const val LEGACY_LAST_DAY = "last_day"
        const val LEGACY_STREAK = "streak"
        const val KEY_LEGACY_DONE = "legacy_migrated"
        const val KEY_GUEST_CLAIMED_BY = "guest_claimed_by"
    }
}
