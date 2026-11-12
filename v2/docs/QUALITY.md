# Continuous acceptance

GitHub issue -> agent plan -> PR -> APK -> emulator E2E -> screenshots ->
agent vision review (future) -> fixes -> APK -> user accepts/rejects.

CI in first iteration: debug APK, lint/unit tests, Compose instrumentation
on Android 35 and screenshots. NOT YET: autonomous vision evaluator,
visual regression grading, repeated self-healing loop, Samsung device lab.
These are required next steps; do not conflate automation with vision review.
Report exact commit, emulator API, tests PASS/FAIL/NOT_RUN and artifacts.
Never include private account data or secrets in screenshots.
