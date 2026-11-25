
#!/usr/bin/env python3
# KALYNO — Refonte professionnelle des écrans d'exercices IA
# Cible : GeneratedExercisesScreen.kt
# Base inspectée : commit fd49b52
#
# Sécurité :
# - Vérification de tous les blocs avant écriture
# - Arrêt si un bloc est absent ou dupliqué
# - Sauvegarde automatique avant modification

from pathlib import Path
from datetime import datetime
import sys

TARGET = Path(
    "app/src/main/java/ci/kalyno/app/ui/exercises/"
    "GeneratedExercisesScreen.kt"
)

if not TARGET.is_file():
    sys.exit(
        f"ARRÊT : fichier introuvable : {TARGET}\n"
        "Lance ce script depuis la racine de KALYNO."
    )

source = TARGET.read_text(encoding="utf-8")
updated = source


def replace_region(text, start, end, replacement, label):
    count_start = text.count(start)
    count_end = text.count(end)

    if count_start != 1 or count_end != 1:
        raise RuntimeError(
            f"ARRÊT : bloc « {label} » : "
            f"début={count_start}, fin={count_end}. "
            "Une occurrence de chaque était attendue."
        )

    a = text.index(start)
    b = text.index(end, a)

    if b <= a:
        raise RuntimeError(
            f"ARRÊT : ordre incorrect des repères : {label}"
        )

    return text[:a] + replacement + text[b:]


