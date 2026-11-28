package ci.kalyno.app.ui.explorer

import androidx.compose.runtime.mutableStateOf

/**
 * Demande d'ouverture d'une leçon, posée par un autre onglet (l'Accueil, « Reprendre l'aventure »)
 * puis consommée par l'Explorer dès que ses leçons sont chargées. Même principe que
 * `StudyReminders.openDojoRequested` : un état partagé, remis à null une fois traité.
 */
object ExplorerDeepLink {
    val openLessonId = mutableStateOf<String?>(null)
}
