# Android preview delivery — low-latency CI

The **FAST APK preview** workflow builds the ARM64 app, verifies signing,
package ID, version code, and native tokenizer, then publishes the APK
and SHA-256 manifest to the existing `preview-pr-62` channel **without
waiting for emulator screenshots**. Compilation is mandatory; this does
not publish a stale APK or bypass integrity checks. It is a development
preview and may contain bugs until the separate full validation passes.

The **FULL Android validation** workflow remains separate and runs on
changes to the Now Brief PR, or when manually dispatched. It retains
instrumentation, unit tests, lint, isolated Android 35 emulator and
screenshots. It does **not** gate the fast installable preview.

Both workflows share GitHub's Gradle cache and cache Rust tokenizer builds
separately for ARM64 and emulator x86_64. The fast job compiles the app only
once, rather than building a second instrumented APK and running unit/lint
on the critical path.

Version codes are monotonically derived from the fast workflow's
GitHub run number and attempt (ten slots per run), ensuring that
reruns create a newer APK and older runs cannot overwrite a newer
preview. The manifest is uploaded only after the binary is uploaded.

The app's global channel check compares verified *versionCode*, not a stale
improvement proposal's original SHA. Proposal approval and PR checks remain
strictly separate; this change affects preview installation only. Existing
app builds with the obsolete SHA guard may require one manual install of
the updated signed APK to pick up this fix.
