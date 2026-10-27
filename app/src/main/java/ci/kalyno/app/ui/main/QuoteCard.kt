package ci.kalyno.app.ui.main

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.data.dashboard.DailyQuote
import ci.kalyno.app.data.dashboard.DashboardUiState
import ci.kalyno.app.data.dashboard.FavoriteQuote
import ci.kalyno.app.data.dashboard.QuoteDay
import ci.kalyno.app.data.dashboard.favoriteKey
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import ci.kalyno.app.ui.theme.KalynoAmberDeep
import ci.kalyno.app.ui.theme.KalynoAmberSoft
import ci.kalyno.app.ui.theme.KalynoBackground
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoOutline
import ci.kalyno.app.ui.theme.KalynoShadow
import ci.kalyno.app.ui.theme.KalynoText
import ci.kalyno.app.ui.theme.KalynoYellow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val NeutralChip = Color(0xFFF1F5F9)
private val QuoteSerif = FontFamily.Serif

/**
 * Bloc 1 : "Citation du jour" interactive.
 *
 * - toucher la carte        : une autre citation (shuffle animé + micro-vibration)
 * - appui long / bouton     : bottom sheet (7 derniers jours + favoris)
 * - cœur                    : épingle la citation (Room, lisible hors connexion)
 * - partage                 : WhatsApp pré-rempli avec la signature KALYNO
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun QuoteModule(
    state: DashboardUiState,
    onShuffle: () -> Unit,
    onToggleFavorite: (DailyQuote) -> Unit,
    modifier: Modifier = Modifier
) {
    val quote = state.shuffledQuote ?: state.dailyQuote
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var sheetOpen by rememberSaveable { mutableStateOf(false) }

    val shape = RoundedCornerShape(24.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && quote != null) 0.965f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "quotePress"
    )

    val spin = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    val favoriteKeys = remember(state.favoriteQuotes) { state.favoriteQuotes.map { it.key }.toSet() }
    val isFavorite = quote != null && quote.favoriteKey() in favoriteKeys

    // Petit "pop" élastique quand le cœur s'allume
    LaunchedEffect(isFavorite) {
        if (isFavorite) {
            pop.snapTo(0.55f)
            pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 420f))
        } else {
            pop.snapTo(1f)
        }
    }

    val shuffle: () -> Unit = {
        if (quote != null) {
            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            onShuffle()
            scope.launch {
                spin.snapTo(0f)
                spin.animateTo(360f, tween(480, easing = FastOutSlowInEasing))
            }
        }
    }
    val toggleFavorite: () -> Unit = {
        if (quote != null) {
            haptic.performHapticFeedback(
                if (isFavorite) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn
            )
            onToggleFavorite(quote)
        }
    }
    val share: () -> Unit = {
        if (quote != null) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            shareOnWhatsApp(context, quote)
        }
    }
    val openHistory: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        sheetOpen = true
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .shadow(4.dp, shape, ambientColor = KalynoShadow, spotColor = KalynoShadow)
            .background(Color.White, shape)
            .border(1.dp, KalynoOutline, shape)
            .clip(shape)
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = quote != null,
                onClickLabel = "Piocher une autre citation",
                onLongClickLabel = "Ouvrir l'historique de la semaine",
                onLongClick = openHistory,
                onClick = shuffle
            )
    ) {
        // Guillemets vectoriels en filigrane (n'influencent pas la taille de la carte)
        Box(modifier = Modifier.matchParentSize()) {
            Box(Modifier.align(Alignment.TopStart).offset(x = (-6).dp, y = (-4).dp)) {
                KalynoSvgIcon(name = "quote", size = 76.dp, tint = KalynoBlue.copy(alpha = 0.07f))
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 6.dp, y = 4.dp)
                    .graphicsLayer { rotationZ = 180f }
            ) {
                KalynoSvgIcon(name = "quote", size = 76.dp, tint = KalynoBlue.copy(alpha = 0.07f))
            }
        }

        Column(modifier = Modifier.fillMaxHeight().padding(18.dp)) {
            ModuleHeader(
                icon = "lightbulb",
                title = "Citation du jour",
                tint = KalynoAmberDeep,
                container = KalynoAmberSoft
            )
            Spacer(Modifier.height(14.dp))

            if (quote == null) {
                SkeletonLines()
            } else {
                AnimatedContent(
                    targetState = quote,
                    transitionSpec = {
                        (fadeIn(tween(240, delayMillis = 90)) + slideInVertically(tween(280)) { it / 5 }) togetherWith
                            (fadeOut(tween(120)) + slideOutVertically(tween(200)) { -it / 6 }) using
                            SizeTransform(clip = false)
                    },
                    label = "quoteSwap",
                    modifier = Modifier.weight(1f)
                ) { q ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            text = q.text,
                            color = KalynoText,
                            fontFamily = QuoteSerif,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (q.author != null) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = q.author,
                                color = KalynoMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Micro-footer d'action : 4 boutons si la place le permet, 3 sur petit écran
                // (le shuffle reste alors accessible d'un simple toucher sur la carte).
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val showShuffle = maxWidth >= 140.dp
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        QuoteActionButton(
                            icon = if (isFavorite) "heart-filled" else "heart",
                            label = if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris",
                            tint = if (isFavorite) KalynoYellow else KalynoMuted,
                            container = if (isFavorite) KalynoAmberSoft else NeutralChip,
                            onClick = toggleFavorite,
                            scale = { pop.value }
                        )
                        QuoteActionButton(
                            icon = "share",
                            label = "Partager sur WhatsApp",
                            tint = KalynoBlue,
                            container = KalynoBlueSoft,
                            onClick = share
                        )
                        if (showShuffle) {
                            QuoteActionButton(
                                icon = "shuffle",
                                label = "Autre citation",
                                tint = KalynoMuted,
                                container = NeutralChip,
                                onClick = shuffle,
                                rotation = { spin.value }
                            )
                        }
                        QuoteActionButton(
                            icon = "history",
                            label = "Historique de la semaine",
                            tint = KalynoMuted,
                            container = NeutralChip,
                            onClick = openHistory
                        )
                    }
                }
            }
        }

        if (sheetOpen) {
            QuoteHistorySheet(
                week = state.quoteWeek,
                favorites = state.favoriteQuotes,
                onToggleFavorite = onToggleFavorite,
                onShare = { shareOnWhatsApp(context, it) },
                onDismiss = { sheetOpen = false }
            )
        }
    }
}

@Composable
private fun QuoteActionButton(
    icon: String,
    label: String,
    tint: Color,
    container: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    rotation: () -> Float = { 0f },
    scale: () -> Float = { 1f }
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.graphicsLayer {
                rotationZ = rotation()
                scaleX = scale()
                scaleY = scale()
            }
        ) {
            KalynoSvgIcon(name = icon, size = 18.dp, tint = tint, contentDescription = label)
        }
    }
}

// ---------- Bottom sheet : 7 derniers jours + favoris ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuoteHistorySheet(
    week: List<QuoteDay>,
    favorites: List<FavoriteQuote>,
    onToggleFavorite: (DailyQuote) -> Unit,
    onShare: (DailyQuote) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val favoriteKeys = remember(favorites) { favorites.map { it.key }.toSet() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(
                text = "Mes citations",
                color = KalynoText,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetTab("Cette semaine", selected = tab == 0) { tab = 0 }
                SheetTab("Favoris (${favorites.size})", selected = tab == 1) { tab = 1 }
            }
            Spacer(Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 28.dp)
            ) {
                if (tab == 0) {
                    if (week.isEmpty()) {
                        item { EmptyHint("Les citations de la semaine apparaîtront dès qu'elles seront téléchargées.") }
                    }
                    items(week, key = { it.date.toEpochDay() }) { day ->
                        QuoteRow(
                            label = dayLabel(day.date),
                            quote = day.quote,
                            isFavorite = day.quote.favoriteKey() in favoriteKeys,
                            onToggleFavorite = onToggleFavorite,
                            onShare = onShare
                        )
                    }
                } else {
                    if (favorites.isEmpty()) {
                        item { EmptyHint("Touche le cœur d'une citation pour la retrouver ici.") }
                    }
                    items(favorites, key = { it.key }) { fav ->
                        QuoteRow(
                            label = null,
                            quote = fav.quote,
                            isFavorite = true,
                            onToggleFavorite = onToggleFavorite,
                            onShare = onShare
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetTab(text: String, selected: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Text(
        text = text,
        color = if (selected) Color.White else KalynoMuted,
        fontSize = 13.sp,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) KalynoBlue else NeutralChip)
            .clickable(role = Role.Tab) {
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 9.dp)
    )
}

@Composable
private fun QuoteRow(
    label: String?,
    quote: DailyQuote,
    isFavorite: Boolean,
    onToggleFavorite: (DailyQuote) -> Unit,
    onShare: (DailyQuote) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(KalynoBackground)
            .border(1.dp, KalynoOutline, shape)
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f)) {
            if (label != null) {
                Text(
                    text = label,
                    color = KalynoBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.4.sp
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                text = quote.text,
                color = KalynoText,
                fontFamily = QuoteSerif,
                fontStyle = FontStyle.Italic,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
            if (quote.author != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = quote.author,
                    color = KalynoMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            QuoteActionButton(
                icon = if (isFavorite) "heart-filled" else "heart",
                label = if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris",
                tint = if (isFavorite) KalynoYellow else KalynoMuted,
                container = if (isFavorite) KalynoAmberSoft else NeutralChip,
                onClick = {
                    haptic.performHapticFeedback(
                        if (isFavorite) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn
                    )
                    onToggleFavorite(quote)
                }
            )
            QuoteActionButton(
                icon = "share",
                label = "Partager sur WhatsApp",
                tint = KalynoBlue,
                container = KalynoBlueSoft,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    onShare(quote)
                }
            )
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        color = KalynoMuted,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        modifier = Modifier.padding(vertical = 12.dp)
    )
}

private fun dayLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "Aujourd'hui"
    today.minusDays(1) -> "Hier"
    else -> date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH))
        .replaceFirstChar { it.uppercase() }
}

// ---------- Partage WhatsApp (levier de viralité) ----------

private fun shareText(quote: DailyQuote): String = buildString {
    append("« ").append(quote.text.trim()).append(" »")
    if (!quote.author.isNullOrBlank()) append("\n— ").append(quote.author.trim())
    append("\n\nCitation du jour sur KALYNO, l'appli qui t'aide à réviser autrement.")
}

/** WhatsApp (ou WhatsApp Business) pré-rempli ; sinon le sélecteur Android classique. */
private fun shareOnWhatsApp(context: Context, quote: DailyQuote) {
    val base = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText(quote))
    }
    for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
        try {
            context.startActivity(Intent(base).setPackage(pkg))
            return
        } catch (_: ActivityNotFoundException) {
            // application absente : on essaie la suivante
        }
    }
    runCatching { context.startActivity(Intent.createChooser(base, "Partager la citation")) }
}
