# Improve Jean-Calcul from Jean-Calcul

PR 62 is merged into `feature/jean-calcul-v2-foundation`. This feature keeps that branch as the review target.

## One-time setup

Install the new signed preview through **Check for updates**. Open **Settings > Improve Jean-Calcul** and connect a fine-grained GitHub token scoped only to `Loe159/Jean-Calcul-Assistant`:

- Contents: read/write.
- Pull requests: read/write.
- Actions and Checks: read.
- Metadata: GitHub grants this automatically.

The token remains in the existing Android Keystore encrypted store. It is not sent to ChatGPT/vLLM, source files, APKs or workflow secrets. Disconnect removes it and disables development actions. No OpenAI API key or paid cloud coding service is required: the already configured model performs the code work through bounded GitHub tools.

## Daily flow

1. Open the Jean-Calcul screen that needs a change and hold Power.
2. Describe the bug or desired implementation, for example: “Here the label is cut off. Make it wrap on two lines and create a test version.”
3. The assistant receives the current app screen, version and source revision. It reads repository instructions and relevant source, applies exact changes, creates a branch and opens a PR.
4. The improvement run uses the existing foreground execution service. You may close the assistant overlay or leave the app while it works; **Stop** in the assistant or the ongoing execution notification still cancels it. If the agent needs a decision, it asks one question and ends that turn. A notification opens the saved conversation so you can answer after reopening the app. The same conversation retains development access for that follow-up; other conversations do not.
5. GitHub Actions builds the ARM64 APK, runs JVM/lint and the isolated Android 35 tests. Only successful workflows publish the preview. The app monitors the PR while its process is running and resumes monitoring when reopened. A notification links to progress when notification permission is enabled.
6. The app downloads the newer signed APK. Confirm installation in Android, then test the change.
7. **App improvements > Approve and merge PR #…** merges only the reviewed head after both branch and PR merge checks succeed. A changed head requires Refresh/review. No model tool can merge.
8. If the change needs refinement, answer in the improvement conversation or invoke Power again inside Jean-Calcul. The assistant updates the existing open PR instead of opening duplicate PRs.

## Context and privacy

The snapshot is taken at invocation, before the assistant overlay opens. The existing Android assist screenshot/text callbacks remain in use. An asynchronous native PixelCopy capture of our own Activity window provides a fallback compatible with hardware rendering, without root or accessibility permission. Encoding waits for the copy callback, and closing the assistant defers bitmap disposal until the native copy finishes. Android's user-disabled assist/screenshot settings are respected. Credential configuration screens and secure windows withhold their contents; only screen identity/version are supplied. Screen images and raw app context go only to the selected conversation model, never into PR attachments. The implementation request and code summary are published to the repository: do not include private information in that request.

The trusted screen identity is carried separately over the existing IPC; external runtime clients cannot enable development actions by inserting text into a screenshot or request. Tools are offered only for Power requests inside Jean-Calcul after GitHub is connected. Screen contents are data, not authorization to change code.

## Boundaries and actual guarantees

- The agent edits text source/tests/docs and app build settings, not workflows, credentials, native binaries or arbitrary repositories. It works from the installed source revision so a bootstrap preview retains its own feature code even before its PR is merged.
- Exact blob revisions and unique before/after edits prevent silent overwrites. An interrupted PR publication resumes the same branch. One open improvement PR is tracked at a time.
- Foreground execution keeps an already started task alive when its overlay closes. Android may still terminate the app process or the user may force-stop it; a model turn interrupted this way is recovered as interrupted, not silently replayed. The user can continue in the saved conversation or start another Power request. Notifications require Android's notification permission; the result remains in the conversation when permission is denied.
- CI failure leaves the previous working channel intact. The agent can read bounded, redacted failed-job logs with its GitHub tool. Open PR/build results or ask the assistant to revise; this version does not launch a second unattended coding run after a failed workflow.
- The preview channel retains its `preview-pr-62` URL for compatibility with installed builds; foundation and `self-improve/**` pushes now publish to it. Publishing is serialized and version codes cannot go backwards. The manifest version is the actual build job's output, including publish-job retries.
- While an improvement PR is active, its source commit must match the update manifest before the APK is offered. Closed/merged requests stop pinning the channel.
- Android always requires user approval to install. Background process termination can delay monitoring until reopening the app. Technical screenshots/tests are not native visual approval or proof of real account/device behavior.
