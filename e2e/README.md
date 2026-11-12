# Agentic Android E2E — issue #54

The suite uses Android Compose instrumentation plus UI Automator, an Android 35 emulator, scenario screenshots and structured PASS/FAIL/NOT_RUN output. A screenshot is **not** a vision review.

## CI

PRs targeting `feature/jean-calcul-v2-foundation` run `v2-preview.yml`. Download the APK, raw screenshots, `report.json`, and `report.md` from workflow artifacts. The tests use *only fake in-memory messages*; never run these captures against a signed-in device or import personal data.

Visual reviews use `OPENAI_API_KEY` (GitHub Actions secret) if configured, via OpenAI's vision-capable API. Without that secret, visual criteria are marked NOT_RUN, not PASS. Reference images are optional and must be approved, synthetic/non-private screenshots stored in `e2e/baselines/` with filenames matching the scenarios. Without a baseline, pixel comparison is NOT_RUN. A qualitative Eta/COUI checklist lives in `visual-criteria.json`; qualitative references do not imply pixel parity.

## Local run

```sh
./gradlew :app:connectedDebugAndroidTest
adb pull /sdcard/Android/data/fr.loevan.jeancalcul.v2/files/e2e e2e-artifacts/
python3 -m pip install -r e2e/requirements.txt
python3 e2e/review.py --captures e2e-artifacts/e2e --out e2e-artifacts --instrumentation PASS
```

CI also captures night mode, small screen, large font and keyboard states with ADB. `review.py` can run without secrets (it still evaluates expected files, dimensions, baselines and the instrumentation result). To exercise the deterministic visual-defect detector:

```sh
python3 e2e/test_visual_diff.py
```

## Autonomous correction

The opt-in workflow `v2-agentic-fix.yml` is launched manually on an *internal branch* only. It requires `OPENAI_API_KEY`, verifies the test report after every attempt and caps the loop at **3**. The model's corrections are committed only to the selected feature branch; it never approves or merges a PR. The main preview workflow does not modify source. Treat every proposed patch as untrusted and review it before acceptance.

The automation cannot certify aesthetic parity with Eta. Keep UX criteria FAIL or NOT_RUN where visual evidence is inconclusive, and do not claim a green CI is product validation.
