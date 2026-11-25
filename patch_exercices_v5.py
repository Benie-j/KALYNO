
from pathlib import Path
import sys

TARGET = Path(
    "app/src/main/java/ci/kalyno/app/ui/exercises/GeneratedExercisesScreen.kt"
)

if not TARGET.is_file():
    sys.exit(f"ARRÊT : fichier introuvable : {TARGET}")

source = TARGET.read_text(encoding="utf-8")

if "var reasoning by remember" in source or "import ci.kalyno.app.ui.explorer.RichText" in source:
    sys.exit("ARRÊT : le correctif v5 semble déjà appliqué. Aucun changement effectué.")

def replace_exact(name, old, new):
    global source
    count = source.count(old)
    if count != 1:
        sys.exit(
            f"ARRÊT : bloc '{name}' trouvé {count} fois au lieu d'une fois. "
            "Aucune modification enregistrée."
        )
    source = source.replace(old, new, 1)

# 1. Import du composant de rendu enrichi.
replace_exact(
    "import RichText",
    "package ci.kalyno.app.ui.exercises\n",
    "package ci.kalyno.app.ui.exercises\n\n"
    "import ci.kalyno.app.ui.explorer.RichText\n"
)

# 2. État de la démarche de l'élève.
replace_exact(
    "état de la démarche",
    '    var freeAnswer by remember { mutableStateOf("") }\n',
    '    var freeAnswer by remember { mutableStateOf("") }\n'
    '    var reasoning by remember { mutableStateOf("") }\n'
)

# 3. Réinitialisation à l'ouverture d'un exercice.
replace_exact(
    "réinitialisation de la démarche",
    '        freeAnswer = ""\n        tableAnswers = emptyMap()\n',
    '        freeAnswer = ""\n'
    '        reasoning = ""\n'
    '        tableAnswers = emptyMap()\n'
)

# 4. Envoi de la démarche et de la réponse finale dans le champ texte existant.
replace_exact(
    "envoi démarche et réponse",
    '''            else -> JSONObject().put("text", freeAnswer.trim())''',
    r'''            else -> JSONObject().put(
                "text",
                buildString {
                    if (reasoning.isNotBlank()) {
                        append("DÉMARCHE DE L'ÉLÈVE :\n")
                        append(reasoning.trim())
                        append("\n\n")
                    }
                    append("RÉPONSE FINALE :\n")
                    append(freeAnswer.trim())
                }
            )'''
)

# 5. Validation des réponses écrites.
replace_exact(
    "validation réponse écrite",
    '''        if (kind == "free_text" && freeAnswer.isBlank()) {
            error = "Écris ta réponse avant de continuer."
            return
        }''',
    '''        if (kind != "qcm" && kind != "multiple_choice" &&
            kind != "table" && freeAnswer.isBlank()
        ) {
            error = "Écris ta réponse finale avant de continuer."
            return
        }'''
)

# 6. Rendu enrichi de l'énoncé dans la liste.
replace_exact(
    "énoncé liste",
    '''                            Text(
                                exercise.optString("statement"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                                maxLines = 3
                            )''',
    '''                            RichText(
                                html = exercise.optString("statement"),
                                color = colors.onSurfaceVariant,
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                            )'''
)

# 7. Rendu enrichi de l'énoncé dans l'exercice ouvert.
replace_exact(
    "énoncé exercice",
    '''                    Text(
                        exercise.optString("statement"),
                        style = MaterialTheme.typography.bodyLarge
                    )''',
    '''                    RichText(
                        html = exercise.optString("statement"),
                        color = colors.onSurface,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                    )'''
)

# 8. Rendu enrichi des propositions QCM.
replace_exact(
    "propositions QCM",
    '''                                    Text(
                                        choices.optString(i),
                                        Modifier.padding(vertical = 8.dp),
                                        color = colors.onSurface
                                    )''',
    '''                                    Box(
                                        Modifier.weight(1f).padding(vertical = 8.dp)
                                    ) {
                                        RichText(
                                            html = choices.optString(i),
                                            color = colors.onSurface,
                                            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                                        )
                                    }'''
)

# 9. Rendu enrichi des cellules du tableau.
replace_exact(
    "cellules tableau",
    '''                                            Text(
                                                cell.optString("value"),
                                                Modifier.padding(12.dp),
                                                color = colors.onSurface
                                            )''',
    '''                                            Box(Modifier.padding(12.dp)) {
                                                RichText(
                                                    html = cell.optString("value"),
                                                    color = colors.onSurface,
                                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                                                )
                                            }'''
)

# 10. Deux champs distincts : démarche et réponse finale.
replace_exact(
    "champs démarche et réponse finale",
    '''                        OutlinedTextField(
                            value = freeAnswer,
                            onValueChange = { freeAnswer = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 5,
                            label = { Text("Ta réponse") },
                            placeholder = { Text("Écris ici…") },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        )''',
    '''                        OutlinedTextField(
                            value = reasoning,
                            onValueChange = { reasoning = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            label = { Text("Ta démarche") },
                            placeholder = {
                                Text("Explique les étapes de ton raisonnement…")
                            },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        )

                        OutlinedTextField(
                            value = freeAnswer,
                            onValueChange = { freeAnswer = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            label = { Text("Ta réponse finale") },
                            placeholder = {
                                Text("Écris uniquement le résultat final…")
                            },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        )'''
)

# 11. Rendu enrichi du retour de correction.
replace_exact(
    "retour correction",
    '''                        Text(
                            correction.optString("feedback"),
                            color = colors.onPrimaryContainer,
                            style = MaterialTheme.typography.bodyLarge
                        )''',
    '''                        RichText(
                            html = correction.optString("feedback"),
                            color = colors.onPrimaryContainer,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                        )'''
)

# 12. Rendu enrichi de l'explication.
replace_exact(
    "explication correction",
    '''                            Text(
                                details.optString("explanation"),
                                color = colors.onPrimaryContainer
                            )''',
    '''                            RichText(
                                html = details.optString("explanation"),
                                color = colors.onPrimaryContainer,
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                            )'''
)

# Toutes les vérifications ont réussi : sauvegarde puis écriture.
backup = TARGET.with_name(TARGET.name + ".v5.bak")
if backup.exists():
    sys.exit(f"ARRÊT : la sauvegarde existe déjà : {backup}")

backup.write_text(TARGET.read_text(encoding="utf-8"), encoding="utf-8")
TARGET.write_text(source, encoding="utf-8")

print("CORRECTIF V5 APPLIQUÉ.")
print(f"Sauvegarde : {backup}")
print("Rendu enrichi : énoncés, QCM, tableaux et corrections.")
print("Saisie séparée : démarche et réponse finale.")
