package ci.kalyno.app.ui.motion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.data.ExplorerBanner
import ci.kalyno.app.data.dashboard.DashboardUiState
import ci.kalyno.app.data.dashboard.LastLessonUi
import ci.kalyno.app.data.dashboard.ReviewItem
import ci.kalyno.app.data.dashboard.UrgentTopic
import ci.kalyno.app.data.dojo.ExerciseKind
import ci.kalyno.app.data.srs.ReviewRating
import ci.kalyno.app.ui.components.KalynoPill
import ci.kalyno.app.ui.components.KalynoPrimaryButton
import ci.kalyno.app.ui.dojo.DojoAnswerArea
import ci.kalyno.app.ui.dojo.DojoExercise
import ci.kalyno.app.ui.dojo.DojoFeedback
import ci.kalyno.app.ui.dojo.DojoMessages
import ci.kalyno.app.ui.dojo.DojoResult
import ci.kalyno.app.ui.dojo.DojoUiState
import ci.kalyno.app.ui.dojo.TopBar
import ci.kalyno.app.ui.dojo.explanationSteps
import ci.kalyno.app.ui.explorer.Discipline
import ci.kalyno.app.ui.explorer.DisciplineLessonsScreen
import ci.kalyno.app.ui.explorer.ExplorerChapter
import ci.kalyno.app.ui.explorer.ExplorerTheme
import ci.kalyno.app.ui.explorer.HeroBannerCarousel
import ci.kalyno.app.ui.explorer.LessonFinishBar
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import ci.kalyno.app.ui.main.AccueilScreen
import ci.kalyno.app.ui.quiz.KalynoQuestionCard
import ci.kalyno.app.ui.quiz.QuizStickyFooter
import ci.kalyno.app.ui.theme.KalynoBackground
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoShadow
import ci.kalyno.app.ui.theme.KalynoText
import ci.kalyno.app.ui.theme.KalynoTheme
import kotlinx.coroutines.delay

/*
 * MOTION DESIGN KALYNO (~58 s) : 100 % composants réels de l'app.
 *
 *  1. Dashboard : HeroBannerCarousel (LazyRow) -> AccueilScreen -> notification "Révisions urgentes"
 *  2. Programme : DisciplineLessonsScreen (4 mini-leçons) -> LessonFinishBar (isCompleted) -> CTA débloqué
 *  3. Dojo      : 3 exercices (QCM, saisie libre sémantique E5, saisie libre mots-clés)
 *
 * Les données ci-dessous (DemoData) sont des FIXTURES de démonstration : elles ne sont jamais lues
 * par l'app réelle. Rien n'est écrit dans Room, Supabase ni dans le moteur FSRS.
 */

/** Écran de lancement de la démo (icône "KALYNO Motion" ajoutée via le manifeste). */
class MotionDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { KalynoTheme { KalynoMotionDemo() } }
    }
}

private enum class Scene { Dashboard, Roadmap, Gate, Dojo }

