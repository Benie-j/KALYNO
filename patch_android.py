import os, sys
root = sys.argv[1] if len(sys.argv) > 1 else "."
B = os.path.join(root, "app/src/main/java/ci/kalyno/app") + "/"

def rw(name, fn):
    p = B + name
    s = open(p, encoding="utf-8").read()
    open(p, "w", encoding="utf-8").write(fn(s))
def rep(s, old, new):
    assert s.count(old) == 1, f"correspondance != 1 ({s.count(old)}): {old[:80]!r}"
    return s.replace(old, new)

# ---------------- 1. Dépôt Supabase : is_available ----------------
def repo(s):
    s = rep(s, 'data class LearningSession(val id: String, val title: String)',
            '/** [isAvailable] = false : séance masquée aux élèves par l\'admin (true si la colonne n\'existe pas encore). */\ndata class LearningSession(val id: String, val title: String, val isAvailable: Boolean = true)')
    s = rep(s, '''            val rows = getArray(
                "lesson_sessions?select=id,title,position&chapter_id=eq.${enc(chapterId)}&order=position.asc"
            )
            List(rows.length()) { i ->
                val row = rows.getJSONObject(i)
                LearningSession(id = row.getString("id"), title = row.optString("title", "").trim())
            }''',
'''            val rows = loadSessionRows(chapterId)
            List(rows.length()) { i ->
                val row = rows.getJSONObject(i)
                LearningSession(
                    id = row.getString("id"),
                    title = row.optString("title", "").trim(),
                    // Colonne absente ou nulle : séance considérée disponible (comportement historique)
                    isAvailable = if (row.isNull("is_available")) true else row.optBoolean("is_available", true)
                )
            }''')
    s = rep(s, '    private fun loadSessions(chapterId: String): List<LearningSession> {',
'''    /** Faux dès qu'on sait que `lesson_sessions` n'a pas (encore) la colonne `is_available`. */
    @Volatile
    private var sessionAvailabilitySupported = true

    private fun loadSessionRows(chapterId: String): JSONArray {
        val filter = "&chapter_id=eq.${enc(chapterId)}&order=position.asc"
        if (sessionAvailabilitySupported) {
            try {
                return getArray("lesson_sessions?select=id,title,position,is_available$filter")
            } catch (e: IOException) {
                if (e.message?.startsWith("Supabase HTTP 400") == true) sessionAvailabilitySupported = false else throw e
            }
        }
        return getArray("lesson_sessions?select=id,title,position$filter")
    }

    private fun loadSessions(chapterId: String): List<LearningSession> {''')
    return s
rw("data/SupabaseLearningRepository.kt", repo)

# ---------------- 2. Cache hors-ligne ----------------
def cache(s):
    s = rep(s, '                                                put("title", se.title)\n',
            '                                                put("title", se.title)\n                                                put("isAvailable", se.isAvailable)\n')
    s = rep(s, 'LearningSession(id = se.getString("id"), title = se.optString("title", ""))',
            'LearningSession(id = se.getString("id"), title = se.optString("title", ""), isAvailable = se.optBoolean("isAvailable", true))')
    return s
rw("ui/explorer/LearningContentCache.kt", cache)

