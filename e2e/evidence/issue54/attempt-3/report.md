# Issue #54 — tentative 3/3

Commit : `4756432e42f437572976d43bede06c54610126cc` ; modifications locales : False.
Dérogation au budget : aucune.
Android API 35 ; émulateur `emulator-5580` ; données synthétiques.

| Critère | Statut |
| --- | --- |
| Tâches Gradle build/lint et instrumentation Compose | PASS |
| Tests JVM réellement exécutés | NOT_RUN |
| Revue visuelle native des états normaux | NOT_RUN |
| Détection du défaut volontaire | NOT_RUN |
| Comparaison aux références Eta sélectionnées par l’utilisateur | NOT_RUN |
| Appareil Samsung / retour tactile physique | NOT_RUN |

Références : Eta, commit `4101bc700be7c13b7b72f85a59c1c320f2183c04`,
chat_home.jpg et settings.jpg (sources et empreintes dans references/manifest.json).
La comparaison porte sur les états communs du prototype ; elle ne prouve pas
la parité fonctionnelle avec Eta. La CI ne juge pas l'UX.

Observation comparative : Tentative interrompue : capture reset vide malgré succès instrumenté. Autres états non exécutés.

| État | Capture et assertions | Revue visuelle | Observation |
| --- | --- | --- | --- |
| empty | PASS | PASS | Accueil lisible, statut contrasté, actions et saisie visibles avec marges système. |
| message | PASS | PASS | Bulle synthétique et avertissement mémoire lisibles, saisie non masquée. |
| reset | FAIL | NOT_RUN | Image non examinée |
| settings | NOT_RUN | NOT_RUN | Image non examinée |
| dark | NOT_RUN | NOT_RUN | Image non examinée |
| small_screen | NOT_RUN | NOT_RUN | Image non examinée |
| large_text | NOT_RUN | NOT_RUN | Image non examinée |
| keyboard | NOT_RUN | NOT_RUN | Image non examinée |
| reduced_motion | NOT_RUN | NOT_RUN | Image non examinée |
| regression_fixture | NOT_RUN | NOT_RUN | Image non examinée |

Erreur : Invalid PNG for reset