@Composable
fun KalynoMotionDemo(onFinished: () -> Unit = {}) {
    var runId by remember { mutableIntStateOf(0) }
    var scene by remember { mutableStateOf(Scene.Dashboard) }

    // Scène 1
    var showCarousel by remember { mutableStateOf(true) }
    var urgentTotal by remember { mutableIntStateOf(0) }
    var notification by remember { mutableStateOf(false) }
    val bannerState = rememberLazyListState()

    // Scène 2
    var firstLessonDiscovered by remember { mutableIntStateOf(0) }
    var pressed by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }

    // Scène 3
    var dojo by remember { mutableStateOf(DojoUiState()) }

    LaunchedEffect(runId) {
        // ---- Remise à zéro (rejouer = toucher l'écran) ----
        scene = Scene.Dashboard; showCarousel = true; urgentTotal = 0; notification = false
        firstLessonDiscovered = 0; pressed = false; completed = false
        dojo = DojoUiState()
        bannerState.scrollToItem(0)

        // ---- 1. Carrousel "À la une" / "Défi" / "Nouveau parcours" ----
        delay(1_300)
        bannerState.animateScrollToItem(1); delay(2_200)
        bannerState.animateScrollToItem(2); delay(2_200)
        showCarousel = false; delay(1_100)            // le carrousel se replie, le dashboard apparaît
        notification = true; delay(1_500)             // notification locale "Révisions urgentes"
        urgentTotal = 3; delay(3_000)                 // le module Dojo passe en alerte (FSRS)
        notification = false; delay(1_500)

        // ---- 2. Mini-leçons du programme ----
        scene = Scene.Roadmap; delay(3_800)
        firstLessonDiscovered = 3; delay(2_800)       // 1re étape validée, la suivante devient "en cours"
        scene = Scene.Gate; delay(2_000)
        pressed = true; delay(180); pressed = false
        completed = true; delay(3_000)                // isCompleted = true : exercices débloqués

        // ---- 3. Session Dojo ----
        scene = Scene.Dojo
        val exercises = DemoData.exercises
        fun start(i: Int, correct: Int) {
            dojo = DojoUiState(
                title = "Programme 3e", total = exercises.size, index = i,
                exercise = exercises[i], correctCount = correct
            )
        }
        suspend fun typing(text: String) {
            for (n in 1..text.length) { dojo = dojo.copy(typed = text.take(n)); delay(42) }
        }

        // Exercice 1 : QCM
        start(0, 0); delay(1_400)
        dojo = dojo.copy(selected = setOf(0)); delay(1_300)
        dojo = dojo.copy(
            result = DojoResult(
                correct = true, partial = false, rating = ReviewRating.SU,
                message = DojoMessages.congrats(), nextReviewDays = 3
            )
        )
        delay(3_300)

        // Exercice 2 : saisie libre corrigée par E5 (simulation, aucune inférence réelle)
        start(1, 1); delay(1_000)
        typing("Quand l'action est passée mais qu'elle a encore un lien avec le présent"); delay(700)
        dojo = dojo.copy(evaluating = true); delay(1_800)
        dojo = dojo.copy(
            evaluating = false,
            result = DojoResult(
                correct = true, partial = false, rating = ReviewRating.SU,
                message = DojoMessages.congrats(), nextReviewDays = 6,
                feedback = "Tu as bien relié l'action passée à son effet sur le présent.",
                similarity = 0.87f
            )
        )
        delay(3_700)

        // Exercice 3 : saisie libre, mots-clés
        start(2, 2); delay(1_000)
        typing("Yamoussoukro"); delay(600)
        dojo = dojo.copy(
            result = DojoResult(
                correct = true, partial = false, rating = ReviewRating.SU,
                message = DojoMessages.congrats(), nextReviewDays = 4
            )
        )
        delay(3_200)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KalynoBackground)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { runId++ }
    ) {
        AnimatedContent(
            targetState = scene,
            transitionSpec = { fadeIn(tween(380)) togetherWith fadeOut(tween(260)) },
            label = "motionScene"
        ) { current ->
            when (current) {
                Scene.Dashboard -> DashboardScene(showCarousel, bannerState, urgentTotal)
                Scene.Roadmap -> RoadmapScene(firstLessonDiscovered)
                Scene.Gate -> GateScene(completed = completed, pressed = pressed)
                Scene.Dojo -> DemoDojoPane(dojo)
            }
        }

        if (scene == Scene.Dashboard) {
            UrgentNotification(
                visible = notification,
                urgent = 3,
                topic = DemoData.urgentTopics.first().label,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Scène 1 : carrousel horizontal (LazyRow) + dashboard + notification
// ---------------------------------------------------------------------------

@Composable
private fun DashboardScene(showCarousel: Boolean, bannerState: LazyListState, urgentTotal: Int) {
    val state = remember(urgentTotal) {
        DashboardUiState(
            loading = false,
            streak = 5,
            urgentTotal = urgentTotal,
            urgentTopics = if (urgentTotal > 0) DemoData.urgentTopics else emptyList(),
            masteredCount = 2,
            retentionScore = 0.82f,
            quoteReady = true,
            lastLesson = LastLessonUi("Mathématiques", "Théorème de Thalès", 0.66f)
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KalynoBackground)
            .statusBarsPadding()
    ) {
        AnimatedVisibility(
            visible = showCarousel,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically(tween(500)) + fadeOut(tween(300))
        ) {
            HeroBannerCarousel(
                banners = DemoData.banners,
                chapters = DemoData.lessons(0),
                onOpenLesson = {},
                modifier = Modifier.padding(top = 12.dp),
                listState = bannerState
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            AccueilScreen(
                state = state,
                onStartMission = {},
                onReviewUrgent = {},
                onRetry = {},
                onResumeLastLesson = {}
            )
        }
    }
}

/**
 * Simulation visuelle de la notification système : mêmes chaîne et même texte que
 * StudyReminderWorker (canal "Révisions urgentes"). La vraie notification est postée par
 * StudyReminders.post() ; ici on la dessine dans l'app pour pouvoir l'animer.
 */
@Composable
private fun UrgentNotification(visible: Boolean, urgent: Int, topic: String?, modifier: Modifier = Modifier) {
    val body = buildString {
        append(if (urgent == 1) "1 notion à sauver" else "$urgent notions à sauver")
        append(" dans le Dojo")
        if (topic != null) append(" · $topic")
        append(". Quelques minutes suffisent pour la garder en tête !")
    }
    val shape = RoundedCornerShape(22.dp)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(tween(450)) { -it } + fadeIn(tween(300)),
        exit = slideOutVertically(tween(350)) { -it } + fadeOut(tween(250))
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .shadow(8.dp, shape, ambientColor = KalynoShadow, spotColor = KalynoShadow)
                .background(Color.White, shape)
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(KalynoBlue),
                contentAlignment = Alignment.Center
            ) {
                KalynoSvgIcon(name = "brain", size = 20.dp, tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("KALYNO · maintenant", color = KalynoMuted, fontSize = 11.sp)
                Text(
                    "Révisions urgentes",
                    color = KalynoText, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(2.dp))
                Text(body, color = KalynoText, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Scène 2 : roadmap de 4 mini-leçons + verrou isCompleted
// ---------------------------------------------------------------------------

@Composable
private fun RoadmapScene(firstLessonDiscovered: Int) {
    val lessons = remember(firstLessonDiscovered) { DemoData.lessons(firstLessonDiscovered) }
    DisciplineLessonsScreen(
        discipline = DemoData.discipline,
        themes = DemoData.themes,
        lessons = lessons,
        onBack = {},
        onOpenLesson = {}
    )
}

/**
 * Même logique que LessonScreen : tant que isCompleted est faux, seul le bouton « TERMINER » existe ;
 * une fois la leçon validée, la barre devient verte et le CTA des questions apparaît.
 */
@Composable
private fun GateScene(completed: Boolean, pressed: Boolean) {
    val lesson = DemoData.lessons(0)[1]
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, tween(120), label = "finishPress")
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KalynoBackground)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 56.dp)
    ) {
        KalynoPill(text = lesson.subjectName.uppercase())
        Spacer(Modifier.height(12.dp))
        Text(
            lesson.title,
            color = KalynoText, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(6.dp))
        Text("${lesson.minutes} min · ${lesson.questionCount} questions", color = KalynoMuted, fontSize = 14.sp)
        Spacer(Modifier.height(28.dp))

        Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
            AnimatedContent(
                targetState = completed,
                transitionSpec = { (fadeIn(tween(350)) + scaleIn(tween(350), initialScale = 0.92f)) togetherWith fadeOut(tween(200)) },
                label = "finishBar"
            ) { done ->
                LessonFinishBar(isCompleted = done, onComplete = {})
            }
        }
        Spacer(Modifier.height(16.dp))
        AnimatedVisibility(
            visible = completed,
            enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 2 } + expandVertically()
        ) {
            KalynoPrimaryButton(label = "DÉMARRER LES ${lesson.questionCount} QUESTIONS", onClick = {})
        }
    }
}

// ---------------------------------------------------------------------------
// Scène 3 : session Dojo (mêmes briques que ExercisePane, sans ViewModel)
// ---------------------------------------------------------------------------

@Composable
private fun DemoDojoPane(state: DojoUiState) {
    val exercise = state.exercise ?: return
    val item = exercise.item
    val result = state.result
    val isLast = state.index + 1 >= state.total

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KalynoBackground)
            .safeDrawingPadding()
    ) {
        TopBar(
            done = state.index + if (result != null) 1 else 0,
            index = state.index,
            total = state.total,
            dayStreak = 5,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            calculatorSuggested = false,
            onReport = {},
            onClose = {},
            onCalculator = {}
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            key(state.index) {
                val enter = remember { Animatable(0f) }
                LaunchedEffect(Unit) { enter.animateTo(1f, tween(320)) }

                Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = enter.value
                                translationX = (1f - enter.value) * 48.dp.toPx()
                            }
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                            .padding(top = 10.dp, bottom = 12.dp)
                    ) {
                        KalynoQuestionCard(
                            question = item.front,
                            lessonLabel = "${item.subjectName} · ${item.chapterTitle}",
                            typeLabel = when {
                                exercise.kind == ExerciseKind.SINGLE_CHOICE -> "Une bonne réponse"
                                else -> "Saisie libre"
                            }
                        ) {
                            DojoAnswerArea(
                                state = state,
                                exercise = exercise,
                                onToggleChoice = {},
                                onTyped = {},
                                onSubmit = {},
                                onHaptic = {}
                            )
                            // Traitement local du moteur sémantique (multilingual-e5-small)
                            AnimatedVisibility(visible = state.evaluating) {
                                Row(
                                    modifier = Modifier.padding(top = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = KalynoBlue
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    KalynoPill(text = "E5 · ANALYSE LOCALE", fontSize = 11)
                                }
                            }
                        }
                    }
                    QuizStickyFooter(
                        validated = result != null,
                        actionLabel = when {
                            state.evaluating -> "ANALYSE EN COURS..."
                            result == null -> "VALIDER MA RÉPONSE"
                            isLast -> "VOIR MON BILAN"
                            else -> "CONTINUER"
                        },
                        actionEnabled = result != null || state.canSubmit,
                        onAction = {},
                        feedback = if (result != null) {
                            { DojoFeedback(exercise = exercise, result = result) }
                        } else null
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// FIXTURES de démonstration (programme ivoirien, 3e BEPC)
// ---------------------------------------------------------------------------

private object DemoData {

    val urgentTopics = listOf(UrgentTopic("Mathématiques", 2), UrgentTopic("Anglais", 1))

    val discipline = Discipline(id = "demo-3e", name = "Programme 3e", iconKey = "book-open", lessonCount = 4)

    val themes = listOf(
        ExplorerTheme("t-math", "demo-3e", "Mathématiques", 2),
        ExplorerTheme("t-eng", "demo-3e", "Anglais", 1),
        ExplorerTheme("t-hg", "demo-3e", "Histoire-Géo", 1)
    )

    /** Les 3 annonces du carrousel : le texte vient de l'admin (table explorer_banners) dans l'app réelle. */
    val banners = listOf(
        ExplorerBanner("b1", "Ta mission du jour t'attend", "3 minutes pour garder ta série allumée", "Découvrir", "math-pythagore"),
        ExplorerBanner("b2", "Défi : teste-toi en 3 questions", "Maths, Anglais, Histoire-Géo", "Relever le défi", "eng-perfect"),
        ExplorerBanner("b3", "Nouveau parcours : Histoire-Géo", "Les indépendances en Afrique de l'Ouest", "Commencer", "hg-indep")
    )

    /** Quatre mini-leçons (maximum demandé). [firstDiscovered] = cartes découvertes de la 1re leçon. */
    fun lessons(firstDiscovered: Int): List<ExplorerChapter> = listOf(
        chapter("math-thales", "t-math", "maths", "Mathématiques", "Théorème de Thalès", 12, firstDiscovered),
        chapter("math-pythagore", "t-math", "maths", "Mathématiques", "Théorème de Pythagore", 10, 0),
        chapter("eng-perfect", "t-eng", "anglais", "Anglais", "Le present perfect", 8, 0),
        chapter("hg-indep", "t-hg", "histoire-geo", "Histoire-Géo", "Les indépendances en Afrique de l'Ouest", 14, 0)
    )

    private fun chapter(
        id: String, themeId: String, subjectId: String, subject: String,
        title: String, minutes: Int, discovered: Int
    ) = ExplorerChapter(
        id = id,
        subjectId = subjectId,
        subjectName = subject,
        subjectEmoji = "",
        title = title,
        minutes = minutes,
        discovered = discovered,
        capsules = emptyList(),
        items = List(3) { n ->
            ReviewItem(
                cardId = "$id-$n", subjectName = subject, chapterTitle = title, front = "",
                choices = emptyList(), correctIndex = null, answer = "", explanation = null
            )
        },
        themeId = themeId
    )

    val exercises: List<DojoExercise> = listOf(
        // 1. QCM (Maths)
        DojoExercise(
            item = ReviewItem(
                cardId = "demo-1", subjectName = "Mathématiques", chapterTitle = "Théorème de Pythagore",
                front = "Dans un triangle ABC rectangle en A, quelle égalité est vraie ?",
                choices = listOf("BC² = AB² + AC²", "AB² = BC² + AC²", "BC = AB + AC", "AC² = AB² + BC²"),
                correctIndex = 0,
                answer = "BC² = AB² + AC²",
                explanation = "L'hypoténuse est le côté opposé à l'angle droit : ici BC.\nSon carré est égal à la somme des carrés des deux autres côtés."
            ),
            kind = ExerciseKind.SINGLE_CHOICE,
            correctIndices = setOf(0),
            acceptedAnswers = emptyList(),
            calculatorSuggested = false
        ),
        // 2. Saisie libre corrigée par le moteur sémantique (Anglais)
        DojoExercise(
            item = ReviewItem(
                cardId = "demo-2", subjectName = "Anglais", chapterTitle = "Le present perfect",
                front = "Explique avec tes mots : quand utilise-t-on le present perfect ?",
                choices = emptyList(), correctIndex = null,
                answer = "Pour une action passée qui a un lien avec le présent.",
                explanation = "Le present perfect relie le passé au présent : le résultat compte plus que la date."
            ),
            kind = ExerciseKind.FREE_TEXT,
            correctIndices = emptySet(),
            acceptedAnswers = listOf("Pour une action passée qui a un lien avec le présent."),
            calculatorSuggested = false,
            stage = 3,
            useSemantic = true
        ),
        // 3. Saisie libre, mots-clés (Histoire-Géo)
        DojoExercise(
            item = ReviewItem(
                cardId = "demo-3", subjectName = "Histoire-Géo", chapterTitle = "Repères de Côte d'Ivoire",
                front = "Quelle est la capitale politique de la Côte d'Ivoire ?",
                choices = emptyList(), correctIndex = null,
                answer = "Yamoussoukro",
                explanation = "Yamoussoukro est la capitale politique ; Abidjan est la capitale économique."
            ),
            kind = ExerciseKind.FREE_TEXT,
            correctIndices = emptySet(),
            acceptedAnswers = listOf("Yamoussoukro"),
            calculatorSuggested = false,
            stage = 2
        )
    )
}
