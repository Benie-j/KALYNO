import sys
import re, sys
import os
root = os.path.join(sys.argv[1] if len(sys.argv) > 1 else ".", "src") + "/"
def rw(name, fn):
    p = root + name
    s = open(p, encoding="utf-8").read()
    s2 = fn(s)
    open(p, "w", encoding="utf-8").write(s2)
def rep(s, old, new, count=1):
    assert s.count(old) >= 1, "introuvable: " + old[:70]
    return s.replace(old, new, count)

# ---- CapsuleEditor : exporter le rendu élève existant
rw("CapsuleEditor.tsx", lambda s: rep(s, "function renderPreview(html: string): string {", "export function renderPreview(html: string): string {"))

def app(s):
    s = rep(s, 'import CapsuleEditor, { type CapsuleEditorDraft } from "./CapsuleEditor";',
            'import CapsuleEditor, { renderPreview, type CapsuleEditorDraft } from "./CapsuleEditor";')
    s = rep(s, '  X, Dumbbell\n} from "lucide-react";', '  X, Dumbbell, Eye, EyeOff, ArrowLeft, ChevronRight\n} from "lucide-react";')
    s = rep(s, 'type LessonSession = { id: string; chapter_id: string; title: string; position: number };',
            'type LessonSession = { id: string; chapter_id: string; title: string; position: number; is_available?: boolean };')
    s = rep(s, 'const KIND_LABEL:', '// Valeur interne : section « Blocs non affectés » (capsules.session_id IS NULL)\nconst UNASSIGNED = "__none__";\nconst KIND_LABEL:')
    # états de navigation + dérivés
    s = rep(s, '  const [lessonTab, setLessonTab] = useState<"content" | "exercises">("content");',
'''  const [lessonTab, setLessonTab] = useState<"content" | "exercises">("content");
  // Navigation pédagogique : séances -> blocs d'une séance -> aperçu complet (lecture seule) d'un bloc
  const [sessionView, setSessionView] = useState<"sessions" | "blocks" | "preview">("sessions");
  const [openSessionId, setOpenSessionId] = useState("");
  const [previewId, setPreviewId] = useState("");
  useEffect(() => { setSessionView("sessions"); setOpenSessionId(""); setPreviewId(""); }, [chapterId]);
  const scopeCapsules = useMemo(() => capsules
    .filter(c => openSessionId === UNASSIGNED ? !c.session_id : c.session_id === openSessionId)
    .sort((a, b) => a.position - b.position), [capsules, openSessionId]);
  const unassignedCount = capsules.filter(c => !c.session_id).length;
  const openSession = sessions.find(s => s.id === openSessionId);
  const previewCapsule = capsules.find(c => c.id === previewId);
  const scopeLabel = openSessionId === UNASSIGNED ? "Blocs non affectés" : openSession ? `Séance ${sessions.findIndex(x => x.id === openSession.id) + 1} — ${openSession.title}` : "Séance";''')
    # chargement is_available avec repli
    s = rep(s, 'supabase.from("lesson_sessions").select("id,chapter_id,title,position").eq("chapter_id", activeChapter).order("position")',
            'supabase.from("lesson_sessions").select("id,chapter_id,title,position,is_available").eq("chapter_id", activeChapter).order("position")')
    s = rep(s, '    setSessions(sessionResult.error ? [] : (sessionResult.data ?? []) as LessonSession[]);',
'''    if (sessionResult.error) {
      // Colonne `is_available` absente : relecture sans elle (les séances restent administrables)
      const retry = await supabase.from("lesson_sessions").select("id,chapter_id,title,position").eq("chapter_id", activeChapter).order("position");
      setSessions(retry.error ? [] : (retry.data ?? []) as LessonSession[]);
    } else setSessions((sessionResult.data ?? []) as LessonSession[]);''')
    # nouvelles séances masquées par défaut
    s = rep(s, 'insert({ chapter_id: chapterId, title: str(v.title), position: sessions.length });',
            'insert({ chapter_id: chapterId, title: str(v.title), position: sessions.length, is_available: false });')
    s = rep(s, 'help: "Le numéro (Séance 1, Séance 2…) est ajouté automatiquement selon l\'ordre.',
            'help: "La séance est créée masquée : les élèves ne la voient qu\'après publication. Le numéro (Séance 1, Séance 2…) est ajouté automatiquement selon l\'ordre.')
    # publier / masquer (écriture vérifiée)
    s = rep(s, '  async function assignCapsule(capsule: Capsule, sessionId: string) {',
'''  async function toggleSessionAvailability(session: LessonSession) {
    const next = !session.is_available;
    if (next && !confirm(`Publier « ${session.title} » ? Les élèves pourront la consulter.`)) return;
    await runAction(async () => {
      const { data, error } = await supabase.from("lesson_sessions").update({ is_available: next }).eq("id", session.id).select("id,is_available").single();
      if (error) throw error;
      if (!data || data.is_available !== next) throw new Error("Le changement de disponibilité n'a pas été confirmé par Supabase.");
      await loadLesson(chapterId);
    }, next ? "Séance publiée pour les élèves." : "Séance masquée aux élèves (son contenu est conservé).");
  }
  async function assignCapsule(capsule: Capsule, sessionId: string) {
    if (!confirm(sessionId ? "Déplacer ce bloc vers la séance choisie ?" : "Retirer ce bloc de sa séance ? Il ira dans « Blocs non affectés ».")) return;''')
    # suppression de séance : quitter la vue si elle était ouverte
    s = rep(s, '      const { error } = await supabase.from("lesson_sessions").delete().eq("id", session.id);\n      if (error) throw error; await loadLesson(chapterId);',
            '      const { error } = await supabase.from("lesson_sessions").delete().eq("id", session.id);\n      if (error) throw error;\n      if (openSessionId === session.id) { setSessionView("sessions"); setOpenSessionId(""); }\n      await loadLesson(chapterId);')
    # création d'un bloc : rattaché à la séance ouverte
    s = rep(s, '          is_high_yield: draft.is_high_yield,\n          position: capsules.length\n        });',
            '          is_high_yield: draft.is_high_yield,\n          position: capsules.length,\n          ...(sessionView !== "sessions" && openSessionId && openSessionId !== UNASSIGNED ? { session_id: openSessionId } : {})\n        });')
    # nouvelle zone de contenu
    start = s.index('{lessonTab==="content" && <>')
    end = s.index('{lessonTab==="exercises" && <>')
    block = r'''{lessonTab==="content" && <>
                {sessionView!=="sessions" && <nav className="crumbs session-crumbs" aria-label="Fil de navigation">
                  <button className="crumb" onClick={()=>{setSessionView("sessions");setPreviewId("");}}>Séances</button><span className="crumb-sep"><ChevronRight size={12}/></span>
                  {sessionView==="blocks" ? <span className="crumb current">{scopeLabel}</span> : <><button className="crumb" onClick={()=>setSessionView("blocks")}>{scopeLabel}</button><span className="crumb-sep"><ChevronRight size={12}/></span><span className="crumb current">Bloc {previewCapsule ? scopeCapsules.findIndex(c=>c.id===previewCapsule.id)+1 : ""}</span></>}
                </nav>}

                {sessionView==="sessions" && <>
                  <div className="content-subhead"><h4>Séances <span>{sessions.length}</span></h4><div className="panel-actions"><small>Ouvre une séance pour voir ses blocs. Une nouvelle séance est masquée aux élèves tant qu’elle n’est pas publiée.</small><button className="small-primary" disabled={busy} onClick={()=>void createSession()}><Plus size={15}/> Séance</button></div></div>
                  {sessions.length===0 && unassignedCount===0 ? <Empty text="Aucune séance et aucun bloc. Crée une séance, puis ajoute ses blocs."/> : <div className="item-list">
                    {sessions.map((session,i)=><div key={session.id} className="chapter-row session-row">
                      <button className="chapter-select" onClick={()=>{setOpenSessionId(session.id);setSessionView("blocks");}}>
                        <span className="chapter-number">{i+1}</span>
                        <span className="chapter-title"><strong><MathHtml text={session.title}/></strong><small>Séance {i+1} · position {session.position+1} · {capsules.filter(c=>c.session_id===session.id).length} bloc(s)</small></span>
                        <span className={`status-pill ${session.is_available?"published":"draft"}`}><span/>{session.is_available?"Disponible":"Masquée"}</span>
                      </button>
                      <div className="row-actions">
                        <button title="Ouvrir la séance" onClick={()=>{setOpenSessionId(session.id);setSessionView("blocks");}}><ChevronRight size={14}/></button>
                        <button title={session.is_available?"Masquer aux élèves":"Publier pour les élèves"} disabled={busy} onClick={()=>void toggleSessionAvailability(session)}>{session.is_available?<EyeOff size={14}/>:<Eye size={14}/>}</button>
                        <button title="Monter" disabled={busy||i===0} onClick={()=>void moveSession(session,-1)}><ArrowUp size={14}/></button>
                        <button title="Descendre" disabled={busy||i===sessions.length-1} onClick={()=>void moveSession(session,1)}><ArrowDown size={14}/></button>
                        <button title="Renommer" onClick={()=>editSession(session)}>✎</button>
                        <button title="Supprimer" onClick={()=>void deleteSession(session)}><Trash2 size={14}/></button>
                      </div>
                    </div>)}
                    {unassignedCount>0 && <div className="chapter-row session-row unassigned-row">
                      <button className="chapter-select" onClick={()=>{setOpenSessionId(UNASSIGNED);setSessionView("blocks");}}>
                        <span className="chapter-number">∅</span>
                        <span className="chapter-title"><strong>Blocs non affectés</strong><small>{unassignedCount} bloc(s) sans séance — à affecter explicitement</small></span>
                      </button>
                      <div className="row-actions"><button title="Ouvrir" onClick={()=>{setOpenSessionId(UNASSIGNED);setSessionView("blocks");}}><ChevronRight size={14}/></button></div>
                    </div>}
                  </div>}
                </>}

                {sessionView==="blocks" && <>
                  <div className="content-subhead"><h4>{scopeLabel} <span>{scopeCapsules.length}</span></h4><div className="panel-actions"><button className="secondary" onClick={()=>setSessionView("sessions")}><ArrowLeft size={14}/> Séances</button><button className="small-primary" disabled={busy} onClick={()=>void createCapsule()}><Plus size={15}/> Bloc</button></div></div>
                  {openSessionId!==UNASSIGNED && !openSession ? <Empty text="Séance introuvable. Reviens à la liste des séances."/> : scopeCapsules.length===0 ? <Empty text={openSessionId===UNASSIGNED?"Plus aucun bloc non affecté.":"Cette séance ne contient aucun bloc. Ajoute-en un avec « Bloc »."}/> : <div className="item-list">
                    {scopeCapsules.map((capsule,i)=><div key={capsule.id} className="chapter-row block-row">
                      <button className="chapter-select" onClick={()=>{setPreviewId(capsule.id);setSessionView("preview");}}>
                        <span className="chapter-number">{i+1}</span>
                        <span className="chapter-title"><strong><MathHtml text={capsule.title}/></strong><small>Bloc {i+1} · {KIND_LABEL[capsule.kind]??capsule.kind}</small></span>
                      </button>
                      <select className="block-move" aria-label="Séance du bloc" value={capsule.session_id??""} disabled={busy||sessions.length===0} onChange={e=>void assignCapsule(capsule,e.target.value)}>
                        <option value="">Non affecté</option>{sessions.map((s,k)=><option key={s.id} value={s.id}>{`Séance ${k+1}`}</option>)}
                      </select>
                      <div className="row-actions">
                        <button title="Voir l’aperçu complet" onClick={()=>{setPreviewId(capsule.id);setSessionView("preview");}}><Eye size={14}/></button>
                        <button title="Supprimer" disabled={busy} onClick={()=>void deleteCapsule(capsule)}><Trash2 size={14}/></button>
                      </div>
                    </div>)}
                  </div>}
                </>}

                {sessionView==="preview" && (!previewCapsule ? <Empty text="Bloc introuvable. Reviens à la liste des blocs."/> : <div className="block-preview">
                  <div className="content-subhead"><h4><MathHtml text={previewCapsule.title}/></h4><div className="panel-actions"><button className="secondary" onClick={()=>{setSessionView("blocks");setPreviewId("");}}><ArrowLeft size={14}/> Retour aux blocs</button><button className="primary" disabled={busy} onClick={()=>editCapsule(previewCapsule)}>Modifier le bloc</button></div></div>
                  <div className="block-preview-meta"><span className="kind-pill">{KIND_LABEL[previewCapsule.kind]??previewCapsule.kind}</span><span>{previewCapsule.minutes} min</span><span>{previewCapsule.is_high_yield?"Prioritaire":"Bloc de leçon"}</span><span className="muted">Aperçu en lecture seule — rendu élève</span></div>
                  <div className="preview-box block-preview-body" dangerouslySetInnerHTML={{ __html: renderPreview(previewCapsule.body) || "<span class=\"muted\">Aucun texte dans ce bloc.</span>" }} />
                </div>)}
                </>}
                '''
    s = s[:start] + block + s[end:]
    return s
rw("App.tsx", app)

# ---- styles
open(root + "styles.css", "a", encoding="utf-8").write('''
/* Séances / blocs : navigation par vues distinctes */
.session-crumbs{display:flex;align-items:center;flex-wrap:wrap;gap:2px;padding:12px 14px 0}
.session-row .status-pill{margin-left:auto}
.unassigned-row{background:#fffaf0;border-color:#f3e3bd}
.block-move{max-width:130px;border:1px solid #dfe5ef;border-radius:7px;padding:6px;font-size:10px;background:#fff;color:#24324d}
.block-preview{padding-bottom:18px}
.block-preview-meta{display:flex;align-items:center;flex-wrap:wrap;gap:10px;padding:0 18px 6px;font-size:10px;color:#8b96aa}
.block-preview-body{margin:6px 18px 0;border:1px solid #e8edf5;border-radius:10px;background:#fff}
@media (max-width:720px){.chapter-row.session-row,.chapter-row.block-row{flex-wrap:wrap}.chapter-row .row-actions{width:100%;justify-content:flex-end}.block-move{max-width:100%;flex:1}.content-subhead{flex-wrap:wrap}}
''')
print("ok")
