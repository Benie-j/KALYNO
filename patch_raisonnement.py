
from pathlib import Path
from datetime import datetime
import sys

FILE = Path(
    "app/src/main/java/ci/kalyno/app/ui/exercises/"
    "GeneratedExercisesScreen.kt"
)

if not FILE.is_file():
    sys.exit(f"ARRÊT : fichier introuvable : {FILE}")

source = FILE.read_text(encoding="utf-8")
modified = source

def replace_once(old, new, label):
    global modified
    count = modified.count(old)
    if count != 1:
        sys.exit(
            f"ARRÊT : bloc '{label}' trouvé {count} fois "
            "au lieu d'une fois.\nAucune modification enregistrée."
        )
    modified = modified.replace(old, new, 1)

# 1. État du raisonnement
replace_once(
    '    var freeAnswer by remember { mutableStateOf("") }\n',
    '    var freeAnswer by remember { mutableStateOf("") }\n'
    '    var reasoningAnswer by remember { mutableStateOf("") }\n',
    "état de la réponse"
)

# 2. Réinitialisation à l'ouverture d'un exercice
replace_once(
    '        freeAnswer = ""\n'
    '        tableAnswers = emptyMap()\n',
    '        freeAnswer = ""\n'
    '        reasoningAnswer = ""\n'
    '        tableAnswers = emptyMap()\n',
    "réinitialisation de la réponse"
)

# 3. Transmission du raisonnement au serveur
replace_once(
    '            else -> JSONObject().put("text", freeAnswer.trim())\n',
    '            else -> JSONObject()\n'
    '                .put("text", freeAnswer.trim())\n'
    '                .put("reasoning", reasoningAnswer.trim())\n',
    "transmission du raisonnement"
)

# 4. Deux zones distinctes : raisonnement et réponse finale
replace_once(
    '                    else -> {\n'
    '                        OutlinedTextField(\n'
    '                            value = freeAnswer,\n',
    '                    else -> {\n'
    '                        OutlinedTextField(\n'
    '                            value = reasoningAnswer,\n'
    '                            onValueChange = { reasoningAnswer = it },\n'
    '                            modifier = Modifier.fillMaxWidth(),\n'
    '                            minLines = 4,\n'
    '                            label = { Text("Mon raisonnement") },\n'
    '                            placeholder = {\n'
    '                                Text("Explique les étapes de ta réflexion...")\n'
    '                            },\n'
    '                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)\n'
    '                        )\n'
    '\n'
    '                        OutlinedTextField(\n'
    '                            value = freeAnswer,\n',
    "zone de raisonnement"
)

replace_once(
    '                            label = { Text("Ta réponse") },\n'
    '                            placeholder = { Text("Écris ici…") },\n',
    '                            label = { Text("Ma réponse finale") },\n'
    '                            placeholder = { Text("Donne ta réponse finale ici…") },\n',
    "libellé de la réponse finale"
)

# 5. Récupération et affichage de l'analyse Gemini
replace_once(
    '                val details = correction.optJSONObject("correction")\n',
    '                val details = correction.optJSONObject("correction")\n'
    '                val reasoningAnalysis = correction.optJSONObject("reasoning_analysis")\n',
    "récupération de l'analyse"
)

# 6. Insérer l'analyse après le feedback général
replace_once(
    '                        Text(\n'
    '                            correction.optString("feedback"),\n'
    '                            color = colors.onPrimaryContainer,\n'
    '                            style = MaterialTheme.typography.bodyLarge\n'
    '                        )\n',
    '                        Text(\n'
    '                            correction.optString("feedback"),\n'
    '                            color = colors.onPrimaryContainer,\n'
    '                            style = MaterialTheme.typography.bodyLarge\n'
    '                        )\n'
    '\n'
    '                        if (reasoningAnalysis != null) {\n'
    '                            HorizontalDivider(\n'
    '                                color = colors.onPrimaryContainer.copy(alpha = 0.2f)\n'
    '                            )\n'
    '                            Text(\n'
    '                                "ANALYSE DE TON RAISONNEMENT",\n'
    '                                style = MaterialTheme.typography.titleMedium,\n'
    '                                color = colors.onPrimaryContainer,\n'
    '                                fontWeight = FontWeight.Bold\n'
    '                            )\n'
    '\n'
    '                            val summary = reasoningAnalysis.optString("summary")\n'
    '                            if (summary.isNotBlank()) {\n'
    '                                Text(summary, color = colors.onPrimaryContainer)\n'
    '                            }\n'
    '\n'
    '                            val steps = reasoningAnalysis.optJSONArray("steps")\n'
    '                            if (steps != null && steps.length() > 0) {\n'
    '                                Text("LES ÉTAPES", fontWeight = FontWeight.Bold,\n'
    '                                    color = colors.onPrimaryContainer)\n'
    '                                for (i in 0 until steps.length()) {\n'
    '                                    Text("${i + 1}. ${steps.optString(i)}",\n'
    '                                        color = colors.onPrimaryContainer)\n'
    '                                }\n'
    '                            }\n'
    '\n'
    '                            val strengths = reasoningAnalysis.optJSONArray("strengths")\n'
    '                            if (strengths != null && strengths.length() > 0) {\n'
    '                                Text("TES POINTS FORTS", fontWeight = FontWeight.Bold,\n'
    '                                    color = colors.onPrimaryContainer)\n'
    '                                for (i in 0 until strengths.length()) {\n'
    '                                    Text("• ${strengths.optString(i)}",\n'
    '                                        color = colors.onPrimaryContainer)\n'
    '                                }\n'
    '                            }\n'
    '\n'
    '                            val errors = reasoningAnalysis.optJSONArray("errors")\n'
    '                            if (errors != null && errors.length() > 0) {\n'
    '                                Text("POINTS À CORRIGER", fontWeight = FontWeight.Bold,\n'
    '                                    color = colors.onPrimaryContainer)\n'
    '                                for (i in 0 until errors.length()) {\n'
    '                                    Text("• ${errors.optString(i)}",\n'
    '                                        color = colors.onPrimaryContainer)\n'
    '                                }\n'
    '                            }\n'
    '\n'
    '                            val advice = reasoningAnalysis.optString("advice")\n'
    '                            if (advice.isNotBlank()) {\n'
    '                                Text("POUR PROGRESSER", fontWeight = FontWeight.Bold,\n'
    '                                    color = colors.onPrimaryContainer)\n'
    '                                Text(advice, color = colors.onPrimaryContainer)\n'
    '                            }\n'
    '                        }\n',
    "affichage de l'analyse Gemini"
)

# Toutes les validations sont terminées. Sauvegarde puis écriture.
backup = FILE.with_name(
    FILE.name + ".backup_" + datetime.now().strftime("%Y%m%d_%H%M%S")
)

backup.write_text(source, encoding="utf-8")
FILE.write_text(modified, encoding="utf-8")

print("CORRECTIF APPLIQUÉ.")
print(f"Sauvegarde : {backup}")
print(f"Fichier modifié : {FILE}")
print("Vérification : les 7 points d'insertion ont été validés.")
