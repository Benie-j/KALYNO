package ci.kalyno.app.ui.progress

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ci.kalyno.app.ui.components.KalynoCard
import ci.kalyno.app.ui.components.KalynoProgressBar
import ci.kalyno.app.ui.components.MathText
import ci.kalyno.app.ui.components.ShimmerBlock
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import ci.kalyno.app.ui.theme.KalynoBackground
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoCorrect
import ci.kalyno.app.ui.theme.KalynoCorrectSoft
import ci.kalyno.app.ui.theme.KalynoCorrectText
import ci.kalyno.app.ui.theme.KalynoDanger
import ci.kalyno.app.ui.theme.KalynoDangerSoft
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoOutline
import ci.kalyno.app.ui.theme.KalynoRetryText
import ci.kalyno.app.ui.theme.KalynoText

/**
 * « Ma progression » : résumé global puis chronologie de toutes les tentatives du Dojo.
 * Tout vient de Room (hors-ligne) ; [retentionScore] est la rétention FSRS déjà calculée par le tableau de bord,
 * pour que le chiffre soit identique à celui de la carte.
 */
@Composable
fun ProgressHistoryScreen(
    retentionScore: Float?,
    onBack: () -> Unit,
    viewModel: ProgressHistoryViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Chargement progressif : on demande la page suivante quand la fin de la liste approche
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, state.canLoadMore, state.rows.size) {
        if (nearEnd && state.canLoadMore) viewModel.loadMore()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(KalynoBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        TopBar(onBack)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "summary") { SummaryCard(state.summary, retentionScore, state.loading) }

            if (state.neverPlayed) {
                item(key = "empty") { EmptyState() }
            } else {
                item(key = "filters") {
                    FilterBar(state, viewModel::selectStatus, viewModel::selectSubject)
                }
                if (!state.loading && state.rows.isEmpty()) {
                    item(key = "no-result") {
                        Text(
                            text = "Aucune tentative pour ce filtre.",
                            color = KalynoMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                        )
                    }
                }
                items(state.rows, key = { it.key }, contentType = { it::class }) { row ->
                    when (row) {
                        is HistoryRow.Day -> DayHeader(row.label)
                        is HistoryRow.Attempt -> AttemptCard(row.item)
                    }
                }
                if (state.canLoadMore) {
                    item(key = "more") {
                        ShimmerBlock(Modifier.fillMaxWidth().height(64.dp), RoundedCornerShape(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            KalynoSvgIcon(name = "arrow-left", size = 22.dp, tint = KalynoText, contentDescription = "Retour")
        }
        Spacer(Modifier.width(4.dp))
        Text("Ma progression", color = KalynoText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun SummaryCard(summary: ProgressSummaryUi?, retentionScore: Float?, loading: Boolean) {
    val retention = retentionScore?.coerceIn(0f, 1f)
    KalynoCard(contentPadding = 20.dp) {
        Text("Taux de rétention", color = KalynoMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(
            text = retention?.let { "${(it * 100).toInt()} %" } ?: "—",
            color = KalynoText,
            fontSize = 40.sp,
            fontWeight = FontWeight.ExtraBold
        )
        if (retention != null) {
            Spacer(Modifier.height(8.dp))
            KalynoProgressBar(progress = retention, height = 8.dp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Ce que tu retiens encore, estimé par ton moteur de révision.",
            color = KalynoMuted,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )

        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(KalynoOutline))
        Spacer(Modifier.height(16.dp))

        if (summary == null && loading) {
            ShimmerBlock(Modifier.fillMaxWidth().height(44.dp))
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(summary?.learnedCards ?: 0, "Fiches apprises", KalynoText)
                StatTile(summary?.successes ?: 0, "Succès", KalynoCorrect)
                StatTile(summary?.failures ?: 0, "Échecs", KalynoDanger)
            }
            val rate = summary?.successRate
            if (summary != null && rate != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "${(rate * 100).toInt()} % de réussite sur ${summary.total} " +
                        if (summary.total == 1) "tentative" else "tentatives",
                    color = KalynoMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun RowScope.StatTile(value: Int, label: String, color: Color) {
    Column(Modifier.weight(1f)) {
        Text(value.toString(), color = color, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = KalynoMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FilterBar(
    state: ProgressHistoryUiState,
    onStatus: (HistoryStatus) -> Unit,
    onSubject: (String?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(HistoryStatus.entries.toList()) { status ->
                val label = when (status) {
                    HistoryStatus.ALL -> "Tout"
                    HistoryStatus.SUCCESS -> "Succès"
                    HistoryStatus.FAILURE -> "Échecs"
                }
                HistoryChip(label, selected = state.filter.status == status) { onStatus(status) }
            }
        }
        if (state.subjects.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.subjects) { subject ->
                    HistoryChip(subject, selected = state.filter.subject == subject) { onSubject(subject) }
                }
            }
        }
    }
}

@Composable
private fun HistoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) KalynoBlue else Color.White)
            .border(1.dp, if (selected) KalynoBlue else KalynoOutline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else KalynoText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun DayHeader(label: String) {
    Text(
        text = label,
        color = KalynoMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp)
    )
}

@Composable
private fun AttemptCard(item: AttemptUi) {
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    KalynoCard(
        modifier = Modifier.animateContentSize(),
        onClick = { expanded = !expanded },
        contentPadding = 16.dp
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.lessonTitle,
                    color = KalynoText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusBadge(item.success)
                    if (item.subjectName.isNotBlank()) {
                        Text(
                            text = item.subjectName,
                            color = KalynoBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clip(RoundedCornerShape(50))
                                .background(KalynoBlueSoft)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(item.time, color = KalynoMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }

        item.attemptLine?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                text = it,
                color = if (item.success) KalynoCorrectText else KalynoRetryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(12.dp))
        FieldLabel("Question")
        Spacer(Modifier.height(4.dp))
        if (expanded) {
            MathText(text = item.question, color = KalynoText, fontSize = 14.sp, lineHeight = 20.sp)
        } else {
            // Aperçu en texte simple : les formules ($…$) ne sont rendues qu'à l'ouverture de la carte
            Text(
                text = item.question.replace("$", ""),
                color = KalynoText,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (expanded) {
            Spacer(Modifier.height(12.dp))
            AnswerBlock(
                label = "Ta réponse",
                text = item.studentAnswer ?: "Non conservée : cette tentative date d'avant la mise à jour de l'historique.",
                background = if (item.success) KalynoCorrectSoft else KalynoDangerSoft,
                textColor = if (item.success) KalynoCorrectText else KalynoDanger
            )
            Spacer(Modifier.height(8.dp))
            AnswerBlock(
                label = "Réponse attendue",
                text = item.referenceAnswer,
                background = KalynoBlueSoft,
                textColor = KalynoText
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (expanded) "Masquer le détail" else "Voir le détail",
                color = KalynoBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.width(4.dp))
            Box(Modifier.rotate(rotation)) { KalynoSvgIcon(name = "chevron-down", size = 14.dp, tint = KalynoBlue) }
        }
    }
}

@Composable
private fun StatusBadge(success: Boolean) {
    Text(
        text = if (success) "Succès" else "Échec",
        color = if (success) KalynoCorrectText else KalynoDanger,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (success) KalynoCorrectSoft else KalynoDangerSoft)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = KalynoMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun AnswerBlock(label: String, text: String, background: Color, textColor: Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .padding(12.dp)
    ) {
        Text(label, color = KalynoMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        MathText(text = text, color = textColor, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun EmptyState() {
    KalynoCard(contentPadding = 24.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            KalynoSvgIcon(name = "history", size = 32.dp, tint = KalynoBlue)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Ton historique est encore vide",
                color = KalynoText,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Dès ta première séance au Dojo, chaque réponse s'ajoute ici : la leçon, la question, ta réponse et la réponse attendue.",
                color = KalynoMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
