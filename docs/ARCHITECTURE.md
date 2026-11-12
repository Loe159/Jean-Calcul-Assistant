# Architecture V2

Source indépendante à la racine de cette branche. Une seule application Gradle
pour commencer : `app/`, Kotlin/Jetpack Compose, COUI 0.0.1-alpha7.
`fr.loevan.jeancalcul.v2` permet l'installation côte à côte avec V1.

Le runtime cible comprend, selon les besoins réels :
- Présentation Compose et ViewModel, aucune dépendance UI dans le runtime.
- Agent loop/session : streaming, annulation, outils, gestion d'erreur.
- Fournisseurs : Codex direct, API OpenAI-compatible, endpoint local.
- Outils Android : permissions et politique par action.
- Stockage des conversations, profils et audits.
- Mastra est un backend optionnel, pas un prérequis Android.

Chat et voix doivent utiliser le même runtime. Ne séparer en modules Gradle
que lorsqu'une frontière indépendante est démontrée.

COUI est présent via `CouiTheme`. Les contrôles Material3 du prototype
sont des adaptateurs provisoires. Remplacer progressivement par des
composants COUI natifs avec preuves de rendu sur émulateur.