# ---------------- 3. Room : entité, DAO, migration ----------------
open(B + "data/lessons/SessionProgress.kt", "w", encoding="utf-8").write('''package ci.kalyno.app.data.lessons

import android.content.Context
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ci.kalyno.app.data.cloud.CloudSync
import ci.kalyno.app.data.cloud.CloudSyncScheduler
import ci.kalyno.app.data.dashboard.KalynoDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Validation d'UNE séance par UN élève : une ligne par (élève, séance), écrite au clic sur « Terminer ».
 * Pas de clé étrangère : le catalogue est réécrit à chaque synchronisation (même raison que `lesson_progress`).
 * `studentId` est l'identifiant LOCAL ; l'UUID Supabase (`user_id`) n'est utilisé que par CloudSync.
 */
@Entity(
    tableName = "session_progress",
    primaryKeys = ["studentId", "sessionId"],
    indices = [Index("studentId")]
)
data class SessionProgressEntity(
    val studentId: String,
    val sessionId: String,
    val completedAt: Long          // epoch ms du clic sur « Terminer »
)

@Dao
abstract class SessionProgressDao {

    @Query("SELECT sessionId FROM session_progress WHERE studentId = :studentId")
    abstract fun observeCompletedSessionIds(studentId: String): Flow<List<String>>

    @Query("SELECT * FROM session_progress WHERE studentId = :studentId")
    abstract suspend fun completedRows(studentId: String): List<SessionProgressEntity>

    /** Idempotent : un second clic (ou un second appareil) ne change pas la date de première validation. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfAbsent(row: SessionProgressEntity)
}

/** Point d'accès unique : « séance terminée = ses exercices (et eux seuls) sont débloqués ». */
class SessionProgressRepository private constructor(context: Context) {

    private val dao = KalynoDatabase.get(context).sessionProgressDao()
    private val cloud = CloudSync(context)

    fun observeCompleted(studentId: String): Flow<Set<String>> =
        dao.observeCompletedSessionIds(studentId).map { it.toSet() }.distinctUntilChanged()

    suspend fun markCompleted(studentId: String, sessionId: String) = withContext(Dispatchers.IO) {
        dao.insertIfAbsent(SessionProgressEntity(studentId, sessionId, System.currentTimeMillis()))
        // Sauvegarde cloud en arrière-plan : la validation est déjà dans Room, rien ne bloque l'élève
        CloudSyncScheduler.request(cloud, studentId)
    }

    companion object {
        @Volatile
        private var instance: SessionProgressRepository? = null

        fun get(context: Context): SessionProgressRepository =
            instance ?: synchronized(this) {
                instance ?: SessionProgressRepository(context.applicationContext).also { instance = it }
            }
    }
}
''')

rw("data/srs/Entities.kt", lambda s: rep(s,
    '    val lessonId: String? = null\n)',
    '    val lessonId: String? = null,\n    // -- Séance de la leçon dont la carte est issue (via son bloc) : déblocage ciblé ; null = carte sans séance --\n    val sessionId: String? = null\n)'))

def lessonprogress(s):
    return rep(s, '''        WHERE lp.studentId = :studentId AND lp.isCompleted = 1
        UNION
        SELECT f.id FROM flashcards f
        INNER JOIN catalog_lessons l''', '''        WHERE lp.studentId = :studentId AND lp.isCompleted = 1
        UNION
        SELECT f.id FROM flashcards f
        INNER JOIN session_progress sp ON sp.sessionId = f.sessionId
        WHERE sp.studentId = :studentId
        UNION
        SELECT f.id FROM flashcards f
        INNER JOIN catalog_lessons l''')
rw("data/lessons/LessonProgress.kt", lessonprogress)

def db(s):
    s = rep(s, 'import ci.kalyno.app.data.lessons.LessonProgressEntity\n',
            'import ci.kalyno.app.data.lessons.LessonProgressEntity\nimport ci.kalyno.app.data.lessons.SessionProgressDao\nimport ci.kalyno.app.data.lessons.SessionProgressEntity\n')
    s = rep(s, '        LessonProgressEntity::class,\n', '        LessonProgressEntity::class,\n        SessionProgressEntity::class,\n')
    s = rep(s, '    version = 14,', '    version = 15,')
    s = rep(s, '    abstract fun lessonProgressDao(): LessonProgressDao\n',
            '    abstract fun lessonProgressDao(): LessonProgressDao\n    abstract fun sessionProgressDao(): SessionProgressDao\n')
    s = rep(s, '        fun get(context: Context): KalynoDatabase =',
'''        /**
         * Séances : progression par séance (nouvelle table) et séance d'origine de chaque carte (nouvelle colonne,
         * remplie à la prochaine synchronisation du catalogue). Rien n'est supprimé ni réécrit.
         */
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE flashcards ADD COLUMN sessionId TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `session_progress` (`studentId` TEXT NOT NULL, `sessionId` TEXT NOT NULL, " +
                        "`completedAt` INTEGER NOT NULL, PRIMARY KEY(`studentId`, `sessionId`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_progress_studentId` ON `session_progress` (`studentId`)")
            }
        }

        fun get(context: Context): KalynoDatabase =''')
    s = rep(s, 'MIGRATION_12_13, MIGRATION_13_14)', 'MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)')
    return s
