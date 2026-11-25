# Design documentation

The design system lives in this repository; nothing here depends on an external repo being reachable.

| File | What |
| --- | --- |
| [`../../DESIGN.md`](../../DESIGN.md) | The design system: North Star, color/type/layout/shape/depth rules, component summary, do's and don'ts. Read first. |
| [`../../PRODUCT.md`](../../PRODUCT.md) | Product truth: user, purpose, constraints, voice and language. |
| [`coui-components.md`](coui-components.md) | Which COUI component for which need, project wrappers, exact visual specs, how to find a real signature. |
| [`coui-pitfalls.md`](coui-pitfalls.md) | Mistakes already made, and their fixes. Check before showing UI. |
| [`coui-motion.md`](coui-motion.md) | Springs, easings, durations, haptics, reduced motion. |
| [`now-brief.md`](now-brief.md) | Surface brief of the Now Brief: the one scoped exception to strict COUI. |
| [`coui-tokens.json`](coui-tokens.json) | All COUI 2.0 tokens (116 colors light/dark, 20 text styles, spacing, radii, shadows, easings, durations) with usage notes (French). Machine-readable source of the values. |
| [`references/`](references/) | Now Brief mockup (HTML, open in a browser) and static captures. |
| [`ATTRIBUTION.md`](ATTRIBUTION.md) | Code adapted from `suqi8/coui` (Apache-2.0). |

## Sources

- COUI Compose library `io.github.suqi8.coui.kmp` 2.0.0 — https://github.com/suqi8/coui (tag `v2.0.0`).
- The owner's COUI 2.0 design system artifact (React previews of every component, icon set): https://claude.ai/artifact/RANbrf9sJZDFkyvBNZ2cdv — synced from `suqi8/coui@df5883f`. Use it to show the owner a mock in the browser; the repo copies above are authoritative for agents.
- The owner's Now Brief mockup: https://claude.ai/artifact/DdGPw6iY5guXQaV7Um7r5o (vendored in `references/`).

## Rendered specimens

`app/src/main/kotlin/io/github/mangi/eta/ui/preview/CouiSpecimenPreviews.kt` renders the canonical assemblies with the real theme: `scripts/preview render Specimen`. Add a specimen when you establish a new reusable pattern.

## Keeping this current

When a design decision changes, update `DESIGN.md` (and the relevant file here) in the same change, from the shipped code — not from intentions. When COUI is upgraded, re-check token names (`COUITheme.colorScheme`), wrappers and pitfalls against the new tag.
