#!/usr/bin/env python3
"""Write a human-readable, per-criterion verdict made by the implementing agent."""
import argparse
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--list", action="store_true")
    parser.add_argument("--out", type=Path, help="Directory for the current attempt")
    parser.add_argument("--criterion")
    parser.add_argument("--status", choices=["PASS", "FAIL", "NOT_RUN"])
    parser.add_argument("--reason")
    args = parser.parse_args()
    criteria = json.loads((ROOT / "visual-criteria.json").read_text(encoding="utf-8"))["criteria"]
    options = {item["id"]: item for item in criteria if item["type"] != "stability"}
    if args.list:
        for key, value in options.items():
            print(f"{key}: {value['description']} | {', '.join(value['captures'])}")
        return 0
    if not args.out or args.criterion not in options or not args.status or not args.reason:
        parser.error("Provide --out, a valid --criterion, --status and --reason")
    if len(args.reason.strip()) < 12:
        parser.error("Reason must describe observable evidence in at least 12 characters")
    if len(args.reason) > 800:
        parser.error("Reason is too long; do not paste logs or sensitive information")
    if any(t in args.reason for t in ("sk-", "Bearer ", "ghp_", "github_pat_")):
        parser.error("Never include credentials in a review")
    expected = options[args.criterion]["captures"]
    missing = [name for name in expected if not (args.out / "captures" / name).is_file()]
    if args.status == "PASS" and missing:
        parser.error("Cannot mark PASS: missing screenshot evidence " + ", ".join(missing))
    args.out.mkdir(parents=True, exist_ok=True)
    path = args.out / "agent-verdicts.json"
    if path.exists():
        data = json.loads(path.read_text(encoding="utf-8"))
        if data.get("schema_version") != 1 or data.get("reviewer") != "implementing-agent":
            parser.error("Existing review schema is invalid")
    else:
        data = {"schema_version": 1, "reviewer": "implementing-agent", "verdicts": {}}
    data["verdicts"][args.criterion] = {
        "status": args.status,
        "reason": args.reason.strip(),
        "evidence": expected,
    }
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n",
                    encoding="utf-8")
    print(f"{args.criterion}: {args.status} recorded in {path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
