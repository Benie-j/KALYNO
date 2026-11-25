
from pathlib import Path
import shutil

path = Path("app/src/main/java/ci/kalyno/app/ui/exercises/GeneratedExercisesScreen.kt")

if not path.exists():
    raise SystemExit("ERREUR : fichier GeneratedExercisesScreen.kt introuvable.")

src = path.read_text(encoding="utf-8")
original = src

def replace_once(old, new, label):
    global src
    count = src.count(old)
    if count != 1:
        raise SystemExit(
            f"ARRÊT : bloc '{label}' trouvé {count} fois au lieu d'une fois. "
            "Aucune modification enregistrée."
        )
    src = src.replace(old, new, 1)

# 1. Réutiliser le moteur de rendu mathématique existant.
replace_once(
    "import ci.kalyno.app.data.SupabaseConfig",
    "import ci.kalyno.app.data.SupabaseConfig\n"
    "import ci.kalyno.app.ui.explorer.RichText",
    "import RichText"
)

# 2. Ajouter un champ distinct pour la démarche de l'élève.
replace_once(
    '    var freeAnswer by remember { mutableStateOf("") }\n',
    '    var freeAnswer by remember { mutableStateOf("") }\n'
    '    var reasoning by remember { mutableStateOf("") }\n',
    "état de la démarche"
)

replace_once(
    '        freeAnswer = ""\n        tableAnswers = emptyMap()\n',
    '        freeAnswer = ""\n        reasoning = ""\n        tableAnswers = emptyMap()\n',
    "réinitialisation de la démarche"
)

# 3. Envoyer la démarche et la réponse finale à l'IA.
replace_once(
    '            else -> JSONObject().put("text", freeAnswer.trim())\n',
    '''            else -> JSONObject()
                .put(
                    "text",
                    buildString {
                        if (reasoning.isNotBlank()) {
                            append("DÉMARCHE DE L'ÉLÈVE :\\n")
                            append(reasoning.trim())
                            append("\\n\\n")
                        }
                        append("RÉPONSE FINALE :\\n")
                        append(freeAnswer.trim())
                    }
                )
                .put("reasoning", reasoning.trim())
''',
    "transmission de la démarche"
)

# 4. Afficher un espace de raisonnement avant la réponse finale.
replace_once(
    '''                    else -> {
                        OutlinedTextField(
                            value = freeAnswer,
                            onValueChange = { freeAnswer = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 5,
                            label = { Text("Ta réponse") },
                            placeholder = { Text("Écris ici…") },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        )
                    }
''',
    '''                    else -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "1. Explique ta démarche",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Note les étapes de ton raisonnement. Cela permet à l'IA de mieux comprendre ta méthode.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = reasoning,
                                onValueChange = { reasoning = it },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 4,
                                label = { Text("Ma démarche (facultatif)") },
                                placeholder = { Text("Je commence par…") },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                            )
                            Text(
                                "2. Donne ta réponse finale",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = freeAnswer,
                                onValueChange = { freeAnswer = it },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                label = { Text("Réponse finale") },
                                placeholder = { Text("Mon résultat est…") },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                            )
                        }
                    }
''',
    "espace de raisonnement"
)

# 5. Les titres et énoncés de la liste utilisent le rendu enrichi.
replace_once(
    '''                            Text(
                                exercise.optString("title").ifBlank { "Défi ${index + 1}" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface
                            )
''',
    '''                            RichText(
                                html = exercise.optString("title").ifBlank { "Exercice ${index + 1}" },
                                color = colors.onSurface,
                                fontSize = MaterialTheme.typography.titleMedium.fontSize,
                                lineHeight = MaterialTheme.typography.titleMedium.lineHeight,
                                fontWeight = FontWeight.Bold
                            )
''',
    "titre de la liste"
)

replace_once(
    '''                            Text(
                                exercise.optString("statement"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                                maxLines = 3
                            )
''',
    '''                            RichText(
                                html = exercise.optString("statement"),
                                color = colors.onSurfaceVariant,
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                            )
''',
    "énoncé de la liste"
)

# 6. Rendu mathématique dans l'écran de résolution.
replace_once(
    '''            Text(
                exercise.optString("title"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
''',
    '''            RichText(
                html = exercise.optString("title"),
                color = colors.onSurface,
                fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                lineHeight = MaterialTheme.typography.headlineSmall.lineHeight,
                fontWeight = FontWeight.ExtraBold
            )
''',
    "titre de l'exercice"
)

replace_once(
    '''                    Text(
                        exercise.optString("statement"),
                        style = MaterialTheme.typography.bodyLarge
                    )
''',
    '''                    RichText(
                        html = exercise.optString("statement"),
                        color = colors.onSurface,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                    )
''',
    "énoncé détaillé"
)

# 7. Rendu mathématique des choix QCM.
replace_once(
    '''                                    Text(
                                        choices.optString(i),
                                        Modifier.padding(vertical = 8.dp),
                                        color = colors.onSurface
                                    )
''',
    '''                                    Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                        RichText(
                                            html = choices.optString(i),
                                            color = colors.onSurface,
                                            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                                        )
                                    }
''',
    "choix QCM"
)

# 8. Rendu mathématique dans les cellules de tableau.
replace_once(
    '''                                            Text(
                                                cell.optString("value"),
                                                Modifier.padding(12.dp),
                                                color = colors.onSurface
                                            )
''',
    '''                                            Box(Modifier.padding(12.dp)) {
                                                RichText(
                                                    html = cell.optString("value"),
                                                    color = colors.onSurface,
                                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                                                    allowKatex = false
                                                )
                                            }
''',
    "cellules de tableau"
)

# 9. Rendu enrichi de la correction et de l'explication.
replace_once(
    '''                        Text(
                            correction.optString("feedback"),
                            color = colors.onPrimaryContainer,
                            style = MaterialTheme.typography.bodyLarge
                        )
''',
    '''                        RichText(
                            html = correction.optString("feedback"),
                            color = colors.onPrimaryContainer,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                        )
''',
    "retour de correction"
)

replace_once(
    '''                            Text(
                                details.optString("explanation"),
                                color = colors.onPrimaryContainer
                            )
''',
    '''                            RichText(
                                html = details.optString("explanation"),
                                color = colors.onPrimaryContainer,
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                            )
''',
    "explication de correction"
)

if src == original:
    raise SystemExit("Aucun changement détecté.")

backup = path.with_suffix(path.suffix + ".before-learning-patch")
shutil.copy2(path, backup)
path.write_text(src, encoding="utf-8")

print("Correctif appliqué.")
print(f"Sauvegarde : {backup}")
print("Fichier modifié :", path)
