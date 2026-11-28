from pathlib import Path
from datetime import datetime
import subprocess
import shutil
import re
import unicodedata
import sys

ROOT = Path.home() / "KALYNO"
REL = Path("app/src/main/java/ci/kalyno/app/ui/explorer/RichText.kt")
FILE = ROOT / REL

def stop(message):
    sys.exit("ARRÊT : " + message)

if not (ROOT / ".git").is_dir():
    stop("Dépôt Git introuvable.")

status = subprocess.run(
    ["git", "-C", str(ROOT), "status", "--porcelain"],
    capture_output=True, text=True, check=True
).stdout.strip()

if status:
    stop("Modifications Git existantes. Aucun fichier modifié.")

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
    val compatible = java.text.Normalizer.normalize(
        tex,
        java.text.Normalizer.Form.NFKC
    )

    return DUPLICATED_TEX_COMMAND_RE.replace(compatible) { "\\" }
        .replace('\u00A0', ' ')
        .replace("\u200B", "")
        .replace('\u2212', '-')
        .replace("\u25A2", "")
        .trim()
}'''

if source.count(old) != 1:
    stop("La fonction normalizeTex ne correspond pas exactement au code attendu.")

if "DUPLICATED_TEX_COMMAND_RE" in source:
    stop("Le correctif semble déjà présent.")

# Tests réels de la logique avant modification.
assert unicodedata.normalize("NFKC", "𝑎𝑏𝑥𝑦") == "abxy"

pattern = re.compile(r"\\\\(?=(?:dfrac|tfrac|frac|left|right)\b)")
sample = r"\\dfrac{1}{𝑎}"
result = pattern.sub(lambda _: "\\", sample)
assert result == r"\dfrac{1}{𝑎}", repr(result)

backup = ROOT / "kalyno-backups" / (
    "math-unicode-" + datetime.now().strftime("%Y%m%d-%H%M%S")
)
backup.mkdir(parents=True, exist_ok=False)
saved = backup / REL
saved.parent.mkdir(parents=True, exist_ok=True)
shutil.copy2(FILE, saved)

try:
    FILE.write_text(source.replace(old, new, 1), encoding="utf-8")

    updated = FILE.read_text(encoding="utf-8")
    if updated.count("private fun normalizeTex(tex: String): String {") != 1:
        raise RuntimeError("Fonction normalisée absente ou dupliquée.")

    check = subprocess.run(
        ["git", "-C", str(ROOT), "diff", "--check"],
        capture_output=True, text=True
    )
    if check.returncode != 0:
        raise RuntimeError(check.stdout + check.stderr)

except Exception as exc:
    shutil.copy2(saved, FILE)
    stop(f"{exc}. Fichier original restauré.")

print("CORRECTIF APPLIQUÉ")
print("Fichier :", REL)
print("Sauvegarde :", saved)
print("\nDIFF :")
subprocess.run(["git", "-C", str(ROOT), "diff", "--", str(REL)])
print("\nAucun commit ni push effectué.")
