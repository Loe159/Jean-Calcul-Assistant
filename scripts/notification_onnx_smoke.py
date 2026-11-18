#!/usr/bin/env python3
"""Offline ONNX/tokenizer smoke benchmark. Synthetic texts ONLY."""
import argparse
import hashlib
import json
import math
import statistics
import time
from pathlib import Path

REVISION = "17af5f8a728d101ce33a9bffad227b0cd005e95e"
SHA_MODEL = "0ac394942a086fb1c02beb0fde0357bf7f3bd739ca07af7fa6c9fa11c9c05512"
SHA_TOKENIZER = "aebee76d0312011b0d73b08a255d4d11d25c83e9639324870c7b916ea102e13e"
LABELS = [
    "a promotional advertisement", "a personal message", "a security alert",
    "a delivery update", "an appointment reminder", "a useful service update",
    "spam or a scam",
]
EXAMPLES = [
    "SHEIN : -70 % seulement aujourd'hui !",
    "Salut, tu peux me rappeler ce soir ?",
    "Google : Nouvelle connexion détectée sur votre compte.",
    "Colissimo : votre colis sera livré demain.",
    "Doctolib : rappel de votre rendez-vous demain à 14 h.",
    "Votre facture du mois est disponible.",
    "URGENT : payez 1,99 € pour débloquer votre colis.",
    "Amazon : découvrez les produits recommandés pour vous.",
    "Salut ! J'ai vu une super promotion, tu veux le lien ?",
    "🔐 Votre code de connexion est 483921.",
]

def sha256_files(files):
    h = hashlib.sha256()
    for path in files:
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1048576), b""):
                h.update(chunk)
    return h.hexdigest()

def scores(rows, multi):
    import numpy as np
    a = np.asarray(rows, dtype="float64")
    if multi:
        a -= a.max(axis=-1, keepdims=True)
        p = np.exp(a)
        return (p[:, 1] / p.sum(axis=-1)).tolist()
    a = a[:, 1]
    p = np.exp(a - a.max())
    return (p / p.sum()).tolist()

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--model-dir", type=Path, required=True)
    p.add_argument("--output", type=Path, required=True)
    p.add_argument("--threads", type=int, choices=(1, 2, 4), default=1)
    p.add_argument("--max-tokens", type=int, choices=(96, 128, 256), default=128)
    p.add_argument("--repeats", type=int, default=3)
    a = p.parse_args()
    import numpy as np
    import onnxruntime as ort
    from tokenizers import Tokenizer
    base = a.model_dir
    parts = [base / "model_quantized.onnx", *[base / f"model_quantized.part{i}" for i in (1, 2, 3)]]
    if sha256_files(parts) != SHA_MODEL or sha256_files([base / "tokenizer.json"]) != SHA_TOKENIZER:
        raise RuntimeError("Model or tokenizer SHA-256 mismatch")
    tokenizer = Tokenizer.from_file(str(base / "tokenizer.json"))
    tokenizer.enable_truncation(max_length=a.max_tokens, strategy="longest_first")
    tokenizer.enable_padding(pad_id=0, pad_token="<pad>")
    t0 = time.perf_counter()
    model = b"".join(f.read_bytes() for f in parts)
    opts = ort.SessionOptions()
    opts.intra_op_num_threads = a.threads
    opts.inter_op_num_threads = 1
    opts.execution_mode = ort.ExecutionMode.ORT_SEQUENTIAL
    opts.add_session_config_entry("session.intra_op.allow_spinning", "0")
    opts.add_session_config_entry("session.inter_op.allow_spinning", "0")
    load_start = time.perf_counter()
    session = ort.InferenceSession(model, sess_options=opts, providers=["CPUExecutionProvider"])
    load_ms = 1000 * (time.perf_counter() - load_start)
    inputs = [(i.name, i.type, i.shape) for i in session.get_inputs()]
    outputs = [(i.name, i.type, i.shape) for i in session.get_outputs()]
    assert [i[0] for i in inputs] == ["input_ids", "attention_mask"], inputs
    assert outputs[0][0] == "logits", outputs
    results = []
    for idx, example in enumerate(EXAMPLES):
        enc = tokenizer.encode_batch([(example, f"This notification is {label}.") for label in LABELS])
        feed = {
            "input_ids": np.asarray([e.ids for e in enc], dtype=np.int64),
            "attention_mask": np.asarray([e.attention_mask for e in enc], dtype=np.int64),
        }
        elapsed = []
        for _ in range(a.repeats):
            start = time.perf_counter()
            logits = session.run(None, feed)[0]
            elapsed.append(1000 * (time.perf_counter() - start))
        single = scores(logits, False)
        multi = scores(logits, True)
        results.append({
            "fixture_index": idx, "max_tokens": max(map(lambda e: len(e.ids), enc)),
            "top_label": LABELS[max(range(len(LABELS)), key=lambda i: single[i])],
            "scores": single, "multi_scores": multi, "logits": logits.tolist(),
            "p50_ms": statistics.median(elapsed), "p95_ms": sorted(elapsed)[math.ceil(.95 * len(elapsed))-1],
            "first_token_ids": enc[0].ids,
        })
    import resource
    report = {
        "platform": "Linux host (NOT Android)", "revision": REVISION,
        "model_sha256": SHA_MODEL, "tokenizer_sha256": SHA_TOKENIZER,
        "onnxruntime": ort.__version__, "tokenizers": __import__("tokenizers").__version__,
        "threads": a.threads, "max_tokens": a.max_tokens, "labels": LABELS,
        "cold_model_and_session_ms": 1000 * (time.perf_counter() - t0),
        "session_load_ms": load_ms, "peak_rss_kib": resource.getrusage(resource.RUSAGE_SELF).ru_maxrss,
        "inputs": inputs, "outputs": outputs, "results": results
    }
    a.output.parent.mkdir(parents=True, exist_ok=True)
    a.output.write_text(json.dumps(report, ensure_ascii=False, indent=2))
    print(json.dumps({k:v for k,v in report.items() if k != "results"}, indent=2))
    for r in results:
        print(f"fixture {r['fixture_index']} => {r['top_label']} p50={r['p50_ms']:.1f}ms")

if __name__ == "__main__":
    main()
