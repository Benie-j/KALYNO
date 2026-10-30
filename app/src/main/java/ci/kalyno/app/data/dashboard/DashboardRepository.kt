package ci.kalyno.app.data.dashboard

import android.content.Context
import androidx.room.withTransaction
import ci.kalyno.app.data.LearningCard
import ci.kalyno.app.data.LearningChapter
import ci.kalyno.app.data.LearningSubject
import ci.kalyno.app.data.AppPrefs
import ci.kalyno.app.data.SupabaseLearningRepository
import ci.kalyno.app.data.cloud.CloudSync
import ci.kalyno.app.data.dojo.DojoStagePolicy
import ci.kalyno.app.data.lessons.LessonProgressRepository
import ci.kalyno.app.data.srs.CardProgressEntity
import ci.kalyno.app.data.srs.FlashcardEntity
import ci.kalyno.app.data.srs.ReviewLogEntity
import ci.kalyno.app.data.srs.ReviewRating
import ci.kalyno.app.data.srs.LearningBehavior
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
    private val lessonProgress = LessonProgressRepository.get(context)

    data class Snapshot(
        val mission: MissionUi?,
        val missionStatus: MissionStatus,
        val urgentTotal: Int,
        val urgentTopics: List<UrgentTopic>,
        val urgentItems: List<ReviewItem>,
        /** Jours restants avant l'examen publié par l'admin (null si aucune date). */
        val examDaysLeft: Int?,
        /** Nombre de notions (chapitres) maitrisees = masteredNotions.size, toujours coherent avec la liste. */
        val masteredCount: Int,
        /** Detail des notions maitrisees (regroupees par chapitre), pour l'ecran "Ma progression". */
        val masteredNotions: List<MasteredNotion>,
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

        // Règle stricte : le Dojo ne propose que les cartes des leçons que l'élève a marquées « terminées »
        val unlockedCardIds = lessonProgress.unlockedCardIds(studentId)

        var due = SrsEngine.prioritize(
            srs.getDueCards(studentId, now),
            now,
            srs.subjectExposure(studentId).associate { it.subjectId to it.lastStudiedAt }
        )
            .filter { it.subjectId in levelSubjectIds && it.flashcardId in unlockedCardIds }
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
                unlocked = unlockedCardIds,
                now = now,
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

        // ----- KPI : score de rétention (niveau fiche, moyenne globale) -----
        val masteryRows = srs.masteryRows(studentId)
        val retentionScore = masteryRows
            .mapNotNull { row ->
                val last = row.lastReviewedAt ?: return@mapNotNull null
                val stability = row.stability.takeIf { it > 0.0 } ?: return@mapNotNull null
                // Courbe de l'oubli FSRS-6, la même que celle du moteur SRS.
                val elapsedDays = (now - last) / DAY_MS.toDouble()
                SrsEngine.retrievability(elapsedDays, stability)
            }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.toFloat()

        // ----- Détail des notions maîtrisées (groupées par chapitre) -----
        // Une "notion" = un chapitre, pas une fiche isolée : c'est le niveau auquel l'élève
        // raisonne. masteredCount en dérive directement pour que le chiffre affiché en tête
        // de l'écran corresponde EXACTEMENT au nombre de cartes listées en dessous.
        val masteredNotions = srs.masteredDetails(studentId, MASTERED_REPETITIONS)
            .groupBy { "${it.subjectId}::${it.chapitre}" }
            .mapNotNull { (key, rows) ->
                val chapitre = rows.first().chapitre.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val subjectName = rows.first().subjectName
                val maxRepetitions = rows.maxOf { it.repetitions }
                val tier = when {
                    maxRepetitions >= EXPERT_REPETITIONS -> MasteryTier.EXPERTE
                    maxRepetitions >= ADVANCED_REPETITIONS -> MasteryTier.AVANCEE
                    else -> MasteryTier.SOLIDE
                }
                val score = rows
                    .mapNotNull { row ->
                        val last = row.lastReviewedAt ?: return@mapNotNull null
                        val stability = row.stability.takeIf { it > 0.0 } ?: return@mapNotNull null
                        SrsEngine.retrievability((now - last) / DAY_MS.toDouble(), stability)
                    }
                    .takeIf { it.isNotEmpty() }
                    ?.average()
                    ?.toFloat()
                MasteredNotion(
                    key = key,
                    label = chapitre,
                    domain = subjectName,
                    masteredCards = rows.size,
                    tier = tier,
                    score = score
                )
            }
            .sortedWith(compareByDescending<MasteredNotion> { it.tier }.thenByDescending { it.masteredCards })
        val masteredCount = masteredNotions.size

        Snapshot(
            mission = when (status) {
                MissionStatus.READY -> mission
                MissionStatus.DONE_TODAY -> mission ?: MissionUi(
                    subjectName = "",
                    subjectEmoji = "",
                    chapterTitle = "",
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
            masteredNotions = masteredNotions,
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
    suspend fun rate(
        studentId: String,
        cardId: String,
        rating: ReviewRating,
        levelId: String?,
        latencyMs: Long = 0L
    ) =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val cap = SrsEngine.examIntervalCap(now, levelId?.let { exams.cached(it) })

            // Progression et historique s'écrivent ensemble ou pas du tout
            db.withTransaction {
                val current = srs.getProgress(studentId, cardId)
                    ?: CardProgressEntity(studentId = studentId, flashcardId = cardId, dueDate = now)
                        .also { srs.initProgress(it) }

                // FSRS replanifie la carte ; la politique Dojo fait évoluer son niveau (QCM -> saisie libre)
                val next = SrsEngine.computeNextProgress(current, rating, now, maxIntervalDays = cap)
                    .let { it.copy(dojoStage = DojoStagePolicy.next(current.dojoStage, rating)) }
                    // Latence, compteurs, réapprentissage ciblé des notions ratées
                    .let { LearningBehavior.afterReview(current, it, rating, latencyMs, now) }
                srs.updateProgress(next)
                srs.logReview(
                    ReviewLogEntity(
                        studentId = studentId,
                        flashcardId = cardId,
                        rating = rating,
                        intervalBefore = current.intervalDays,
                        intervalAfter = next.intervalDays,
                        reviewedAt = now,
                        latencyMs = latencyMs.coerceAtLeast(0L),
                        retrievabilityBefore = LearningBehavior.retrievabilityNow(current, now)
                    )
                )

                // Intervalle planifié par FSRS (affiché à l'élève dans la correction)
                next.intervalDays
            }
        }

    /** Niveau Dojo d'un élève et référence admin pour chaque carte d'une session (une lecture Room groupée). */
    suspend fun dojoMeta(studentId: String, cardIds: List<String>): Map<String, DojoCardMeta> =
        withContext(Dispatchers.IO) {
            if (cardIds.isEmpty()) return@withContext emptyMap()
            val stages = cardIds.chunked(500)
                .flatMap { srs.stagesFor(studentId, it) }
                .associate { it.flashcardId to it.dojoStage }
            val questions = cardIds.chunked(500)
                .flatMap { dao.questionsWithLesson(it) }
                .associateBy { it.question.id }
            cardIds.associateWith { id ->
                val q = questions[id]
                DojoCardMeta(
                    stage = stages[id] ?: 0,
                    adminReference = q?.question?.adminReference,
                    lessonTitle = q?.lessonTitle,
                    lessonSummary = q?.lessonSummary
                )
            }
        }

    private suspend fun sync(
        levelId: String,
        subjects: List<LearningSubject>,
        coefficients: Map<String, Float>
    ) {
        // Si la lecture des coefficients a échoué (hors ligne…), on garde ceux déjà en base locale
        // au lieu de les écraser par 1 : le moteur FSRS continue avec la dernière valeur de l'admin.
        val known = dao.subjectsForLevel(levelId).associate { it.id to it.coefficient }
        dao.upsertSubjects(
            subjects.map { SubjectEntity(it.id, it.name, levelId, coefficients[it.id] ?: known[it.id] ?: 1f) }
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
                        explanation = c.explanation,
                        adminReference = c.adminReference,
                        lessonId = ch.id
                    )
                }
            }
        }
        cards.chunked(300).forEach { dao.upsertFlashcards(it) }
    }

    /**
     * Construit la mission du jour à partir des SEULES leçons terminées par l'élève :
     * - une carte n'est candidate que si elle est débloquée ([unlocked]) et pas encore découverte ;
     * - les matières sont classées par score = coefficient × (1 + jours depuis la dernière nouvelle carte) ;
     * - les cartes sont ensuite prises à tour de rôle dans chaque matière (entrelacement global),
     *   dans la limite du quota restant : une session alterne Français, SVT, Maths… au lieu d'enchaîner une matière.
     */
    private fun buildMission(
        subjects: List<LearningSubject>,
        coefficients: Map<String, Float>,
        introduced: Set<String>,
        lastIntroduced: Map<String, Long>,
        unlocked: Set<String>,
        now: Long,
        remainingQuota: Int,
        doneToday: Int,
        targetToday: Int
    ): MissionUi? {
        class Candidate(val subject: LearningSubject, val score: Float, val queue: ArrayDeque<ReviewItem>)

        val candidates = subjects.mapNotNull { s ->
            val items = s.chapters.flatMap { ch ->
                ch.cards
                    .mapNotNull { itemFrom(it, s.name, ch.title) }
                    .filter { it.cardId !in introduced && it.cardId in unlocked }
            }
            if (items.isEmpty()) return@mapNotNull null
            val last = lastIntroduced[s.id]
            val daysSince = if (last == null) SrsConfig.NEVER_STUDIED_DAYS
            else ((now - last) / DAY_MS).toInt().coerceAtLeast(0)
            Candidate(s, (coefficients[s.id] ?: 1f) * (1 + daysSince), ArrayDeque(items))
        }.sortedByDescending { it.score }
        if (candidates.isEmpty()) return null

        val items = ArrayList<ReviewItem>()
        val used = LinkedHashSet<String>()
        while (items.size < remainingQuota && candidates.any { it.queue.isNotEmpty() }) {
            for (c in candidates) {
                if (items.size >= remainingQuota) break
                val next = c.queue.removeFirstOrNull() ?: continue
                items += next
                used += c.subject.name
            }
        }

        val mixed = used.size > 1
        return MissionUi(
            subjectName = if (mixed) used.joinToString(" · ") else used.firstOrNull() ?: candidates.first().subject.name,
            subjectEmoji = if (mixed) "" else candidates.first().subject.emoji,
            chapterTitle = if (mixed) "Révision mixte" else items.firstOrNull()?.chapterTitle.orEmpty(),
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
        /** Paliers d'affichage de l'écran "Ma progression" (dérivés du seuil de maîtrise ci-dessus). */
        const val ADVANCED_REPETITIONS = 5
        const val EXPERT_REPETITIONS = 8
    }
}
