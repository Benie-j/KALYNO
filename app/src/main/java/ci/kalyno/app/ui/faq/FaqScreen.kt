package ci.kalyno.app.ui.faq

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ci.kalyno.app.data.auth.findActivity
import ci.kalyno.app.data.faq.FaqItem
import ci.kalyno.app.data.faq.FaqSearch
import ci.kalyno.app.ui.components.KalynoCard
import ci.kalyno.app.ui.components.KalynoErrorCard
import ci.kalyno.app.ui.components.KalynoIconEmptyState
import ci.kalyno.app.ui.components.KalynoPrimaryButton
import ci.kalyno.app.ui.components.ShimmerBlock
import ci.kalyno.app.ui.components.rememberHaptic
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import ci.kalyno.app.ui.theme.KalynoAmberSoft
import ci.kalyno.app.ui.theme.KalynoBackground
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoOutline
import ci.kalyno.app.ui.theme.KalynoText

/**
 * Centre d'aide & FAQ. Tout le contenu vient de Room (donc de l'admin, via Supabase) : rien n'est codé en dur.
 *
 * Écran sans navigation propre : le retour, le contact et le signalement sortent par callbacks.
 *
 * @param onContactSupport ouvre le contact avec l'équipe (même action que dans le Profil).
 * @param onReportIssue ouvre la feuille de signalement existante.
 */
@Composable
fun FaqScreen(
    onBack: () -> Unit,
    onContactSupport: () -> Unit,
    onReportIssue: () -> Unit,
    viewModel: FaqViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()
    val activity = LocalContext.current.findActivity()

    // Fermeture réelle de l'écran (pas une rotation) : la prochaine ouverture repart propre
    DisposableEffect(viewModel) {
        onDispose { if (activity?.isChangingConfigurations != true) viewModel.resetUi() }
    }
    // Le clavier se range dès que l'élève fait défiler les résultats
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) focus.clearFocus()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(KalynoBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        TopBar(onBack)
        SearchField(
            query = state.query,
            onChange = viewModel::onQueryChange,
            onClear = { viewModel.onQueryChange("") },
            onSearch = { focus.clearFocus() }
        )
        // Mise à jour discrète en arrière-plan : le contenu local reste affiché pendant ce temps
        Box(Modifier.fillMaxWidth().height(2.dp).padding(horizontal = 16.dp)) {
            if (state.syncing && state.totalCount > 0) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    color = KalynoBlue,
                    trackColor = KalynoBlueSoft
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when {
                !state.loaded -> items(SKELETON_COUNT) { SkeletonCard() }

                state.neverSynced -> item(key = "empty") {
                    when {
                        state.syncing -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            repeat(SKELETON_COUNT) { SkeletonCard() }
                        }
                        state.syncFailed -> KalynoErrorCard(
                            message = "Le centre d'aide n'a pas pu être chargé. Vérifie ta connexion : une fois récupéré, il restera disponible hors ligne.",
                            onRetry = viewModel::refresh
                        )
                        else -> KalynoIconEmptyState(
                            icon = "lightbulb",
                            title = "Rien ici pour l'instant",
                            text = "Les questions fréquentes arrivent bientôt."
                        )
                    }
                }

                state.searching && state.resultCount == 0 -> item(key = "no-result") {
                    KalynoIconEmptyState(
                        icon = "search",
                        title = "Aucun résultat",
                        text = "Essaie avec d'autres mots, ou écris-nous juste en dessous."
                    )
                }

                else -> {
                    if (state.syncFailed) item(key = "offline") { OfflineNote(onRetry = viewModel::refresh) }
                    if (state.searching) item(key = "count") {
                        Text(
                            text = "${state.resultCount} résultat${if (state.resultCount > 1) "s" else ""}",
                            color = KalynoMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                    state.sections.forEach { section ->
                        item(key = "cat-${section.category}") { CategoryHeader(section.category) }
                        items(section.items, key = { it.id }) { faq ->
                            FaqCard(
                                item = faq,
                                expanded = faq.id in state.expanded,
                                terms = state.terms,
                                onToggle = { viewModel.toggle(faq.id) }
                            )
                        }
                    }
                }
            }

            // Passerelle de contact : toujours en bas, surtout utile quand la recherche ne donne rien
            item(key = "contact") {
                Spacer(Modifier.height(6.dp))
                ContactGateway(onContactSupport = onContactSupport, onReportIssue = onReportIssue)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// En-tête et recherche
// ---------------------------------------------------------------------------

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            KalynoSvgIcon(name = "arrow-left", size = 22.dp, tint = KalynoText, contentDescription = "Retour")
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = "Centre d'aide & FAQ",
            color = KalynoText,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.semantics { heading() }
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    onChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        singleLine = true,
        placeholder = { Text("Rechercher une question…", color = KalynoMuted) },
        leadingIcon = { KalynoSvgIcon(name = "search", size = 20.dp, tint = KalynoMuted) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    KalynoSvgIcon(name = "x", size = 20.dp, tint = KalynoMuted, contentDescription = "Effacer la recherche")
                }
            }
        },
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedBorderColor = KalynoBlue,
            unfocusedBorderColor = KalynoOutline,
            cursorColor = KalynoBlue,
            focusedTextColor = KalynoText,
            unfocusedTextColor = KalynoText
        )
    )
}

