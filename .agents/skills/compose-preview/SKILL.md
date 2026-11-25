---
name: compose-preview
description: Render Jean-Calcul Compose UI to PNG without an emulator to see a change — write @Preview functions with the project's COUI preview kit and run scripts/preview (Google Android CLI `android preview render`). Use whenever you need to look at a composable, verify a visual bug fix, compare light/dark/200 % font, or produce images to show the owner.
---

# Headless Compose previews

Full guide: `docs/agents/PREVIEWS.md`. Essentials:

1. The composable must be stateless (state + lambdas). Split `XScreen(state, on…)` from its wiring if needed.
2. At the bottom of its file:
   ```kotlin
   @CouiPhonePreviews          // light + dark, 393×852 dp   (screens)
   @CouiStressPreviews         // 200 % font + 320×640 dp     (screens you changed)
   @Composable
   private fun XScreenPreview() {
       CouiPreviewTheme { XScreen(state = XPreviewData.populated, onBack = {}) }
   }
   ```
   Components: `@CouiComponentPreviews` + `CouiPreviewTheme(fullScreen = false) { … }`. Kit: `ui/preview/CouiPreviewKit.kt`. Synthetic data only (`FakeAgentUiStates`, `BriefDemoData`, a `XPreviewData` object).
3. Render: `scripts/preview render XScreenPreview` (several names or substrings allowed; `scripts/preview list` to discover). First run in a fresh checkout ≈ 5 min, then ≈ 30–50 s.
4. Open each printed `build/previews/<Preview>.sheet.png` with your image tool and fix every `!` lint line (touch target < 40 dp, unlabeled clickable, text past the edge).
5. Previews are one static frame: no motion, haptics, real blur, IME or system insets. Use `docs/agents/DEVICE.md` for those and say which evidence you have.

Failures: see Troubleshooting in `docs/agents/PREVIEWS.md` (KSP cache corruption, legacy `android` on PATH, build log path).
