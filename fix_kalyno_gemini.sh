
#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

FILE="supabase/functions/kalyno-gemini/index.ts"
BACKUP_DIR="$HOME/kalyno-backups"
STAMP="$(date +%Y%m%d-%H%M%S)"

echo "=== CORRECTIF KALYNO : GEMINI + GROQ ==="

# 1. Vérifier le dépôt et le fichier
if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "ERREUR : ~/KALYNO n'est pas un dépôt Git."
  exit 1
fi

if [ ! -f "$FILE" ]; then
  echo "ERREUR : fichier introuvable : $FILE"
  exit 1
fi

# 2. Ne pas écraser des modifications préexistantes
if ! git diff --quiet -- "$FILE"; then
  echo "ARRÊT : le fichier possède déjà des modifications locales."
  echo "Sauvegarde ou examine-les avant de continuer."
  git diff -- "$FILE"
  exit 1
fi

mkdir -p "$BACKUP_DIR"
cp -p "$FILE" "$BACKUP_DIR/index.ts.before-gemini-groq-$STAMP"

echo "Sauvegarde créée."

# 3. Appliquer le correctif avec des contrôles stricts
python - "$FILE" <<'PY'
import sys
from pathlib import Path

path = Path(sys.argv[1])
src = path.read_text(encoding="utf-8")
original = src

old_model = 'const DEFAULT_MODEL = "gemini-3.6-flash";'
new_model = 'const DEFAULT_MODEL = "gemini-3.1-flash-lite";'

old_override = (
    'const model = (Deno.env.get("GEMINI_MODEL") ?? DEFAULT_MODEL)'
    '.trim() || DEFAULT_MODEL;'
)
new_override = 'const model = DEFAULT_MODEL;'

# Modèle Gemini
if old_model in src:
    src = src.replace(old_model, new_model, 1)
elif new_model not in src:
    raise SystemExit("ARRÊT : constante Gemini inconnue.")

# Interdire la surcharge du modèle par un secret
if old_override in src:
    src = src.replace(old_override, new_override, 1)
elif new_override not in src:
    raise SystemExit("ARRÊT : ligne de sélection Gemini inconnue.")

# Vérifier que Groq est toujours présent
required = [
    "async function callGroq",
    'Deno.env.get("GROQ_API_KEY")',
    "const groqConfigured",
    "-> relais groq",
    "FALLBACK_TIMEOUT_MS",
]

missing = [item for item in required if item not in src]
if missing:
    raise SystemExit(
        "ARRÊT : mécanisme Groq incomplet : " + ", ".join(missing)
    )

# Vérifier le verrouillage du modèle Gemini
if 'Deno.env.get("GEMINI_MODEL")' in src:
    raise SystemExit("ARRÊT : surcharge GEMINI_MODEL encore présente.")

if 'const DEFAULT_MODEL = "gemini-3.1-flash-lite";' not in src:
    raise SystemExit("ARRÊT : modèle Gemini incorrect.")

# Vérifier que la bascule est réellement codée
if "await callGroq(req, FALLBACK_TIMEOUT_MS)" not in src:
    raise SystemExit("ARRÊT : appel Groq de secours absent.")

path.write_text(src, encoding="utf-8")
print("Correctif appliqué.")
PY

# 4. Contrôles Git
if ! git diff --check -- "$FILE"; then
  echo "ERREUR : contrôle Git échoué. Restauration."
  cp -p "$BACKUP_DIR/index.ts.before-gemini-groq-$STAMP" "$FILE"
  exit 1
fi

if grep -q 'Deno.env.get("GEMINI_MODEL")' "$FILE"; then
  echo "ERREUR : GEMINI_MODEL est encore utilisé."
  cp -p "$BACKUP_DIR/index.ts.before-gemini-groq-$STAMP" "$FILE"
  exit 1
fi

echo
echo "=== RÉSULTAT ==="
grep -nE 'DEFAULT_MODEL|DEFAULT_GROQ_MODEL|callGroq|relais groq|GROQ_API_KEY' "$FILE"

echo
echo "=== DIFF ==="
git diff -- "$FILE"

echo
echo "Correctif local validé."
echo "Sauvegarde : $BACKUP_DIR/index.ts.before-gemini-groq-$STAMP"
echo "Aucun commit, push ou déploiement automatique."
