#!/usr/bin/env python3
"""
Calibre les seuils de similarité E5 de KALYNO sur de VRAIES réponses d'élèves.

Reproduit le pipeline de l'app (UnigramTokenizer.kt + SemanticEvaluationEngine.kt) : préfixes E5, tokenisation
Unigram sur le vocabulaire élagué, modèle int8, moyenne des tokens, normalisation L2, cosinus, puis garde-fous
(guards.py = portage de LogicGuard.kt et ContentGuard.kt).

Fiche E5 : réponse de l'élève et réponse officielle sont de même nature -> tâche symétrique, « query: » des deux côtés.
Les scores E5 se concentrent entre 0,7 et 1,0 : seul l'ordre relatif compte, et un score élevé ne prouve pas qu'un fait
est exact (réponses fausses mais sur le sujet mesurées entre 0,82 et 0,98). Objectif de la calibration : ZÉRO faux positif.

Usage :
    pip install onnxruntime numpy
    python tools/calibrate_e5.py                    # cas de contrôle intégrés (smoke test)
    python tools/calibrate_e5.py reponses.csv       # calibration sur vos données

reponses.csv (UTF-8, en-tête obligatoire ; la colonne question est facultative) :
    lesson_title,lesson_summary,question,reference,answer,label
    label ∈ correct | partial | incorrect  (jugé par un humain, pas par l'app)

Moins de ~30 exemples par classe = valeurs indicatives seulement.
"""
import csv
import sys
import unicodedata
from pathlib import Path

import numpy as np
import onnxruntime as ort

sys.path.insert(0, str(Path(__file__).resolve().parent))
import guards  # noqa: E402

ASSETS = Path(__file__).resolve().parent.parent / "app/src/main/assets/semantic"
MAX_SEQ = 256        # SemanticEvaluationEngine.Config.maxSeqLen
MAX_CHARS = 1200     # UnigramTokenizer.MAX_CHARS
SPACE = "\u2581"
BOS, EOS, UNK, SPECIAL = 0, 2, 3, 4


class Tok:
    def __init__(self, path: Path):
        self.pieces, scores = {}, []
        for idx, line in enumerate(path.read_text(encoding="utf-8").split("\n")):
            if not line:
                continue
            piece, score = line.rsplit("\t", 1)
            scores.append(float(score))
            if idx >= SPECIAL:
                self.pieces[piece] = idx
        self.scores = scores
        self.max_len = max(len(p) for p in self.pieces)
        self.unk_score = min(scores[SPECIAL:]) - 10.0

    def encode(self, text: str) -> list[int]:
        t = unicodedata.normalize("NFKC", text).replace("\u2019", "'").lower()
        t = " ".join(t.split())[:MAX_CHARS]
        body = self._segment(SPACE + t.replace(" ", SPACE)) if t else []
        body = body[: MAX_SEQ - 2]
        return [BOS] + body + [EOS]

    def _segment(self, s: str) -> list[int]:
        n = len(s)
        best = [float("-inf")] * (n + 1)
        prev_start, prev_id = [0] * (n + 1), [0] * (n + 1)
        best[0] = 0.0
        for i in range(n):
            if best[i] == float("-inf"):
                continue
            has_single = False
            for end in range(i + 1, min(n, i + self.max_len) + 1):
                pid = self.pieces.get(s[i:end])
                if pid is None:
                    continue
                if end == i + 1:
                    has_single = True
                cand = best[i] + self.scores[pid]
                if cand > best[end]:
                    best[end], prev_start[end], prev_id[end] = cand, i, pid
            if not has_single:
                cand = best[i] + self.unk_score
                if cand > best[i + 1]:
                    best[i + 1], prev_start[i + 1], prev_id[i + 1] = cand, i, UNK
        out, pos = [], n
        while pos > 0:
            pid = prev_id[pos]
            if not (pid == UNK and out and out[-1] == UNK):
                out.append(pid)
            pos = prev_start[pos]
        return out[::-1]


class Engine:
    def __init__(self):
        self.tok = Tok(ASSETS / "e5_vocab.tsv")
        self.sess = ort.InferenceSession(str(ASSETS / "e5_model_int8.onnx"))
        self.names = {i.name for i in self.sess.get_inputs()}

    def embed(self, text: str) -> np.ndarray:
        if not (text.startswith("query: ") or text.startswith("passage: ")):
            raise ValueError(f"préfixe E5 manquant : {text[:40]!r}")
        ids = np.array([self.tok.encode(text)], dtype=np.int64)
        feeds = {"input_ids": ids, "attention_mask": np.ones_like(ids)}
        if "token_type_ids" in self.names:
            feeds["token_type_ids"] = np.zeros_like(ids)
        v = self.sess.run(None, feeds)[0][0].mean(axis=0)
        return v / np.linalg.norm(v)

    def score(self, reference: str, answer: str) -> float:
        """Tâche symétrique : « query: » des deux côtés (SemanticEvaluationEngine.evaluate)."""
        return float(self.embed("query: " + reference.strip()) @ self.embed("query: " + answer.strip()))


