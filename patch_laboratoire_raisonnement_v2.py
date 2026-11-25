
from pathlib import Path
from datetime import datetime
import shutil
import sys

FILE = Path(
    "app/src/main/java/ci/kalyno/app/ui/exercises/"
    "GeneratedExercisesScreen.kt"
)

def stop(message):
    sys.exit("ARRÊT : " + message + "\nAucune modification enregistrée.")

if not FILE.is_file():
    stop(f"fichier introuvable : {FILE}")

original = FILE.read_text(encoding="utf-8")
source = original

def replace_once(old, new, label):
    global source
    count = source.count(old)
    if count != 1:
        stop(
            f"bloc '{label}' trouvé {count} fois au lieu d'une fois."
        )
    source = source.replace(old, new, 1)

# 1. État de la démarche
replace_once(
'''    var freeAnswer by remember { mutableStateOf("") }
    var tableAnswers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }''',
'''    var freeAnswer by remember { mutableStateOf("") }
    var reasoningDraft by remember { mutableStateOf("") }
    var tableAnswers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }''',
"état des réponses"
)

# 2. Réinitialisation à l'ouverture d'un exercice
replace_once(
'''        freeAnswer = ""
        tableAnswers = emptyMap()''',
'''        freeAnswer = ""
        reasoningDraft = ""
        tableAnswers = emptyMap()''',
"réinitialisation des réponses"
)

# 3. Envoi séparé de la démarche et de la réponse finale
replace_once(
'''            else -> JSONObject().put("text", freeAnswer.trim())''',
'''            else -> JSONObject()
                .put("text", freeAnswer.trim())
                .put("reasoning", reasoningDraft.trim())''',
"construction de la réponse libre"
)

# 4. Validation de la réponse finale
replace_once(
'''            error = "Écris ta réponse avant de continuer."''',
'''            error = "Écris ta réponse finale avant de continuer."''',
"message de validation"
)

# 5. Remplacement du champ de réponse libre
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
                    }''',
'''                    else -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "01 / MA DÉMARCHE",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.primary,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                "Explique les étapes de ton raisonnement, " +
                                    "tes calculs et tes justifications.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = reasoningDraft,
                                onValueChange = { reasoningDraft = it },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 5,
                                label = {
                                    Text("Comment arrives-tu à cette solution ?")
                                },
                                placeholder = {
                                    Text("Étape 1…\\nÉtape 2…\\nPourquoi ?")
                                },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                            )

                            Text(
                                "02 / MA RÉPONSE FINALE",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.primary,
                                fontWeight = FontWeight.Bold
                            )

                            OutlinedTextField(
                                value = freeAnswer,
                                onValueChange = { freeAnswer = it },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                label = { Text("Ta réponse finale") },
                                placeholder = {
                                    Text("Écris ta réponse ici…")
                                },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                            )
                        }
                    }''',
"interface de réponse libre"
)

# 6. Analyse du raisonnement dans la correction.
# Dans le vrai fichier, details désigne correction.optJSONObject("correction").
replace_once(
'''                        Text(
                            correction.optString("feedback"),
                            color = colors.onPrimaryContainer,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (details != null && details.optString("explanation").isNotBlank()) {''',
'''                        Text(
                            correction.optString("feedback"),
                            color = colors.onPrimaryContainer,
                            style = MaterialTheme.typography.bodyLarge
                        )

                        details?.optJSONObject("reasoning_analysis")?.let { analysis ->
                            HorizontalDivider(
                                color = colors.onPrimaryContainer.copy(alpha = 0.2f)
                            )

                            Text(
                                "ANALYSE DE TON RAISONNEMENT",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )

                            val summary = analysis.optString("summary")
                            if (summary.isNotBlank()) {
                                Text(
                                    summary,
                                    color = colors.onPrimaryContainer,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            val steps = analysis.optJSONArray("steps")
                            if (steps != null && steps.length() > 0) {
                                Text(
                                    "ÉTAPES ANALYSÉES",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                for (i in 0 until steps.length()) {
                                    val item = steps.optString(i)
                                    if (item.isNotBlank()) {
                                        Text(
                                            "• $item",
                                            color = colors.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            val strengths = analysis.optJSONArray("strengths")
                            if (strengths != null && strengths.length() > 0) {
                                Text(
                                    "POINTS MAÎTRISÉS",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                for (i in 0 until strengths.length()) {
                                    val item = strengths.optString(i)
                                    if (item.isNotBlank()) {
                                        Text(
                                            "• $item",
                                            color = colors.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            val errors = analysis.optJSONArray("errors")
                            if (errors != null && errors.length() > 0) {
                                Text(
                                    "ERREURS À CORRIGER",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                for (i in 0 until errors.length()) {
                                    val item = errors.optString(i)
                                    if (item.isNotBlank()) {
                                        Text(
                                            "• $item",
                                            color = colors.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            val advice = analysis.optString("advice")
                            if (advice.isNotBlank()) {
                                Text(
                                    "CONSEIL POUR PROGRESSER",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    advice,
                                    color = colors.onPrimaryContainer,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        if (details != null && details.optString("explanation").isNotBlank()) {''',
"emplacement réel de l'analyse de correction"
)

if source == original:
    stop("aucune modification détectée.")

# Contrôles simples avant écriture
required = [
    "var reasoningDraft by remember",
    '.put("reasoning", reasoningDraft.trim())',
    "ANALYSE DE TON RAISONNEMENT",
    "reasoning_analysis"
]
for token in required:
    if token not in source:
        stop(f"vérification finale échouée : {token}")

backup = FILE.with_name(
    FILE.name + ".backup_" + datetime.now().strftime("%Y%m%d_%H%M%S")
)

shutil.copy2(FILE, backup)
FILE.write_text(source, encoding="utf-8")

print("CORRECTIF APPLIQUÉ AVEC SUCCÈS.")
print("Fichier :", FILE)
print("Sauvegarde :", backup)
print("6 blocs contrôlés et modifiés.")