rw("data/dashboard/KalynoDatabase.kt", db)

def dash(s):
    s = rep(s, '            s.chapters.flatMap { ch ->\n                ch.cards.mapNotNull { c ->',
            '            s.chapters.flatMap { ch ->\n                // Séance de chaque bloc : une carte appartient à la séance de son bloc (null = sans bloc ou sans séance)\n                val sessionOfCapsule = ch.capsules.associate { it.id to it.sessionId }\n                ch.cards.mapNotNull { c ->')
    s = rep(s, '                        adminReference = c.adminReference,\n                        lessonId = ch.id\n                    )',
            '                        adminReference = c.adminReference,\n                        lessonId = ch.id,\n                        sessionId = c.capsuleId?.let { sessionOfCapsule[it] }\n                    )')
    return s
rw("data/dashboard/DashboardRepository.kt", dash)

# ---------------- 4. CloudSync ----------------
def cloud(s):
    s = rep(s, 'import ci.kalyno.app.data.lessons.LessonProgressEntity\n',
            'import ci.kalyno.app.data.lessons.LessonProgressEntity\nimport ci.kalyno.app.data.lessons.SessionProgressEntity\n')
    s = rep(s, '    private val lessonDao = KalynoDatabase.get(appContext).lessonProgressDao()\n',
            '    private val lessonDao = KalynoDatabase.get(appContext).lessonProgressDao()\n    private val sessionDao = KalynoDatabase.get(appContext).sessionProgressDao()\n')
    s = rep(s, '        step("leçons reçues") { pullLessons(studentId, userId, token) }\n',
            '        step("leçons reçues") { pullLessons(studentId, userId, token) }\n        step("séances reçues") { pullSessions(studentId, userId, token) }\n')
    s = rep(s, '        step("leçons envoyées") { pushLessons(studentId, userId, token) }\n',
            '        step("leçons envoyées") { pushLessons(studentId, userId, token) }\n        step("séances envoyées") { pushSessions(studentId, userId, token) }\n')
    s = rep(s, '    /** Vrai quand Supabase répond « table ou colonne inconnue »',
'''    /**
     * Séances terminées (table `lesson_session_progress`). `user_id` est l'UUID du compte Supabase (store.userId()),
     * jamais l'identifiant local de l'élève. Fusion : on ajoute seulement les séances absentes en local.
     */
    private suspend fun pullSessions(studentId: String, userId: String, token: String) {
        val rows = try {
            JSONArray(
                SupabaseRest.request(
                    "GET",
                    "lesson_session_progress?user_id=eq.$userId&select=session_id,completed_at&limit=$LESSON_LIMIT",
                    token
                )
            )
        } catch (e: IOException) {
            if (isMissingSchema(e)) {
                Log.w(TAG, "Table lesson_session_progress absente côté Supabase : séances non restaurées")
                return
            }
            throw e
        }
        for (i in 0 until rows.length()) {
            val r = rows.getJSONObject(i)
            sessionDao.insertIfAbsent(
                SessionProgressEntity(
                    studentId = studentId,
                    sessionId = r.getString("session_id"),
                    completedAt = parseCompletedAt(r.opt("completed_at"))
                )
            )
        }
    }

    /** `completed_at` peut être un horodatage ISO (timestamptz) ou un nombre (epoch ms) selon le schéma. */
    private fun parseCompletedAt(value: Any?): Long = when (value) {
        is Number -> value.toLong()
        is String -> runCatching { java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .getOrElse { value.toLongOrNull() ?: System.currentTimeMillis() }
        else -> System.currentTimeMillis()
    }

    /** Envoie les séances terminées (idempotent : « ignore-duplicates » sur la clé primaire, aucun doublon). */
    private suspend fun pushSessions(studentId: String, userId: String, token: String) {
        val done = sessionDao.completedRows(studentId)
        if (done.isEmpty()) return
        val signature = done.map { it.sessionId }.sorted().hashCode()
        if (signature == pushedSessionsSignature) return

        try {
            done.chunked(200).forEach { chunk -> postSessionRows(chunk, userId, token) }
        } catch (e: IOException) {
            if (isMissingSchema(e)) {
                Log.w(TAG, "Table lesson_session_progress absente côté Supabase : séances non envoyées")
                return
            }
            throw e
        }
        pushedSessionsSignature = signature
    }

    private fun postSessionRows(rows: List<SessionProgressEntity>, userId: String, token: String) {
        fun send(asIso: Boolean) {
            val body = JSONArray()
            rows.forEach { r ->
                body.put(
                    JSONObject()
                        .put("user_id", userId)
                        .put("session_id", r.sessionId)
                        .put("completed_at", if (asIso) java.time.Instant.ofEpochMilli(r.completedAt).toString() else r.completedAt)
                )
            }
            SupabaseRest.request("POST", "lesson_session_progress", token, body.toString(), IGNORE_DUPLICATES)
        }
        try {
            send(asIso = true)
        } catch (e: IOException) {
            // Colonne numérique plutôt que timestamptz : on retente avec des millisecondes
            if (e.message?.startsWith("Supabase HTTP 400") == true) send(asIso = false) else throw e
        }
    }

    /** Vrai quand Supabase répond « table ou colonne inconnue »''')
    s = rep(s, '        var pushedLessonsSignature = 0\n', '        var pushedLessonsSignature = 0\n\n        @Volatile\n        var pushedSessionsSignature = 0\n')
    return s
