
#!/usr/bin/env python3
# KALYNO — Correctif 3
# En-tête intelligent de l'écran d'exercice.
# Cible : GeneratedExercisesScreen.kt
#
# Sécurité :
# - Repères vérifiés avant toute modification
# - Sauvegarde automatique
# - Aucune modification de l'API ou de Gemini

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
        "Exécute ce script depuis la racine de KALYNO."
    )

source = TARGET.read_text(encoding="utf-8")
updated = source

# --------------------------------------------------
# 1. Ajouter les informations de position et difficulté
# --------------------------------------------------

anchor = '''        val isQcm = kind == "qcm" || kind == "multiple_choice"'''

if updated.count(anchor) != 1:
    sys.exit(
        "ARRÊT : déclaration isQcm trouvée "
        f"{updated.count(anchor)} fois au lieu d'une.\n"
        "Aucune modification enregistrée."
    )

addition = '''

        val exercisePosition = (
            exercises.indexOfFirst {
                it.optString("id") == exercise.optString("id")
            } + 1
        ).coerceAtLeast(1)

        val exerciseTotal = exercises.size.coerceAtLeast(1)

        val difficultyLabel = when (
            exercise.optString("difficulty").lowercase()
        ) {
            "easy", "facile", "beginner" -> "FACILE"
            "medium", "intermediate", "moyen" -> "INTERMÉDIAIRE"
            "hard", "difficult", "difficile", "advanced" -> "AVANCÉ"
            else -> "ENTRAÎNEMENT"
        }

        val exerciseTypeLabel = when {
            isQcm -> "CHOIX MULTIPLES"
            kind == "table" -> "TABLEAU À COMPLÉTER"
            else -> "RÉPONSE ÉCRITE"
        }
'''

updated = updated.replace(
    anchor,
    anchor + addition,
    1
)

# --------------------------------------------------
# 2. Remplacer l'ancien en-tête
# --------------------------------------------------

start = '''            TextButton(onClick = {
                selected = null
                result = null
                error = null
            }) {
                Text("←  Mes exercices")
            }'''

end = '''            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colors.surface
                ),'''

if updated.count(start) != 1:
    sys.exit(
        "ARRÊT : ancien bouton de retour trouvé "
        f"{updated.count(start)} fois au lieu d'une.\n"
        "Aucune modification enregistrée."
    )

if updated.count(end) != 1:
    sys.exit(
        "ARRÊT : carte de l'énoncé trouvée "
        f"{updated.count(end)} fois au lieu d'une.\n"
        "Aucune modification enregistrée."
    )

a = updated.index(start)
b = updated.index(end, a)

if b <= a:
    sys.exit(
        "ARRÊT : ordre des repères incorrect.\n"
        "Aucune modification enregistrée."
    )

replacement = '''            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colors.primary
                ),
                elevation = CardDefaults.cardElevation(3.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(15.dp)
                ) {

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                selected = null
                                result = null
                                error = null
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = colors.onPrimary
                            )
                        ) {
                            Text(
                                "←  Mes exercices",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            color = colors.onPrimary.copy(alpha = 0.14f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
                        ) {
                            Text(
                                difficultyLabel,
                                Modifier.padding(
                                    horizontal = 11.dp,
                                    vertical = 7.dp
                                ),
                                color = colors.onPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text(
                            "DÉFI $exerciseTypeLabel",
                            color = colors.onPrimary.copy(alpha = 0.76f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold
                        )

                        RichText(
                            html = exercise.optString("title"),
                            color = Color.White,
                            fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                            lineHeight = MaterialTheme.typography.headlineSmall.lineHeight,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "TON PARCOURS",
                                color = colors.onPrimary.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                "$exercisePosition / $exerciseTotal",
                                color = colors.onPrimary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        LinearProgressIndicator(
                            progress = (
                                exercisePosition.toFloat() /
                                    exerciseTotal.toFloat()
                            ).coerceIn(0f, 1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = KalynoLightBlue,
                            trackColor = colors.onPrimary.copy(alpha = 0.18f)
                        )
                    }
                }
            }

'''

updated = updated[:a] + replacement + updated[b:]

if updated == source:
    sys.exit(
        "ARRÊT : aucun changement produit."
    )

# --------------------------------------------------
# 3. Sauvegarde et écriture
# --------------------------------------------------

backup = TARGET.with_name(
    TARGET.name + ".bak-" +
    datetime.now().strftime("%Y%m%d-%H%M%S")
)

backup.write_text(source, encoding="utf-8")
TARGET.write_text(updated, encoding="utf-8")

print("\nCORRECTIF 3 APPLIQUÉ.")
print(f"Sauvegarde : {backup}")
print(f"Fichier modifié : {TARGET}")
print("\nModifications :")
print("- En-tête regroupé")
print("- Position dans le parcours")
print("- Progression de position")
print("- Niveau de difficulté")
print("- Type d'exercice")
print("- Bouton de retour intégré")
print("\nAPI, réponses JSON et correction Gemini inchangées.")
