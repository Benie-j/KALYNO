package ci.kalyno.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import ci.kalyno.app.data.NoContentRepository
import ci.kalyno.app.ui.challenge.ChallengeScreen
import ci.kalyno.app.ui.home.HomeScreen

@Composable
fun KalynoApp() {
    var levelId by rememberSaveable { mutableStateOf<String?>(null) }
    val repository = remember { NoContentRepository() }
    val current = levelId

    if (current == null) {
        HomeScreen(onLevelSelected = { levelId = it })
    } else {
        BackHandler { levelId = null }
        ChallengeScreen(
            levelId = current,
            repository = repository,
            onExit = { levelId = null }
        )
    }
}
