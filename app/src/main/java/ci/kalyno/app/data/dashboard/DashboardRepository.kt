package ci.kalyno.app.data.dashboard

import android.content.Context
import ci.kalyno.app.data.LearningCard
import ci.kalyno.app.data.LearningChapter
import ci.kalyno.app.data.LearningSubject
import ci.kalyno.app.data.SupabaseLearningRepository
import ci.kalyno.app.data.cloud.CloudSync
import ci.kalyno.app.data.srs.CardProgressEntity
import ci.kalyno.app.data.srs.FlashcardEntity
import ci.kalyno.app.data.srs.ReviewLogEntity
import ci.kalyno.app.data.srs.ReviewRating
import ci.kalyno.app.data.srs.SrsEngine
import ci.kalyno.app.data.srs.SubjectEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URLEncoder

class DashboardRepository(context: Context) {

    private val db = KalynoDatabase.get(context)
    private val srs = db.srsDao()
    private val dao = db.dashboardDao()
    private val remote = SupabaseLearningRepository()
    private val cloud = CloudSync(context)

    data class Snapshot(
        val mission: MissionUi?,
        val missionStatus: MissionStatus,
        val urgentTotal: Int,
        val urgentTopics: List<UrgentTopic>,
        val urgentItems: List<ReviewItem>
    )

    /**
     * 1) charge le contenu publié par l'admin (Supabase) et le synchronise dans Room,
     * 2) calcule les révisions urgentes avec le moteur SRS (fonctionne aussi hors ligne),
     * 3) choisit la mission du jour (nécessite le contenu en ligne).
     */
    suspend fun load(levelId: String, studentId: String): Snapshot = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        var content: List<LearningSubject>? = null
        var coefficients: Map<String, Float> = emptyMap()
        try {
            val loaded = remote.loadPublishedContent(levelId)
            coefficients = fetchCoefficients(levelId)
            sync(levelId, loaded, coefficients)
            content = loaded
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Hors ligne ou erreur serveur : on continue avec la base locale.
        }

        val cardIndex: Map<String, Triple<LearningSubject, LearningChapter, LearningCard>> =
            content.orEmpty().flatMap { s ->
                s.chapters.flatMap { ch -> ch.cards.map { c -> c.id to Triple(s, ch, c) } }
            }.toMap()

        // ----- Synchronisation du compte (si connecté) -----
        try {
            cloud.syncProgress(studentId)
            cloud.pushProfile()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Hors ligne ou erreur serveur : la progression reste locale et partira plus tard.
        }

        // ----- Révisions urgentes (SRS) -----
        val levelSubjects = dao.subjectsForLevel(levelId)
        val levelSubjectIds = levelSubjects.map { it.id }.toSet()
        val subjectNames = levelSubjects.associate { it.id to it.name }

        var due = SrsEngine.prioritize(srs.getDueCards(studentId, now), now)
            .filter { it.subjectId in levelSubjectIds }
        if (content != null) due = due.filter { it.flashcardId in cardIndex }

        val entities: Map<String, FlashcardEntity> = due.map { it.flashcardId }
            .chunked(500)
            .flatMap { dao.flashcardsByIds(it) }
            .associateBy { it.id }

        val urgentItems = due.mapNotNull { d ->
            cardIndex[d.flashcardId]?.let { (s, ch, c) -> itemFrom(c, s.name, ch.title) }
                ?: entities[d.flashcardId]?.let { e ->
                    ReviewItem(
                        cardId = e.id,
                        subjectName = subjectNames[d.subjectId].orEmpty(),
                        chapterTitle = e.chapitre,
                        front = e.question,
                        choices = emptyList(),
                        correctIndex = null,
                        answer = e.reponse,
                        explanation = null
                    )
                }
        }

        val topics = due
            .groupBy { (entities[it.flashcardId]?.chapitre ?: "") to it.subjectId }
            .filterKeys { it.first.isNotBlank() }
            .map { (key, list) ->
                UrgentTopic(
                    label = "${subjectNames[key.second].orEmpty()} · ${key.first}".trim(' ', '·'),
                    count = list.size
                )
            }
            .take(SrsConfig.MAX_TOPICS)

        // ----- Mission du jour -----
        val dueCount = urgentItems.size
        val startOfToday = SrsEngine.startOfDay(now)
        val introducedToday = dao.countIntroducedSince(studentId, startOfToday)
        val allowedToday = SrsConfig.newCardQuota(dueCount)
        val introduced = dao.progressedCardIds(studentId).toSet()
        val lastIntroduced = dao.lastIntroductionBySubject(studentId).associate { it.subjectId to it.lastIntroducedAt }

        val remainingQuota = (allowedToday - introducedToday).coerceAtLeast(0)
        val mission = content?.let {
            buildMission(
                subjects = it,
                coefficients = coefficients,
                introduced = introduced,
                lastIntroduced = lastIntroduced,
                now = now,
                startOfToday = startOfToday,
                remainingQuota = remainingQuota,
                doneToday = introducedToday,
                targetToday = maxOf(allowedToday, introducedToday)
            )
        }
        val status = when {
            content == null -> MissionStatus.EMPTY
            introducedToday > 0 && remainingQuota == 0 -> MissionStatus.DONE_TODAY
            allowedToday == 0 -> MissionStatus.BACKLOG
            mission != null && mission.items.isNotEmpty() -> MissionStatus.READY
            introducedToday > 0 -> MissionStatus.DONE_TODAY
            else -> MissionStatus.EMPTY
        }

