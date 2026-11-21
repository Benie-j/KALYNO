package ci.kalyno.app.ui.challenge

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ci.kalyno.app.data.ai.AiExerciseContext
import ci.kalyno.app.data.ai.KalynoRepository
import ci.kalyno.app.data.ai.GradeResult
import ci.kalyno.app.ui.ai.FreeTextGrader
import ci.kalyno.app.ui.ai.GradingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * État de correction IA d'une question du Challenge (une instance par question).
 * Il vit dans un ViewModel pour survivre aux rotations d'écran : la requête en cours n'est pas perdue.
 * Même [FreeTextGrader] que le Dojo : la logique de correction n'est pas dupliquée.
 */
class ChallengeGradingViewModel(app: Application) : AndroidViewModel(app) {

    private val grader = FreeTextGrader(KalynoRepository.get(app), viewModelScope)

    val grading: StateFlow<GradingState> = grader.state
    val available: Boolean get() = grader.available

    private val _graded = MutableStateFlow<GradeResult?>(null)

    /** Correction validée de la question ; null tant qu'aucun verdict réel n'est revenu. */
    val graded: StateFlow<GradeResult?> = _graded.asStateFlow()

    fun grade(context: AiExerciseContext, answer: String) {
        if (_graded.value != null) return // question déjà corrigée : verrouillée
        grader.grade(context, answer) { result ->
            if (_graded.value == null) _graded.value = result
        }
    }
}
