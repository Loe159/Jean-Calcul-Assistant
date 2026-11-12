#!/usr/bin/env python3
"""Aggregate deterministic Android E2E checks and the IMPLEMENTING AGENT's image reviews.

This tool never invokes a model. It cannot decide that an interface looks good.
"""
import argparse
import json
from pathlib import Path
import re
import sys
from PIL import Image, ImageChops

ROOT = Path(__file__).resolve().parent
STATES = {"PASS", "FAIL", "NOT_RUN"}
SAFE_IMAGE = re.compile(r"^[0-9]{2}-[a-z-]+[.]png$")
SECRET = re.compile(r"(?i)(sk-[a-z0-9_-]+|Bearer\s+\S+|gh[pousr]_[a-z0-9_]+)")


def redact(value):
    return SECRET.sub("[REDACTED]", str(value))[:800]


def visual_difference(actual, baseline):
    """A pixel-alarm only. A small score does NOT prove acceptable UI."""
    with Image.open(actual) as image, Image.open(baseline) as reference:
        image, reference = image.convert("RGB"), reference.convert("RGB")
        if image.size != reference.size:
            return 1.0
        histogram = ImageChops.difference(image, reference).histogram()
        changed = sum(histogram[256 * channel + level]
                      for channel in range(3) for level in range(21, 256))
        return changed / (image.width * image.height * 3)


def validate_image(path):
    if not SAFE_IMAGE.fullmatch(path.name) or not path.is_file():
        return False, "absent or unexpected filename"
    try:
        with Image.open(path) as image:
            if image.format != "PNG" or image.width < 300 or image.height < 500:
                return False, "not a valid emulator-sized PNG"
            image.verify()
    except (OSError, ValueError) as exc:
        return False, "unreadable PNG (" + type(exc).__name__ + ")"
    return True, "PNG present; content MUST be inspected by the agent"


def aggregate(checks):
    if "FAIL" in checks:
        return "FAIL"
    if not checks or "NOT_RUN" in checks:
        return "NOT_RUN"
    return "PASS"


def make_report(captures, out, instrumentation, verdict_path, baselines):
    criteria = json.loads((ROOT / "visual-criteria.json").read_text(encoding="utf-8"))["criteria"]
    allowed_ids = {item["id"] for item in criteria if item["type"] != "stability"}
    review = {}
    if verdict_path.is_file():
        doc = json.loads(verdict_path.read_text(encoding="utf-8"))
        if doc.get("schema_version") != 1 or doc.get("reviewer") != "implementing-agent":
            raise ValueError("Invalid implementing-agent review schema")
        review = doc.get("verdicts", {})
        if not isinstance(review, dict) or set(review) - allowed_ids:
            raise ValueError("Unrecognized criterion in agent review")
    rows = []
    for criterion in criteria:
        key = criterion["id"]
        expected = criterion["captures"]
        checks, notes = [], []
        if criterion["type"] == "stability":
            checks.append(instrumentation)
            notes.append("Instrumentation outcome: " + instrumentation)
        else:
            if criterion["type"] == "functional":
                checks.append(instrumentation)
                notes.append("Functional assertions: " + instrumentation)
            for name in expected:
                ok, explanation = validate_image(captures / name)
                notes.append(name + ": " + explanation)
                if not ok:
                    checks.append("FAIL" if instrumentation == "PASS" else "NOT_RUN")
                    continue
                reference = baselines / name
                if reference.is_file():
                    score = visual_difference(captures / name, reference)
                    verdict = "FAIL" if score > 0.025 else "PASS"
                    notes.append("Approved baseline mismatch " + name + ": " + f"{score:.2%}")
                    # Only regressions veto a human PASS; a match is not visual validation.
                    if verdict == "FAIL":
                        checks.append("FAIL")
            observed = review.get(key)
            if observed is None:
                checks.append("NOT_RUN")
                notes.append("No visual review by implementing agent")
            elif not isinstance(observed, dict):
                checks.append("FAIL")
                notes.append("Malformed agent verdict")
            else:
                status = observed.get("status")
                reason = str(observed.get("reason", ""))
                evidence = observed.get("evidence")
                if status not in STATES or len(reason.strip()) < 12 or evidence != expected:
                    checks.append("FAIL")
                    notes.append("Incomplete/invalid agent verdict (status, rationale or evidence)")
                else:
                    checks.append(status)
                    notes.append("Agent visually inspected: " + redact(reason))
        rows.append({"id": key, "type": criterion["type"],
                     "status": aggregate(checks), "evidence": expected, "notes": notes})
    counts = {state: sum(row["status"] == state for row in rows) for state in sorted(STATES)}
    report = {"schema_version": 2, "reviewer": "implementing-agent" if review else None,
              "instrumentation": instrumentation, "summary": counts, "criteria": rows}
    out.mkdir(parents=True, exist_ok=True)
    (out / "report.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n",
                                     encoding="utf-8")
    lines = ["# Jean-Calcul V2 — Android E2E + revue de l'agent", "",
             f"Instrumentation : **{instrumentation}**. "
             f"PASS : {counts['PASS']}, FAIL : {counts['FAIL']}, NOT_RUN : {counts['NOT_RUN']}.", "",
             "| Critère | Type | Résultat |", "|---|---|---|"]
    lines += [f"| {row['id']} | {row['type']} | **{row['status']}** |" for row in rows]
    lines += ["", "## Justificatifs", ""]
    for row in rows:
        lines += ["### " + row["id"], *("- " + redact(n) for n in row["notes"])]
    (out / "report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    return counts


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--captures", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--instrumentation", choices=sorted(STATES), required=True)
    parser.add_argument("--verdicts", type=Path)
    parser.add_argument("--baselines", type=Path, default=ROOT / "baselines")
    parser.add_argument("--strict", action="store_true",
                        help="Fail on FAIL or NOT_RUN (required before claiming a completed review)")
    args = parser.parse_args()
    try:
        counts = make_report(args.captures, args.out, args.instrumentation,
                             args.verdicts or args.out / "agent-verdicts.json", args.baselines)
    except (ValueError, OSError, json.JSONDecodeError) as exc:
        print("Invalid E2E evidence: " + redact(exc), file=sys.stderr)
        return 2
    print(json.dumps({"summary": counts, "report": str(args.out / "report.json")}))
    return 1 if counts["FAIL"] or (args.strict and counts["NOT_RUN"]) else 0


if __name__ == "__main__":
    sys.exit(main())
