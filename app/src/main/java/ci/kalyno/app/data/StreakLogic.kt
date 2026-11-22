package ci.kalyno.app.data

/**
 * Série de jours consécutifs : [streak] jours, le dernier ayant eu lieu au jour [lastDay]
 * (numéro de jour depuis 1970, en UTC ; -1 = aucune session terminée).
 */
data class StreakState(val streak: Int, val lastDay: Long) {
    val isEmpty: Boolean get() = lastDay < 0L || streak <= 0

    companion object {
        val EMPTY = StreakState(0, -1L)
    }
}

/** Résultat d'une fusion local / distant. */
data class StreakMerge(val state: StreakState, val restoreLocal: Boolean, val pushRemote: Boolean)

/** Règles de la série, sans aucune dépendance Android (testables en tests unitaires JVM). */
object StreakLogic {
    const val DAY_MS = 86_400_000L

    fun dayOf(nowMillis: Long): Long = nowMillis / DAY_MS

    /** Nouvelle série après une session terminée au jour [today]. Une pause de plus d'un jour repart à 1. */
    fun recordCompletion(state: StreakState, today: Long): StreakState {
        val last = if (state.streak <= 0) -1L else state.lastDay
        val updated = when {
            last == today -> maxOf(state.streak, 1)
            last == today - 1 -> state.streak + 1
            else -> 1
        }
        return StreakState(updated, today)
    }

    /** Série affichée : 0 si elle est interrompue (dernier jour antérieur à hier). */
    fun current(state: StreakState, today: Long): Int =
        if (!state.isEmpty && state.lastDay >= today - 1) state.streak else 0

    /**
     * Fusionne la série locale et la série distante, avec le dernier jour comme référence :
     * - jamais de remplacement d'une série distante par une série locale vide ;
     * - dernier jour identique : on garde la série la plus longue pour ce même jour ;
     * - jours différents : la plus récente gagne ; si l'autre chaîne la touche ou la chevauche
     *   (jours calendaires contigus), la chaîne est prolongée jusqu'à son vrai début, sans additionner
     *   les compteurs.
     */
    fun merge(local: StreakState, remote: StreakState?): StreakMerge {
        val l = local.takeIf { !it.isEmpty }
        val r = remote?.takeIf { !it.isEmpty }
        val merged = when {
            l == null && r == null -> StreakState.EMPTY
            r == null -> l!!
            l == null -> r
            l.lastDay == r.lastDay -> if (l.streak >= r.streak) l else r
            else -> {
                val newer = if (l.lastDay > r.lastDay) l else r
                val older = if (newer === l) r else l
                val newerStart = newer.lastDay - newer.streak + 1
                if (older.lastDay >= newerStart - 1) {
                    val start = minOf(newerStart, older.lastDay - older.streak + 1)
                    StreakState((newer.lastDay - start + 1).toInt(), newer.lastDay)
                } else newer
            }
        }
        if (merged.isEmpty) return StreakMerge(local, restoreLocal = false, pushRemote = false)
        return StreakMerge(
            state = merged,
            restoreLocal = merged != local,
            pushRemote = remote == null || merged != remote
        )
    }
}
