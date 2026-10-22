package ci.kalyno.app.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.data.Levels
import ci.kalyno.app.data.StreakStore
import ci.kalyno.app.ui.theme.*

private data class TabItem(val emoji: String, val label: String)

private val TABS = listOf(
    TabItem("🏠", "Accueil"),
    TabItem("🧠", "Dojo"),
    TabItem("📚", "Explorer"),
    TabItem("👤", "Profil")
)

@Composable
fun MainScreen(levelId: String, onChangeLevel: () -> Unit, onLoginClick: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    BackHandler(enabled = tab != 0) { tab = 0 }

    Scaffold(
        containerColor = KalynoBackground,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                TABS.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Text(item.emoji, fontSize = 22.sp) },
                        label = { Text(item.label, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedTextColor = KalynoBlue,
                            unselectedTextColor = KalynoMuted,
                            indicatorColor = KalynoBlueSoft
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> DashboardScreen(levelId)
                1 -> EmptyTab(
                    emoji = "🧠",
                    title = "Le Dojo",
                    text = "Tes cartes de révision apparaîtront ici dès que les premiers chapitres de ${Levels.label(levelId)} seront publiés."
                )
                2 -> EmptyTab(
                    emoji = "📚",
                    title = "Explorer",
                    text = "Les matières et les chapitres de ${Levels.label(levelId)} apparaîtront ici dès qu'ils seront publiés."
                )
                else -> ProfileScreen(levelId, onChangeLevel, onLoginClick)
            }
        }
    }
}

// ---------- Accueil ----------

@Composable
private fun DashboardScreen(levelId: String) {
    val context = LocalContext.current.applicationContext
    val streak = remember { StreakStore(context).current() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "KALYNO",
                    color = KalynoBlue,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${Levels.emoji(levelId)} ${Levels.label(levelId)}",
                    color = KalynoMuted,
                    fontSize = 13.sp
                )
            }
            Text(
                text = "🔥 $streak",
                color = KalynoText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(KalynoYellow.copy(alpha = 0.3f))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }

        // Ces trois blocs seront branchés aux vraies données avec le Dojo
        MissionCard(mission = null)
        RescueCard(urgentCount = 0)
        MasteryCard(levelId = levelId, percent = 0)
    }
}

@Composable
private fun MissionCard(mission: String?) {
    DashCard {
        CardLabel("🎯 MISSION DU JOUR", KalynoBlue)
        Spacer(Modifier.height(8.dp))
        Text(
            text = mission ?: "Ta prochaine étape apparaîtra ici dès que les premiers chapitres seront publiés.",
            color = KalynoText,
            fontSize = 16.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun RescueCard(urgentCount: Int) {
    val calm = urgentCount == 0
    val textColor = if (calm) KalynoCorrectText else KalynoRetryText
    DashCard(
        background = if (calm) KalynoCorrectSoft else KalynoRetrySoft,
        stroke = if (calm) KalynoCorrect else KalynoRetry
    ) {
        CardLabel(if (calm) "✅ ZONE DE SAUVETAGE" else "🚨 ZONE DE SAUVETAGE", textColor)
        Spacer(Modifier.height(8.dp))
        Text(
            text = when {
                calm -> "Rien d'urgent à réviser pour l'instant."
                urgentCount == 1 -> "1 notion à sauver aujourd'hui"
                else -> "$urgentCount notions à sauver aujourd'hui"
            },
            color = textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MasteryCard(levelId: String, percent: Int) {
    val value = percent.coerceIn(0, 100)
    DashCard {
        CardLabel("📈 MAÎTRISE DU PROGRAMME", KalynoBlue)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "$value %",
            color = KalynoBlue,
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = "du programme ${Levels.label(levelId)}",
            color = KalynoMuted,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(KalynoBlueSoft)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(value / 100f)
                    .fillMaxHeight()
                    .background(KalynoYellow)
            )
        }
    }
}

// ---------- Profil ----------

@Composable
private fun ProfileScreen(levelId: String, onChangeLevel: () -> Unit, onLoginClick: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val streak = remember { StreakStore(context).current() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "👤 Profil",
            color = KalynoText,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold
        )

        DashCard {
            CardLabel("🎓 MA CLASSE", KalynoBlue)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${Levels.emoji(levelId)} ${Levels.label(levelId)}",
                color = KalynoText,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(12.dp))
            SoftButton("Changer de classe", onChangeLevel)
        }

        DashCard {
            CardLabel("🔥 MA SÉRIE", KalynoBlue)
            Spacer(Modifier.height(8.dp))
            Text(
                text = when (streak) {
                    0 -> "Pas de série en cours. Termine un défi pour la lancer !"
                    1 -> "1 jour de suite"
                    else -> "$streak jours de suite"
                },
                color = KalynoText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        DashCard {
            CardLabel("☁️ MON COMPTE", KalynoBlue)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Connecte-toi avec Google pour retrouver ta progression si tu changes de téléphone.",
                color = KalynoMuted,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(12.dp))
            SoftButton("Se connecter avec Google", onLoginClick)
        }
    }
}

// ---------- Éléments communs ----------

@Composable
private fun EmptyTab(emoji: String, title: String, text: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 56.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            color = KalynoText,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = text,
            color = KalynoMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DashCard(
    background: Color = Color.White,
    stroke: Color = KalynoOutline,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = background,
        border = BorderStroke(2.dp, stroke)
    ) {
        Column(modifier = Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun CardLabel(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp
    )
}

@Composable
private fun SoftButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(KalynoBlueSoft)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = KalynoBlue, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
