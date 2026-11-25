from pathlib import Path
from datetime import datetime
import shutil
import sys

TARGET = Path(
    "app/src/main/java/ci/kalyno/app/ui/exercises/"
    "GeneratedExercisesScreen.kt"
)

if not TARGET.exists():
    sys.exit("ARRÊT : fichier Kotlin introuvable.")

source = TARGET.read_text(encoding="utf-8")
updated = source

# CORRECTION 1 :
# La carte est déjà fermée à l'indentation 20.
# La boucle visible.forEachIndexed doit ensuite se fermer
# à l'indentation 16, et non par une deuxième accolade à 20.

wrong = (
    "                    }\n"
    "                    }\n"
    "                }\n"
    "            }\n"
    "            Spacer(Modifier.height(8.dp))"
)

correct = (
    "                    }\n"
    "                }\n"
    "            }\n"
    "            Spacer(Modifier.height(8.dp))"
)

count = updated.count(wrong)

if count != 1:
    sys.exit(
        f"ARRÊT : bloc d'accolades trouvé {count} fois "
        "au lieu d'une fois. Aucune modification."
    )

updated = updated.replace(wrong, correct, 1)

# CORRECTION 2 :
# La branche résultat utilise correction sans la déclarer.
# result contient le JSONObject retourné par la correction.

wrong_result = (
    "            } else {\n"
    "                val verdict = correction.optString(\"verdict\").lowercase()"
)

correct_result = (
    "            } else {\n"
    "                val correction = result ?: JSONObject()\n"
    "                val verdict = correction.optString(\"verdict\").lowercase()"
)

count = updated.count(wrong_result)

if count != 1:
    sys.exit(
        f"ARRÊT : branche résultat trouvée {count} fois "
        "au lieu d'une fois. Aucune modification."
    )

updated = updated.replace(wrong_result, correct_result, 1)

if updated == source:
    sys.exit("ARRÊT : aucune modification détectée.")

backup = TARGET.with_name(
    TARGET.name + ".backup_v7_" +
    datetime.now().strftime("%Y%m%d_%H%M%S")
)

shutil.copy2(TARGET, backup)
TARGET.write_text(updated, encoding="utf-8")

print("CORRECTIF V7 APPLIQUÉ.")
print(f"Sauvegarde : {backup}")
print(" - Fermeture de la boucle corrigée.")
print(" - Variable correction déclarée.")