rw("data/cloud/CloudSync.kt", cloud)

# ---------------- 5. Contenu élève : séances disponibles ----------------
def sessions(s):
    s = rep(s, 'import ci.kalyno.app.data.LearningCapsule\n',
            'import ci.kalyno.app.data.LearningCapsule\nimport ci.kalyno.app.data.LearningSession\nimport ci.kalyno.app.data.dashboard.ReviewItem\n')
    s = rep(s, '''        chapter.sessions.forEachIndexed { index, session ->
            val capsules = bySession[session.id].orEmpty()
            // Une séance encore vide n'est pas montrée à l'élève
            if (capsules.isNotEmpty()) {
                add(
                    SessionGroup(
                        number = index + 1,
                        title = session.title.takeIf { it.isNotBlank() },
                        blocks = capsules.map { it.toLessonBlock() }
                    )
                )
            }
        }''', '''        // Séances masquées ou vides : jamais montrées ; la numérotation suit les séances visibles
        chapter.availableSessions().forEachIndexed { index, session ->
            add(
                SessionGroup(
                    number = index + 1,
                    title = session.title.takeIf { it.isNotBlank() },
                    blocks = bySession[session.id].orEmpty().map { it.toLessonBlock() }
                )
            )
        }''')
    s = rep(s, '/** Regroupe les blocs de la leçon par séance',
'''/** Séances visibles par l'élève : publiées (`is_available`) ET contenant au moins un bloc. */
fun ExplorerChapter.availableSessions(): List<LearningSession> =
    sessions.filter { s -> s.isAvailable && capsules.any { it.sessionId == s.id } }

/** Exercices rattachés à une séance (carte -> bloc -> séance). */
fun ExplorerChapter.itemsOfSession(sessionId: String): List<ReviewItem> {
    val cardIds = capsules.filter { it.sessionId == sessionId }.flatMap { c -> c.cards.map { it.id } }.toHashSet()
    return items.filter { it.cardId in cardIds }
}

/**
 * Exercices jouables : tous si la leçon est terminée (règle historique, leçons sans séances),
 * sinon uniquement ceux des séances terminées.
 */
fun unlockedItemsOf(chapter: ExplorerChapter, completedLessons: Set<String>, completedSessions: Set<String>): List<ReviewItem> {
    if (chapter.id in completedLessons) return chapter.items
    if (completedSessions.isEmpty()) return emptyList()
    return completedSessions.flatMap { chapter.itemsOfSession(it) }.distinctBy { it.cardId }
}

/** Lignes d'UNE séance : son en-tête puis ses blocs, rien d'autre. */
fun buildSessionRows(chapter: ExplorerChapter, sessionId: String): List<LessonRow> {
    val visible = chapter.availableSessions()
    val index = visible.indexOfFirst { it.id == sessionId }
    if (index < 0) return emptyList()
    val blocks = chapter.capsules.filter { it.sessionId == sessionId }.map { it.toLessonBlock() }
    val group = SessionGroup(index + 1, visible[index].title.takeIf { it.isNotBlank() }, blocks)
    return buildList {
        add(LessonRow.Header(group, key = "session-${group.number}"))
        blocks.forEach { add(LessonRow.Block(it)) }
    }
}

/** Regroupe les blocs de la leçon par séance''')
    return s