try:

    # -------------------------------------------------
    # 1. EN-TÊTE ET TABLEAU DE PROGRESSION
    # -------------------------------------------------

    start = '''            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {'''

    end = '''            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "À toi de jouer",'''

    replacement = '''            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        "KALYNO / LABORATOIRE D’APPRENTISSAGE",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primary,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "Ton espace de défis",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.onBackground
                    )
                    Text(
                        "Apprends. Applique. Progresse.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }

                Surface(
                    onClick = { refresh() },
                    enabled = !loading,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    color = colors.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        colors.outlineVariant
                    )
                ) {
                    Box(
                        Modifier.size(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "↻",
                            color = colors.primary,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colors.primary
                ),
                elevation = CardDefaults.cardElevation(5.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(21.dp),
                    verticalArrangement = Arrangement.spacedBy(17.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Surface(
                                color = colors.onPrimary.copy(alpha = 0.14f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
                            ) {
                                Text(
                                    "TON PARCOURS",
                                    Modifier.padding(
                                        horizontal = 11.dp,
                                        vertical = 6.dp
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onPrimary,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            Text(
                                if (remaining > 0)
                                    "Chaque défi te rapproche de la maîtrise."
                                else if (exercises.isEmpty())
                                    "Ton prochain progrès commence ici."
                                else
                                    "Parcours accompli. Bravo !",
                                style = MaterialTheme.typography.titleLarge,
                                color = colors.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            Modifier.size(72.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = progress,
                                modifier = Modifier.fillMaxSize(),
                                color = colors.onPrimary,
                                trackColor = colors.onPrimary.copy(alpha = 0.2f),
                                strokeWidth = 6.dp
                            )

                            Text(
                                "${(progress * 100).toInt()}%",
                                color = colors.onPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            Modifier.weight(1f),
                            color = colors.onPrimary.copy(alpha = 0.12f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    "$remaining",
                                    color = colors.onPrimary,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    "À relever",
                                    color = colors.onPrimary.copy(alpha = 0.82f),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        Surface(
                            Modifier.weight(1f),
                            color = colors.onPrimary.copy(alpha = 0.12f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    "$completed",
                                    color = colors.onPrimary,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    "Accomplis",
                                    color = colors.onPrimary.copy(alpha = 0.82f),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Progression globale",
                                color = colors.onPrimary.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                "$completed / ${exercises.size}",
                                color = colors.onPrimary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp),
                            color = KalynoLightBlue,
                            trackColor = colors.onPrimary.copy(alpha = 0.18f)
                        )
                    }
                }
            }

'''

    updated = replace_region(
        updated, start, end, replacement,
        "en-tête et progression"
    )

    # -------------------------------------------------
    # 2. CARTES DES EXERCICES
    # -------------------------------------------------

    start = '''                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { openExercise(exercise) },'''

    end = '''                    }
                }
            }
            Spacer(Modifier.height(8.dp))'''

    replacement = '''                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { openExercise(exercise) },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = colors.surface
                        ),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(Modifier.fillMaxWidth()) {

                            Box(
                                Modifier
                                    .width(5.dp)
                                    .height(150.dp)
                                    .background(
                                        if (done) colors.secondary
                                        else colors.tertiary
                                    )
                            )

                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(
                                        horizontal = 16.dp,
                                        vertical = 15.dp
                                    ),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        type,
                                        color = colors.primary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold
                                    )

                                    Surface(
                                        color = if (done)
                                            colors.secondaryContainer
                                        else
                                            colors.tertiaryContainer,
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
                                    ) {
                                        Text(
                                            if (done) "✓ Terminé" else "À faire",
                                            Modifier.padding(
                                                horizontal = 10.dp,
                                                vertical = 5.dp
                                            ),
                                            color = if (done)
                                                colors.onSecondaryContainer
                                            else
                                                colors.onTertiaryContainer,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Text(
                                    exercise.optString("title")
                                        .ifBlank { "Défi ${index + 1}" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onSurface
                                )

                                RichText(
                                    html = exercise.optString("statement"),
                                    color = colors.onSurfaceVariant,
                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                                )

                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (level.isNotBlank()) {
                                        Surface(
                                            color = colors.surfaceVariant,
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                level,
                                                Modifier.padding(
                                                    horizontal = 9.dp,
                                                    vertical = 5.dp
                                                ),
                                                color = colors.onSurfaceVariant,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    } else {
                                        Spacer(Modifier.width(1.dp))
                                    }

                                    Text(
                                        if (done)
                                            "Revoir le défi  →"
                                        else
                                            "Entrer dans le défi  →",
                                        color = colors.primary,
                                        fontWeight = FontWeight.ExtraBold,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
'''

    updated = replace_region(
        updated, start, end, replacement,
        "cartes des exercices"
    )

    # -------------------------------------------------
    # 3. ÉNONCÉ
    # -------------------------------------------------

    start = '''            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colors.surfaceVariant.copy(alpha = 0.55f)
                )
            ) {'''

    end = '''            if (result == null) {'''

    replacement = '''            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colors.surface
                ),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Row(Modifier.fillMaxWidth()) {

                    Box(
                        Modifier
                            .width(5.dp)
                            .height(112.dp)
                            .background(KalynoLightBlue)
                    )

                    Column(
                        Modifier
                            .weight(1f)
                            .padding(17.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = colors.secondaryContainer,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "ÉNONCÉ",
                                    Modifier.padding(
                                        horizontal = 9.dp,
                                        vertical = 5.dp
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSecondaryContainer,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            Text(
                                "Lis attentivement",
                                color = colors.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        RichText(
                            html = exercise.optString("statement"),
                            color = colors.onSurface,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                        )
                    }
                }
            }

'''

    updated = replace_region(
        updated, start, end, replacement,
        "énoncé de l'exercice"
    )

    # -------------------------------------------------
    # 4. RÉSULTAT ET ANALYSE DU RAISONNEMENT
    # -------------------------------------------------

    start = '''                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.primaryContainer)
                ) {'''

    end = '''                Button(
                    onClick = {
                        selected = null
                        result = null'''

    replacement = '''                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = colors.surface
                    ),
                    elevation = CardDefaults.cardElevation(3.dp)
                ) {
                    Column(Modifier.fillMaxWidth()) {

                        Surface(
                            color = when (verdict) {
                                "correct", "success", "passed" -> colors.secondary
                                "partial", "partially_correct" -> KalynoYellow
                                else -> colors.tertiary
                            },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                                topStart = 26.dp,
                                topEnd = 26.dp
                            )
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = 19.dp,
                                        vertical = 18.dp
                                    ),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        "BILAN DE TON DÉFI",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.82f),
                                        fontWeight = FontWeight.ExtraBold
                                    )

                                    Text(
                                        when (verdict) {
                                            "correct", "success", "passed" ->
                                                "Excellent travail !"
                                            "partial", "partially_correct" ->
                                                "Tu progresses, continue !"
                                            else ->
                                                "On apprend de chaque essai."
                                        },
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                }

                                Surface(
                                    color = Color.White.copy(alpha = 0.17f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                                ) {
                                    Text(
                                        "$score/100",
                                        Modifier.padding(
                                            horizontal = 13.dp,
                                            vertical = 10.dp
                                        ),
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }

                        Column(
                            Modifier.padding(19.dp),
                            verticalArrangement = Arrangement.spacedBy(13.dp)
                        ) {
                            RichText(
                                html = correction.optString("feedback"),
                                color = colors.onSurface,
                                fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                            )

                            reasoningAnalysis?.let { analysis ->
                                val summary = analysis.optString("summary")
                                val steps = analysis.optJSONArray("steps")
                                val strengths = analysis.optJSONArray("strengths")
                                val errors = analysis.optJSONArray("errors")
                                val advice = analysis.optString("advice")

                                HorizontalDivider(color = colors.outlineVariant)

                                Text(
                                    "ANALYSE DE TON RAISONNEMENT",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = colors.tertiary
                                )

                                if (summary.isNotBlank()) {
                                    Surface(
                                        color = colors.tertiaryContainer.copy(alpha = 0.55f),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                                    ) {
                                        Text(
                                            summary,
                                            Modifier.padding(13.dp),
                                            color = colors.onSurface,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }

                                if (steps != null && steps.length() > 0) {
                                    Text(
                                        "Étapes de ton raisonnement",
                                        fontWeight = FontWeight.Bold,
                                        color = colors.onSurface
                                    )

                                    for (i in 0 until steps.length()) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Surface(
                                                color = colors.primary,
                                                shape = androidx.compose.foundation.shape.CircleShape
                                            ) {
                                                Text(
                                                    "${i + 1}",
                                                    Modifier.padding(
                                                        horizontal = 8.dp,
                                                        vertical = 4.dp
                                                    ),
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            Text(
                                                steps.optString(i),
                                                Modifier.weight(1f),
                                                color = colors.onSurface
                                            )
                                        }
                                    }
                                }

                                if (strengths != null && strengths.length() > 0) {
                                    Surface(
                                        color = colors.secondaryContainer.copy(alpha = 0.55f),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                                    ) {
                                        Column(
                                            Modifier.padding(13.dp),
                                            verticalArrangement = Arrangement.spacedBy(7.dp)
                                        ) {
                                            Text(
                                                "POINTS FORTS",
                                                color = colors.onSecondaryContainer,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.ExtraBold
                                            )

                                            for (i in 0 until strengths.length()) {
                                                Text(
                                                    "✓  ${strengths.optString(i)}",
                                                    color = colors.onSecondaryContainer
                                                )
                                            }
                                        }
                                    }
                                }

                                if (errors != null && errors.length() > 0) {
                                    Surface(
                                        color = colors.errorContainer.copy(alpha = 0.55f),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                                    ) {
                                        Column(
                                            Modifier.padding(13.dp),
                                            verticalArrangement = Arrangement.spacedBy(7.dp)
                                        ) {
                                            Text(
                                                "POINTS À CORRIGER",
                                                color = colors.onErrorContainer,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.ExtraBold
                                            )

                                            for (i in 0 until errors.length()) {
                                                Text(
                                                    "•  ${errors.optString(i)}",
                                                    color = colors.onErrorContainer
                                                )
                                            }
                                        }
                                    }
                                }

                                if (advice.isNotBlank()) {
                                    Surface(
                                        color = colors.surfaceVariant,
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                                    ) {
                                        Column(
                                            Modifier.padding(13.dp),
                                            verticalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Text(
                                                "PROCHAINE ÉTAPE",
                                                color = colors.primary,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            Text(advice, color = colors.onSurface)
                                        }
                                    }
                                }
                            }

                            if (
                                details != null &&
                                details.optString("explanation").isNotBlank()
                            ) {
                                HorizontalDivider(color = colors.outlineVariant)

                                Text(
                                    "À RETENIR",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.primary,
                                    fontWeight = FontWeight.ExtraBold
                                )

                                RichText(
                                    html = details.optString("explanation"),
                                    color = colors.onSurface,
                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                                )
                            }
                        }
                    }
                }

'''

    updated = replace_region(
        updated, start, end, replacement,
        "résultat et diagnostic"
    )

