---
name: android-e2e
description: Piloter l'émulateur Android depuis l'environnement de l'agent, lancer les parcours Compose/UI Automator et capturer des états reproductibles sans données privées.
---

Lire `AGENTS.md` et `docs/QUALITY.md`. Ce skill est à exécuter **par l'agent développeur** avant de déclarer son travail terminé.

## Prérequis
- Java 25, SDK Android 37 (preview), `adb`, un émulateur API 34 (AOSP, x86_64, accélération KVM) **dédié à l'application de test**, Python 3 et Pillow (`python3 -m pip install -r e2e/requirements.txt`).
- `adb devices` doit lister **un émulateur**, pas le téléphone réel de l'utilisateur.
- Les scénarios utilisent exclusivement des messages synthétiques, aucun compte, API LLM ou donnée privée.

## Exécution
```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
python3 e2e/test_visual_diff.py
bash e2e/agentic_loop.sh run 1
```
Le script compile et installe APK + AndroidTest, exécute Compose/UI Automator, récupère les captures dans `e2e-artifacts/attempt-1/captures`, puis capture sombre, petit écran, police agrandie et clavier via ADB. Il conserve un statut d'instrumentation et les anomalies d'exécution.

En cas d'ANR/crash de l'OS de l'émulateur : **FAIL stabilité**, ne pas noter les écrans Android comme des écrans Jean-Calcul. Consulter les diagnostics conservés dans le dossier de tentative : `startup.log`, `instrumentation.log`, `android-errors.log`, `app-meminfo.txt` et `captures/00-failure-diagnostic.png`. Séparer démarrage OS, crash de l'app et échec de test avant de corriger.

## Après capture
Charger `.agents/skills/android-visual-review/SKILL.md`. **L'agent ouvre personnellement tous les PNG** et note les anomalies; aucun script de capture ne valide l'UX par lui-même.
