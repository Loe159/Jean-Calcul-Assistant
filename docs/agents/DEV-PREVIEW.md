# Jean-Calcul Preview — show the owner work in progress

**Jean-Calcul Preview** is a second app on the owner's phone (`fr.loevan.jeancalcul.v2.preview`, own data, own icon) that runs development builds. It is installed **next to** the real Jean-Calcul and never replaces it. Agents push builds and review requests to **devhub**, a small server on the dev machine; the owner picks a branch, installs it with one tap, looks, and answers. The answer comes back to the agent's terminal.

Use it whenever the owner has to see, feel or choose something: a screen in progress, A/B/C component variants, motion, haptics, a flow that is not finished. Headless previews (`PREVIEWS.md`) stay your own check; Preview is the owner's.

```
agent worktree ──jc-preview push──▶ devhub (acer-nitro, 127.0.0.1:4120) ◀──Tailscale HTTPS :4120── Preview app
               ──jc-preview ask ───▶   builds/ reviews/ events             (Preview Hub: Reviews · Builds · Components)
               ◀─ verdict + comment ─────────────────────────────────────── owner taps Approve / Request changes
```

## Agent workflow

```sh
scripts/jc-preview push -m "Brief: planning card, first pass"            # build this worktree (ARM64, incremental) + publish
scripts/jc-preview ask "Planning card — which density?" \
    --body "A is the current one, B drops the subtitle." \
    --option A="Current" --option B="Compact:build/previews/Compact.sheet.png" \
    --image build/previews/PlanningCard.sheet.png --wait                # blocks until the owner answers
scripts/jc-preview push -m "…" --ask "Does the new header read well?" --wait   # both in one go
scripts/jc-preview status                                               # branches, builds, open reviews
scripts/jc-preview wait <review-id>                                     # resume waiting on an earlier review
```

- `push` never touches the phone. The owner installs from the app. Pushing and asking need no permission: the owner asked for this channel (2026-11-26). Installing over ADB still needs an explicit request (`DEVICE.md`).
- A review with `--image` reaches the phone in seconds (no build needed). Use rendered sheets from `scripts/preview` for "which of these?" questions, and a build when the owner must touch it.
- `--option ID=LABEL[:image.png][@preview.FQN]` — with `@FQN` the owner can open that `@Preview` live on the phone (real blur, motion, haptics).
- `--wait` exits 0 on **approved** or a **choice** (prints the chosen option), 3 on **changes requested** (prints the comment), 4 on timeout (default 30 min; the review stays open — `wait` again later), and 5 on **cancelled**. Act on the comment; do not ask again for the same thing without changing it.
- Never put secrets, personal data or real accounts in review text or images (same rule as screenshots).

## Owner setup (once)

1. Expose devhub on the tailnet: entry `jean-calcul-devhub` (port 4120) in `~/nixos-config/hosts/acer-nitro/homelab.nix`, then `sudo nixos-rebuild switch`.
2. `scripts/jc-preview service install` — systemd user service, survives reboots.
3. On the phone, open `https://<pc>.<tailnet>.ts.net:4120/` in the browser, download the latest APK, install it once. From then on, everything happens in **Preview Hub**.
4. In Preview Hub, enable **Live link** to get notified of new builds and review requests instantly.

## Build

`-Pjeancalcul.devPreview=true` on `:app:assembleDebug`. `jc-preview push` passes everything; you should not need to call Gradle yourself.

| | Distributed debug | Dev preview |
| --- | --- | --- |
| applicationId | `fr.loevan.jeancalcul.v2` | `fr.loevan.jeancalcul.v2.preview` |
| Label / icon | Jean-Calcul | Jean-Calcul Preview + Preview Hub (dev icon) |
| versionCode | CI run number | fixed `2099000000` — any branch replaces any other, no downgrade block |
| Update channel | `preview-pr-62` | none (devhub only) |
| Build output | `app/build/` | `app/build-devpreview/` (no recompile thrash with your tests) |
| Build identity | `BuildConfig.SOURCE_COMMIT` | `assets/dev-build.json` (changing it recompiles nothing) |

`BuildConfig.DEV_PREVIEW` and `BuildConfig.DEV_HUB_URL` are stable per machine. Per-push data (build id, branch, commit, dirty flag, time) lives only in the `dev-build.json` asset, so an incremental push only re-dexes what you changed.

`jc-preview push` also passes `--project-cache-dir .gradle/dev-preview`: both modes run `:app:assembleDebug`, and separate task history keeps Gradle from deleting the other mode's outputs when switching. Pass it too if you ever call Gradle by hand for a dev build.

The CLI writes `buildId`, `branch`, `commit`, `dirty` and `builtAt` (UTC ISO 8601) in the asset. It also records `worktree`, `message` and `hubUrl` there for diagnostics. A local build with no asset shows **Unknown build**. The app connects using `BuildConfig.DEV_HUB_URL`.

The two apps coexist because nothing in the manifest or code hardcodes `fr.loevan.jeancalcul.v2`: authorities, permissions and package checks derive from `${applicationId}` / `BuildConfig.APPLICATION_ID` / `context.packageName`. `PackageIdentityTest` guards this. Only one app can be the default assistant at a time; switch in Android settings when testing the overlay in Preview.