rw("ui/explorer/LessonSessions.kt", sessions)

# ---------------- 6. LessonScreen : mode séance ----------------
def lesson(s):
    s = rep(s, '    onBack: () -> Unit,\n    onStartQuestions: () -> Unit\n) {',
            '    onBack: () -> Unit,\n    onStartQuestions: () -> Unit,\n    /** Non nul : affiche UNE séance (ses blocs seuls) ; `isCompleted` / `onComplete` concernent alors cette séance. */\n    sessionId: String? = null,\n    questionCount: Int = chapter.questionCount\n) {')
    s = rep(s, '    val rows = remember(chapter) { buildLessonRows(chapter) }\n    val hasComplement = !chapter.complement.isNullOrBlank()',
            '    val rows = remember(chapter, sessionId) { if (sessionId == null) buildLessonRows(chapter) else buildSessionRows(chapter, sessionId) }\n    val hasComplement = sessionId == null && !chapter.complement.isNullOrBlank()')
    s = rep(s, '        val hasSessions = rows.any { it is LessonRow.Header }', '        val hasSessions = sessionId == null && rows.any { it is LessonRow.Header }')
    s = rep(s, 'LessonFinishBar(isCompleted = isCompleted, onComplete = onComplete)',
            'LessonFinishBar(isCompleted = isCompleted, onComplete = onComplete, unitLabel = if (sessionId == null) "leçon" else "séance")')
    s = rep(s, 'if (chapter.questionCount > 0 && isCompleted) {', 'if (questionCount > 0 && isCompleted) {')
    s = rep(s, 'label = "DÉMARRER LES ${chapter.questionCount} QUESTIONS"', 'label = "DÉMARRER LES $questionCount QUESTIONS"')
    s = rep(s, 'private fun LessonFinishBar(isCompleted: Boolean, onComplete: () -> Unit) {',
            'private fun LessonFinishBar(isCompleted: Boolean, onComplete: () -> Unit, unitLabel: String = "leçon") {\n    val isSession = unitLabel == "séance"')
    s = rep(s, 'text = "Leçon terminée · ses exercices sont débloqués dans le Dojo",',
            'text = if (isSession) "Séance terminée · ses exercices sont débloqués dans le Dojo" else "Leçon terminée · ses exercices sont débloqués dans le Dojo",')
    s = rep(s, 'KalynoPrimaryButton(label = "TERMINER · J\'AI LU CETTE LEÇON", onClick = onComplete)',
            'KalynoPrimaryButton(label = "TERMINER · J\'AI LU CETTE ${unitLabel.uppercase()}", onClick = onComplete)')
    s = rep(s, 'text = "Appuie quand tu as fini de lire pour débloquer les exercices dans le Dojo.",',
            'text = if (isSession) "Appuie quand tu as fini de lire pour débloquer les exercices de cette séance dans le Dojo." else "Appuie quand tu as fini de lire pour débloquer les exercices dans le Dojo.",')
    return s
