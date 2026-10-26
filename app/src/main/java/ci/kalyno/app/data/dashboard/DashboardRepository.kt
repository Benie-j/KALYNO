package ci.kalyno.app.data.dashboard

import android.content.Context
import androidx.room.withTransaction
import ci.kalyno.app.data.LearningCard
import ci.kalyno.app.data.LearningChapter
import ci.kalyno.app.data.LearningSubject
import ci.kalyno.app.data.AppPrefs
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
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URLEncoder

class DashboardRepository(context: Context) {

    private val db = KalynoDatabase.get(context)
    private val srs = db.srsDao()
    private val dao = db.dashboardDao()
    private val remote = SupabaseLearningRepository()
    private val cloud = CloudSync(context)
    private val exams = ExamSchedule(context)
    private val prefs = AppPrefs(context)

    data class Snapshot(
        val mission: MissionUi?,
        val missionStatus: MissionStatus,
        val urgentTotal: Int,
        val urgentTopics: List<UrgentTopic>,
        val urgentItems: List<ReviewItem>,
        /** Jours restants avant l'examen publié par l'admin (null si aucune date). */
        val examDaysLeft: Int?,
        /** Notions revisees au moins MASTERED_REPETITIONS fois d'affilee avec succes. */
        val masteredCount: Int,
        /** Probabilite moyenne de retention (courbe de l'oubli FSRS), null si aucune carte revisee. */
        val retentionScore: Float?,
        /** Derniere lecon ouverte dans l'Explorer ("Reprendre l'aventure"), null si aucune encore. */
        val lastLesson: LastLessonUi?
    )

    /**
     * 1) charge le contenu publié par l'admin (Supabase) et le synchronise dans Room,
     * 2) calcule les révisions urgentes avec le moteur SRS (fonctionne aussi hors ligne),
     * 3) choisit la mission du jour (nécessite le contenu en ligne).
     */
    suspend fun load(levelId: String, studentId: String): Snapshot = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        // La date d'examen se charge en parallèle du contenu
        val examLookup = async { exams.refresh(levelId) }

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
                    val storedIndices = ci.kalyno.app.data.dojo.parseIndexList(e.correctIndices)
                    ReviewItem(
                        cardId = e.id,
                        subjectName = subjectNames[d.subjectId].orEmpty(),
                        chapterTitle = e.chapitre,
                        front = e.question,
                        choices = choicesFromJson(e.choicesJson),
                        correctIndex = storedIndices.firstOrNull(),
                        answer = e.reponse,
                        explanation = e.explanation,
                        kind = e.kind,
                        correctIndices = storedIndices
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

        // ----- KPI : notions maîtrisées et score de rétention -----
        val masteryRows = srs.masteryRows(studentId)
        val masteredCount = masteryRows.count { it.repetitions >= MASTERED_REPETITIONS }
        val retentionScore = masteryRows
            .mapNotNull { row ->
                val last = row.lastReviewedAt ?: return@mapNotNull null
                val stability = row.stability.takeIf { it > 0.0 } ?: return@mapNotNull null
                // Courbe de l'oubli FSRS : R(t) = exp(-t / S), t et S en jours.
                val elapsedDays = (now - last) / DAY_MS.toDouble()
                kotlin.math.exp(-elapsedDays / stability)
            }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.toFloat()

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
            urgentItems = urgentItems.take(SrsConfig.SESSION_SIZE),
            examDaysLeft = examLookup.await()
                ?.let { SrsEngine.daysUntil(now, it) }
                ?.takeIf { it >= 0 },
            masteredCount = masteredCount,
            retentionScore = retentionScore,
            lastLesson = prefs.lastLessonChapterTitle?.let { chapterTitle ->
                LastLessonUi(
                    subjectName = prefs.lastLessonSubjectName.orEmpty(),
                    chapterTitle = chapterTitle,
                    progress = prefs.lastLessonProgress
                )
            }
        )
    }

    /**
     * Enregistre l'évaluation de l'élève et fait avancer la carte dans le moteur SRS.
     * La date d'examen de la classe ([levelId]) plafonne l'intervalle : aucune carte ne
     * sera programmée après le jour J.
     */
    suspend fun rate(studentId: String, cardId: String, rating: ReviewRating, levelId: String?) =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val cap = SrsEngine.examIntervalCap(now, levelId?.let { exams.cached(it) })

            // Progression et historique s'écrivent ensemble ou pas du tout
            db.withTransaction {
                val current = srs.getProgress(studentId, cardId)
                    ?: CardProgressEntity(studentId = studentId, flashcardId = cardId, dueDate = now)
                        .also { srs.initProgress(it) }

                val next = SrsEngine.computeNextProgress(current, rating, now, maxIntervalDays = cap)
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

                // Intervalle planifié par FSRS (affiché à l'élève dans la correction)
                next.intervalDays
            }
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
                    else FlashcardEntity(
                        id = c.id,
                        subjectId = s.id,
                        chapitre = ch.title,
                        question = c.front,
                        reponse = answer,
                        kind = c.kind,
                        choicesJson = JSONArray(c.choices).toString(),
                        correctIndices = ci.kalyno.app.data.dojo
                            .mergeCorrectIndices(c.correctIndices, c.correctIndex)
                            .joinToString(","),
                        explanation = c.explanation
                    )
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
        ci.kalyno.app.data.dojo.answerText(
            card.choices,
            ci.kalyno.app.data.dojo.mergeCorrectIndices(card.correctIndices, card.correctIndex),
            card.back
        )

    private fun choicesFromJson(json: String?): List<String> = try {
        if (json.isNullOrBlank()) {
            emptyList()
        } else {
            JSONArray(json).let { array -> List(array.length()) { array.optString(it) } }
        }
    } catch (e: Exception) {
        emptyList()
    }

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
            explanation = card.explanation,
            kind = card.kind,
            correctIndices = ci.kalyno.app.data.dojo.mergeCorrectIndices(card.correctIndices, card.correctIndex)
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
        /** Nombre de réussites d'affilée pour considérer une notion comme maîtrisée. */
        const val MASTERED_REPETITIONS = 3
    }
}
