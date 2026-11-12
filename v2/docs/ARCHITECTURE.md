# Minimal architecture

Standalone Gradle project in v2/, separate from the V1 source.
Single app module at first, split modules only for demonstrated boundaries.
Kotlin Compose, CouiTheme pinned to COUI alpha7, with temporary Material3
adapters. Prioritize true COUI widgets in subsequent UI slices.
Separate presentation, agent run lifecycle, model providers, local Android
tool policy, storage and optional Mastra transport. One runtime for voice/chat.
Application ID fr.loevan.jeancalcul.v2 permits side-by-side installation.
