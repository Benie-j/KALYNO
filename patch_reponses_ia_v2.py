
#!/usr/bin/env python3
# KALYNO — Correctif 2 : expérience de réponse IA
# Modifie uniquement la zone de saisie des réponses.
# Ne modifie ni l'API, ni le format des réponses envoyées.

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
        "Lance le script depuis la racine de KALYNO."
    )

source = TARGET.read_text(encoding="utf-8")

start = "            if (result == null) {"

end = """            } else {
                val correction = result!!"""

if source.count(start) != 1:
    sys.exit(
        "ARRÊT : début de la zone de réponse trouvé "
        f"{source.count(start)} fois au lieu d'une.\n"
        "Aucune modification enregistrée."
    )

if source.count(end) != 1:
    sys.exit(
        "ARRÊT : début de la zone de résultat trouvé "
        f"{source.count(end)} fois au lieu d'une.\n"
        "Aucune modification enregistrée."
    )

a = source.index(start)
b = source.index(end, a)

if b <= a:
    sys.exit(
        "ARRÊT : ordre des repères incorrect.\n"
        "Aucune modification enregistrée."
    )

replacement = r'''            if (result == null) {

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = colors.surface
                    ),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(17.dp),
                        verticalArrangement = Arrangement.spacedBy(15.dp)
                    ) {

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "TA MISSION",
                                    color = colors.primary,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold
                                )

                                Text(
                                    when {
                                        isQcm -> "Fais ton choix"
                                        kind == "table" -> "Complète le tableau"
                                        else -> "Construis ta réponse"
                                    },
                                    color = colors.onSurface,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            Surface(
                                color = colors.primaryContainer,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    when {
                                        isQcm -> "QCM"
                                        kind == "table" -> "TABLEAU"
                                        else -> "RÉDACTION"
                                    },
                                    Modifier.padding(
                                        horizontal = 11.dp,
                                        vertical = 7.dp
                                    ),
                                    color = colors.onPrimaryContainer,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        HorizontalDivider(color = colors.outlineVariant)

                        when {
                            isQcm -> {
                                val choices =
                                    payload.optJSONArray("choices") ?: JSONArray()

                                Text(
                                    "Sélectionne la ou les propositions correctes.",
                                    color = colors.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "TES SÉLECTIONS",
                                        color = colors.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Text(
                                        "${selectedChoices.size} sélectionnée(s)",
                                        color = colors.primary,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                for (i in 0 until choices.length()) {
                                    val checked = i in selectedChoices

                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            selectedChoices =
                                                if (checked) selectedChoices - i
                                                else selectedChoices + i
                                        },
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(17.dp),
                                        color = if (checked)
                                            colors.secondaryContainer
                                        else
                                            colors.surfaceVariant.copy(alpha = 0.35f),
                                        border = androidx.compose.foundation.BorderStroke(
                                            if (checked) 1.5.dp else 1.dp,
                                            if (checked) colors.primary
                                            else colors.outlineVariant
                                        )
                                    ) {
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(
                                                    horizontal = 12.dp,
                                                    vertical = 10.dp
                                                ),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Surface(
                                                color = if (checked)
                                                    colors.primary
                                                else
                                                    colors.surface,
                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                                                border = if (checked) null
                                                else androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    colors.outlineVariant
                                                )
                                            ) {
                                                Text(
                                                    "${i + 1}",
                                                    Modifier.padding(
                                                        horizontal = 11.dp,
                                                        vertical = 8.dp
                                                    ),
                                                    color = if (checked)
                                                        colors.onPrimary
                                                    else
                                                        colors.onSurface,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }

                                            Box(Modifier.weight(1f)) {
                                                RichText(
                                                    html = choices.optString(i),
                                                    color = colors.onSurface,
                                                    fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                                                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                                                )
                                            }

                                            Checkbox(
                                                checked = checked,
                                                onCheckedChange = { value ->
                                                    selectedChoices =
                                                        if (value) selectedChoices + i
                                                        else selectedChoices - i
                                                }
                                            )
                                        }
                                    }
                                }

                                if (choices.length() == 0) {
                                    Text(
                                        "Aucune proposition disponible pour cet exercice.",
                                        color = colors.error,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            kind == "table" -> {
                                val front =
                                    payload.optJSONObject("table_front") ?: JSONObject()
                                val rows =
                                    front.optJSONArray("rows") ?: JSONArray()

                                Text(
                                    "Renseigne les cellules manquantes pour compléter le tableau.",
                                    color = colors.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                for (r in 0 until rows.length()) {
                                    val row = rows.optJSONArray(r) ?: JSONArray()

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(15.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = colors.surfaceVariant.copy(alpha = 0.35f)
                                        )
                                    ) {
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(7.dp),
                                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                                        ) {
                                            for (c in 0 until row.length()) {
                                                val cell =
                                                    row.optJSONObject(c) ?: JSONObject()

                                                if (cell.optString("type") == "blank") {
                                                    val key = cell.optString("key")

                                                    OutlinedTextField(
                                                        value = tableAnswers[key] ?: "",
                                                        onValueChange = {
                                                            tableAnswers =
                                                                tableAnswers + (key to it)
                                                        },
                                                        label = {
                                                            Text("Réponse")
                                                        },
                                                        placeholder = {
                                                            Text("À compléter")
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        singleLine = true,
                                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                                                    )
                                                } else {
                                                    Surface(
                                                        modifier = Modifier.weight(1f),
                                                        color = colors.surface,
                                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                                        border = androidx.compose.foundation.BorderStroke(
                                                            1.dp,
                                                            colors.outlineVariant
                                                        )
                                                    ) {
                                                        Box(
                                                            Modifier.padding(12.dp),
                                                            contentAlignment = Alignment.CenterStart
                                                        ) {
                                                            RichText(
                                                                html = cell.optString("value"),
                                                                color = colors.onSurface,
                                                                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                if (rows.length() == 0) {
                                    Text(
                                        "Le tableau ne contient aucune ligne.",
                                        color = colors.error,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            else -> {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "1. TON RAISONNEMENT",
                                        color = colors.primary,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold
                                    )

                                    Text(
                                        "Explique les étapes de ta réflexion. Cette partie aide à comprendre ta méthode.",
                                        color = colors.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )

                                    OutlinedTextField(
                                        value = reasoningAnswer,
                                        onValueChange = {
                                            reasoningAnswer = it
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 3,
                                        placeholder = {
                                            Text("Comment as-tu procédé ?")
                                        },
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                                    )
                                }

                                HorizontalDivider(color = colors.outlineVariant)

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "2. TA RÉPONSE FINALE",
                                        color = colors.primary,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold
                                    )

                                    Text(
                                        "Présente clairement le résultat auquel tu es arrivé.",
                                        color = colors.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )

                                    OutlinedTextField(
                                        value = freeAnswer,
                                        onValueChange = {
                                            freeAnswer = it
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 5,
                                        placeholder = {
                                            Text("Écris ta réponse ici…")
                                        },
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                error?.let {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.errorContainer,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            Modifier.padding(13.dp),
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "!",
                                color = colors.onErrorContainer,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                it,
                                color = colors.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Button(
                    onClick = { sendAnswer() },
                    enabled = !loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(17.dp)
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(21.dp),
                            color = colors.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Analyse en cours…")
                    } else {
                        Text(
                            "Vérifier ma réponse  →",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    "Prends ton temps : ta réponse sera analysée après validation.",
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )

            } else {'''

new_source = (
    source[:a]
    + replacement
    + source[b + len(end):]
)

if new_source == source:
    sys.exit("ARRÊT : aucun changement produit.")

backup = TARGET.with_name(
    TARGET.name + ".bak-" +
    datetime.now().strftime("%Y%m%d-%H%M%S")
)

backup.write_text(source, encoding="utf-8")
TARGET.write_text(new_source, encoding="utf-8")

print("CORRECTIF 2 APPLIQUÉ.")
print(f"Sauvegarde : {backup}")
print(f"Fichier modifié : {TARGET}")
print("Zone modifiée : formulaire de réponse uniquement.")
print("Format des réponses et appel Gemini conservés.")
