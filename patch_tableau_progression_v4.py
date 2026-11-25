
from pathlib import Path
from datetime import datetime
import sys

FILE = Path(
    "app/src/main/java/ci/kalyno/app/ui/exercises/"
    "GeneratedExercisesScreen.kt"
)

def stop(message):
    print(f"ARRÊT : {message}")
    print("Aucune modification enregistrée.")
    sys.exit(1)

if not FILE.exists():
    stop(f"Fichier introuvable : {FILE}")

source = FILE.read_text(encoding="utf-8")

def replace_once(content, old, new, label):
    count = content.count(old)
    if count != 1:
        stop(
            f"bloc '{label}' trouvé {count} fois au lieu d'une fois"
        )
    return content.replace(old, new, 1)

# 1. Validation des cellules avant l'envoi
old_validation = '''        if (kind == "free_text" && freeAnswer.isBlank()) {
            error = "Écris ta réponse avant de continuer."
            return
        }
'''

new_validation = '''        if (kind == "table") {
            val payload = exercise.optJSONObject("payload") ?: JSONObject()
            val front = payload.optJSONObject("table_front") ?: JSONObject()
            val rows = front.optJSONArray("rows") ?: JSONArray()
            val blankKeys = linkedSetOf<String>()

            for (r in 0 until rows.length()) {
                val row = rows.optJSONArray(r) ?: JSONArray()

                for (c in 0 until row.length()) {
                    val cell = row.optJSONObject(c) ?: JSONObject()

                    if (cell.optString("type") == "blank") {
                        val key = cell.optString("key")
                        if (key.isNotBlank()) blankKeys.add(key)
                    }
                }
            }

            if (blankKeys.isEmpty()) {
                error = "Ce tableau ne contient aucune cellule à compléter."
                return
            }

            val missingCells = blankKeys.count {
                tableAnswers[it].isNullOrBlank()
            }

            if (missingCells > 0) {
                error = "Il te reste $missingCells cellule(s) à compléter."
                return
            }
        }

        if (kind == "free_text" && freeAnswer.isBlank()) {
            error = "Écris ta réponse avant de continuer."
            return
        }
'''

# 2. Indicateur de progression du tableau
old_table = '''                                Text(
                                    "Renseigne les cellules manquantes pour compléter le tableau.",
                                    color = colors.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                for (r in 0 until rows.length()) {
'''

new_table = '''                                Text(
                                    "Renseigne les cellules manquantes pour compléter le tableau.",
                                    color = colors.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                val blankKeys = linkedSetOf<String>()

                                for (r in 0 until rows.length()) {
                                    val row = rows.optJSONArray(r) ?: JSONArray()

                                    for (c in 0 until row.length()) {
                                        val cell = row.optJSONObject(c) ?: JSONObject()

                                        if (cell.optString("type") == "blank") {
                                            val key = cell.optString("key")
                                            if (key.isNotBlank()) blankKeys.add(key)
                                        }
                                    }
                                }

                                val filledCells = blankKeys.count {
                                    !tableAnswers[it].isNullOrBlank()
                                }

                                val tableProgress = if (blankKeys.isEmpty()) {
                                    0f
                                } else {
                                    filledCells.toFloat() / blankKeys.size.toFloat()
                                }

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = colors.primaryContainer.copy(alpha = 0.45f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(15.dp)
                                ) {
                                    Column(
                                        Modifier.padding(13.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "PROGRESSION DU TABLEAU",
                                                color = colors.onSurfaceVariant,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.ExtraBold
                                            )

                                            Text(
                                                "$filledCells / ${blankKeys.size}",
                                                color = colors.primary,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }

                                        LinearProgressIndicator(
                                            progress = tableProgress,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(7.dp),
                                            color = KalynoLightBlue,
                                            trackColor = colors.surfaceVariant
                                        )

                                        Text(
                                            if (blankKeys.isEmpty())
                                                "Aucune cellule à compléter détectée."
                                            else if (filledCells == blankKeys.size)
                                                "Toutes les cellules sont renseignées."
                                            else
                                                "${blankKeys.size - filledCells} cellule(s) restante(s).",
                                            color = colors.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                for (r in 0 until rows.length()) {
'''

# Vérifier tous les blocs AVANT toute écriture
if source.count(old_validation) != 1:
    stop(
        "bloc de validation de réponse trouvé "
        f"{source.count(old_validation)} fois au lieu d'une fois"
    )

if source.count(old_table) != 1:
    stop(
        "bloc d'affichage du tableau trouvé "
        f"{source.count(old_table)} fois au lieu d'une fois"
    )

updated = replace_once(
    source,
    old_validation,
    new_validation,
    "validation des cellules du tableau"
)

updated = replace_once(
    updated,
    old_table,
    new_table,
    "indicateur de progression du tableau"
)

# Sauvegarde puis écriture
backup = FILE.with_name(
    FILE.name + ".bak-" + datetime.now().strftime("%Y%m%d-%H%M%S")
)

backup.write_text(source, encoding="utf-8")
FILE.write_text(updated, encoding="utf-8")

print("CORRECTIF APPLIQUÉ.")
print(f"Sauvegarde : {backup}")
print("Deux améliorations ajoutées :")
print("- progression visuelle des cellules renseignées")
print("- blocage de l'envoi si le tableau est incomplet")
