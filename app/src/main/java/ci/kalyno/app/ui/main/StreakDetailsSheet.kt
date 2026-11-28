package ci.kalyno.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.data.StreakLogic
import ci.kalyno.app.data.badges.StreakGoals
import ci.kalyno.app.ui.components.KalynoPrimaryButton
import ci.kalyno.app.ui.components.KalynoProgressBar
import ci.kalyno.app.ui.components.rememberCountUp
import ci.kalyno.app.ui.components.rememberPulse
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import ci.kalyno.app.ui.theme.KalynoAmberDeep
import ci.kalyno.app.ui.theme.KalynoAmberSoft
import ci.kalyno.app.ui.theme.KalynoBackground
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoOutline
import ci.kalyno.app.ui.theme.KalynoText
import ci.kalyno.app.ui.theme.KalynoYellow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Un jour de la semaine glissante : actif = couvert par la série en cours. */
internal data class WeekDay(val label: String, val active: Boolean, val today: Boolean)

/**
 * Les 7 derniers jours (aujourd'hui à droite), déduits de la série : une série de N jours couvre
 * les N derniers jours jusqu'à aujourd'hui si la session du jour est faite, jusqu'à hier sinon.
 * Même calendrier que [StreakLogic] (numéro de jour UTC), donc jamais de décalage avec la série.
 */
internal fun weekOf(streak: Int, doneToday: Boolean): List<WeekDay> {
    val today = LocalDate.ofEpochDay(StreakLogic.dayOf(System.currentTimeMillis()))
    val lastActive = if (doneToday) today else today.minusDays(1)
    return (6 downTo 0).map { back ->
        val day = today.minusDays(back.toLong())
        val fromLast = ChronoUnit.DAYS.between(day, lastActive)
        WeekDay(
            label = day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.FRENCH).uppercase(Locale.FRENCH),
            active = streak > 0 && fromLast >= 0 && fromLast < streak,
            today = back == 0
        )
    }
}

@Composable
private fun WeekStrip(days: List<WeekDay>, modifier: Modifier = Modifier) {
    // Le cercle d'aujourd'hui palpite tant que la session du jour n'est pas faite
    val pulse = rememberPulse(min = 1f, max = 1.12f, durationMillis = 900)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        days.forEach { day ->
            val waiting = day.today && !day.active
            val dotScale = if (waiting) pulse else 1f
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = day.label,
                    color = if (day.today) KalynoText else KalynoMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = dotScale
                            scaleY = dotScale
                        }
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (day.active) KalynoYellow else KalynoOutline.copy(alpha = 0.6f))
                        .then(if (waiting) Modifier.border(2.dp, KalynoYellow, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    if (day.active) {
                        KalynoSvgIcon(name = "flame", size = 18.dp, tint = Color.White)
                    } else if (waiting) {
                        KalynoSvgIcon(name = "flame", size = 16.dp, tint = KalynoYellow)
                    }
                }
            }
        }
    }
}

/**
 * Détail de la série, ouvert depuis la flamme de l'Accueil : le nombre de jours, la semaine en un
 * coup d'œil et le prochain badge (même source que le Profil : [StreakGoals]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StreakDetailsSheet(
    streak: Int,
    doneToday: Boolean,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val week = remember(streak, doneToday) { weekOf(streak, doneToday) }
    val goal = StreakGoals.next(streak)
    val shown = rememberCountUp(streak, durationMillis = 900)
    val flame = rememberPulse(min = 1f, max = 1.14f, durationMillis = 800)

    val statusLine = when {
        streak <= 0 -> "Termine une session aujourd'hui pour lancer ta série."
        doneToday -> "Série de $streak validée aujourd'hui."
        else -> "Une session aujourd'hui et ta série passe à ${streak + 1}."
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(KalynoAmberSoft),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.graphicsLayer {
                        val s = if (streak > 0) flame else 1f
                        scaleX = s
                        scaleY = s
                    }
                ) {
                    KalynoSvgIcon(name = "flame", size = 46.dp, tint = KalynoYellow)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = shown.toString(),
                color = KalynoAmberDeep,
                fontSize = 56.sp,
                lineHeight = 60.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = if (streak <= 1) "jour d'affilée" else "jours d'affilée",
                color = KalynoMuted,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = statusLine,
                color = KalynoText,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(KalynoBackground)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "CETTE SEMAINE",
                    color = KalynoMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(12.dp))
                WeekStrip(days = week)
            }

            Spacer(Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(KalynoAmberSoft)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        KalynoSvgIcon(name = "trophy", size = 22.dp, tint = KalynoAmberDeep)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (goal != null) "PROCHAIN BADGE" else "TOUS LES BADGES DE SÉRIE",
                            color = KalynoAmberDeep,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = goal?.title ?: "Série de légende",
                            color = KalynoText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                KalynoProgressBar(
                    progress = StreakGoals.progress(streak),
                    height = 8.dp,
                    color = KalynoYellow,
                    track = Color.White
                )
                Spacer(Modifier.height(8.dp))
                val left = StreakGoals.daysLeft(streak)
                Text(
                    text = if (goal != null) {
                        "Encore $left ${if (left == 1) "jour" else "jours"} pour le débloquer."
                    } else {
                        "Tu les as tous débloqués. Chaque jour de plus est une victoire."
                    },
                    color = KalynoMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }

            Spacer(Modifier.height(20.dp))
            KalynoPrimaryButton(
                label = "Compris",
                onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) onDismiss()
                    }
                }
            )
        }
    }
}
