#!/usr/bin/env python3
"""
Exporte intfloat/multilingual-e5-small pour KALYNO :
  app/src/main/assets/semantic/e5_model_int8.onnx   (modèle quantifié int8)
  app/src/main/assets/semantic/e5_vocab.tsv         (vocabulaire élagué, "piece<TAB>score", id = numéro de ligne)

Méthode de la fiche du modèle : XLM-R + moyenne des tokens + normalisation L2, préfixes "query: " / "passage: ".
Le vocabulaire (250 002 pièces) est élagué aux pièces en minuscules latines/chiffres/ponctuation : l'app
met le texte en minuscules (UnigramTokenizer) et le modèle passe de ~118 Mo à ~40 Mo.

    pip install "optimum[onnxruntime]" onnx onnxruntime sentencepiece transformers torch numpy
    python tools/export_e5.py
"""
import re
import sys
from pathlib import Path

import numpy as np
import onnx
import sentencepiece as spm
from onnx import numpy_helper
from onnxruntime.quantization import QuantType, quantize_dynamic
from optimum.onnxruntime import ORTModelForFeatureExtraction
from transformers import AutoTokenizer

MODEL = "intfloat/multilingual-e5-small"
OUT = Path("app/src/main/assets/semantic")
TMP = Path("build/e5_export")
SPECIAL = 4  # <s>, <pad>, </s>, <unk> : mêmes ids que dans l'app
KEEP = re.compile(r"^[\u2581a-z0-9àâäçéèêëîïôöùûüÿœæ\s.,;:!?'’\"«»()\[\]{}<>=+\-*/%^_°€$&@#|\\~…–—]+$")


def main() -> None:
    TMP.mkdir(parents=True, exist_ok=True)
    OUT.mkdir(parents=True, exist_ok=True)

    # 1) Export ONNX FP32 (entrées input_ids + attention_mask, sortie last_hidden_state)
    ORTModelForFeatureExtraction.from_pretrained(MODEL, export=True).save_pretrained(TMP)
    tok = AutoTokenizer.from_pretrained(MODEL)
    sp = spm.SentencePieceProcessor(model_file=tok.vocab_file)

    # 2) Pièces conservées : ids XLM-R (HF) = id sentencepiece + 1 pour les pièces ordinaires
    vocab_size = len(tok)
    keep = list(range(SPECIAL))
    for hf_id in range(SPECIAL, vocab_size):
        spm_id = hf_id - 1
        if spm_id >= sp.get_piece_size():
            continue
        piece = sp.id_to_piece(spm_id)
        if piece == piece.lower() and KEEP.match(piece):
            keep.append(hf_id)
    print(f"{len(keep)} pièces conservées sur {vocab_size}")

    with open(OUT / "e5_vocab.tsv", "w", encoding="utf-8") as f:
        for hf_id in keep:
            if hf_id < SPECIAL:
                piece, score = ("<s>", "<pad>", "</s>", "<unk>")[hf_id], 0.0
            else:
                piece, score = sp.id_to_piece(hf_id - 1), sp.get_score(hf_id - 1)
            f.write(f"{piece}\t{score}\n")

    # 3) Matrice d'embeddings élaguée dans le graphe ONNX
    model = onnx.load(str(TMP / "model.onnx"))
    table = next(
        (i for i in model.graph.initializer
         if len(i.dims) == 2 and i.dims[0] >= vocab_size - 2 and i.dims[1] == 384),
        None,
    )
    if table is None:
        sys.exit("Matrice d'embeddings introuvable dans le graphe ONNX")
    weights = numpy_helper.to_array(table)[np.array(keep)]
    table.CopyFrom(numpy_helper.from_array(weights.astype(np.float32), table.name))
    onnx.save(model, str(TMP / "model_pruned.onnx"))

    # 4) Quantification int8 dynamique
    target = OUT / "e5_model_int8.onnx"
    quantize_dynamic(str(TMP / "model_pruned.onnx"), str(target), weight_type=QuantType.QInt8)
    mb = target.stat().st_size / 1e6
    print(f"{target} : {mb:.1f} Mo")
    if mb > 95:
        sys.exit("Modèle trop lourd pour GitHub (limite 100 Mo par fichier)")

    # 5) Contrôle : l'ordre des similarités doit être cohérent (valeurs attendues entre ~0,7 et 1,0)
    import onnxruntime as ort
    new_id = {old: new for new, old in enumerate(keep)}
    session = ort.InferenceSession(str(target))

    def embed(text: str) -> np.ndarray:
        ids = [new_id.get(i, 3) for i in tok(text.lower())["input_ids"]]
        feeds = {"input_ids": np.array([ids], dtype=np.int64),
                 "attention_mask": np.ones((1, len(ids)), dtype=np.int64)}
        hidden = session.run(None, feeds)[0][0]
        v = hidden.mean(axis=0)
        return v / np.linalg.norm(v)

    passage = embed("passage: Leçon : La photosynthèse - Les plantes fabriquent leur matière. "
                    "Réponse officielle : Elle a lieu dans les chloroplastes")
    for label, text in [
        ("reformulation", "query: ça se passe dans les chloroplastes des cellules végétales"),
        ("hors sujet   ", "query: la capitale de la Côte d'Ivoire est Yamoussoukro"),
    ]:
        print(f"{label} : {float(passage @ embed(text)):.3f}")


if __name__ == "__main__":
    main()
