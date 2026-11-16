# PR 63 validation and native review

Application source: `64e82cc9ff586ade2d2380a84e13c49d883747b7`.
[Verified workflow](https://github.com/Loe159/Jean-Calcul-Assistant/actions/runs/43776334724).
[Original Android artifact](https://github.com/Loe159/Jean-Calcul-Assistant/actions/runs/43776334724/artifacts/14187655414).
[Original build reports](https://github.com/Loe159/Jean-Calcul-Assistant/actions/runs/43776334724/artifacts/14186613736).

The user explicitly authorized the necessary validation/correction iterations through delivery by replying Oui on 2026-11-15. This is overall validation attempt **9**. The raw isolated CI runner uses its existing ci/attempt-1 naming; this does not reset or conceal the overall history below.

## Results

- ARM64 application and Android instrumentation compilation: PASS.
- JVM: 322 suites, 2,041 tests, 2,040 passed, one existing skipped, zero failure/error.
- Lint: PASS.
- Android 35 synthetic smoke: 3 tests, zero failure/error/skip. Includes actual nonblank native own-window capture, context IPC and exclusion of credential screens.
- All 15 visual scenarios completed. The implementing agent opened every original PNG using native vision.
- 14 normal images: PASS for observed readability/layout. Intentional red regression image: FAIL, so regression detection: PASS.
- Reference comparison: NOT_RUN for the new App improvements page; historical Home/settings references were consulted for COUI patterns. No pixel-parity claim.
- Preview published: `2.0.0-preview.2030009801-debug`, versionCode `2030009801`.
- SHA-256: `d8acaef19b49af024c04a65472c51b872582e42e687ac1d8e98faa8e5415bc3e`; 32588029 bytes; applicationId `fr.loevan.jeancalcul.v2`.

The public update manifest was read and checked against the actual ARM64 build artifact (version, source commit, hash and size). The x86_64 synthetic build suppresses external public update checks so its lower fixture version cannot be interrupted by an unrelated ARM64 preview dialog. Distributed ARM64 builds retain automatic updates.

## Native inspection recovery

The workspace disconnected after compilation. A separate read-only GitHub Actions workflow on the evidence branch retrieved the original artifact, verified every PNG against its recorded SHA-256, and forwarded its unchanged bytes for native vision. [Reader run](https://github.com/Loe159/Jean-Calcul-Assistant/actions/runs/43778407304). No external vision model, personal device or account was used. These evidence-only commits do not change the validated application branch.

## Visual observations

| Scenario | Native result | Observation |
| --- | --- | --- |
| empty | PASS | Home heading, four fully readable wrapping suggestions, provider selector and composer fit without overlap. |
| message | PASS | Synthetic request and local reply are readable; reply action icons remain distinct. |
| reset | PASS | Reset restores an empty Home with Synthetic vLLM selected and a clear composer. |
| settings | PASS | Set up heading, reasoning/context/extensions preferences and capitalized Tools section are legible in English. |
| dark | PASS | Light text, blue icons and distinct cards retain contrast on the dark Home background. |
| small_screen | PASS | All suggestion labels wrap readably on the narrower display; composer and actions fit. |
| large_text | PASS | At 200% text all four suggestions are readable in a single column; composer and its actions remain visible. |
| keyboard | PASS | Actual Gboard keys are visible below the two-line draft; the blue Send button remains above the IME with no overlap. |
| reduced_motion | PASS | Static reduced-motion Home is legible and complete; a still image cannot establish animation smoothness or physical haptics. |
| menu | PASS | New conversation, Open terminal and Open browser popup entries are readable and distinct. |
| providers | PASS | Add a vLLM server and the endpoint summary are English; the ChatGPT/Codex and vLLM local choices are legible. |
| tools | PASS | Capitalized Tools heading and Watch the screen title are readable; the accessibility badge sits below the title and the toggle remains visible. |
| appearance | PASS | Appearance & Theme title fits fully in the compact header; Theme mode, gestures and interface scale controls are readable. |
| improvements | PASS | App improvements heading, Power instructions, GitHub token field and repository-scoped token action are legible in English; remaining footer content continues below the viewport. |
| regression_fixture | FAIL | Intentional solid red rectangle covers the upper Home area, hiding its provider controls and heading. |

## Preserved attempt history

| Attempt | Run | Outcome |
| --- | --- | --- |
| 1 | [43752877353](https://github.com/Loe159/Jean-Calcul-Assistant/actions/runs/43752877353) | Missing exhaustive AppRoute branch (compilation failure). |
| 2 | [43753380170](https://github.com/Loe159/Jean-Calcul-Assistant/actions/runs/43753380170) | Android test ModelConfig fixture omitted mandatory fields (compilation failure). |
| 3 | [43754886940](https://github.com/Loe159/Jean-Calcul-Assistant/actions/runs/43754886940) | JVM development fixture omitted mandatory fields (compilation failure). |
| 4 | [43758493055](https://github.com/Loe159/Jean-Calcul-Assistant/actions/runs/43758493055) | JVM/lint passed; software own-window screenshot failed; native review found typography/wording issues. |
| 5 | [43771248973](https://github.com/Loe159/Jean-Calcul-Assistant/actions/runs/43771248973) | 2,040 JVM and all 3 Android smokes passed; Appearance tap was behind the sticky header; native keyboard screenshot lacked keys. |
| 6 | [43773417512](https://github.com/Loe159/Jean-Calcul-Assistant/actions/runs/43773417512) | Superseded/cancelled to wait for actual keyboard keys; no failed scenario was silently retried. |
| 7 | [43773729216](https://github.com/Loe159/Jean-Calcul-Assistant/actions/runs/43773729216) | Failed-native-copy JVM test exposed cleanup ordering race; corrected in coroutine finally without changing its assertion. |
| 8 | [43774685778](https://github.com/Loe159/Jean-Calcul-Assistant/actions/runs/43774685778) | JVM/lint passed; real public-update modal interrupted synthetic Android scenarios after Wi-Fi reactivation. |
| 9 | [43776334724](https://github.com/Loe159/Jean-Calcul-Assistant/actions/runs/43776334724) | Build/JVM/lint, 3 Android smokes and all 15 capture scenarios passed; signed preview published. |

## Limits

No physical Samsung Power key, haptics or animation-smoothness verification was performed. The model/tool loop uses a scripted provider plus bounded GitHub transport fixtures; a separate Android fixture verifies real loopback vLLM streaming/persistence. A real personal GitHub token was not entered into the app in tests. The user still validates the preview on their phone and connects their repository-scoped token in App improvements. CI failures do not start a second unattended coding turn; the user can request a revision with Power.

`raw-report.json` preserves the original CI report. `report.json` adds the agent's native review results, with matching image hashes in `visual-review.json`. The historical-reference and physical-device limits remain NOT_RUN. PR 63 has not been merged.
