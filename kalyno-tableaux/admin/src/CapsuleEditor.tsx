import { useEffect, useRef, useState } from "react";
import katex from "katex";
import {
  Bold, Italic, Underline, Strikethrough, List, ListOrdered, Heading2, Superscript, Subscript,
  Quote, Eraser, Sigma, Palette, Square, X, Save, Highlighter, Eye, ChevronDown, Table as TableIcon
} from "lucide-react";
import { ColorPanel, MathKeyboard, PH, PRESETS, PresetBar, type Preset } from "./FormatKit";

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

// Attributs de tableau conservés, avec des valeurs strictement validées.
function keepTableAttr(tag: string, name: string, value: string): boolean {
  const v = value.trim().toLowerCase();
  const cell = tag === "TD" || tag === "TH";
  if (cell && (name === "rowspan" || name === "colspan")) return /^\d{1,2}$/.test(v);
  if (cell && name === "align") return /^(left|center|right)$/.test(v);
  if (cell && name === "valign") return /^(top|middle|bottom)$/.test(v);
  if (cell && name === "width") return /^\d{1,3}%?$/.test(v);
  if (cell && name === "data-answer") return value.length <= 80;
  if (tag === "TABLE" && name === "border") return /^\d$/.test(v);
  if (tag === "TABLE" && name === "width") return /^\d{1,3}%?$/.test(v);
  if (tag === "TABLE" && name === "data-kalyno") return v === "exercise";
  return false;
}

