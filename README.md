# Jean Calcul Assistant

Jean Calcul Assistant est un projet d'application Android open-source visant à transformer un agent Hermes exécuté localement dans Termux en assistant système vocal, contrôlable et sécurisé.

L'objectif n'est pas de recréer toute l'intelligence côté Android. L'application Android agit comme un client système : elle capture la voix ou le texte, appelle Hermes en HTTP local, affiche les réponses, et exécute uniquement les outils Android que l'utilisateur a explicitement autorisés.

## Vision

- Assistant Android open-source inspiré de Gemini.
- Interface minimaliste, glassmorphism / liquid glass, fond gris et dégradés bleu-vert.
- Interaction vocale avec transcription, synthèse vocale et overlay flottant.
- Hermes comme orchestrateur principal : mémoire, planification, sous-agents et tâches longues.
- Protocole HTTP local adapté à Hermes lancé via Termux.
- Outils Android sécurisés : volume, ouverture d'apps, lampe torche, notifications, puis accessibilité optionnelle.
- Permissions granulaires, confirmations locales et journal d'actions.

## Documentation du projet

- [Plan produit complet](docs/plan-complet.md)
- [Architecture HTTP Android ↔ Hermes Termux](docs/hermes-http-android.md)
- [Guide d'implémentation Android](docs/implementation-android.md)
- [Protocole HTTP Hermes](docs/protocole-http-hermes.md)
- [Roadmap](docs/roadmap.md)

## Principe d'architecture

```text
Utilisateur
  -> Application Android
  -> HTTP local vers Hermes dans Termux
  -> Hermes orchestre et retourne des actions structurées
  -> L'application valide permissions / confirmations
  -> L'application exécute les outils Android autorisés
```

Le principe de sécurité central est : **Hermes propose, Android dispose**.

Hermes ne reçoit jamais un accès brut au téléphone. Il demande l'exécution d'un outil, et l'application Android décide localement si cette action est autorisée.
## Compilation Android

Ce dépôt contient un module Android minimal compilable dans `app/`. Pour générer un APK de test :

```bash
ANDROID_HOME=/opt/android-sdk gradle assembleDebug
```

L'APK debug est généré dans `app/build/outputs/apk/debug/app-debug.apk`.
Pour l'installer sur un téléphone connecté en USB avec le débogage Android activé :

```bash
/opt/android-sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

