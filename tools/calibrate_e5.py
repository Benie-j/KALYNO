#!/usr/bin/env python3
"""
Calibre les seuils de similarité E5 de KALYNO sur de VRAIES réponses d'élèves.

Reproduit à l'identique le pipeline de l'app (UnigramTokenizer.kt + SemanticEvaluationEngine.kt) :
préfixes "passage: " / "query: ", tokenisation Unigram sur le vocabulaire élagué, modèle int8,
moyenne des tokens, normalisation L2, cosinus.

Usage :
    pip install onnxruntime numpy
    python tools/calibrate_e5.py                    # cas de contrôle intégrés (smoke test)
    python tools/calibrate_e5.py reponses.csv       # calibration sur vos données

reponses.csv (UTF-8, en-tête obligatoire) :
    lesson_title,lesson_summary,reference,answer,label
    label ∈ correct | partial | incorrect  (jugé par un humain, pas par l'app)

Règle de lecture : le seuil `valid` doit être SUPÉRIEUR au meilleur score d'une réponse « incorrect »
(zéro faux positif toléré), le seuil `partial` au meilleur « incorrect » hors-sujet-total.
Moins de ~30 exemples par classe = valeurs indicatives seulement.
"""
import csv
import sys
import unicodedata
from pathlib import Path

import numpy as np
import onnxruntime as ort

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

    def score(self, title: str, summary: str, reference: str, answer: str) -> float:
        ctx = ""
        title, summary = title.strip(), summary.strip().rstrip(". ")
        if title and summary:
            ctx = f"Leçon : {title} - {summary}. "
        elif title:
            ctx = f"Leçon : {title}. "
        passage = "passage: " + ctx + "Réponse officielle : " + reference.strip()
        return float(self.embed(passage) @ self.embed("query: " + answer.strip()))


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


def main() -> None:
    eng = Engine()
    rows = SMOKE
    if len(sys.argv) > 1:
        with open(sys.argv[1], encoding="utf-8", newline="") as f:
            rows = [(r["lesson_title"], r["lesson_summary"], r["reference"], r["answer"], r["label"].strip())
                    for r in csv.DictReader(f)]
    by = {"correct": [], "partial": [], "incorrect": []}
    for title, summary, ref, ans, label in rows:
        s = eng.score(title, summary, ref, ans)
        by.setdefault(label, []).append(s)
        print(f"{s:.3f}  [{label:9}]  {ans[:70]}")
    print()
    for label, vals in by.items():
        if vals:
            print(f"{label:9} n={len(vals):3}  min={min(vals):.3f}  moy={np.mean(vals):.3f}  max={max(vals):.3f}")
    if by["incorrect"] and by["correct"]:
        worst_wrong, worst_right = max(by["incorrect"]), min(by["correct"])
        print(f"\nPire « incorrect » : {worst_wrong:.3f} | pire « correct » : {worst_right:.3f}")
        if worst_right <= worst_wrong:
            print("CHEVAUCHEMENT : aucun seuil ne sépare les classes sans faux positifs -> "
                  "le moteur seul ne suffit pas (garde-fous, modèle plus grand, ou référence mieux rédigée).")
        else:
            print(f"Seuil `valid` possible : entre {worst_wrong:.3f} et {worst_right:.3f} "
                  f"(viser ≈ {worst_wrong + 0.3 * (worst_right - worst_wrong):.3f}, côté strict).")


if __name__ == "__main__":
    main()