function cleanHtml(html: string): string {
  const doc = new DOMParser().parseFromString(html, "text/html");
  const allowed = new Set([
    "P", "DIV", "BR", "STRONG", "B", "EM", "I", "U", "S", "STRIKE", "DEL",
    "UL", "OL", "LI", "H2", "H3", "BLOCKQUOTE",
    "SPAN", "MARK", "SUB", "SUP",
    "TABLE", "THEAD", "TBODY", "TFOOT", "TR", "TH", "TD", "CAPTION"
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
          .filter(part => /^(color|background-color)\s*:\s*#[0-9a-f]{3,6}$/i.test(part)
            || /^border\s*:\s*\d+px\s+solid\s+#[0-9a-f]{3,6}$/i.test(part)
            || /^padding\s*:\s*\d+px$/i.test(part)
            || /^border-radius\s*:\s*\d+px$/i.test(part)
            || /^border-width\s*:\s*\d+px$/i.test(part)
            || /^border-color\s*:\s*#[0-9a-f]{3,6}$/i.test(part)
            || /^text-align\s*:\s*(left|center|right)$/i.test(part)
            || /^vertical-align\s*:\s*(top|middle|bottom)$/i.test(part)
            || /^width\s*:\s*\d{1,3}(px|%)$/i.test(part)
            || /^font-weight\s*:\s*bold$/i.test(part))
          .join("; ");
        if (safe) node.setAttribute("style", safe);
        else node.removeAttribute("style");
      } else if (!keepTableAttr(node.tagName, attr.name.toLowerCase(), attr.value)) {
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

// Couleur posée à l'intérieur d'une formule : on la convertit en commande KaTeX (comme dans l'appli élève).
function tintFormula(html: string): string {
  let prev: string;
  do {
    prev = html;
    html = html.replace(/<span\b[^>]*?style="([^"]*)"[^>]*>((?:(?!<span\b|<\/span>)[\s\S])*)<\/span>/gi,
      (_m: string, style: string, inner: string) => {
        const color = /(?:^|[;\s])color\s*:\s*(#[0-9a-f]{3,6})/i.exec(style);
        const back = /background-color\s*:\s*(#[0-9a-f]{3,6})/i.exec(style);
        let out = inner;
        if (color) out = `\\textcolor{${color[1]}}{${out}}`;
        if (back) out = `\\colorbox{${back[1]}}{$${out}$}`;
        return out;
      });
  } while (html !== prev);
  return html;
}

// Aperçu élève : le texte nettoyé, avec chaque $...$ / $$...$$ rendu par KaTeX.
function renderPreview(html: string): string {
  return cleanHtml(html).replace(/\$\$([\s\S]+?)\$\$|\$([^$]+?)\$/g,
    (match: string, block?: string, inline?: string) => {
      try {
        return katex.renderToString(plainText(tintFormula(block ?? inline ?? "")), {
          displayMode: block !== undefined,
          throwOnError: false,
          errorColor: "#B91C1C"
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
  // Tous les panneaux d'aide sont repliés : l'éditeur ne les déplie qu'au clic.
  const [tools, setTools] = useState({ math: false, color: false, presets: false, table: false, preview: false });
  const [grid, setGrid] = useState({ rows: 3, cols: 3, header: true });
  const toggleTool = (key: keyof typeof tools) => setTools(t => ({ ...t, [key]: !t[key] }));
  const [tick, setTick] = useState(0);
  const [preview, setPreview] = useState("");
  const [formError, setFormError] = useState("");
  const [brokenList, setBrokenList] = useState<string[]>([]);

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

  function applyPreset(p: Preset) {
    paint("foreColor", p.text);
    paint("hiliteColor", p.bg);
    setTick(n => n + 1);
  }

  function toggleStrike() {
    editorRef.current?.focus();
    document.execCommand("strikeThrough", false);
    setTick(n => n + 1);
  }

  function insertBox(p?: Preset) {
    const root = editorRef.current;
    if (!root) return;
    root.focus();
    document.execCommand("formatBlock", false, "div");
    const selection = window.getSelection();
    const node = selection?.anchorNode;
    const element = node?.nodeType === Node.ELEMENT_NODE
      ? node as HTMLElement
      : node?.parentElement ?? null;
    const block = element?.closest<HTMLElement>("div:not(.rich-editor)");
    if (block && root.contains(block)) {
      block.style.border = `1px solid ${p?.border ?? "#0F766E"}`;
      block.style.backgroundColor = p?.bg ?? "#E6F4F1";
      block.style.padding = "8px";
      block.style.borderRadius = "8px";
    }
    setTick(n => n + 1);
  }

  // Insère un tableau vide (bordure fine) au curseur ; les cellules sont remplies directement dans l'éditeur.
  function insertTable() {
    const root = editorRef.current;
    if (!root) return;
    root.focus();
    const selection = window.getSelection();
    if (selection && savedRange.current && !root.contains(selection.anchorNode)) {
      selection.removeAllRanges();
      selection.addRange(savedRange.current);
    }
    const rows = Math.min(12, Math.max(1, grid.rows));
    const cols = Math.min(8, Math.max(1, grid.cols));
    const cell = (tag: string) => `<${tag}>&nbsp;</${tag}>`;
    const line = (tag: string) => `<tr>${Array.from({ length: cols }, () => cell(tag)).join("")}</tr>`;
    const head = grid.header ? `<thead>${line("th")}</thead>` : "";
    const body = `<tbody>${Array.from({ length: grid.header ? rows - 1 : rows }, () => line("td")).join("")}</tbody>`;
    document.execCommand("insertHTML", false, `<table border="1">${head}${body}</table><p><br></p>`);
    setTick(n => n + 1);
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
    const digit = /^Digit([1-7])$/.exec(event.code);
    if (event.altKey && digit) {
      event.preventDefault();
      const preset = PRESETS[Number(digit[1]) - 1];
      if (event.shiftKey) insertBox(preset); else applyPreset(preset);
      return;
    }
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

  function attempt(force: boolean) {
    const cleanTitle = title.trim();
    const body = cleanHtml(editorRef.current?.innerHTML ?? "");
    setFormError("");
    if (!cleanTitle) {
      setFormError("Le titre du bloc est obligatoire.");
      return;
    }
    if (!body.replace(/<[^>]*>/g, "").trim()) {
      setFormError("Le contenu pédagogique est obligatoire.");
      return;
    }
    const broken: string[] = [];
    const re = /\$\$([\s\S]+?)\$\$|\$([^$]+?)\$/g;
    let m: RegExpExecArray | null;
    while ((m = re.exec(body)) !== null) {
      const src = plainText(m[1] ?? m[2] ?? "");
      try { katex.renderToString(src, { throwOnError: true }); } catch { broken.push(src.slice(0, 70)); }
    }
    if (broken.length && !force) { setBrokenList(broken); return; }
    setBrokenList([]);
    onSave({ title: cleanTitle, body, kind, minutes, is_high_yield: priority });
  }

  function submit(event: React.FormEvent) {
    event.preventDefault();
    attempt(false);
  }

  // Ctrl + Entrée : enregistrer ; Échap : fermer
  function onFormKey(event: React.KeyboardEvent<HTMLFormElement>) {
    if ((event.ctrlKey || event.metaKey) && event.key === "Enter") { event.preventDefault(); attempt(false); }
    else if (event.key === "Escape") { event.preventDefault(); onCancel(); }
  }

  const tool = (label: string, icon: React.ReactNode, action: () => void, on = false) => (
    <button type="button" title={label} aria-label={label} className={on ? "on" : ""}
      onMouseDown={e => e.preventDefault()} onClick={action}>{icon}</button>
  );

  const toggle = (key: keyof typeof tools, label: string, icon: React.ReactNode) => (
    <button type="button" className={`tool-toggle ${tools[key] ? "on" : ""}`} aria-expanded={tools[key]}
      onMouseDown={e => e.preventDefault()} onClick={() => toggleTool(key)}>
      {icon}{label}<ChevronDown size={14} className="tool-chevron"/>
    </button>
  );

  return (
    <div className="editor-overlay" role="presentation">
      <form className="capsule-editor" onSubmit={submit} onKeyDown={onFormKey} role="dialog"
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
            {tool("Barré", <Strikethrough size={17}/>, toggleStrike)}
            {tool("Encadré coloré", <Square size={17}/>, () => insertBox())}
            {tool("Exposant", <Superscript size={17}/>, () => format("superscript"))}
            {tool("Indice", <Subscript size={17}/>, () => format("subscript"))}
            <span className="editor-toolbar-divider"/>
            {tool("Titre", <Heading2 size={17}/>, () => format("formatBlock", "h2"))}
            {tool("Citation", <Quote size={17}/>, () => format("formatBlock", "blockquote"))}
            {tool("Liste à puces", <List size={17}/>, () => format("insertUnorderedList"))}
            {tool("Liste numérotée", <ListOrdered size={17}/>, () => format("insertOrderedList"))}
            {tool("Effacer la mise en forme", <Eraser size={17}/>, () => format("removeFormat"))}
          </div>

          <div className="tool-toggles" role="group" aria-label="Outils d'aide à la rédaction">
            {toggle("math", "Formules LaTeX", <Sigma size={15}/>)}
            {toggle("color", "Couleurs", <Palette size={15}/>)}
            {toggle("presets", "Styles rapides", <Highlighter size={15}/>)}
            {toggle("table", "Tableau", <TableIcon size={15}/>)}
            {toggle("preview", "Aperçu élève", <Eye size={15}/>)}
          </div>

          {tools.presets && <PresetBar onText={applyPreset} onBox={insertBox}/>}
          {tools.table && (
            <div className="table-tool">
              <label>Lignes
                <input type="number" min={1} max={12} value={grid.rows}
                  onChange={e => setGrid(g => ({ ...g, rows: Number(e.target.value) || 1 }))}/>
              </label>
              <label>Colonnes
                <input type="number" min={1} max={8} value={grid.cols}
                  onChange={e => setGrid(g => ({ ...g, cols: Number(e.target.value) || 1 }))}/>
              </label>
              <label className="table-tool-check">
                <input type="checkbox" checked={grid.header} onChange={e => setGrid(g => ({ ...g, header: e.target.checked }))}/>
                Ligne d'en-tête
              </label>
              <button type="button" className="secondary" onMouseDown={e => e.preventDefault()} onClick={insertTable}>Insérer le tableau</button>
              <small>Écris ensuite dans les cases ($…$ pour une formule). Les cases vides restent vides pour l'élève.</small>
            </div>
          )}
          {tools.math && <MathKeyboard onInsert={insertTex}/>}
          {tools.color && (
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

          {tools.preview && (
            <>
              <div className="preview-head"><strong>Aperçu élève</strong><span>Rendu des formules en direct</span></div>
              <div className="preview-box"
                dangerouslySetInnerHTML={{ __html: preview || "<span class=\"muted\">L'aperçu apparaît ici.</span>" }} />
            </>
          )}
        </section>

        <label className="editor-priority">
          <input type="checkbox" checked={priority}
            onChange={e => setPriority(e.target.checked)}/>
          <span>
            <strong>Bloc prioritaire</strong>
            <small>Signaler ce contenu comme important.</small>
          </span>
        </label>

        {formError && <p className="ep-errors" role="alert">{formError}</p>}
        {brokenList.length > 0 && (
          <div className="ep-errors" role="alert">
            <strong>{brokenList.length} formule(s) invalide(s)</strong>
            <ul>{brokenList.map((b, i) => <li key={i}><code>{b}</code></li>)}</ul>
            <button type="button" className="secondary" onClick={() => attempt(true)}>Enregistrer quand même</button>
          </div>
        )}

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
