
from pathlib import Path
from datetime import datetime
import shutil
import sys

TARGET = Path(
    "app/src/main/java/ci/kalyno/app/ui/exercises/"
    "GeneratedExercisesScreen.kt"
)

if not TARGET.is_file():
    sys.exit("ARRÊT : fichier GeneratedExercisesScreen.kt introuvable.")

source = TARGET.read_text(encoding="utf-8")

# Vérifier que result est bien un JSONObject nullable.
declaration = (
    "var result by remember { mutableStateOf<JSONObject?>(null) }"
)

if source.count(declaration) != 1:
    sys.exit(
        "ARRÊT : déclaration exacte de result introuvable "
        "ou présente plusieurs fois. Aucun changement."
    )

# CORRECTIF 1 : supprimer l'accolade excédentaire.
wrong_braces = (
    "                    }\n"
    "                    }\n"
    "                }\n"
    "            }\n"
    "            Spacer(Modifier.height(8.dp))"
)

correct_braces = (
    "                    }\n"
    "                }\n"
    "            }\n"
    "            Spacer(Modifier.height(8.dp))"
)

if source.count(wrong_braces) != 1:
    sys.exit(
        "ARRÊT : bloc d'accolades attendu trouvé "
        f"{source.count(wrong_braces)} fois au lieu d'une. "
        "Aucun changement."
    )

# CORRECTIF 2 : déclarer correction à partir de result.
wrong_correction = (
    '            } else {\n'
    '                val verdict = correction.optString("verdict").lowercase()'
)

correct_correction = (
    '            } else {\n'
    '                val correction = result ?: JSONObject()\n'
    '                val verdict = correction.optString("verdict").lowercase()'
)

if source.count(wrong_correction) != 1:
    sys.exit(
        "ARRÊT : emplacement de déclaration de correction "
        f"trouvé {source.count(wrong_correction)} fois au lieu d'une. "
        "Aucun changement."
    )

# Préparer les deux changements en mémoire.
updated = source.replace(wrong_braces, correct_braces, 1)
updated = updated.replace(wrong_correction, correct_correction, 1)

# Vérifications avant écriture.
if updated.count(wrong_braces) != 0:
    sys.exit("ARRÊT : l'accolade excédentaire est toujours présente.")

if updated.count(correct_correction) != 1:
    sys.exit("ARRÊT : déclaration de correction incorrecte.")

if updated == source:
    sys.exit("ARRÊT : aucune modification détectée.")

# Sauvegarder l'original.
backup = TARGET.with_name(
    TARGET.name + ".backup_v8_" +
    datetime.now().strftime("%Y%m%d_%H%M%S")
)

shutil.copy2(TARGET, backup)

# Écrire uniquement après toutes les vérifications.
TARGET.write_text(updated, encoding="utf-8")

print("CORRECTIF V8 APPLIQUÉ.")
print(f"Sauvegarde : {backup}")
print("1. Accolade excédentaire supprimée.")
print("2. Déclaration de correction ajoutée.")
print("Vérifications internes : OK.")