rw("ui/explorer/LessonScreen.kt", lesson)

# ---------------- 7. ViewModel ----------------
def vm(s):
    s = rep(s, 'import ci.kalyno.app.data.lessons.LessonProgressRepository\n',
            'import ci.kalyno.app.data.lessons.LessonProgressRepository\nimport ci.kalyno.app.data.lessons.SessionProgressRepository\n')
    s = rep(s, '    val completedLessonIds: Set<String> = emptySet(),\n',
            '    val completedLessonIds: Set<String> = emptySet(),\n    /** Séances que l\'élève a validées (Room) : seuls leurs exercices sont jouables. */\n    val completedSessionIds: Set<String> = emptySet(),\n    /** Séance ouverte dans la leçon (null = liste des séances). */\n    val openSessionId: String? = null,\n')
    s = rep(s, '    fun isCompleted(lessonId: String): Boolean = lessonId in completedLessonIds\n',
            '    fun isCompleted(lessonId: String): Boolean = lessonId in completedLessonIds\n\n    fun isSessionCompleted(sessionId: String): Boolean = sessionId in completedSessionIds\n\n    /** Exercices que l\'élève a le droit de jouer dans cette leçon (leçon terminée, ou séances terminées). */\n    fun unlockedItems(chapter: ExplorerChapter): List<ReviewItem> =\n        unlockedItemsOf(chapter, completedLessonIds, completedSessionIds)\n')
    s = rep(s, '    private val lessonProgress = LessonProgressRepository.get(app)\n',
            '    private val lessonProgress = LessonProgressRepository.get(app)\n    private val sessionProgress = SessionProgressRepository.get(app)\n    private var sessionCompletionJob: Job? = null\n')
    s = rep(s, '        observeCompletion()\n        refresh(silent = false)', '        observeCompletion()\n        observeSessionCompletion()\n        refresh(silent = false)')
    s = rep(s, '    /**\n     * Leçons à réviser, classées par urgence FSRS',
'''    /** Séances terminées lues dans Room : les boutons et les exercices se mettent à jour seuls. */
    private fun observeSessionCompletion() {
        sessionCompletionJob?.cancel()
        sessionCompletionJob = viewModelScope.launch {
            sessionProgress.observeCompleted(studentId).collect { ids ->
                _state.update { it.copy(completedSessionIds = ids) }
            }
        }
    }

    fun onSessionClick(sessionId: String) {
        _state.update { it.copy(openSessionId = sessionId) }
    }

    /** Clic sur « Terminer » d'une séance : seule cette séance est validée (idempotent), seuls ses exercices se débloquent. */
    fun onSessionCompleted(sessionId: String) {
        if (_state.value.isSessionCompleted(sessionId)) return
        viewModelScope.launch {
            try {
                sessionProgress.markCompleted(studentId, sessionId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Écriture perdue : le bouton reste actif, l'élève peut réessayer
            }
        }
    }

    /**
     * Leçons à réviser, classées par urgence FSRS''')
    s = rep(s, '_state.map { it.chapters to it.completedLessonIds }.distinctUntilChanged(),',
            '_state.map { Triple(it.chapters, it.completedLessonIds, it.completedSessionIds) }.distinctUntilChanged(),')
    s = rep(s, '.collectLatest { (chapters, completed) ->', '.collectLatest { (chapters, completed, completedSessions) ->')
    s = rep(s, 'computeForYou(chapters, completed) }', 'computeForYou(chapters, completed, completedSessions) }')
    s = rep(s, 'private suspend fun computeForYou(chapters: List<ExplorerChapter>, completed: Set<String>): List<ForYouPick> {\n        val playable = chapters.filter { it.id in completed && it.questionCount > 0 }',
            'private suspend fun computeForYou(\n        chapters: List<ExplorerChapter>,\n        completed: Set<String>,\n        completedSessions: Set<String>\n    ): List<ForYouPick> {\n        // Exercices jouables : leçon terminée (historique) ou séances terminées\n        val playable = chapters.mapNotNull { ch ->\n            val unlocked = unlockedItemsOf(ch, completed, completedSessions)\n            if (unlocked.isEmpty()) null else ch.copy(items = unlocked)\n        }')
    s = rep(s, '        _state.update { it.copy(openLessonId = chapter.id) }', '        _state.update { it.copy(openLessonId = chapter.id, openSessionId = null) }')
    s = rep(s, '''            st.openLessonId != null -> {
                _state.update { it.copy(openLessonId = null) }
                true
            }''', '''            st.openSessionId != null -> {
                // Retour de l'écran d'une séance vers la liste des séances de la leçon
                _state.update { it.copy(openSessionId = null) }
                true
            }
            st.openLessonId != null -> {
                _state.update { it.copy(openLessonId = null) }
                true
            }''')
    s = rep(s, '            next = next.copy(openLessonId = null)\n        }',
            '            next = next.copy(openLessonId = null, openSessionId = null)\n        }\n        // Séance masquée ou supprimée par l\'admin pendant la lecture : retour à la liste des séances\n        if (next.openSessionId != null) {\n            val lesson = next.chapters.firstOrNull { it.id == next.openLessonId }\n            if (lesson == null || lesson.availableSessions().none { it.id == next.openSessionId }) next = next.copy(openSessionId = null)\n        }')
    return s
