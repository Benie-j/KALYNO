import { useEffect, useRef, useState } from "react";
import katex from "katex";
import {
  Bold, Italic, Underline, List, ListOrdered, Heading2, Superscript, Subscript,
  Quote, Eraser, Sigma, Palette, X, Save
} from "lucide-react";
import { ColorPanel, MathKeyboard, PH } from "./FormatKit";

export type CapsuleEditorDraft = {
  title: string;
  body: string;
  kind: string;
  minutes: number;
  is_high_yield: boolean;
};

type Props = {
  initial: CapsuleEditorDraft;
  editing: boolean;
  saving: boolean;
  onCancel: () => void;
  onSave: (draft: CapsuleEditorDraft) => void;
};

function rgbToHex(value: string): string {
  return value.replace(/rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)(?:\s*,\s*[\d.]+)?\s*\)/gi,
    (_m, r: string, g: string, b: string) =>
      "#" + [r, g, b].map(n => Math.min(255, Number(n)).toString(16).padStart(2, "0")).join(""));
}

function cleanHtml(html: string): string {
  const doc = new DOMParser().parseFromString(html, "text/html");
  const allowed = new Set([
    "P", "BR", "STRONG", "B", "EM", "I", "U",
    "UL", "OL", "LI", "H2", "H3", "BLOCKQUOTE",
    "SPAN", "MARK", "SUB", "SUP"
  ]);

  doc.querySelectorAll("script,style,iframe,object,embed,form,svg,math")
    .forEach(node => node.remove());

  doc.body.querySelectorAll("*").forEach(node => {
    for (const attr of Array.from(node.attributes)) {
      if (attr.name.toLowerCase().startsWith("on")) {
        node.removeAttribute(attr.name);
      } else if (attr.name === "class") {
        if (!/^(frac|fn|fd)$/.test(attr.value)) node.removeAttribute("class");
      } else if (attr.name === "style") {
        const safe = rgbToHex(attr.value).split(";").map(part => part.trim())
          .filter(part => /^(color|background-color)\s*:\s*#[0-9a-f]{3,8}$/i.test(part))
          .join("; ");
        if (safe) node.setAttribute("style", safe);
        else node.removeAttribute("style");
      } else {
        node.removeAttribute(attr.name);
      }
    }

    if (!allowed.has(node.tagName)) {
      node.replaceWith(...Array.from(node.childNodes));
    }
  });

  return doc.body.innerHTML.replace(new RegExp(PH, "g"), "");
}

function plainText(html: string): string {
  const box = document.createElement("textarea");
  box.innerHTML = html.replace(/<br\s*\/?>/gi, " ").replace(/<[^>]*>/g, "");
  return box.value;
}

// Aperçu élève : le texte nettoyé, avec chaque $...$ / $$...$$ rendu par KaTeX.
function renderPreview(html: string): string {
  return cleanHtml(html).replace(/\$\$([\s\S]+?)\$\$|\$([^$]+?)\$/g,
    (match: string, block?: string, inline?: string) => {
      try {
        return katex.renderToString(plainText(block ?? inline ?? ""), {
          displayMode: block !== undefined,
          throwOnError: false
        });
      } catch {
        return match;
      }
    });
}

function selectChar(node: Node, index: number) {
  const range = document.createRange();
  range.setStart(node, index);
  range.setEnd(node, index + 1);
  const selection = window.getSelection();
  selection?.removeAllRanges();
  selection?.addRange(range);
}

function findPlaceholder(root: Node, after: Node | null, afterOffset: number): { node: Node; index: number } | null {
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  let passed = after === null;
  let node: Node | null;
  while ((node = walker.nextNode())) {
    const text = node.textContent ?? "";
    if (!passed) {
      if (node === after) {
        passed = true;
        const idx = text.indexOf(PH, afterOffset);
        if (idx >= 0) return { node, index: idx };
      }
      continue;
    }
    const idx = text.indexOf(PH);
    if (idx >= 0) return { node, index: idx };
  }
  return null;
}

export default function CapsuleEditor({
  initial, editing, saving, onCancel, onSave
}: Props) {
  const editorRef = useRef<HTMLDivElement>(null);
  const savedRange = useRef<Range | null>(null);
  const [title, setTitle] = useState(initial.title);
  const [kind, setKind] = useState(initial.kind);
  const [minutes, setMinutes] = useState(initial.minutes);
  const [priority, setPriority] = useState(initial.is_high_yield);
  const [panel, setPanel] = useState<"math" | "color" | null>("math");
  const [tick, setTick] = useState(0);
  const [preview, setPreview] = useState("");

  useEffect(() => {
    if (editorRef.current) editorRef.current.innerHTML = initial.body;
  }, [initial.body]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setPreview(renderPreview(editorRef.current?.innerHTML ?? ""));
    }, 250);
    return () => window.clearTimeout(timer);
  }, [tick, initial.body]);

  function format(command: string, value?: string) {
    editorRef.current?.focus();
    document.execCommand(command, false, value);
  }

  // Les couleurs passent par des styles CSS (sinon le navigateur crée des balises <font> perdues à l'enregistrement).
  function paint(command: "foreColor" | "hiliteColor", color: string) {
    editorRef.current?.focus();
    document.execCommand("styleWithCSS", false, "true");
    document.execCommand(command, false, color);
    document.execCommand("styleWithCSS", false, "false");
  }

  function rememberSelection() {
    const root = editorRef.current;
    const selection = window.getSelection();
    if (root && selection && selection.rangeCount > 0) {
      const range = selection.getRangeAt(0);
      if (root.contains(range.commonAncestorContainer)) savedRange.current = range.cloneRange();
    }
  }

  // Vrai si le curseur est à l'intérieur d'un $...$ (nombre impair de $ avant lui).
  function insideMath(): boolean {
    const root = editorRef.current;
    const selection = window.getSelection();
    if (!root || !selection || selection.rangeCount === 0) return false;
    const range = selection.getRangeAt(0);
    if (!root.contains(range.startContainer)) return false;
    const before = document.createRange();
    before.selectNodeContents(root);
    before.setEnd(range.startContainer, range.startOffset);
    return ((before.toString().match(/\$/g) ?? []).length % 2) === 1;
  }

  function insertTex(tex: string) {
    const root = editorRef.current;
    if (!root) return;
    root.focus();
    const selection = window.getSelection();
    if (selection && savedRange.current && !root.contains(selection.anchorNode)) {
      selection.removeAllRanges();
      selection.addRange(savedRange.current);
    }
    const text = tex.startsWith("$") || insideMath() ? tex : `$${tex}$`;
    document.execCommand("insertText", false, text);
    const after = window.getSelection();
    const node = after?.anchorNode;
    if (after && node && node.nodeType === Node.TEXT_NODE) {
      const content = node.textContent ?? "";
      const idx = content.indexOf(PH, Math.max(0, after.anchorOffset - text.length));
      if (idx >= 0 && idx < after.anchorOffset) selectChar(node, idx);
    }
    setTick(n => n + 1);
  }

  // Tab : champ suivant, puis sortie de la formule.
  function onEditorKey(event: React.KeyboardEvent<HTMLDivElement>) {
    if (event.key !== "Tab" || event.shiftKey) return;
    const root = editorRef.current;
    const selection = window.getSelection();
    if (!root || !selection || selection.rangeCount === 0) return;
    const range = selection.getRangeAt(0);
    const anchor = range.endContainer.nodeType === Node.TEXT_NODE ? range.endContainer : null;
    const found = findPlaceholder(root, anchor, range.endOffset);
    if (found) {
      event.preventDefault();
      selectChar(found.node, found.index);
      return;
    }
    if (anchor && insideMath()) {
      const text = anchor.textContent ?? "";
      let idx = text.indexOf("$", range.endOffset);
      if (idx >= 0) {
        idx += 1;
        if (text[idx] === "$") idx += 1;
        event.preventDefault();
        const next = document.createRange();
        next.setStart(anchor, idx);
        next.collapse(true);
        selection.removeAllRanges();
        selection.addRange(next);
      }
    }
  }

  function submit(event: React.FormEvent) {
    event.preventDefault();
    const cleanTitle = title.trim();
    const body = cleanHtml(editorRef.current?.innerHTML ?? "");
    if (!cleanTitle) {
      window.alert("Le titre du bloc est obligatoire.");
      return;
    }
    if (!body.replace(/<[^>]*>/g, "").trim()) {
      window.alert("Le contenu pédagogique est obligatoire.");
      return;
    }
    onSave({ title: cleanTitle, body, kind, minutes, is_high_yield: priority });
  }

  const tool = (label: string, icon: React.ReactNode, action: () => void, on = false) => (
    <button type="button" title={label} aria-label={label} className={on ? "on" : ""}
      onMouseDown={e => e.preventDefault()} onClick={action}>{icon}</button>
  );

  return (
    <div className="editor-overlay" role="presentation">
      <form className="capsule-editor" onSubmit={submit} role="dialog"
        aria-modal="true" aria-labelledby="capsule-editor-title">
        <header className="editor-header">
          <div>
            <span className="eyebrow">CONTENU PÉDAGOGIQUE</span>
            <h2 id="capsule-editor-title">
              {editing ? "Modifier le bloc" : "Créer un bloc"}
            </h2>
            <p>Écris le texte, puis les formules en LaTeX entre $ … $ (ex. $x = 3x + 2$).</p>
          </div>
          <button type="button" className="icon-button"
            onClick={onCancel} aria-label="Fermer">
            <X size={18}/>
          </button>
        </header>

        <div className="editor-fields">
          <label>
            Titre du bloc
            <input value={title} onChange={e => setTitle(e.target.value)}
              maxLength={180} required placeholder="Ex. : Règle à retenir"/>
          </label>
          <div className="editor-field-grid">
            <label>
              Type de contenu
              <select value={kind} onChange={e => setKind(e.target.value)}>
                <option value="intro">Introduction</option>
                <option value="text">Explication</option>
                <option value="rule">Règle / formule</option>
                <option value="tip">Astuce</option>
                <option value="pitfall">Piège à éviter</option>
              </select>
            </label>
            <label>
              Durée estimée
              <select value={minutes}
                onChange={e => setMinutes(Number(e.target.value))}>
                {[1,2,3,4,5,6,7,8,9,10].map(n =>
                  <option key={n} value={n}>{n} min</option>
                )}
              </select>
            </label>
          </div>
        </div>

        <section className="rich-editor-section">
          <div className="rich-editor-label">
            <strong>Contenu de la leçon</strong>
            <span>Formatage, LaTeX et couleurs</span>
          </div>

          <div className="editor-toolbar">
            {tool("Gras", <Bold size={17}/>, () => format("bold"))}
            {tool("Italique", <Italic size={17}/>, () => format("italic"))}
            {tool("Souligné", <Underline size={17}/>, () => format("underline"))}
            {tool("Exposant", <Superscript size={17}/>, () => format("superscript"))}
            {tool("Indice", <Subscript size={17}/>, () => format("subscript"))}
            <span className="editor-toolbar-divider"/>
            {tool("Titre", <Heading2 size={17}/>, () => format("formatBlock", "h2"))}
            {tool("Citation", <Quote size={17}/>, () => format("formatBlock", "blockquote"))}
            {tool("Liste à puces", <List size={17}/>, () => format("insertUnorderedList"))}
            {tool("Liste numérotée", <ListOrdered size={17}/>, () => format("insertOrderedList"))}
            {tool("Effacer la mise en forme", <Eraser size={17}/>, () => format("removeFormat"))}
            <span className="editor-toolbar-divider"/>
            {tool("Raccourcis LaTeX", <Sigma size={17}/>, () => setPanel(p => (p === "math" ? null : "math")), panel === "math")}
            {tool("Palette de couleurs", <Palette size={17}/>, () => setPanel(p => (p === "color" ? null : "color")), panel === "color")}
          </div>

          {panel === "math" && <MathKeyboard onInsert={insertTex}/>}
          {panel === "color" && (
            <ColorPanel onText={c => paint("foreColor", c)} onBack={c => paint("hiliteColor", c)}/>
          )}

          <div
            ref={editorRef}
            className="rich-editor"
            contentEditable={!saving}
            suppressContentEditableWarning
            role="textbox"
            aria-label="Contenu de la leçon"
            aria-multiline="true"
            onKeyDown={onEditorKey}
            onKeyUp={rememberSelection}
            onMouseUp={rememberSelection}
            onBlur={rememberSelection}
            onInput={() => setTick(n => n + 1)}
            data-placeholder="Écris ici le cours. Pour une formule : $x = 3x + 2$"
          />
          <p className="editor-help">
            Garde chaque formule sur une seule ligne. Les ▢ sont les champs à remplir (Tab pour passer au suivant).
          </p>

          <div className="preview-head"><strong>Aperçu élève</strong><span>Rendu des formules en direct</span></div>
          <div className="preview-box"
            dangerouslySetInnerHTML={{ __html: preview || "<span class=\"muted\">L'aperçu apparaît ici.</span>" }} />
        </section>

        <label className="editor-priority">
          <input type="checkbox" checked={priority}
            onChange={e => setPriority(e.target.checked)}/>
          <span>
            <strong>Bloc prioritaire</strong>
            <small>Signaler ce contenu comme important.</small>
          </span>
        </label>

        <footer className="editor-actions">
          <button type="button" className="secondary" onClick={onCancel}
            disabled={saving}>Annuler</button>
          <button type="submit" className="primary" disabled={saving}>
            <Save size={16}/>{saving ? "Enregistrement…" : "Enregistrer le bloc"}
          </button>
        </footer>
      </form>
    </div>
  );
}
