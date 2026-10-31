package ci.kalyno.app.ui.explorer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ci.kalyno.app.data.ExplorerBanner
import ci.kalyno.app.ui.theme.KalynoBlue

/**
 * Bannières « À la une » en carrousel horizontal : l'admin peut en publier plusieurs sans retirer les anciennes.
 * La carte suivante dépasse légèrement (indice de défilement), le défilement s'aligne sur chaque carte
 * et des points indiquent la position. Une seule annonce : carte pleine largeur, sans points.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeroBannerCarousel(
    banners: List<ExplorerBanner>,
    chapters: List<ExplorerChapter>,
    onOpenLesson: (ExplorerChapter) -> Unit,
    modifier: Modifier = Modifier
) {
    if (banners.isEmpty()) return
    val chapterById = remember(chapters) { chapters.associateBy { it.id } }

    if (banners.size == 1) {
        val banner = banners.first()
        ExplorerHeroBanner(
            banner = banner,
            onClick = chapterById[banner.chapterId]?.let { chapter -> { onOpenLesson(chapter) } },
            modifier = modifier.padding(horizontal = 16.dp)
        )
        return
    }

    val listState = rememberLazyListState()
    val current by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Clé = id + position : sûre même si l'admin laisse deux annonces sans identifiant
            itemsIndexed(banners, key = { index, banner -> "${banner.id}#$index" }) { _, banner ->
                ExplorerHeroBanner(
                    banner = banner,
                    onClick = chapterById[banner.chapterId]?.let { chapter -> { onOpenLesson(chapter) } },
                    modifier = Modifier.fillParentMaxWidth(0.88f)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            banners.indices.forEach { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (index == current) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (index == current) KalynoBlue else KalynoBlue.copy(alpha = 0.25f))
                )
            }
        }
    }
}
