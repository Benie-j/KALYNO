
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

old_validation = '''        if (kind == "free_text" && freeAnswer.isBlank()) {
            error = "Écris ta réponse avant de continuer."
            return
        }
'''

new_validation = '''        if (
            kind != "qcm" &&
            kind != "multiple_choice" &&
            kind != "table" &&
            freeAnswer.isBlank()
        ) {
            error = "Écris ta réponse avant de continuer."
            return
        }
'''

old_answer_field = '''                                        placeholder = {
                                            Text("Écris ta réponse ici…")
                                        },
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                                    )
'''

new_answer_field = '''                                        placeholder = {
                                            Text("Écris ta réponse ici…")
                                        },
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                                    )

                                    val wordCount = freeAnswer.trim()
                                        .split(Regex("\\\\s+"))
                                        .count { it.isNotBlank() }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "LONGUEUR DE TA RÉPONSE",
                                            color = colors.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Surface(
                                            color = colors.primaryContainer,
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
                                        ) {
                                            Text(
                                                "$wordCount mot(s)",
                                                Modifier.padding(
                                                    horizontal = 11.dp,
                                                    vertical = 6.dp
                                                ),
                                                color = colors.onPrimaryContainer,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }

                                    Text(
                                        "Privilégie une réponse claire, précise et bien structurée.",
                                        color = colors.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
'''

# Vérification complète avant modification
checks = [
    (old_validation, "validation des réponses écrites"),
    (old_answer_field, "champ de réponse finale")
]

for anchor, label in checks:
    count = source.count(anchor)
    if count != 1:
        stop(f"bloc '{label}' trouvé {count} fois au lieu d'une fois")

updated = source.replace(old_validation, new_validation, 1)
updated = updated.replace(old_answer_field, new_answer_field, 1)

backup = FILE.with_name(
    FILE.name + ".bak-" + datetime.now().strftime("%Y%m%d-%H%M%S")
)

backup.write_text(source, encoding="utf-8")
FILE.write_text(updated, encoding="utf-8")

print("CORRECTIF APPLIQUÉ.")
print(f"Sauvegarde : {backup}")
print("- Validation harmonisée des réponses écrites.")
print("- Compteur de mots actualisé pendant la rédaction.")
print("- Aucun changement au format envoyé à Supabase.")
