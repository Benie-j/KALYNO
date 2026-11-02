KALYNO — correctif de récupération de session Supabase

Cible : dépôt Benie-j/KALYNO, branche main.
Fichiers : AuthStore.kt et CloudSync.kt.

Le correctif :
- expose le refresh token même si l'access token est absent ;
- renouvelle la session d'un compte connecté ;
- propage les erreurs de renouvellement pour que WorkManager réessaie ;
- garde le mode invité silencieux ;
- ne touche pas aux données locales ni aux repères de synchronisation.

Application :
  cd ~/KALYNO
  git switch main
  git pull --ff-only origin main
  # place kalyno-sync-session-fix.patch à la racine de ~/KALYNO
  git apply --check kalyno-sync-session-fix.patch
  git apply kalyno-sync-session-fix.patch
  ./gradlew :app:assembleDebug

Si git apply --check échoue, ne force pas l'application.
