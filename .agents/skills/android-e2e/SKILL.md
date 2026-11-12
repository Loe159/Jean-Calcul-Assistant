---
name: android-e2e
description: Tests Android sur émulateur, captures et rapport reproductible.
---
Lire `docs/QUALITY.md`. Exécuter `./gradlew :app:connectedDebugAndroidTest`,
capturer les états demandés, inspecter les logs expurgés.
Ne pas appeler « agentique » une simple capture ou un test instrumenté.
