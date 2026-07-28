# 0013 - Android MVP tools outside volume

Status: accepted
Issue: #31
Tool contract version: 1.0.0

## Decision

The seven phase-1 tools are strict `ToolDefinition` contracts in `core-domain`. Android implementations
remain in `tool-bridge`, except the local-only task store owned by `feature-tasks`. The assistant session
uses one registry containing the existing volume tools and all seven new tools. Every call therefore keeps
the same schema validation, Policy Engine receipt, idempotence, result validation, and persistent audit path.

| Tool | Risk | Default policy | Availability |
| --- | --- | --- | --- |
| `media.play_pause` | R2 | confirm | unlocked device |
| `device.open_settings` | R1 | allow | unlocked device |
| `apps.launch` | R2 | confirm | installed and visible launcher application |
| `device.toggle_flashlight` | R2 | confirm | camera flash and `CAMERA` permission |
| `device.get_battery` | R0 | allow | also available while locked |
| `device.get_local_time` | R0 | allow | also available while locked |
| `tasks.create_local` | R2 | confirm | unlocked device |

`device.open_settings` only starts a documented Android settings intent. It cannot write a protected
setting. `apps.launch` resolves the exact requested package through `PackageManager` before it starts an
activity. Package visibility is limited to launcher applications through the manifest `queries` element.

## Permissions and personal data

`CAMERA` is the only new Android permission. It is used exclusively by `CameraManager.setTorchMode`; a
missing permission produces an explicit Policy Engine `OPEN_SYSTEM_PANEL` decision while the app is in the
foreground, and a denial while locked. The tool is not advertised when either the flash capability or the
permission is absent.

Local tasks contain a title, optional notes, optional due time, and creation time. They are persisted in
private app preferences, are never sent to a provider, and inherit application-data deletion. The action id
produces a stable task id so a replay after process restart does not duplicate a task.

## Android adapters

- Media control dispatches the official play/pause media key through `AudioManager`.
- Settings use official `Settings.ACTION_*` intents.
- Application launch uses the installed package launch intent.
- Flashlight control uses `CameraManager` and a flash-capable camera id.
- Battery data comes from the sticky Android battery broadcast and `PowerManager`.
- Local time uses `java.time` and the device time zone without network access.

Each adapter converts Android failures to stable, parameter-free tool error codes. Raw exceptions and
external values are not logged. `PersistentAuditLogger` continues to redact arguments and results before
Room persistence and export.