except RuntimeError as exc:
    sys.exit(
        str(exc) + "\n"
        "Aucune modification enregistrée."
    )


# -------------------------------------------------
# 5. IMPORTS REQUIS
# -------------------------------------------------

required_imports = [
    "import androidx.compose.foundation.background",
    "import androidx.compose.ui.graphics.Color",
    "import ci.kalyno.app.ui.theme.KalynoLightBlue",
    "import ci.kalyno.app.ui.theme.KalynoYellow",
]

missing_imports = [
    item for item in required_imports
    if item not in updated
]

if missing_imports:
    package_line = "package ci.kalyno.app.ui.exercises"
    if package_line not in updated:
        sys.exit(
            "ARRÊT : déclaration package introuvable. "
            "Aucune modification enregistrée."
        )

    updated = updated.replace(
        package_line,
        package_line + "\n\n" + "\n".join(missing_imports),
        1
    )


if updated == source:
    sys.exit("ARRÊT : aucune modification produite.")


# -------------------------------------------------
# 6. SAUVEGARDE ET ÉCRITURE
# -------------------------------------------------

backup = TARGET.with_name(
    TARGET.name + ".bak-" +
    datetime.now().strftime("%Y%m%d-%H%M%S")
)

backup.write_text(source, encoding="utf-8")
TARGET.write_text(updated, encoding="utf-8")

print("\nCORRECTIF APPLIQUÉ AVEC SUCCÈS.")
print(f"Sauvegarde : {backup}")
print(f"Fichier modifié : {TARGET}")
print("\nZones refondues :")
print("- Tableau de progression")
print("- Cartes des exercices")
print("- Présentation de l'énoncé")
print("- Résultat et analyse du raisonnement")
