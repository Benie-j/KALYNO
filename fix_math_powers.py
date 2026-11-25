
from pathlib import Path
from datetime import datetime
import shutil
import subprocess
import sys

file = Path("app/src/main/java/ci/kalyno/app/ui/explorer/RichText.kt")

if not file.is_file():
    sys.exit("ARRÊT : RichText.kt introuvable.")

# Ne pas écraser des modifications locales préexistantes.
for flag in ("--quiet", "--cached"):
    cmd = ["git", "diff", flag, "--", str(file)]
    if subprocess.run(cmd).returncode != 0:
        sys.exit("ARRÊT : impossible de vérifier Git.")
    if flag == "--quiet" and subprocess.run(
        ["git", "diff", "--quiet", "--", str(file)]
    ).returncode != 0:
        sys.exit("ARRÊT : RichText.kt possède déjà des modifications locales.")

original = file.read_text(encoding="utf-8")
updated = original

anchor = 'private val TAG_RE = Regex("<[^>]*>")'
helper = r'''
private val POWER_RE = Regex(
    """(?<![A-Za-z0-9\\])(\([^()<>]{1,40}\)|[A-Za-z0-9]+)\s*\^\s*(\([^()<>]{1,20}\)|[+-]?\d+|[A-Za-z][A-Za-z0-9]*)"""
)

private fun renderPlainPowers(text: String): String =
    POWER_RE.replace(text) { match ->
        val base = match.groupValues[1].trim()
        val exponent = match.groupValues[2].trim()

        val texBase = if (base.startsWith("(") && base.endsWith(")")) {
            "\\left(${base.substring(1, base.length - 1)}\\right)"
        } else {
            base
        }

        val texExponent = if (
            exponent.startsWith("(") && exponent.endsWith(")")
        ) {
            "\\left(${exponent.substring(1, exponent.length - 1)}\\right)"
        } else {
            exponent
        }

        "\$" + texBase + "^{" + texExponent + "}" + "\$"
    }

private fun normalizePowerText(text: String): String {
    val result = StringBuilder(text.length + 16)
    var cursor = 0

    for (match in MATH_RE.findAll(text)) {
        result.append(renderPlainPowers(text.substring(cursor, match.range.first)))
        result.append(match.value)
        cursor = match.range.last + 1
    }

    result.append(renderPlainPowers(text.substring(cursor)))
    return result.toString()
}

private fun normalizePowerNotation(html: String): String {
    if (!html.contains('^')) return html

    val result = StringBuilder(html.length + 16)
    var cursor = 0

    for (tag in TAG_RE.findAll(html)) {
        if (tag.range.first > cursor) {
            result.append(
                normalizePowerText(html.substring(cursor, tag.range.first))
            )
        }

        result.append(tag.value)
        cursor = tag.range.last + 1
    }

    if (cursor < html.length) {
        result.append(normalizePowerText(html.substring(cursor)))
    }

    return result.toString()
}
'''

if updated.count(anchor) != 1:
    sys.exit("ARRÊT : ancre TAG_RE trouvée un nombre inattendu de fois.")

if "private fun normalizePowerNotation" in updated:
    sys.exit("ARRÊT : le correctif semble déjà présent.")

updated = updated.replace(anchor, anchor + "\n" + helper, 1)

old = '''    // Tableaux : rendu natif (voir ui/explorer/tables). Sans <table>, rien ne change ci-dessous.
    if (allowKatex && containsTable(html)) {
        RichTextWithTables(html, color, fontSize, lineHeight, fontWeight, fontStyle)
        return
    }
    if (allowKatex && KATEX_ENABLED && html.contains('$') && katexAvailable()) {
        KatexText(html, color, fontSize, lineHeight, fontWeight, fontStyle)
        return
    }
    if (!html.contains('<') && !html.contains('$')) {
        Text(text = html, color = color, fontSize = fontSize, lineHeight = lineHeight, fontWeight = fontWeight, fontStyle = fontStyle, textAlign = textAlign)
        return
    }
    val blocks = remember(html) { parseRich(html) }'''

new = '''    // Normalise les puissances écrites avec ^ avant de choisir le moteur d'affichage.
    val renderedHtml = remember(html) { normalizePowerNotation(html) }

    // Tableaux : rendu natif (voir ui/explorer/tables).
    if (allowKatex && containsTable(renderedHtml)) {
        RichTextWithTables(renderedHtml, color, fontSize, lineHeight, fontWeight, fontStyle)
        return
    }
    if (allowKatex && KATEX_ENABLED && renderedHtml.contains('$') && katexAvailable()) {
        KatexText(renderedHtml, color, fontSize, lineHeight, fontWeight, fontStyle)
        return
    }
    if (!renderedHtml.contains('<') && !renderedHtml.contains('$')) {
        Text(text = renderedHtml, color = color, fontSize = fontSize, lineHeight = lineHeight, fontWeight = fontWeight, fontStyle = fontStyle, textAlign = textAlign)
        return
    }
    val blocks = remember(renderedHtml) { parseRich(renderedHtml) }'''

if updated.count(old) != 1:
    sys.exit("ARRÊT : bloc RichText trouvé un nombre inattendu de fois.")

updated = updated.replace(old, new, 1)

backup_dir = Path.home() / "kalyno-backups"
backup_dir.mkdir(parents=True, exist_ok=True)
backup = backup_dir / f"RichText.kt.{datetime.now().strftime('%Y%m%d-%H%M%S')}.bak"
shutil.copy2(file, backup)

file.write_text(updated, encoding="utf-8")

print("CORRECTIF APPLIQUÉ")
print(f"Sauvegarde : {backup}")
print(f"Fichier modifié : {file}")

