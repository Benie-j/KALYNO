
from pathlib import Path
from datetime import datetime
import shutil
import subprocess
import sys

root = Path.home() / "KALYNO"

files = {
    "app/src/main/java/ci/kalyno/app/ui/explorer/tables/RichTable.kt": [
        (
            """    fontWeight: FontWeight,
    fontStyle: FontStyle
) {""",
            """    fontWeight: FontWeight,
    fontStyle: FontStyle,
    allowKatex: Boolean = true
) {"""
        )
    ]
}

rich = root / "app/src/main/java/ci/kalyno/app/ui/explorer/RichText.kt"
table = root / "app/src/main/java/ci/kalyno/app/ui/explorer/tables/RichTable.kt"

if not (root / ".git").exists():
    sys.exit("Dépôt KALYNO introuvable.")

targets = [rich, table]
originals = {}

for p in targets:
    if not p.is_file():
        sys.exit(f"Fichier absent : {p}")
    originals[p] = p.read_text(encoding="utf-8")

# Vérifier les ancres avant toute modification.
old_call = """            renderedHtml, color, fontSize, lineHeight,
            fontWeight, fontStyle, allowKatex
        )"""
new_call = """            renderedHtml, color, fontSize, lineHeight,
            fontWeight, fontStyle
        )"""

old_signature = """    fontWeight: FontWeight,
    fontStyle: FontStyle
) {"""
new_signature = """    fontWeight: FontWeight,
    fontStyle: FontStyle,
    allowKatex: Boolean = true
) {"""

old_segment = """                is HtmlSegment.Html -> RichText(
                    seg.html, color, fontSize, lineHeight,
                    fontWeight, fontStyle, allowKatex = allowKatex
                )"""

checks = [
    (rich, old_call, new_call),
    (table, old_signature, new_signature),
]

for path, old, new in checks:
    if originals[path].count(old) != 1:
        sys.exit(
            f"Structure inattendue dans {path}. "
            "Aucune modification effectuée."
        )

if originals[table].count(old_segment) != 1:
    sys.exit("Appel RichText des segments HTML inattendu.")

# Sauvegarde.
backup = Path.home() / "kalyno-backups" / (
    "fix-katex-build-" + datetime.now().strftime("%Y%m%d-%H%M%S")
)
backup.mkdir(parents=True, exist_ok=False)

for p in targets:
    dest = backup / p.relative_to(root)
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(p, dest)

# Correctifs.
updated_rich = originals[rich].replace(old_call, new_call, 1)
updated_table = originals[table].replace(old_signature, new_signature, 1)

updated_table = updated_table.replace(
    old_segment,
    old_segment,
    1
)

# Écriture.
rich.write_text(updated_rich, encoding="utf-8")
table.write_text(updated_table, encoding="utf-8")

check = subprocess.run(
    ["git", "-C", str(root), "diff", "--check"],
    capture_output=True,
    text=True
)

if check.returncode != 0:
    print(check.stdout, check.stderr)
    sys.exit("Échec de git diff --check. Sauvegarde : " + str(backup))

print("Correctif appliqué.")
print("Sauvegarde :", backup)
subprocess.run(["git", "-C", str(root), "diff", "--stat"])
