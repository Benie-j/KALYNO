import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { ArrowDown, ArrowUp, Edit3, Save, Search, Trash2, X } from "lucide-react";
import { supabase } from "./lib/supabase";

type Faq = { id: string; category: string; question: string; answer: string; order_index: number; updated_at: string };
type FaqDraft = { category: string; question: string; answer: string; order_index: string };

const MAX = { category: 80, question: 300, answer: 5000 };
const emptyDraft: FaqDraft = { category: "", question: "", answer: "", order_index: "0" };
const errText = (e: unknown) => e && typeof e === "object" && "message" in e ? String((e as { message: unknown }).message) : String(e);
const fold = (s: string) => s.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase();
const excerpt = (s: string, n: number) => s.length > n ? s.slice(0, n) + "…" : s;

type Props = { busy: boolean; onNotice: (s: string) => void };

/**
 * Centre d’aide & FAQ : questions/réponses lues par l’app élève (table `faq_items`).
 * L’ordre est global (order_index) : les catégories apparaissent dans l’ordre de leur première question.
 */
export default function FaqManager({ busy, onNotice }: Props) {
  const [items, setItems] = useState<Faq[]>([]);
  const [draft, setDraft] = useState<FaqDraft>(emptyDraft);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [filter, setFilter] = useState("");

  const load = useCallback(async () => {
    const { data, error } = await supabase
      .from("faq_items")
      .select("id,category,question,answer,order_index,updated_at")
      .order("order_index", { ascending: true })
      .order("question", { ascending: true });
    if (error) { onNotice("Échec : " + errText(error)); return; }
    setItems((data ?? []) as Faq[]);
  }, [onNotice]);

  useEffect(() => { void load(); }, [load]);

  const categories = useMemo(() => [...new Set(items.map(i => i.category))], [items]);
  const visible = useMemo(() => {
    const terms = fold(filter).split(/\s+/).filter(Boolean);
    if (terms.length === 0) return items;
    return items.filter(i => { const hay = fold(`${i.category}\n${i.question}\n${i.answer}`); return terms.every(t => hay.includes(t)); });
  }, [items, filter]);

  function reset() { setEditingId(null); setDraft({ ...emptyDraft, order_index: String(items.length) }); }

  async function submit(event: FormEvent) {
    event.preventDefault();
    const category = draft.category.trim();
    const question = draft.question.trim();
    const answer = draft.answer.trim();
    const order = Number(draft.order_index);
    if (!category || !question || !answer) { onNotice("Échec : la catégorie, la question et la réponse sont obligatoires."); return; }
    if (category.length > MAX.category || question.length > MAX.question || answer.length > MAX.answer) {
      onNotice(`Échec : limites dépassées (catégorie ${MAX.category}, question ${MAX.question}, réponse ${MAX.answer} caractères).`); return;
    }
    if (!Number.isInteger(order) || order < 0) { onNotice("Échec : l’ordre doit être un entier positif ou nul."); return; }
    setSaving(true); onNotice("");
    try {
      const values = { category, question, answer, order_index: order };
      const result = editingId
        ? await supabase.from("faq_items").update(values).eq("id", editingId)
        : await supabase.from("faq_items").insert(values);
      if (result.error) throw result.error;
      await load();
      setEditingId(null);
      setDraft({ ...emptyDraft, category, order_index: String(order + 1) }); // garde la catégorie pour enchaîner les saisies
      onNotice(editingId ? "Question mise à jour. Les élèves la voient à leur prochaine ouverture du Centre d’aide." : "Question ajoutée. Les élèves la verront à leur prochaine ouverture du Centre d’aide.");
    } catch (e) { onNotice("Échec : " + errText(e)); } finally { setSaving(false); }
  }

  async function remove(item: Faq) {
    if (!window.confirm("Supprimer définitivement cette question ? Elle disparaîtra aussi du téléphone des élèves à la prochaine synchronisation.")) return;
    setSaving(true); onNotice("");
    try {
      const { error } = await supabase.from("faq_items").delete().eq("id", item.id);
      if (error) throw error;
      if (editingId === item.id) reset();
      await load();
      onNotice("Question supprimée.");
    } catch (e) { onNotice("Échec : " + errText(e)); } finally { setSaving(false); }
  }

  /** Échange la question avec sa voisine puis renumérote 0…n : l’ordre reste propre même si des numéros étaient en double. */
  async function move(item: Faq, direction: -1 | 1) {
    const from = items.findIndex(i => i.id === item.id);
    const to = from + direction;
    if (from < 0 || to < 0 || to >= items.length) return;
    const next = [...items];
    [next[from], next[to]] = [next[to], next[from]];
    const changed = next
      .map((i, index) => ({ id: i.id, category: i.category, question: i.question, answer: i.answer, order_index: index, was: i.order_index }))
      .filter(r => r.order_index !== r.was)
      .map(({ was: _was, ...row }) => row);
    if (changed.length === 0) return;
    setSaving(true); onNotice("");
    try {
      // Toutes les colonnes obligatoires sont renvoyées : un upsert est un INSERT … ON CONFLICT et vérifie les NOT NULL
      const { error } = await supabase.from("faq_items").upsert(changed, { onConflict: "id" });
      if (error) throw error;
      await load();
    } catch (e) { onNotice("Échec : " + errText(e)); } finally { setSaving(false); }
  }

  const filtering = filter.trim() !== "";
  return <div className="levels-layout">
    <form className="panel levels-form" onSubmit={submit}>
      <div className="panel-heading"><div><h3>{editingId ? "Modifier une question" : "Ajouter une question"}</h3><p>L’app la récupère à l’ouverture du Centre d’aide. Hors ligne, l’élève garde la dernière version reçue.</p></div></div>
      <div className="levels-fields">
        <label>Catégorie
          <input list="faq-categories" value={draft.category} maxLength={MAX.category} onChange={e => setDraft(d => ({ ...d, category: e.target.value }))} placeholder="Ex. Démarrer, Compte, Dojo…" required />
          <datalist id="faq-categories">{categories.map(c => <option key={c} value={c} />)}</datalist>
          <small>Choisis une catégorie existante ou tape-en une nouvelle : elle devient un titre de groupe dans l’app.</small>
        </label>
        <label>Question<input value={draft.question} maxLength={MAX.question} onChange={e => setDraft(d => ({ ...d, question: e.target.value }))} placeholder="Ex. À quoi sert le SRS ?" required /></label>
        <label>Réponse<textarea rows={7} value={draft.answer} maxLength={MAX.answer} onChange={e => setDraft(d => ({ ...d, answer: e.target.value }))} placeholder="Texte affiché quand l’élève ouvre la question (les retours à la ligne sont conservés)" required /></label>
        <label>Ordre<input type="number" min="0" step="1" value={draft.order_index} onChange={e => setDraft(d => ({ ...d, order_index: e.target.value }))} required /><small>Plus petit = plus haut. Les catégories suivent l’ordre de leur première question. Tu peux aussi réordonner avec les flèches.</small></label>
      </div>
      <div className="levels-form-actions"><button className="primary" type="submit" disabled={saving || busy}><Save size={15} />{saving ? "Enregistrement…" : editingId ? "Enregistrer" : "Ajouter la question"}</button>{editingId && <button className="secondary" type="button" onClick={reset} disabled={saving}><X size={15} /> Annuler</button>}</div>
    </form>
    <div className="panel levels-list">
      <div className="panel-heading"><div><h3>Questions fréquentes</h3><p>{items.length} question(s) · {categories.length} catégorie(s)</p></div></div>
      <div className="levels-fields" style={{ paddingBottom: 0 }}>
        <label><span style={{ display: "flex", alignItems: "center", gap: 6 }}><Search size={13} /> Filtrer la liste</span><input value={filter} onChange={e => setFilter(e.target.value)} placeholder="Mot de la question, de la réponse ou de la catégorie" /></label>
        {filtering && <small>Réordonnancement désactivé pendant le filtre.</small>}
      </div>
      {items.length === 0
        ? <div className="empty-state"><p>Aucune question pour l’instant : le Centre d’aide de l’app s’affiche vide.</p></div>
        : visible.length === 0
          ? <div className="empty-state"><p>Aucune question ne correspond à ce filtre.</p></div>
          : <div className="levels-items">{visible.map(item => {
            const index = items.findIndex(i => i.id === item.id);
            return <article className="level-card" key={item.id}>
              <div className="level-order">{item.order_index}</div>
              <div className="level-main">
                <div className="level-title"><strong>{excerpt(item.question, 90)}</strong><span className="level-status on">{item.category}</span></div>
                <small>{excerpt(item.answer.replace(/\s+/g, " "), 120)}</small>
              </div>
              <div className="level-actions">
                <button className="icon-button" type="button" title="Monter" disabled={saving || busy || filtering || index <= 0} onClick={() => void move(item, -1)}><ArrowUp size={15} /></button>
                <button className="icon-button" type="button" title="Descendre" disabled={saving || busy || filtering || index >= items.length - 1} onClick={() => void move(item, 1)}><ArrowDown size={15} /></button>
                <button className="icon-button" type="button" title="Modifier" disabled={saving || busy} onClick={() => { setEditingId(item.id); setDraft({ category: item.category, question: item.question, answer: item.answer, order_index: String(item.order_index) }); onNotice(""); }}><Edit3 size={15} /></button>
                <button className="icon-button danger" type="button" title="Supprimer" disabled={saving || busy} onClick={() => void remove(item)}><Trash2 size={15} /></button>
              </div>
            </article>;
          })}</div>}
    </div>
  </div>;
}
