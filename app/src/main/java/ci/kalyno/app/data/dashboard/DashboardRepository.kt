package ci.kalyno.app.data.dashboard

import android.content.Context
import ci.kalyno.app.data.LearningCard
import ci.kalyno.app.data.LearningChapter
import ci.kalyno.app.data.LearningSubject
import ci.kalyno.app.data.SupabaseLearningRepository
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

    data class Snapshot(
        val mission: MissionUi?,
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
            .take(MAX_TOPICS)

        // ----- Mission du jour -----
        val introduced = dao.progressedCardIds(studentId).toSet()
        val mission = content?.let { buildMission(it, coefficients, introduced) }

        Snapshot(
            mission = mission,
            urgentTotal = urgentItems.size,
            urgentTopics = topics,
            urgentItems = urgentItems.take(SESSION_SIZE)
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

    private fun buildMission(
        subjects: List<LearningSubject>,
        coefficients: Map<String, Float>,
        introduced: Set<String>
    ): MissionUi? {
        var bestSubject: LearningSubject? = null
        var bestChapter: LearningChapter? = null
        var bestCoefficient = 0f

        for (s in subjects) for (ch in s.chapters) {
            val usable = ch.cards.mapNotNull { itemFrom(it, s.name, ch.title) }
            if (usable.isEmpty() || usable.all { it.cardId in introduced }) continue
            val coefficient = coefficients[s.id] ?: 1f
            if (bestSubject == null || coefficient > bestCoefficient) {
                bestSubject = s
                bestChapter = ch
                bestCoefficient = coefficient
            }
        }

        val subject = bestSubject ?: return null
        val chapter = bestChapter ?: return null
        val usable = chapter.cards.mapNotNull { itemFrom(it, subject.name, chapter.title) }
        val remaining = usable.filter { it.cardId !in introduced }

        return MissionUi(
            subjectName = subject.name,
            subjectEmoji = subject.emoji,
            chapterTitle = chapter.title,
            minutes = chapter.capsules.sumOf { it.minutes },
            toDiscover = minOf(remaining.size, SESSION_SIZE),
            done = usable.size - remaining.size,
            total = usable.size,
            items = remaining.take(SESSION_SIZE)
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
        const val SESSION_SIZE = 10
        const val MAX_TOPICS = 3
    }
}
