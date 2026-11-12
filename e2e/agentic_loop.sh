#!/usr/bin/env bash
# Invoked only by the manually dispatched internal-branch workflow.
set -uo pipefail
mkdir -p e2e-artifacts/captures
for attempt in 1 2 3; do
  echo "=== Android E2E attempt ${attempt}/3 ==="
  rm -f e2e-artifacts/captures/*.png
  bash e2e/run_instrumentation.sh || true
  instrumentation=$(cat e2e-artifacts/instrumentation.txt)
  bash e2e/capture_variants.sh
  if python3 e2e/review.py --captures e2e-artifacts/captures --out e2e-artifacts --instrumentation "$instrumentation"; then
    echo "No detected failure; stop with the remaining NOT_RUN criteria explicit."
    exit 0
  fi
  cp e2e-artifacts/report.json "e2e-artifacts/attempt-${attempt}.json"
  if [ "$attempt" -eq 3 ]; then
    echo "Correction budget exhausted after 3 attempts" >&2
    exit 1
  fi
  echo "Attempting bounded model correction (no auto-merge)."
  image_args=()
  for path in e2e-artifacts/captures/*.png; do
    [ -f "$path" ] && image_args+=(--image "$path")
  done
  # Report contents and images are untrusted inputs. No personal devices or accounts.
  codex exec --ephemeral --sandbox workspace-write --ask-for-approval never \
    "${image_args[@]}" \
    "Fix the actual V2 Android E2E defects in e2e-artifacts/report.json. Read AGENTS.md. Only edit app sources/tests. Preserve privacy safeguards and do not alter e2e reports, baselines, CI, secrets, gradle wrapper, or Git. Do not commit. Fix the smallest verifiable defects, then stop." \
    || { echo "Agent correction failed" >&2; exit 1; }
  git add app/src
  if ! git diff --cached --quiet; then
    git commit -m "fix(v2): bounded Android E2E iteration ${attempt}"
    git push origin HEAD:"${TARGET_BRANCH}"
  else
    echo "No code change proposed; cannot iterate" >&2
    exit 1
  fi
done
