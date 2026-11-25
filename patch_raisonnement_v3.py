
from pathlib import Path
from datetime import datetime
import shutil
import sys

FILE = Path(
    "app/src/main/java/ci/kalyno/app/ui/exercises/"
    "GeneratedExercisesScreen.kt"
)

if not FILE.exists():
    sys.exit(f"ARRÊT : fichier introuvable : {FILE}")

source = FILE.read_text(encoding="utf-8")
updated = source

patches = [
    (
        "état des réponses",
        '''    var freeAnswer by remember { mutableStateOf("") }
    var tableAnswers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }''',
        '''    var freeAnswer by remember { mutableStateOf("") }
    var reasoningAnswer by remember { mutableStateOf("") }
    var tableAnswers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }'''
    ),
    (
        "réinitialisation des réponses",
        '''        freeAnswer = ""
        tableAnswers = emptyMap()''',
        '''        freeAnswer = ""
        reasoningAnswer = ""
        tableAnswers = emptyMap()'''
    ),
    (
        "envoi du raisonnement",
        '''            else -> JSONObject().put("text", freeAnswer.trim())''',
        '''            else -> JSONObject()
                .put("text", freeAnswer.trim())
                .put("reasoning", reasoningAnswer.trim())'''
    ),
    (
        "champ réel de réponse écrite",
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
                    }''',
        '''                    else -> {
                        OutlinedTextField(
                            value = reasoningAnswer,
                            onValueChange = { reasoningAnswer = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            label = { Text("Mon raisonnement (facultatif)") },
                            placeholder = {
                                Text("Explique comment tu as réfléchi...")
                            },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        )

                        OutlinedTextField(
                            value = freeAnswer,
                            onValueChange = { freeAnswer = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 5,
                            label = { Text("Ma réponse finale") },
                            placeholder = { Text("Écris ta réponse ici…") },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        )
                    }'''
    ),
    (
        "récupération de l'analyse",
        '''                val details = correction.optJSONObject("correction")''',
        '''                val details = correction.optJSONObject("correction")
                val reasoningAnalysis =
                    details?.optJSONObject("reasoning_analysis")'''
    ),
    (
        "affichage réel du feedback Gemini",
        '''                        Text(
                            correction.optString("feedback"),
                            color = colors.onPrimaryContainer,
                            style = MaterialTheme.typography.bodyLarge
                        )''',
        '''                        Text(
                            correction.optString("feedback"),
                            color = colors.onPrimaryContainer,
                            style = MaterialTheme.typography.bodyLarge
                        )

                        reasoningAnalysis?.let { analysis ->
                            val summary = analysis.optString("summary")
                            val steps = analysis.optJSONArray("steps")
                            val strengths = analysis.optJSONArray("strengths")
                            val errors = analysis.optJSONArray("errors")
                            val advice = analysis.optString("advice")

                            HorizontalDivider(
                                color = colors.onPrimaryContainer.copy(alpha = 0.2f)
                            )

                            Text(
                                "ANALYSE DE TON RAISONNEMENT",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.onPrimaryContainer
                            )

                            if (summary.isNotBlank()) {
                                Text(
                                    summary,
                                    color = colors.onPrimaryContainer
                                )
                            }

                            if (steps != null && steps.length() > 0) {
                                Text(
                                    "Étapes de ton raisonnement",
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onPrimaryContainer
                                )
                                for (i in 0 until steps.length()) {
                                    Text(
                                        "• ${steps.optString(i)}",
                                        color = colors.onPrimaryContainer
                                    )
                                }
                            }

                            if (strengths != null && strengths.length() > 0) {
                                Text(
                                    "Points forts",
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onPrimaryContainer
                                )
                                for (i in 0 until strengths.length()) {
                                    Text(
                                        "✓ ${strengths.optString(i)}",
                                        color = colors.onPrimaryContainer
                                    )
                                }
                            }

                            if (errors != null && errors.length() > 0) {
                                Text(
                                    "Points à corriger",
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onPrimaryContainer
                                )
                                for (i in 0 until errors.length()) {
                                    Text(
                                        "• ${errors.optString(i)}",
                                        color = colors.onPrimaryContainer
                                    )
                                }
                            }

                            if (advice.isNotBlank()) {
                                Text(
                                    "Conseil pour progresser",
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onPrimaryContainer
                                )
                                Text(
                                    advice,
                                    color = colors.onPrimaryContainer
                                )
                            }
                        }'''
    )
]

# Vérifier TOUS les emplacements avant la moindre modification.
errors = []

for name, old, new in patches:
    count = updated.count(old)
    if count != 1:
        errors.append((name, count, old))

if errors:
    print("ARRÊT : aucun changement enregistré.")
    for name, count, anchor in errors:
        print(f"\nBloc '{name}' trouvé {count} fois au lieu d'une fois.")
        print("Extrait recherché :")
        print(anchor[:500])
    sys.exit(1)

# Appliquer uniquement après validation de tous les blocs.
for name, old, new in patches:
    updated = updated.replace(old, new, 1)

if updated == source:
    sys.exit("ARRÊT : aucune différence détectée.")

backup = FILE.with_name(
    FILE.name + ".backup_" + datetime.now().strftime("%Y%m%d_%H%M%S")
)

shutil.copy2(FILE, backup)
FILE.write_text(updated, encoding="utf-8")

print("CORRECTIF APPLIQUÉ.")
print(f"Sauvegarde : {backup}")
print(f"Fichier modifié : {FILE}")
print(f"Blocs modifiés : {len(patches)}")
