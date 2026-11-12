# Jean-Calcul V2 — scope

Goal: modern ColorOS-inspired Android assistant, rootless on Samsung,
text/voice/power entry, direct Codex and custom local models, homelab optional.

Delivery slices:
1. Independently installable Compose/COUI UI, automated APK and emulator tests.
2. Real conversation, storage, Codex direct, streaming, cancel and retries.
3. Assistant role, same runtime for chat and voice, on-screen context.
4. Android tools, explicit permissions, confirmations, audit.
5. GUI agent (Accessibility, snapshots, visual verification).
6. MCP, skills, memory, remote Mastra workflows and durable tasks.

Out of scope: OEM hooks, root dependencies, Linux terminal suite, roleplay.
Current UI is only a prototype; it does not contact a model.