rw("ui/explorer/ExplorerViewModel.kt", vm)

# ---------------- 8. ExplorerScreen ----------------
def screen(s):
    s = rep(s, '''    val start: (ExplorerChapter) -> Unit = { chapter ->
        when {
            !ui.isCompleted(chapter.id) -> viewModel.onLessonClick(chapter)
            chapter.items.isNotEmpty() -> onStartChapter(chapter.title, chapter.items)
            else -> Unit
        }
    }''', '''    val start: (ExplorerChapter) -> Unit = { chapter ->
        // Exercices jouables : toute la leçon si elle est terminée, sinon ceux des séances terminées
        val unlocked = ui.unlockedItems(chapter)
        when {
            unlocked.isNotEmpty() -> onStartChapter(chapter.title, unlocked)
            !ui.isCompleted(chapter.id) -> viewModel.onLessonClick(chapter)
            else -> Unit
        }
    }''')
    s = rep(s, '''                if (lesson != null) {
                    LessonScreen(
                        chapter = lesson,
                        isCompleted = ui.isCompleted(lesson.id),
                        onComplete = { viewModel.onLessonCompleted(lesson.id) },
                        onBack = { viewModel.onBack() },
                        onStartQuestions = { start(lesson) }
                    )
                }''', '''                if (lesson != null) {
                    val visibleSessions = lesson.availableSessions()
                    val openSession = visibleSessions.firstOrNull { it.id == ui.openSessionId }
                    when {
                        // Leçon sans séance publiée : fonctionnement historique inchangé
                        visibleSessions.isEmpty() -> LessonScreen(
                            chapter = lesson,
                            isCompleted = ui.isCompleted(lesson.id),
                            onComplete = { viewModel.onLessonCompleted(lesson.id) },
                            onBack = { viewModel.onBack() },
                            onStartQuestions = { start(lesson) }
                        )
                        // Une séance : ses blocs seuls, son propre « Terminer »
                        openSession != null -> {
                            val sessionItems = lesson.itemsOfSession(openSession.id)
                            LessonScreen(
                                chapter = lesson,
                                isCompleted = ui.isSessionCompleted(openSession.id),
                                onComplete = { viewModel.onSessionCompleted(openSession.id) },
                                onBack = { viewModel.onBack() },
                                onStartQuestions = { onStartChapter(lesson.title, sessionItems) },
                                sessionId = openSession.id,
                                questionCount = sessionItems.size
                            )
                        }
                        // Liste des séances publiées de la leçon
                        else -> LessonSessionListScreen(
                            chapter = lesson,
                            sessions = visibleSessions,
                            completedIds = ui.completedSessionIds,
                            unlockedCount = ui.unlockedItems(lesson).size,
                            onOpenSession = { viewModel.onSessionClick(it) },
                            onStartQuestions = { start(lesson) },
                            onBack = { viewModel.onBack() }
                        )
                    }
                }''')
    return s
