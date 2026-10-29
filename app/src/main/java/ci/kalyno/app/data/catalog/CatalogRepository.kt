package ci.kalyno.app.data.catalog

import android.content.Context
import ci.kalyno.app.data.LearningSubject
import ci.kalyno.app.data.LevelInfo
import ci.kalyno.app.data.Levels
import ci.kalyno.app.data.SupabaseLearningRepository
import ci.kalyno.app.data.dashboard.KalynoDatabase
import ci.kalyno.app.data.dojo.LessonPassages
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class CatalogSubject(val id: String, val name: String, val iconKey: String?, val lessonCount: Int)

data class CatalogTheme(val id: String, val subjectId: String, val title: String, val lessonCount: Int)

data class CatalogTree(val subjects: List<CatalogSubject>, val themes: List<CatalogTheme>)

/**
 * Pont entre l'admin (Supabase) et Room : le réseau remplit Room, l'interface observe Room.
 * Online-first : on interroge toujours le réseau, Room sert d'affichage immédiat et de secours.
 */
class CatalogRepository private constructor(context: Context) {

    private val dao = KalynoDatabase.get(context).catalogDao()
    private val remote = SupabaseLearningRepository()

    // ----- Classes / séries -----

    fun observeLevels(): Flow<List<LevelInfo>> =
        dao.observeLevels().map { rows -> rows.map { it.toInfo() } }

    /** Charge les classes de Room (rapide, hors ligne) pour que Levels.label() réponde correctement. */
    suspend fun hydrateLabels() {
        val rows = dao.levelsOnce()
        if (rows.isNotEmpty()) Levels.register(rows.map { it.toInfo() })
    }

    /**
     * Une liste vide signifie que Supabase a répondu correctement sans classe active.
     * Les erreurs réseau/HTTP sont propagées et ne vident donc jamais le cache par erreur.
     */
    suspend fun refreshLevels() {
        val levels = remote.loadLevels()
        if (levels.isEmpty()) {
            dao.deleteAllLevels()
        } else {
            dao.upsertLevels(levels.map { it.toEntity() })
            dao.deleteLevelsNotIn(levels.map { it.id })
        }
        Levels.register(levels)
    }

    suspend fun warmUp() {
        hydrateLabels()
        runCatching { refreshLevels() }
    }

    // ----- Arborescence Discipline > Thème > Leçon -----

    fun observeTree(levelId: String): Flow<CatalogTree> = combine(
        dao.observeSubjects(levelId),
        dao.observeThemes(levelId),
        dao.observeLessons(levelId)
    ) { subjects, themes, lessons ->
        val byTheme = lessons.groupingBy { it.themeId }.eachCount()
        val bySubject = lessons.groupingBy { it.subjectId }.eachCount()
        CatalogTree(
            subjects = subjects.map { CatalogSubject(it.id, it.name, it.iconKey, bySubject[it.id] ?: 0) },
            themes = themes.map { CatalogTheme(it.id, it.subjectId, it.title, byTheme[it.id] ?: 0) }
        )
    }

    /** Enregistre dans Room la structure d'une classe (les blocs de leçon restent dans le cache de contenu). */
    suspend fun storeTree(levelId: String, content: List<LearningSubject>) {
        val info = Levels.info(levelId)
        val level = CatalogLevelEntity(
            id = levelId,
            label = info?.label ?: Levels.label(levelId),
            cycle = info?.cycle.orEmpty(),
            iconKey = info?.iconKey,
            tagline = info?.tagline,
            position = info?.position ?: 0
        )

        val subjects = ArrayList<CatalogSubjectEntity>()
        val themes = ArrayList<CatalogThemeEntity>()
        val lessons = ArrayList<CatalogLessonEntity>()

        content.forEachIndexed { sIndex, subject ->
            subjects += CatalogSubjectEntity(subject.id, levelId, subject.name, subject.iconKey, sIndex)
            val themeIds = HashSet<String>()
            subject.themes.forEachIndexed { tIndex, theme ->
                if (themeIds.add(theme.id)) {
                    themes += CatalogThemeEntity(theme.id, subject.id, theme.title, tIndex)
                }
            }
            subject.chapters.forEachIndexed { cIndex, chapter ->
                lessons += CatalogLessonEntity(
                    id = chapter.id,
                    subjectId = subject.id,
                    themeId = chapter.themeId?.takeIf { it in themeIds },
                    title = chapter.title,
                    position = cIndex,
                    tag = chapter.tag,
                    summary = LessonPassages.summaryOf(chapter)
                )
            }
        }

        dao.replaceLevelTree(level, subjects, themes, lessons)
    }

    // ----- Mapping -----

    private fun CatalogLevelEntity.toInfo() = LevelInfo(id, label, cycle, iconKey, tagline, position)

    private fun LevelInfo.toEntity() = CatalogLevelEntity(id, label, cycle, iconKey, tagline, position)

    companion object {
        @Volatile
        private var instance: CatalogRepository? = null

        fun get(context: Context): CatalogRepository =
            instance ?: synchronized(this) {
                instance ?: CatalogRepository(context.applicationContext).also { instance = it }
            }
    }
}
