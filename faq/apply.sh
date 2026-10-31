#!/data/data/com.termux/files/usr/bin/bash
# Centre d'aide & FAQ dynamique : Room v13, FaqRepository (offline-first), écran Compose, table Supabase, section admin.
# Usage : bash apply.sh [~/KALYNO] [~/KALYNO-admin]
set -e
APP="${1:-$HOME/KALYNO}"
ADMIN="${2:-$HOME/KALYNO-admin}"
HERE="$(cd "$(dirname "$0")" && pwd)"
command -v git >/dev/null || { echo "git est requis (pkg install git)"; exit 1; }
[ -d "$APP/app/src/main/java/ci/kalyno/app" ] || { echo "Projet KALYNO introuvable dans $APP"; exit 1; }
[ -f "$ADMIN/src/App.tsx" ] || { echo "Projet KALYNO-admin introuvable dans $ADMIN"; exit 1; }

# ---------- App Android ----------
cd "$APP"
if grep -q "FaqEntity" app/src/main/java/ci/kalyno/app/data/dashboard/KalynoDatabase.kt; then
  echo "App : déjà appliqué."
else
  if ! git apply -C1 --check "$HERE/faq-app-wiring.patch" 2>/tmp/kalyno-faq.err; then
    cat /tmp/kalyno-faq.err
    echo
    echo "Arrêt : un de ces fichiers a changé depuis mon analyse (rien n'a été modifié) :"
    echo "  KalynoDatabase.kt, KalynoApp.kt, MainScreen.kt"
    exit 1
  fi
  git apply -C1 "$HERE/faq-app-wiring.patch"
  cp -r "$HERE/app/." .
  echo "App : appliqué (Room v13, data/faq, ui/faq, migration SQL, tests)."
fi

# ---------- Admin ----------
cd "$ADMIN"
if grep -q "FaqManager" src/App.tsx; then
  echo "Admin : déjà appliqué."
else
  if ! git apply -C1 --check "$HERE/faq-admin-wiring.patch" 2>/tmp/kalyno-faq-admin.err; then
    cat /tmp/kalyno-faq-admin.err
    echo "Arrêt : src/App.tsx a changé depuis mon analyse (admin non modifié)."
    exit 1
  fi
  git apply -C1 "$HERE/faq-admin-wiring.patch"
  cp "$HERE/admin/src/FaqManager.tsx" src/FaqManager.tsx
  echo "Admin : appliqué (section « Centre d'aide · FAQ »)."
fi

echo
echo "Suite :"
echo "  1) Supabase > SQL Editor : coller supabase/migrations/20261101_faq_items.sql (dans $APP) puis Run."
echo "  2) cd $APP   && ./gradlew testDebugUnitTest && git add -A && git commit -m \"Centre d'aide : FAQ dynamique\" && git push"
echo "  3) cd $ADMIN && git add -A && git commit -m \"Admin : gestion de la FAQ\" && git push"
