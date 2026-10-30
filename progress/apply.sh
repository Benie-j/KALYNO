#!/data/data/com.termux/files/usr/bin/bash
# Refonte de « Ma progression » : historique des tentatives du Dojo (Room), ViewModel et écran Compose.
# Usage : bash apply.sh [~/KALYNO]
set -e
ROOT="${1:-$HOME/KALYNO}"
HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"
[ -d app/src/main/java/ci/kalyno/app ] || { echo "Projet KALYNO introuvable dans $ROOT"; exit 1; }
command -v git >/dev/null || { echo "git est requis (pkg install git)"; exit 1; }

if grep -q "DojoAttemptEntity" app/src/main/java/ci/kalyno/app/data/dashboard/KalynoDatabase.kt; then
  echo "Déjà appliqué."; exit 0
fi

# Le patch modifie 5 fichiers existants ; s'il ne s'applique pas à 100 %, rien n'est modifié.
if ! git apply -C1 --check "$HERE/progress-wiring.patch" 2>/tmp/kalyno-patch.err; then
  cat /tmp/kalyno-patch.err
  echo
  echo "Arrêt : un de ces fichiers a changé depuis mon analyse (rien n'a été modifié) :"
  echo "  KalynoDatabase.kt, DojoViewModel.kt, MainScreen.kt, AccueilScreen.kt, AccueilModules.kt"
  exit 1
fi
git apply -C1 "$HERE/progress-wiring.patch"
cp -r "$HERE/app/." app/

echo "Ma progression : appliqué (base Room v10, historique des tentatives, nouvel écran)."
echo "Suite : git add -A && git commit -m \"Ma progression : historique des tentatives\" && git push"
