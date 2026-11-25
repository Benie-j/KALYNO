from pathlib import Path
from datetime import datetime
import shutil
import sys

TARGET = Path(
    "app/src/main/java/ci/kalyno/app/ui/exercises/"
    "GeneratedExercisesScreen.kt"
)

if not TARGET.is_file():
    sys.exit(f"ARRÊT : fichier introuvable : {TARGET}")

source = TARGET.read_text(encoding="utf-8")
updated = source

# 1. Retirer uniquement l'accolade surnuméraire
# située après la fermeture de la carte de la liste.
bad_braces = (
    "                    }\n"
    "                    }\n"
    "                }\n"
    "            }\n"
            "            Spacer(Modifier.height(8.dp))"
)

good_braces = (
    "                    }\n"
    "                }\n"
    "            }\n"
    "            Spacer(Modifier.height(8.dp))"
)

brace_count = updated.count(bad_braces)

if brace_count != 1:
    sys.exit(
        "ARRÊT : bloc d'accolades trouvé "
        f"{brace_count} fois au lieu d'une fois. "
        "Aucune modification enregistrée."
    )

updated = updated.replace(bad_braces, good_braces, 1)

# 2. Déclarer le JSON de correction dans la branche résultat.
result_anchor = (
    "            } else {\n"
    "                val verdict = correction.optString(\"verdict\").lowercase()"
)

result_replacement = (
    "            } else {\n"
    "                val correction = result ?: JSONObject()\n"
    "                val verdict = correction.optString(\"verdict\").lowercase()"
)

result_count = updated.count(result_anchor)

if result_count != 1:
    sys.exit(
        "ARRÊT : branche d'affichage du résultat trouvée "
        f"{result_count} fois au lieu d'une fois. "
        "Aucune modification enregistrée."
    )

updated = updated.replace(result_anchor, result_replacement, 1)

# Vérification finale avant toute écriture.
if updated == source:
    sys.exit("ARRÊT : aucune différence détectée.")

backup = TARGET.with_name(
    TARGET.name + ".backup_" +
    datetime.now().strftime("%Y%m%d_%H%M%S")
)

shutil.copy2(TARGET, backup)
TARGET.write_text(updated, encoding="utf-8")

print("CORRECTIF V6 APPLIQUÉ")
print(f"Sauvegarde : {backup}")
print("1. Accolade surnuméraire retirée.")
print("2. Variable correction déclarée depuis result.")
print("Aucune autre logique modifiée.")