rw("ui/explorer/ExplorerScreen.kt", screen)

# ---------------- 9. Écran « liste des séances » ----------------
open(B + "ui/explorer/SessionNavigation.kt", "w", encoding="utf-8").write('''package ci.kalyno.app.ui.explorer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.data.LearningSession
import ci.kalyno.app.ui.components.KalynoCard
import ci.kalyno.app.ui.components.KalynoPrimaryButton
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import ci.kalyno.app.ui.icons.iconAssetFor
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoCorrectSoft
import ci.kalyno.app.ui.theme.KalynoCorrectText
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoText

/**
 * Liste des séances PUBLIÉES d'une leçon. Chaque séance s'ouvre sur son propre écran (jamais empilées),
 * avec son état « terminée » indépendant des autres.
 */
@Composable
fun LessonSessionListScreen(
    chapter: ExplorerChapter,
    sessions: List<LearningSession>,
    completedIds: Set<String>,
    /** Nombre d'exercices déjà débloqués dans cette leçon (0 = bouton d'entraînement masqué). */
    unlockedCount: Int,
    onOpenSession: (String) -> Unit,
    onStartQuestions: () -> Unit,
    onBack: () -> Unit
) {
    val doneCount = sessions.count { it.id in completedIds }
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExplorerBackButton(onClick = onBack)
            Spacer(Modifier.width(12.dp))
            KalynoSvgIcon(name = iconAssetFor(chapter.subjectIconKey), size = 18.dp, tint = KalynoBlue)
            Spacer(Modifier.width(8.dp))
            Text(
                text = chapter.subjectName,
                color = KalynoBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "title") {
                Column(modifier = Modifier.padding(bottom = 4.dp)) {
                    Text(
                        text = chapter.title,
                        color = KalynoText,
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "$doneCount / ${sessions.size} séance(s) terminée(s)",
                        color = KalynoMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            itemsIndexed(sessions, key = { _, s -> s.id }) { index, session ->
                SessionListCard(
                    number = index + 1,
                    session = session,
                    blockCount = chapter.capsules.count { it.sessionId == session.id },
                    completed = session.id in completedIds,
                    onClick = { onOpenSession(session.id) }
                )
            }
            if (unlockedCount > 0) {
                item(key = "train") {
                    KalynoPrimaryButton(label = "S'ENTRAÎNER · $unlockedCount EXERCICES DÉBLOQUÉS", onClick = onStartQuestions)
                }
            }
        }
    }
}

@Composable
private fun SessionListCard(
    number: Int,
    session: LearningSession,
    blockCount: Int,
    completed: Boolean,
    onClick: () -> Unit
) {
    KalynoCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        contentPadding = 14.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape).background(if (completed) KalynoCorrectSoft else KalynoBlue),
                contentAlignment = Alignment.Center
            ) {
                if (completed) {
                    KalynoSvgIcon(name = "check-circle", size = 20.dp, tint = KalynoCorrectText)
                } else {
                    Text(text = number.toString(), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SÉANCE $number" + if (completed) " · TERMINÉE" else "",
                    color = if (completed) KalynoCorrectText else KalynoBlue,
                    fontSize = 11.sp,
                    letterSpacing = 0.8.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                if (session.title.isNotBlank()) {
                    Text(
                        text = session.title,
                        color = KalynoText,
                        fontSize = 16.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(text = "$blockCount bloc(s)", color = KalynoMuted, fontSize = 12.sp)
            }
            KalynoSvgIcon(name = "chevron-right", size = 18.dp, tint = KalynoMuted)
        }
    }
}
''')
print("android patch ok")
