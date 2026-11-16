#!/usr/bin/env python3
"""Reference/parity harness for Horizon-Labs multilingual-zeroshot-small.

Run with an explicit immutable Hugging Face commit revision (HF_REVISION).
No notification contents are transmitted by this script: use synthetic fixtures only.
This development-only script accesses Hugging Face to fetch model artifacts.
Do NOT package it into the Android APK or use it for mobile inference.
"""
import argparse
import json
import os
import time
from pathlib import Path

MODEL_ID = "Horizon-Labs/multilingual-zeroshot-small"
LABELS = [
    "a promotional advertisement",
    "a personal message",
    "a security alert",
    "a delivery update",
    "an appointment reminder",
    "a useful service update",
    "spam or a scam",
]
TEMPLATE = "This notification is {}."
EXAMPLES = [
    "SHEIN : -70 % seulement aujourd'hui !",
    "Salut, tu peux me rappeler ce soir ?",
    "Google : Nouvelle connexion détectée sur votre compte.",
    "Colissimo : votre colis sera livré demain.",
    "Doctolib : rappel de votre rendez-vous demain à 14 h.",
    "Votre facture du mois est disponible.",
    "URGENT : payez 1,99 € pour débloquer votre colis.",
    "Amazon : découvrez les produits recommandés pour vous.",
    "Salut ! J'ai vu une super promotion, tu veux que je t'envoie le lien ?",
    "🔐 Votre code de connexion est 483921.",
    "La réunion a été décalée à 15 h. À bientôt !",
    "A" * 1200 + " facture disponible",
]


def softmax(values):
    import numpy as np
    values = np.asarray(values, dtype="float64")
    exp = np.exp(values - values.max(axis=-1, keepdims=True))
    return exp / exp.sum(axis=-1, keepdims=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--revision", default=os.getenv("HF_REVISION"), required=False)
    parser.add_argument("--onnx", type=Path, help="Downloaded onnx/model_quantized.onnx")
    parser.add_argument("--output", type=Path, default=Path("poc-results.json"))
    parser.add_argument("--max-length", type=int, default=256)
    args = parser.parse_args()
    if not args.revision or len(args.revision) != 40 or any(c not in "0123456789abcdef" for c in args.revision.lower()):
        parser.error("Provide an immutable 40-hex Hugging Face commit SHA using --revision.")
    import numpy as np
    import torch
    from transformers import AutoTokenizer, AutoModelForSequenceClassification, pipeline

    tokenizer = AutoTokenizer.from_pretrained(MODEL_ID, revision=args.revision, use_fast=True)
    model = AutoModelForSequenceClassification.from_pretrained(MODEL_ID, revision=args.revision)
    model.eval()
    print("id2label:", model.config.id2label)
    id2label = {int(k): str(v).lower() for k, v in model.config.id2label.items()}
    if "entailment" not in id2label.get(1, ""):
        raise RuntimeError("Entailment must be index 1; check model config.")
    pairs = [(example, TEMPLATE.format(label)) for example in EXAMPLES for label in LABELS]
    encoded = tokenizer(
        [a for a, _ in pairs], [b for _, b in pairs],
        padding=True, truncation=True, max_length=args.max_length, return_tensors="pt",
    )
    with torch.inference_mode():
        outputs = model(**{k: v for k, v in encoded.items() if k in ("input_ids", "attention_mask", "token_type_ids")})
    ref_logits = outputs.logits.detach().cpu().numpy().reshape(len(EXAMPLES), len(LABELS), -1)
    if ref_logits.shape[-1] != 2:
        raise RuntimeError(f"Expected two NLI logits, got {ref_logits.shape}")
    ref_single = softmax(ref_logits[:, :, 1])
    ref_multi = softmax(ref_logits)[:, :, 1]
    oracle = pipeline("zero-shot-classification", model=model, tokenizer=tokenizer, device=-1)
    oracle_results = []
    for example in EXAMPLES:
        oracle_results.append(oracle(example, candidate_labels=LABELS, hypothesis_template=TEMPLATE, multi_label=False))
    for i, item in enumerate(oracle_results):
        oracle_by_label = dict(zip(item["labels"], item["scores"]))
        for j, label in enumerate(LABELS):
            if abs(oracle_by_label[label] - float(ref_single[i, j])) > 1e-5:
                raise AssertionError(f"Pipeline parity mismatch at {i}/{label}")
    report = {
        "model": MODEL_ID, "revision": args.revision, "max_length": args.max_length,
        "labels": LABELS, "template": TEMPLATE, "id2label": model.config.id2label,
        "examples": [
            {"text": text, "scores_single": dict(zip(LABELS, map(float, ref_single[i]))),
             "scores_multi": dict(zip(LABELS, map(float, ref_multi[i]))),
             "logits": ref_logits[i].tolist()}
            for i, text in enumerate(EXAMPLES)
        ],
        "tokenizer": {"input_names": list(encoded.keys()),
                      "input_ids": encoded["input_ids"].tolist(),
                      "attention_mask": encoded["attention_mask"].tolist(),
                      "token_type_ids": encoded["token_type_ids"].tolist() if "token_type_ids" in encoded else None},
    }
    if args.onnx:
        import onnxruntime as ort
        session = ort.InferenceSession(str(args.onnx), providers=["CPUExecutionProvider"])
        inputs = {x.name: {"type": x.type, "shape": x.shape} for x in session.get_inputs()}
        report["onnx_inputs"] = inputs
        report["onnx_outputs"] = [{"name": x.name, "type": x.type, "shape": x.shape} for x in session.get_outputs()]
        feed = {name: encoded[name].numpy().astype(np.int64) for name in inputs}
        started = time.perf_counter()
        onnx_logits = session.run(None, feed)[0]
        elapsed = (time.perf_counter() - started) * 1000
        onnx_logits = onnx_logits.reshape(ref_logits.shape)
        report["onnx_comparison"] = {
            "environment": "development host, NOT Android",
            "batch_ms": elapsed,
            "max_absolute_logit_delta": float(np.max(np.abs(onnx_logits - ref_logits))),
            "top_label_agreement": float(np.mean(np.argmax(onnx_logits[:, :, 1], axis=1) == np.argmax(ref_logits[:, :, 1], axis=1))),
            "max_single_score_delta": float(np.max(np.abs(softmax(onnx_logits[:, :, 1]) - ref_single))),
        }
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Wrote {args.output}; only synthetic fixtures. Do not upload real notifications.")


if __name__ == "__main__":
    main()
