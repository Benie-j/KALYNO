#!/data/data/com.termux/files/usr/bin/bash
# Applique les correctifs « séances et blocs » aux deux dépôts. Usage : bash appliquer_seances.sh
set -e
cd ~/KALYNO-admin && git status --short && git branch --show-current
git checkout -b feature/seances-blocs 2>/dev/null || git checkout feature/seances-blocs
python3 ~/patch_admin.py . && npm ci && npm run build
cd ~/KALYNO && git status --short && git branch --show-current
git checkout -b feature/seances-blocs 2>/dev/null || git checkout feature/seances-blocs
python3 ~/patch_android.py .
echo "OK. Vérifie avec git diff, puis compile (GitHub Actions ou gradle assembleDebug) avant de commit."
