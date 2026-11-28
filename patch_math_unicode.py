from pathlib import Path
from datetime import datetime
import subprocess
import shutil
import sys
import unicodedata
import re

ROOT = Path.home() / "KALYNO"
REL = Path("app/src/main/java/ci/kalyno/app/ui/explorer/RichText.kt")
FILE = ROOT / REL

def stop(message):
    sys.exit("\nARRÊT : " + message)

if not (ROOT / ".git").is_dir():
    stop("Dépôt Git introuvable.")

status = subprocess.run(
    ["git", "-C", str(ROOT), "status", "--porcelain"],
    capture_output=True, text=True, check=True
).stdout.strip()

if status:
    stop("Le dépôt contient des modifications. Aucun fichier ne sera écrasé.")

if not FILE.is_file():
    stop("RichText.kt introuvable.")

source = FILE.read_text(encoding="utf-8")

old = r'''private fun normalizeTex(tex: String): String = tex
    .replace('\u00A0', ' ').replace("\u200B", "")
    .replace('\u2212', '-')
    .replace("\u25A2", "")
    .trim()'''

new = r'''private val DUPLICATED_TEX_COMMAND_RE =
    Regex("""\\\\(?=(?:dfrac|tfrac|frac|left|right)\b)""")

private fun normalizeTex(tex: String): String {
    // Convertit les lettres mathématiques Unicode (𝑎, 𝑏, 𝑥...) en lettres compatibles LaTeX.
    val compatible = java.text.Normalizer.normalize(
        tex,
        java.text.Normalizer.Form.NFKC
    )

    // Répare les commandes enregistrées avec deux antislashs.
    return DUPLICATED_TEX_COMMAND_RE.replace(compatible) { "\\" }
        .replace('\u00A0', ' ')
        .replace("\u200B", "")
        .replace('\u2212', '-')
        .replace("\u25A2", "")
        .trim()
}'''

count = source.count(old)
if count != 1:
    stop(f"Fonction normalizeTex inattendue ({count} occurrence(s)).")

if "DUPLICATED_TEX_COMMAND_RE" in source:
    stop("Le correctif semble déjà présent.")

# Vérifications préalables de la logique.
assert unicodedata.normalize("NFKC", "𝑎𝑏𝑥𝑦") == "abxy"
assert re.sub(r"\\\\(?=(?:dfrac|tfrac|frac|left|right)\b)",
              lambda _: "\\", r"\\dfrac{1}{𝑎}") == r"\dfrac{1}{𝑎}"

backup = ROOT / "kalyno-backups" / (
    "math-unicode-" + datetime.now().strftime("%Y%m%d-%H%M%S")
)
backup.mkdir(parents=True, exist_ok=False)
destination = backup / REL
destination.parent.mkdir(parents=True, exist_ok=True)
shutil.copy2(FILE, destination)

updated = source.replace(old, new, 1)
FILE.write_text(updated, encoding="utf-8")

check = subprocess.run(
    ["git", "-C", str(ROOT), "diff", "--check"],
    capture_output=True, text=True
)

if check.returncode != 0:
    shutil.copy2(destination, FILE)
    stop("git diff --check a échoué. Le fichier original a été restauré.")

if "java.text.Normalizer.Form.NFKC" not in updated:
    shutil.copy2(destination, FILE)
    stop("Vérification NFKC échouée. Fichier restauré.")

if 'DUPLICATED_TEX_COMMAND_RE.replace(compatible) { "\\" }' not in updated:
    shutil.copy2(destination, FILE)
    stop("Vérification des antislashs échouée. Fichier restauré.")

print("\nCORRECTIF APPLIQUÉ")
print("Fichier :", REL)
print("Sauvegarde :", backup)
print("\nDifférence :")
subprocess.run(["git", "-C", str(ROOT), "diff", "--", str(REL)])
print("\nAucun commit ni push automatique.")