## devhub API (v1)

Server: `scripts/devhub/devhub.py`, Python stdlib only, binds `127.0.0.1:4120`; Tailscale Serve exposes it over HTTPS to the tailnet. Data: `${XDG_DATA_HOME:-~/.local/share}/jean-calcul-devhub/`.

**Auth.** Agent endpoints (marked *agent*) require `Authorization: Bearer <token>`; the token is generated on first start in `<data>/token` (mode 0600) and read by the CLI. Phone endpoints accept either the token or a request that came through Tailscale Serve (it carries `Tailscale-User-Login`). Anything else gets 403.

**Types.**

```jsonc
Build  { "id": "20261126-221530-a1b2c3", "branch": "feat/x", "worktree": "Jean-Calcul-X",
         "commit": "<40 hex>", "dirty": true, "message": "…", "versionName": "2.0.0-dev+…",
         "sha256": "<64 hex>", "size": 47126495, "createdAt": "2026-11-26T22:15:30Z" }
Review { "id": "r-20261126-221600-7f3a", "branch": "feat/x", "buildId": "…|null",
         "title": "…", "body": "…", "images": ["sheet.png"],
         "options": [{ "id": "A", "label": "…", "image": "a.png|null", "previewFqn": "…|null" }],
         "previewFqn": "…|null", "status": "open|answered|cancelled", "createdAt": "…",
         "answer": null | { "verdict": "approved|changes|choice", "choice": "A|null",
                            "comment": "…", "answeredAt": "…", "installedBuildId": "…|null" } }
Event  { "seq": 42, "type": "build|review|answer|cancel", "id": "<build or review id>", "branch": "…" }
```

| Method & path | Who | Does |
| --- | --- | --- |
| `GET /` | browser | HTML page: latest build per branch with download links (first install) |
| `GET /api/v1/health` | any | `{ "ok": true, "seq": n }` (no auth) |
| `GET /api/v1/state` | phone | `{ "seq", "branches": [{ "branch", "worktree", "builds": [Build…newest first, ≤5] }], "reviews": [Review…open first, ≤30] }` |
| `GET /api/v1/events?since=SEQ&wait=25` | phone | long-poll ≤ `wait` s (max 30): `{ "seq", "events": [Event…] }` |
| `GET /api/v1/builds/{id}/apk` | phone | the APK (`Content-Length`, matches `sha256`) |
| `PUT /api/v1/builds/{id}/apk` | agent | raw APK body; stored as pending |
| `POST /api/v1/builds` | agent | Build JSON (without `size`); server checks the pending APK's SHA-256, publishes, emits `build` |
| `POST /api/v1/reviews` | agent | `{ branch, buildId?, title, body?, previewFqn?, images: [{name, data(base64 PNG)}], options: [{id, label, image?: {name,data}, previewFqn?}] }` → Review |
| `GET /api/v1/reviews/{id}?wait=S` | agent/phone | Review; with `wait`, returns as soon as it is no longer `open` |
| `GET /api/v1/reviews/{id}/images/{name}` | phone | PNG |
| `POST /api/v1/reviews/{id}/answer` | phone | `{ verdict, choice?, comment?, installedBuildId? }` → Review, emits `answer` |
| `POST /api/v1/reviews/{id}/cancel` | agent | emits `cancel` |

The event buffer holds the last 500 events in memory. If `since` is ahead of the current sequence or the requested history is unavailable, including after a restart, the server returns `{ "seq": n, "events": [], "resync": true }`. Fetch `/api/v1/state` and resume from its `seq`.

Retention: 5 builds per branch; a branch with no push for 14 days is dropped with its builds; answered and cancelled reviews are kept 30 days after closure. IDs and names match `[A-Za-z0-9._-]{1,96}` except `.` and `..`; `review.json` is reserved for review metadata. Upload caps are 250 MB for APKs, 8 MB per image, and 30 MB per JSON body.

## Preview Hub (on the phone)

Launcher entry **Preview Hub** (only in dev builds; `DevHubActivity`). Three sections:

- **Reviews** — open requests first. Detail: title, text, full-screen images, options as selectable cards (each can **Open live** its `@Preview`), comment field, **Approve** / **Request changes**. If the review targets another build, **Install this build** before opening its preview live. Approving with a selected option sends a `choice` verdict.
- **Builds** — one row per branch: message, commit, age, "installed" marker. Tap → download, verify (size, SHA-256, package, signing certificate — shared with the in-app updater), install with `PackageInstaller`. Grant install permission once in Settings. The app requests a silent self-update and presents the system confirmation whenever Android requires it. Update ownership can only be established during the initial installation.
- **Components** — every `@Preview` in the build (`assets/dev-previews.json`, generated at build time), searchable, grouped by file. Tap → rendered live through `androidx.compose.ui.tooling.PreviewActivity`.

**Live link** (toggle) runs a foreground service that long-polls `/events` and notifies "New build on `<branch>`" (action **Install**) and "Review: `<title>`". After an update, `MY_PACKAGE_REPLACED` posts "`<branch>` installed — Open".
