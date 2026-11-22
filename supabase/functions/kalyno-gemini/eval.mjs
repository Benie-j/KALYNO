// Évaluation manuelle de la correction sémantique sur la fonction déployée.
// Usage (Termux) : KALYNO_JWT=<access_token d'un compte> node supabase/functions/kalyno-gemini/eval.mjs
const URL = "https://nscxeuaouowaemlqjagk.supabase.co/functions/v1/kalyno-gemini";
const KEY = "sb_publishable_PkWTixwM1OedfGURTxVXIQ_JKMUYvCi";
const jwt = process.env.KALYNO_JWT;
if (!jwt) { console.error("Définis KALYNO_JWT"); process.exit(1); }

const ctx = {
  subject: "SVT", level: "3e", lesson: "La photosynthèse",
  question: "Que produit la photosynthèse ?",
  reference: "La photosynthèse produit du glucose (matière organique) et du dioxygène à partir de CO2, d'eau et de lumière.",
  accepted: ["glucose et dioxygène", "sucre et oxygène"],
  explanation: "Les plantes vertes captent la lumière pour fabriquer leur matière.",
};
const cases = [
  ["1 parfaite", "La photosynthèse produit du glucose et du dioxygène à partir de CO2, d'eau et de lumière.", "CORRECT"],
  ["2 reformulée", "Avec la lumière, la plante transforme l'eau et le gaz carbonique en sucre et rejette de l'oxygène.", "CORRECT"],
  ["3 synonymes", "Elle fabrique du sucre et de l'oxygène.", "CORRECT"],
  ["4 partielle", "Elle produit de l'oxygène.", "PARTIAL"],
  ["5 incorrecte", "Elle produit du dioxyde de carbone et de l'eau.", "INCORRECT"],
  ["6 hors sujet", "Le football se joue à onze contre onze.", "INCORRECT"],
  ["7 mots-clés, raisonnement faux", "La plante absorbe du glucose et du dioxygène pour fabriquer du CO2.", "INCORRECT"],
  ["8 ambiguë", "Ça produit des trucs utiles.", "INCORRECT|PARTIAL"],
];
for (const [name, answer, expected] of cases) {
  const res = await fetch(URL, {
    method: "POST",
    headers: { "Content-Type": "application/json", apikey: KEY, Authorization: `Bearer ${jwt}` },
    body: JSON.stringify({ action: "grade", answer, context: ctx }),
  });
  const body = await res.json();
  const v = body?.result?.verdict ?? `ERREUR ${body?.error?.code}`;
  const ok = expected.split("|").includes(v);
  console.log(`${ok ? "OK  " : "KO  "} ${name} -> ${v} (attendu ${expected}) confiance=${body?.result?.confidence ?? "-"} via ${body?.provider ?? "?"}`);
}
