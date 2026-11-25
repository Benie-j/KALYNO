from pathlib import Path
import sys

target = Path(
    "app/src/main/java/ci/kalyno/app/ui/exercises/GeneratedExercisesScreen.kt"
)

if not target.exists():
    sys.exit("ERREUR : fichier GeneratedExercisesScreen.kt introuvable.")

source = target.read_text(encoding="utf-8")

replacements = [
(
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
),
(
'''            Text(
                exercise.optString("title"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )''',
'''            RichText(
                html = exercise.optString("title"),
                color = colors.onBackground,
                fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                lineHeight = MaterialTheme.typography.headlineSmall.lineHeight,
                fontWeight = FontWeight.ExtraBold
            )'''
),
(
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
),
(
'''                                    Text(
                                        choices.optString(i),
                                        Modifier.padding(vertical = 8.dp),
                                        color = colors.onSurface
                                    )''',
'''                                    Box(Modifier.padding(vertical = 8.dp)) {
                                        RichText(
                                            html = choices.optString(i),
                                            color = colors.onSurface,
                                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                                        )
                                    }'''
),
(
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
),
(
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
),
(
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
]

# Vérification complète avant toute modification.
for index, (old, new) in enumerate(replacements, 1):
    count = source.count(old)
    if count != 1:
        sys.exit(
            f"ARRÊT : remplacement {index} attendu une fois, trouvé {count}. "
            "Aucun fichier modifié."
        )

updated = source

for old, new in replacements:
    updated = updated.replace(old, new, 1)

import_line = "import ci.kalyno.app.ui.explorer.RichText"

if import_line not in updated:
    updated = updated.replace(
        "package ci.kalyno.app.ui.exercises\n",
        "package ci.kalyno.app.ui.exercises\n\n" + import_line,
        1
    )

backup = target.with_suffix(".kt.before-math")
if not backup.exists():
    backup.write_text(source, encoding="utf-8")

target.write_text(updated, encoding="utf-8")

print("CORRECTIF APPLIQUÉ")
print("7 zones raccordées au moteur mathématique.")
print(f"Sauvegarde : {backup}")
print("Aucun changement apporté à Supabase ou à l'API.")
