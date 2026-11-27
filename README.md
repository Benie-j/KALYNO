# KALYNO — séances, phase 1

Le script `apply_sessions_phase1.py` ajoute le contrôle de disponibilité des séances.

- Admin : bouton œil pour rendre chaque séance visible ou masquée.
- Les nouvelles séances sont masquées par défaut.
- Android : seules les séances `is_available=true` sont chargées.

## Prérequis
Les dépôts Git doivent être dans `~/KALYNO` et `~/KALYNO-admin`.
La migration Supabase `add_session_availability_and_student_progress` doit être appliquée.

## Application dans Termux
Décompresse l'archive, puis exécute :
```sh
cd ~/kalyno_sessions_phase1
python apply_sessions_phase1.py
```
Le script affiche l'état Git, vérifie chaque motif, crée des sauvegardes horodatées,
puis écrit les deux fichiers. Si le code local ne correspond pas exactement, il s'arrête
sans écrire les fichiers.

## Tests
```sh
cd ~/KALYNO-admin && npm run build
cd ~/KALYNO && ./gradlew :app:compileDebugKotlin
```
La livraison n'est validée qu'après réussite de ces commandes.

## Retour arrière
Restaurer les fichiers `.bak-sessions-HORODATAGE` sur leurs fichiers d'origine.

## Limites
Cette phase ne comprend pas encore l'écran d'une séance à la fois, le bouton Terminer
par séance, le déblocage de l'exercice lié ni la synchronisation de progression.
La table Supabase/RLS existe déjà. La suite doit transmettre le vrai jeton de session
Supabase de l'élève; une clé publique seule ne permet pas d'écrire sous cette politique.
