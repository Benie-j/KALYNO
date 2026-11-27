#!/usr/bin/env python3
from pathlib import Path
from datetime import datetime
import subprocess, sys

home = Path.home()
app_repo, admin_repo = home / "KALYNO", home / "KALYNO-admin"
app_file = app_repo / "app/src/main/java/ci/kalyno/app/data/SupabaseLearningRepository.kt"
admin_file = admin_repo / "src/App.tsx"

def stop(msg):
    print("ERREUR:", msg, file=sys.stderr)
    raise SystemExit(1)

def replace_once(src, old, new, label):
    n = src.count(old)
    if n != 1:
        stop(f"{label}: motif attendu 1 fois, trouvé {n}. Aucun fichier écrit.")
    return src.replace(old, new, 1)

for repo in (app_repo, admin_repo):
    if not (repo / ".git").exists():
        stop(f"Dépôt Git absent: {repo}")
    p = subprocess.run(["git", "status", "--short", "--branch"], cwd=repo,
                       text=True, capture_output=True)
    if p.returncode:
        stop(f"git status impossible dans {repo}: {p.stderr}")
    print(f"État Git {repo.name}:\n{p.stdout.strip() or '(propre)'}")

if not app_file.is_file() or not admin_file.is_file():
    stop("Un des fichiers ciblés est introuvable.")

app = app_file.read_text(encoding="utf-8")
adm = admin_file.read_text(encoding="utf-8")

app = replace_once(app,
    'data class LearningSession(val id: String, val title: String)',
    'data class LearningSession(val id: String, val title: String, val isAvailable: Boolean = true)',
    "Modèle LearningSession")
app = replace_once(app,
    '"lesson_sessions?select=id,title,position&chapter_id=eq.${enc(chapterId)}&order=position.asc"',
    '"lesson_sessions?select=id,title,position,is_available&is_available=eq.true&chapter_id=eq.${enc(chapterId)}&order=position.asc"',
    "Requête séances disponibles")
app = replace_once(app,
    'LearningSession(id = row.getString("id"), title = row.optString("title", "").trim())',
    'LearningSession(\\n                    id = row.getString("id"),\\n                    title = row.optString("title", "").trim(),\\n                    isAvailable = row.optBoolean("is_available", true)\\n                )',
    "Mapping disponibilité")

adm = replace_once(adm,
    'type LessonSession = { id: string; chapter_id: string; title: string; position: number };',
    'type LessonSession = { id: string; chapter_id: string; title: string; position: number; is_available: boolean };',
    "Type LessonSession")
adm = replace_once(adm,
    'supabase.from("lesson_sessions").select("id,chapter_id,title,position").eq("chapter_id", activeChapter).order("position")',
    'supabase.from("lesson_sessions").select("id,chapter_id,title,position,is_available").eq("chapter_id", activeChapter).order("position")',
    "Chargement des séances")
adm = replace_once(adm,
    'supabase.from("lesson_sessions").insert({ chapter_id: chapterId, title: str(v.title), position: sessions.length })',
    'supabase.from("lesson_sessions").insert({ chapter_id: chapterId, title: str(v.title), position: sessions.length, is_available: false })',
    "Création masquée par défaut")
adm = replace_once(adm,
    '  async function moveSession(session: LessonSession, direction: -1 | 1) {',
    "  async function toggleSessionAvailability(session: LessonSession) {\n    const is_available = !session.is_available;\n    await runAction(async () => {\n      const { error } = await supabase.from(\"lesson_sessions\").update({ is_available }).eq(\"id\", session.id);\n      if (error) throw error;\n      await loadLesson(chapterId);\n    }, is_available ? \"Séance rendue disponible aux élèves.\" : \"Séance masquée aux élèves.\");\n  }\n  async function moveSession(session: LessonSession, direction: -1 | 1) {",
    "Fonction de bascule")
adm = replace_once(adm,
    '  X, Dumbbell\\n} from "lucide-react";',
    '  X, Dumbbell, Eye, EyeOff\\n} from "lucide-react";',
    "Import Eye/EyeOff")
adm = replace_once(adm,
    '<small>Séance {i+1} · {capsules.filter(c=>c.session_id===session.id).length} bloc(s)</small></span><div className="row-actions"><button title="Monter"',
    '<small>Séance {i+1} · {capsules.filter(c=>c.session_id===session.id).length} bloc(s) · {session.is_available?"Visible aux élèves":"Masquée aux élèves"}</small></span><div className="row-actions"><button title={session.is_available?"Masquer aux élèves":"Rendre disponible"} onClick={()=>void toggleSessionAvailability(session)}>{session.is_available?<Eye size={14}/>:<EyeOff size={14}/>}</button><button title="Monter"',
    "Bouton de visibilité")

stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
for f in (app_file, admin_file):
    b = f.with_name(f.name + ".bak-sessions-" + stamp)
    b.write_bytes(f.read_bytes())
    print("Sauvegarde:", b)

app_file.write_text(app, encoding="utf-8")
admin_file.write_text(adm, encoding="utf-8")
print("Correctif appliqué. Lance npm run build puis ./gradlew :app:compileDebugKotlin.")
