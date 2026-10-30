#!/usr/bin/env python3
"""
Génère app/src/test/resources/semantic/tokenizer_golden.tsv : les ids produits par le tokenizer de RÉFÉRENCE du
modèle (sentencepiece via HuggingFace, intfloat/multilingual-e5-small) sur des phrases de cours, convertis dans le
vocabulaire élagué de l'app. Le test Kotlin UnigramTokenizerTest exige les mêmes ids : c'est la preuve que
UnigramTokenizer.kt segmente comme le tokenizer de la fiche du modèle.

    pip install transformers sentencepiece numpy onnxruntime
    python tools/make_tokenizer_golden.py
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
VOCAB = ROOT / "app/src/main/assets/semantic/e5_vocab.tsv"
OUT = ROOT / "app/src/test/resources/semantic/tokenizer_golden.tsv"
MODEL = "intfloat/multilingual-e5-small"

SENTENCES = [
    "query: le noyau contient l'information génétique et contrôle les activités de la cellule",
    "query: la photosynthèse a lieu dans les chloroplastes des cellules végétales",
    "query: dans un triangle rectangle, le carré de l'hypoténuse est égal à la somme des carrés des deux autres côtés",
    "query: la côte d'ivoire est devenue indépendante en 1960 sous la présidence de félix houphouët-boigny",
    "query: la somme des angles d'un triangle est égale à 180 degrés",
    "query: le passage de l'état liquide à l'état gazeux s'appelle la vaporisation",
    "query: le cœur pompe le sang dans tout l'organisme pour apporter l'oxygène aux organes",
    "query: le sud a un climat équatorial de transition avec quatre saisons",
    "query: la démocratie est un régime dans lequel le pouvoir appartient au peuple",
    "query: on forme la négation en encadrant le verbe avec ne et pas",
    "query: x² + 3x = 0 ; résoudre l'équation du premier degré",
    "passage: leçon : la décolonisation - les indépendances africaines. réponse officielle : indépendante en 1960",
    "query: l'eau bout à 100 °c ; elle gèle à 0 °c",
    "query: qu'est-ce que l'état-nation ? l'être humain est-il libre ?",
    "query: the heart pumps blood through the body",
]


def write_golden(hf_ids) -> None:
    """hf_ids(text) -> ids de référence dans le vocabulaire élagué."""
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from calibrate_e5 import Tok

    mine = Tok(VOCAB)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        for text in SENTENCES:
            expected = hf_ids(text)
            if mine.encode(text) != expected:
                sys.exit(f"Tokenizer incohérent avec la référence sur : {text!r}\n  app : {mine.encode(text)}\n  réf : {expected}")
            f.write(text + "\t" + ",".join(map(str, expected)) + "\n")
    print(f"Tokenizer conforme sur {len(SENTENCES)} phrases -> {OUT}")


def main() -> None:
    import sentencepiece as spm
    from transformers import AutoTokenizer

    tok = AutoTokenizer.from_pretrained(MODEL)
    sp = spm.SentencePieceProcessor(model_file=tok.vocab_file)
    pieces = [line.rstrip("\n").rsplit("\t", 1)[0] for line in open(VOCAB, encoding="utf-8")]
    new_id = {p: i for i, p in enumerate(pieces) if i >= 4}

    def hf_ids(text: str) -> list[int]:
        out = []
        for i in tok(" ".join(text.lower().split()))["input_ids"]:
            out.append(i if i < 4 else new_id.get(sp.id_to_piece(i - 1), 3))  # id HF = id sentencepiece + 1
        return out

    write_golden(hf_ids)


if __name__ == "__main__":
    main()