        Snapshot(
            mission = when (status) {
                MissionStatus.READY -> mission
                MissionStatus.DONE_TODAY -> mission ?: MissionUi(
                    subjectName = "",
                    subjectEmoji = "",
                    chapterTitle = "",
                    minutes = 0,
                    doneToday = introducedToday,
                    targetToday = maxOf(allowedToday, introducedToday),
                    items = emptyList()
                )
                else -> null
            },
            missionStatus = status,
            urgentTotal = urgentItems.size,
            urgentTopics = topics,
            urgentItems = urgentItems.take(SrsConfig.SESSION_SIZE)
        )
    }

    /** Enregistre l'évaluation de l'élève et fait avancer la carte dans le moteur SRS. */
    suspend fun rate(studentId: String, cardId: String, rating: ReviewRating) =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val current = srs.getProgress(studentId, cardId)
                ?: CardProgressEntity(studentId = studentId, flashcardId = cardId, dueDate = now)
                    .also { srs.initProgress(it) }

            val next = SrsEngine.computeNextProgress(current, rating, now)
            srs.updateProgress(next)
            srs.logReview(
                ReviewLogEntity(
                    studentId = studentId,
                    flashcardId = cardId,
                    rating = rating,
                    intervalBefore = current.intervalDays,
                    intervalAfter = next.intervalDays,
                    reviewedAt = now
                )
            )
        }

    private suspend fun sync(
        levelId: String,
        subjects: List<LearningSubject>,
        coefficients: Map<String, Float>
    ) {
        dao.upsertSubjects(
            subjects.map { SubjectEntity(it.id, it.name, levelId, coefficients[it.id] ?: 1f) }
        )
        val cards = subjects.flatMap { s ->
            s.chapters.flatMap { ch ->
                ch.cards.mapNotNull { c ->
                    val answer = answerOf(c)
                    if (c.front.isBlank() || answer.isNullOrBlank()) null
                    else FlashcardEntity(c.id, s.id, ch.title, c.front, answer)
                }
            }
        }
        cards.chunked(300).forEach { dao.upsertFlashcards(it) }
    }

    /**
     * Choisit la matière du jour puis les cartes à découvrir :
     * - on reste sur la matière déjà commencée aujourd'hui (mission stable dans la journée) ;
     * - sinon, score = coefficient × (1 + jours depuis la dernière nouvelle carte de la matière),
     *   ce qui fait tourner les matières tout en favorisant les gros coefficients ;
     * - les cartes sont prises chapitre par chapitre, dans la limite du quota restant.
     */
    private fun buildMission(
        subjects: List<LearningSubject>,
        coefficients: Map<String, Float>,
        introduced: Set<String>,
        lastIntroduced: Map<String, Long>,
        now: Long,
        startOfToday: Long,
        remainingQuota: Int,
        doneToday: Int,
        targetToday: Int
    ): MissionUi? {
        class Candidate(
            val subject: LearningSubject,
            val chapters: List<Pair<LearningChapter, List<ReviewItem>>>
        )

        val candidates = subjects.mapNotNull { s ->
            val chapters = s.chapters.mapNotNull { ch ->
                val remaining = ch.cards
                    .mapNotNull { itemFrom(it, s.name, ch.title) }
                    .filter { it.cardId !in introduced }
                if (remaining.isEmpty()) null else ch to remaining
            }
            if (chapters.isEmpty()) null else Candidate(s, chapters)
        }
        if (candidates.isEmpty()) return null

        val startedToday = candidates
            .filter { (lastIntroduced[it.subject.id] ?: 0L) >= startOfToday }
            .maxByOrNull { lastIntroduced[it.subject.id] ?: 0L }

        val chosen = startedToday ?: candidates.maxByOrNull { c ->
            val last = lastIntroduced[c.subject.id]
            val daysSince = if (last == null) SrsConfig.NEVER_STUDIED_DAYS
            else ((now - last) / DAY_MS).toInt().coerceAtLeast(0)
            (coefficients[c.subject.id] ?: 1f) * (1 + daysSince)
        } ?: return null

        val items = chosen.chapters.flatMap { it.second }.take(remainingQuota)
        val firstChapter = chosen.chapters.first().first

        return MissionUi(
            subjectName = chosen.subject.name,
            subjectEmoji = chosen.subject.emoji,
            chapterTitle = firstChapter.title,
            minutes = firstChapter.capsules.sumOf { it.minutes },
            doneToday = doneToday,
            targetToday = targetToday,
            items = items
        )
    }

    private fun answerOf(card: LearningCard): String? =
        card.correctIndex?.let { card.choices.getOrNull(it) } ?: card.back

    private fun itemFrom(card: LearningCard, subject: String, chapter: String): ReviewItem? {
        val answer = answerOf(card)
        if (card.front.isBlank() || answer.isNullOrBlank()) return null
        return ReviewItem(
            cardId = card.id,
            subjectName = subject,
            chapterTitle = chapter,
            front = card.front,
            choices = card.choices,
            correctIndex = card.correctIndex,
            answer = answer,
            explanation = card.explanation
        )
    }

    /** Coefficient officiel de chaque matière (colonne `coefficient` de `subjects`, 1 si absente). */
    private fun fetchCoefficients(levelId: String): Map<String, Float> = try {
        val rows = JSONArray(
            SupabaseHttp.get(
                "subjects?select=id,coefficient&level_id=eq.${URLEncoder.encode(levelId, "UTF-8")}"
            )
        )
        buildMap {
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val value = row.optDouble("coefficient", 1.0)
                put(row.getString("id"), if (value.isNaN() || value <= 0.0) 1f else value.toFloat())
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyMap()
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