// ---------------------------------------------------------------------------
// Catégories et accordéons
// ---------------------------------------------------------------------------

@Composable
private fun CategoryHeader(category: String) {
    Text(
        text = category.uppercase(),
        color = KalynoMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp,
        modifier = Modifier
            .padding(start = 4.dp, top = 8.dp)
            .semantics { heading() }
    )
}

@Composable
private fun FaqCard(item: FaqItem, expanded: Boolean, terms: List<String>, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "faqChevron"
    )
    val question = remember(item.question, terms) { highlighted(item.question, terms) }
    val answer = remember(item.answer, terms) { highlighted(item.answer, terms) }
    // Mots trouvés seulement dans la réponse : un extrait sous la question explique pourquoi la carte apparaît
    val preview = remember(item.answer, terms) { snippet(item.answer, terms) }

    KalynoCard(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (expanded) "Replier la réponse" else "Voir la réponse",
                    onClick = onToggle
                )
                .semantics { stateDescription = if (expanded) "Déplié" else "Replié" }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = question,
                    color = KalynoText,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                if (!expanded && preview != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = preview,
                        color = KalynoMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(24.dp).rotate(rotation), contentAlignment = Alignment.Center) {
                KalynoSvgIcon(name = "chevron-down", size = 22.dp, tint = if (expanded) KalynoBlue else KalynoMuted)
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(250)) + fadeIn(tween(250)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
        ) {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(KalynoOutline))
                Text(
                    text = answer,
                    color = KalynoText,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 18.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// États annexes
// ---------------------------------------------------------------------------

@Composable
private fun SkeletonCard() {
    ShimmerBlock(
        modifier = Modifier.fillMaxWidth().height(58.dp),
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun OfflineNote(onRetry: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KalynoSvgIcon(name = "wifi-off", size = 16.dp, tint = KalynoMuted)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Hors ligne : contenu enregistré sur ton téléphone.",
            color = KalynoMuted,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "Réessayer",
            color = KalynoBlue,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = onRetry)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

/** Passerelle vers l'équipe : contact direct ou signalement (feuille existante de l'app). */
@Composable
private fun ContactGateway(onContactSupport: () -> Unit, onReportIssue: () -> Unit) {
    val haptic = rememberHaptic()
    KalynoCard(modifier = Modifier.fillMaxWidth(), contentPadding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(KalynoBlueSoft),
                contentAlignment = Alignment.Center
            ) {
                KalynoSvgIcon(name = "message-circle", size = 20.dp, tint = KalynoBlue)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Tu n'as pas trouvé ta réponse ?",
                    color = KalynoText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "L'équipe KALYNO est là pour t'aider.",
                    color = KalynoMuted,
                    fontSize = 13.sp
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        KalynoPrimaryButton(label = "CONTACTER L'ASSISTANCE", onClick = onContactSupport, height = 52.dp)
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = {
                haptic()
                onReportIssue()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, KalynoOutline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = KalynoBlue)
        ) {
            Text(
                text = "SIGNALER UN BUG OU UN CONTENU",
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Surlignage des mots cherchés
// ---------------------------------------------------------------------------

private val MATCH_STYLE = SpanStyle(background = KalynoAmberSoft, fontWeight = FontWeight.ExtraBold)

private fun highlighted(text: String, terms: List<String>): AnnotatedString {
    val ranges = FaqSearch.highlightRanges(text, terms)
    if (ranges.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        ranges.forEach { addStyle(MATCH_STYLE, it.first, it.last + 1) }
    }
}

/** Court extrait de la réponse autour du premier mot trouvé ; null si la réponse ne contient aucun mot cherché. */
private fun snippet(answer: String, terms: List<String>): AnnotatedString? {
    val flat = answer.replace('\n', ' ') // même longueur : les plages restent valables
    val ranges = FaqSearch.highlightRanges(flat, terms)
    if (ranges.isEmpty()) return null
    val start = (ranges.first().first - SNIPPET_LEAD).coerceAtLeast(0)
    val end = (start + SNIPPET_LENGTH).coerceAtMost(flat.length)
    val lead = if (start > 0) "…" else ""
    val tail = if (end < flat.length) "…" else ""
    return buildAnnotatedString {
        append(lead + flat.substring(start, end) + tail)
        for (r in ranges) {
            val from = maxOf(r.first, start)
            val to = minOf(r.last + 1, end)
            if (from < to) addStyle(MATCH_STYLE, from - start + lead.length, to - start + lead.length)
        }
    }
}

private const val SKELETON_COUNT = 5
private const val SNIPPET_LEAD = 30
private const val SNIPPET_LENGTH = 110
