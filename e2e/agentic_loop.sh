#!/usr/bin/env bash
# A bounded LOCAL iteration controlled by the agent that edits the app.
# This script does NOT call an LLM, review screenshots, edit code, or push commits.
set -euo pipefail

usage() {
  echo 'Usage: bash e2e/agentic_loop.sh run|report|status 1|2|3' >&2
  exit 2
}
[[ $# -eq 2 ]] || usage
action="$1"
attempt="$2"
[[ "$action" =~ ^(run|report|status)$ ]] || usage
[[ "$attempt" =~ ^[123]$ ]] || usage

out="e2e-artifacts/attempt-$attempt"
if [[ "$action" == "run" ]]; then
  if [[ -e "$out" ]]; then
    echo "This attempt already exists: $out. Do not overwrite visual evidence." >&2
    exit 2
  fi
  if [[ "$attempt" -gt 1 ]]; then
    previous="e2e-artifacts/attempt-$((attempt - 1))/report.json"
    [[ -f "$previous" ]] || { echo "Finish previous attempt first: $previous" >&2; exit 2; }
    if python3 - "$previous" <<'PY'
import json, sys
summary = json.load(open(sys.argv[1], encoding="utf-8"))["summary"]
sys.exit(0 if summary["FAIL"] == 0 and summary["NOT_RUN"] == 0 else 1)
PY
    then
      echo "Previous attempt passed all criteria; do not run another." >&2
      exit 2
    fi
  fi

  mkdir -p "$out/captures"
  echo "=== Agent-controlled E2E attempt $attempt/3 ==="
  if E2E_OUTPUT_DIR="$out" bash e2e/run_instrumentation.sh; then
    echo "Instrumentation PASS"
  else
    echo "Instrumentation FAIL: inspect $out/instrumentation.log and investigate."
  fi
  if E2E_OUTPUT_DIR="$out" bash e2e/capture_variants.sh; then
    echo "Variant captures collected; not yet visually validated."
  else
    echo "Variant collection incomplete: record FAIL or NOT_RUN after inspection."
  fi
  echo "Agent MUST open screenshots, record verdicts with record_verdict.py, then run:"
  echo "bash e2e/agentic_loop.sh report $attempt"
  exit 0
fi

[[ -d "$out" ]] || { echo "No evidence for attempt $attempt" >&2; exit 2; }
if [[ "$action" == "status" ]]; then
  [[ -f "$out/report.md" ]] && cat "$out/report.md" || echo "Review not completed"
  exit 0
fi

instrumentation=NOT_RUN
if [[ -f "$out/instrumentation.txt" ]]; then
  instrumentation=$(tr -d '[:space:]' < "$out/instrumentation.txt")
fi
python3 e2e/review.py \
  --captures "$out/captures" \
  --out "$out" \
  --verdicts "$out/agent-verdicts.json" \
  --instrumentation "$instrumentation" --strict
