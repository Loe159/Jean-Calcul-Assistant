# Jean-Calcul V2

Assistant Android expérimental inspiré de l'UX Eta/COUI, développé indépendamment de V1.

## État
- Branche V2 autonome et distincte de `main` (qui conserve V1).
- Application Kotlin/Compose avec thème COUI, écran de discussion et réglages en mode démonstration.
- Aucune inférence IA ni outil Android dans le prototype initial. Les messages ne sont pas persistés.
- `applicationId = fr.loevan.jeancalcul.v2` : installation parallèle à V1 possible.

## Build et tests
Prérequis : JDK 25, Android SDK 37.0 (canal preview), Gradle Wrapper inclus.
```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```
APK local : `app/build/outputs/apk/debug/app-debug.apk`.

## Tester sans compiler
Sur la branche V2, ouvrir [GitHub Actions](https://github.com/Loe159/Jean-Calcul-Assistant/actions/workflows/v2-preview.yml), choisir le dernier run vert, télécharger l'artefact `JeanCalcul-V2-<SHA>`, extraire le ZIP et installer l'APK. Les résultats instrumentés sont séparés de l'APK. Les captures doivent être revues dans l'environnement de l'agent, pas par GitHub Actions.

## Validation visuelle par l'agent développeur

GitHub Actions ne vérifie **pas** la qualité visuelle et n'appelle aucun modèle vision.
L'agent qui réalise une modification doit charger `AGENTS.md` et les skills
`android-e2e` / `android-visual-review`, lancer les tests sur son émulateur,
**ouvrir les captures** et enregistrer des verdicts PASS/FAIL/NOT_RUN motivés,
corriger et retester (maximum trois tentatives). Procédure :
[e2e/README.md](e2e/README.md).

## Agents et développement
Lire [AGENTS.md](AGENTS.md), [docs/PRODUCT.md](docs/PRODUCT.md), [docs/QUALITY.md](docs/QUALITY.md), [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) et [docs/MASTRA-FACTORY.md](docs/MASTRA-FACTORY.md).
Les changements doivent partir d'une issue V2 puis d'une PR **vers cette branche**.
Pas de fusion automatique vers `main`.

## Référence UX
Eta (https://github.com/Mangi-11/Eta) est une inspiration visuelle et fonctionnelle, pas une dépendance applicative. Utiliser COUI-Kit selon sa licence propre et ne pas copier le runtime GPL-3.0 d'Eta sans analyse de licence.
