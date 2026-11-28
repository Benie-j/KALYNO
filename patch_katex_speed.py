
from pathlib import Path
from datetime import datetime
import shutil
import subprocess
import sys

ROOT = Path.home() / "KALYNO"

FILES = [
    "app/src/main/java/ci/kalyno/app/ui/explorer/RichText.kt",
    "app/src/main/java/ci/kalyno/app/ui/explorer/LessonBlocks.kt",
    "app/src/main/java/ci/kalyno/app/ui/explorer/tables/RichTable.kt",
]

def stop(message):
    print(f"\nARRÊT : {message}")
    sys.exit(1)

if not (ROOT / ".git").exists():
    stop(f"Dépôt Git introuvable : {ROOT}")

status = subprocess.run(
    ["git", "-C", str(ROOT), "status", "--porcelain"],
    capture_output=True, text=True, check=True
).stdout.strip()

if status:
    stop(
        "Le dépôt contient des modifications ou fichiers non suivis. "
        "Ne rien écraser : termine ou sauvegarde d'abord ces changements."
    )

contents = {}
for relative in FILES:
    path = ROOT / relative
    if not path.is_file():
        stop(f"Fichier introuvable : {relative}")
    contents[relative] = path.read_text(encoding="utf-8")

if "allowKatex = false" in contents[FILES[1]]:
    stop("Le correctif semble déjà appliqué dans LessonBlocks.kt.")

def replace_once(relative, old, new):
    content = contents[relative]
    count = content.count(old)
    if count != 1:
        stop(
            f"Ancre introuvable ou ambiguë dans {relative} "
            f"(occurrences trouvées : {count}). Aucun fichier modifié."
        )
    contents[relative] = content.replace(old, new, 1)

# 1. Conserver le rendu natif des tableaux même lorsque KaTeX est désactivé.
replace_once(
    FILES[0],
    """    if (allowKatex && containsTable(renderedHtml)) {
        RichTextWithTables(renderedHtml, color, fontSize, lineHeight, fontWeight, fontStyle)
        return
    }""",
    """    if (containsTable(renderedHtml)) {
        RichTextWithTables(
            renderedHtml, color, fontSize, lineHeight,
            fontWeight, fontStyle, allowKatex
        )
        return
    }"""
)

# 2. Désactiver KaTeX uniquement dans les paragraphes des leçons.
replace_once(
    FILES[1],
    """                fontStyle = FontStyle.Italic
            )""",
    """                fontStyle = FontStyle.Italic,
                allowKatex = false
            )"""
)

replace_once(
    FILES[1],
    """            RichText(it, KalynoText, 16.sp, 26.sp)""",
    """            RichText(
                it, KalynoText, 16.sp, 26.sp,
                allowKatex = false
            )"""
)

replace_once(
    FILES[1],
    """                    if (emphasizeBody) FontWeight.SemiBold else FontWeight.Normal
                )""",
    """                    if (emphasizeBody) FontWeight.SemiBold else FontWeight.Normal,
                    allowKatex = false
                )"""
)

# 3. Transmettre le choix du moteur aux segments HTML entourant les tableaux.
replace_once(
    FILES[2],
    """                is HtmlSegment.Html -> RichText(seg.html, color, fontSize, lineHeight, fontWeight, fontStyle)""",
    """                is HtmlSegment.Html -> RichText(
                    seg.html, color, fontSize, lineHeight,
                    fontWeight, fontStyle, allowKatex = allowKatex
                )"""
)

# Vérifications avant toute écriture.
for relative, content in contents.items():
    if not content.strip():
        stop(f"Le contenu de {relative} est vide.")

backup = Path.home() / "kalyno-backups" / (
    "katex-speed-" + datetime.now().strftime("%Y%m%d-%H%M%S")
)
backup.mkdir(parents=True, exist_ok=False)

for relative in FILES:
    source = ROOT / relative
    destination = backup / relative
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)

try:
    for relative, content in contents.items():
        (ROOT / relative).write_text(content, encoding="utf-8")
except Exception as error:
    print("Erreur pendant l'écriture :", error)
    print("Sauvegarde disponible :", backup)
    stop("Écriture interrompue. Restaure les fichiers depuis la sauvegarde.")

check = subprocess.run(
    ["git", "-C", str(ROOT), "diff", "--check"],
    capture_output=True, text=True
)

if check.returncode != 0:
    print(check.stdout)
    print(check.stderr)
    print("Sauvegarde :", backup)
    stop("git diff --check a détecté un problème.")

print("\nCORRECTIF APPLIQUÉ")
print("Sauvegarde :", backup)
print("\nFichiers modifiés :")
for relative in FILES:
    print(" -", relative)

print("\nRésumé Git :")
subprocess.run(["git", "-C", str(ROOT), "diff", "--stat"])
print("\nVérification des espaces : OK")
print("Aucun commit ni push automatique.")
