package ci.kalyno.app.data.badges

import android.content.Context
import ci.kalyno.app.data.AppPrefs
import ci.kalyno.app.data.StreakStore
import ci.kalyno.app.data.dashboard.KalynoDatabase
import ci.kalyno.app.data.srs.ReviewRating

/** État d'un badge pour l'écran : progression réelle + date de déblocage si atteint. */
data class BadgeUiState(
    val def: BadgeDef,
    val currentProgress: Int,
    val isUnlocked: Boolean,
    val unlockedAt: Long?
)

class BadgesRepository(private val context: Context) {

    private val dao = KalynoDatabase.get(context).badgeDao()
    private val srs = KalynoDatabase.get(context).srsDao()
    private val dashboard = KalynoDatabase.get(context).dashboardDao()
    private val prefs = AppPrefs(context)
    private val streak = StreakStore(context)

    /** Calcule l'état de tous les badges et persiste les nouveaux déblocages. Retourne ceux fraîchement débloqués (pour la modale). */
    suspend fun refresh(studentId: String): Pair<List<BadgeUiState>, List<BadgeDef>> {
        val existing = dao.unlocksFor(studentId).associateBy { it.badgeId }
        val masteryRows = srs.masteryRows(studentId)
        val history = srs.reviewHistory(studentId)

        val firstAttemptSuccessRate = run {
            val byCard = history.groupBy { it.flashcardId }
            if (byCard.isEmpty()) null
            else {
                val firstAttempts = byCard.values.map { it.first() } // déjà trié par date
                val successes = firstAttempts.count { it.rating != ReviewRating.OUBLIE }
                (successes * 100) / firstAttempts.size
            }
        }

        val retentionPercent = masteryRows
            .mapNotNull { row ->
                val last = row.lastReviewedAt ?: return@mapNotNull null
                val stability = row.stability.takeIf { it > 0.0 } ?: return@mapNotNull null
                val elapsedDays = (System.currentTimeMillis() - last) / 86_400_000.0
                kotlin.math.exp(-elapsedDays / stability)
            }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.let { (it * 100).toInt() }

        // "Major du Canton" : plus haute proportion de cartes déjà introduites, discipline par discipline.
        val disciplineBestPercent = run {
            val introduced = runCatching { dashboard.progressedCardIds(studentId).toHashSet() }.getOrDefault(hashSetOf())
            val bySubject = srs.allFlashcardSubjects().groupBy { it.subjectId }
            if (bySubject.isEmpty()) 0
            else bySubject.values.maxOf { cards ->
                if (cards.isEmpty()) 0
                else (cards.count { it.id in introduced } * 100) / cards.size
            }
        }

        val progressByBadge: Map<String, Int> = mapOf(
            "streak_1" to streak.current(),
            "streak_7" to streak.current(),
            "streak_30" to streak.current(),
            "fsrs_50cards" to masteryRows.size,
            "fsrs_zero_pioche" to (firstAttemptSuccessRate ?: 0),
            "fsrs_retention_85" to (retentionPercent ?: 0),
            "explo_5_chapters" to prefs.openedLessonsCount,
            "explo_discipline_100" to disciplineBestPercent,
            "social_first_share" to (if (prefs.hasSharedOnce) 1 else 0)
        )

        val freshlyUnlocked = mutableListOf<BadgeDef>()
        val states = BadgeCatalog.all.map { def ->
            val progress = (progressByBadge[def.id] ?: 0).coerceAtMost(def.targetValue)
            val alreadyUnlocked = existing.containsKey(def.id)
            val qualifies = progress >= def.targetValue
            if (qualifies && !alreadyUnlocked) {
                dao.unlock(BadgeUnlockEntity(studentId, def.id, System.currentTimeMillis()))
                freshlyUnlocked += def
            }
            BadgeUiState(
                def = def,
                currentProgress = progress,
                isUnlocked = qualifies || alreadyUnlocked,
                unlockedAt = existing[def.id]?.unlockedAt
                    ?: System.currentTimeMillis().takeIf { qualifies && !alreadyUnlocked }
            )
        }
        return states to freshlyUnlocked
    }
}
