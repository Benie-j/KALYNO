import { useEffect, useState } from "react";
import { ChevronDown, Keyboard } from "lucide-react";
import { MathKeyboard } from "./FormatKit";
import { getActiveField, type FieldHandle } from "./mathBus";

type Quick = { label: string; tip: string; run: (h: FieldHandle) => void };
const tex = (label: string, tip: string, code: string): Quick => ({ label, tip, run: h => h.insert(code) });

const QUICK: Quick[] = [
  { label: "$…$", tip: "Formule dans une phrase (Alt+M)", run: h => h.wrap("$") },
  { label: "$$…$$", tip: "Formule centrée (Alt+Maj+M)", run: h => h.wrap("$$") },
  tex("a/b", "Fraction (Alt+F)", "\\dfrac{▢}{▢}"),
  tex("√", "Racine carrée (Alt+R)", "\\sqrt{▢}"),
  tex("xⁿ", "Puissance (Alt+P)", "^{▢}"),
  tex("x²", "Carré (Alt+2)", "^{2}"),
  tex("xₙ", "Indice (Alt+I)", "_{▢}"),
  tex("×", "Multiplication", "\\times"),
  tex("÷", "Division", "\\div"),
  tex("≤", "Inférieur ou égal", "\\leq"),
  tex("≥", "Supérieur ou égal", "\\geq"),
  tex("≠", "Différent", "\\neq"),
  tex("π", "Pi", "\\pi"),
  tex("∞", "Infini", "\\infty"),
  tex("Σ", "Somme (Alt+S)", "\\sum_{k=▢}^{▢} ▢"),
  tex("∫", "Intégrale (Alt+N)", "\\int_{▢}^{▢} ▢ \\, dx"),
  tex("lim", "Limite (Alt+L)", "\\lim_{x \\to ▢} ▢"),
  tex("→u", "Vecteur (Alt+V)", "\\overrightarrow{▢}"),
  tex("|x|", "Valeur absolue (Alt+A)", "\\left|▢\\right|")
];

/** Barre de formules + clavier complet : agit sur le dernier champ de saisie utilisé. */
export default function MathPalette(_props: { defaultOpen?: boolean } = {}) {
  // Replié par défaut : la palette ne se déplie qu'au clic de l'éditeur.
  const [st, setSt] = useState({ expanded: false, keyboard: false });

  useEffect(() => {
    // Alt+K : déplie la palette avec le clavier complet, ou bascule le clavier si elle est déjà dépliée.
    const toggle = () => setSt(p => (p.expanded ? { ...p, keyboard: !p.keyboard } : { expanded: true, keyboard: true }));
    window.addEventListener("kalyno:keyboard", toggle);
    return () => window.removeEventListener("kalyno:keyboard", toggle);
  }, []);

  const act = (run: (h: FieldHandle) => void) => { const h = getActiveField(); if (h) run(h); };

  return (
    <div className="mp">
      <button type="button" className={`mp-toggle ${st.expanded ? "on" : ""}`} aria-expanded={st.expanded}
        onMouseDown={e => e.preventDefault()}
        onClick={() => setSt(p => ({ expanded: !p.expanded, keyboard: p.expanded ? false : p.keyboard }))}>
        <strong>Formules LaTeX</strong>
        <span>{st.expanded ? "Clique dans un champ, puis un raccourci" : "Afficher les raccourcis"}</span>
        <ChevronDown size={16} className="tool-chevron" />
      </button>
      {st.expanded && (
        <>
          <div className="mp-quick" role="group" aria-label="Raccourcis de formules">
            {QUICK.map(q => (
              <button type="button" key={q.tip} title={q.tip} onMouseDown={e => e.preventDefault()} onClick={() => act(q.run)}>{q.label}</button>
            ))}
            <button type="button" className={`mp-more ${st.keyboard ? "on" : ""}`} title="Clavier complet (Alt+K)"
              onMouseDown={e => e.preventDefault()} onClick={() => setSt(p => ({ ...p, keyboard: !p.keyboard }))}>
              <Keyboard size={15} /> Clavier complet
            </button>
          </div>
          {st.keyboard && <MathKeyboard onInsert={t => act(h => h.insert(t))} />}
        </>
      )}
    </div>
  );
}
