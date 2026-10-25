#!/bin/sh
# Applique le patch KALYNO depuis ~/KALYNO : sauvegarde les fichiers remplacés puis copie les nouveaux.
set -e
SRC="$(cd "$(dirname "$0")" && pwd)"
DEST="$(pwd)"
if [ ! -f "$DEST/settings.gradle.kts" ]; then
  echo "Lance ce script depuis ~/KALYNO (cd ~/KALYNO && sh chemin/APPLY.sh)"
  exit 1
fi
BK="$DEST/.git-local-backups/hierarchie-series-$(date +%Y%m%d-%H%M%S)"
cd "$SRC"
find app supabase -type f | while read -r f; do
  if [ -f "$DEST/$f" ]; then
    mkdir -p "$BK/$(dirname "$f")"
    cp "$DEST/$f" "$BK/$f"
  fi
  mkdir -p "$DEST/$(dirname "$f")"
  cp "$f" "$DEST/$f"
done
echo "Patch applique. Sauvegarde des anciens fichiers : $BK"