SMOKE = [
    # (titre, résumé, référence, réponse, label attendu)
    ("La photosynthèse", "Les plantes fabriquent leur matière.", "Elle a lieu dans les chloroplastes",
     "ça se passe dans les chloroplastes des cellules végétales", "correct"),
    ("La photosynthèse", "Les plantes fabriquent leur matière.", "Elle a lieu dans les chloroplastes",
     "dans les cellules", "partial"),
    ("La photosynthèse", "Les plantes fabriquent leur matière.", "Elle a lieu dans les chloroplastes",
     "dans les mitochondries", "incorrect"),
    ("La photosynthèse", "Les plantes fabriquent leur matière.", "Elle a lieu dans les chloroplastes",
     "les plantes ont besoin de lumière", "incorrect"),
    ("La photosynthèse", "Les plantes fabriquent leur matière.", "Elle a lieu dans les chloroplastes",
     "la capitale de la Côte d'Ivoire est Yamoussoukro", "incorrect"),
    ("Le théorème de Pythagore", "Relation entre les côtés d'un triangle rectangle.",
     "Le carré de l'hypoténuse est égal à la somme des carrés des deux autres côtés",
     "hypoténuse au carré = somme des carrés des deux autres côtés", "correct"),
    ("Le théorème de Pythagore", "Relation entre les côtés d'un triangle rectangle.",
     "Le carré de l'hypoténuse est égal à la somme des carrés des deux autres côtés",
     "la somme des angles d'un triangle vaut 180 degrés", "incorrect"),
    ("La décolonisation", "Les indépendances africaines.", "La Côte d'Ivoire est indépendante en 1960",
     "la Côte d'Ivoire a obtenu son indépendance en 1960", "correct"),
    ("La décolonisation", "Les indépendances africaines.", "La Côte d'Ivoire est indépendante en 1960",
     "la Côte d'Ivoire a obtenu son indépendance en 1975", "incorrect"),
]


VALID, PARTIAL = 0.95, 0.88      # SemanticThresholds.DEFAULT
MIN_COVERAGE = 0.5               # ContentGuard.MIN_COVERAGE


def decide(score: float, cov: float, violation, valid=VALID, partial=PARTIAL) -> str:
    if violation:
        return "contradiction"
    if score >= valid and cov >= MIN_COVERAGE:
        return "correct"
    return "partial" if score >= partial else "incorrect"


def main() -> None:
    eng = Engine()
    rows = [dict(title=t, summary=s, question="", reference=r, answer=a, label=l) for t, s, r, a, l in SMOKE]
    if len(sys.argv) > 1:
        with open(sys.argv[1], encoding="utf-8", newline="") as f:
            rows = [dict(title=r["lesson_title"], summary=r["lesson_summary"], question=r.get("question", ""),
                         reference=r["reference"], answer=r["answer"], label=r["label"].strip())
                    for r in csv.DictReader(f)]
    for r in rows:
        r["score"] = eng.score(r["reference"], r["answer"])
        known = r["question"] or (r["title"] + " " + r["summary"])
        r["cov"] = guards.coverage(r["reference"], r["answer"], known)
        r["guard"] = guards.inspect(r["reference"], r["answer"])
        r["verdict"] = decide(r["score"], r["cov"], r["guard"])
        print(f"{r['score']:.3f} cov={r['cov']:.2f} {str(r['guard'] or '-'):4} -> {r['verdict']:13} [{r['label']:9}] {r['answer'][:60]}")

    wrong = [r for r in rows if r["label"] == "incorrect"]
    right = [r for r in rows if r["label"] == "correct"]
    fp = [r for r in wrong if r["verdict"] == "correct"]
    print(f"\nSeuils testés : valid={VALID} partial={PARTIAL} couverture>={MIN_COVERAGE} + garde-fous")
    print(f"Faux positifs (fausse réponse VALIDÉE) : {len(fp)}/{len(wrong)}")
    print(f"Bonnes réponses validées               : {sum(r['verdict'] == 'correct' for r in right)}/{len(right)}")
    for r in fp:
        print(f"  FAUX POSITIF {r['score']:.3f} cov={r['cov']:.2f} : {r['answer']}")
    if fp:
        print("-> Remonter `valid` (SemanticThresholds.DEFAULT), compléter les antonymes de LogicGuard, ou écrire une "
              "réponse officielle plus précise : aucun réglage n'élimine à coup sûr une erreur d'un seul mot.")
    print("\nRappel : une réponse validée par le moteur vaut au mieux « Hésité » (DojoGrading.fromSemantic).")


if __name__ == "__main__":
    main()
