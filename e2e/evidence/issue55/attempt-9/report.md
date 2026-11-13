# Issue #55 — tentative 9/9

Commit : `590b8c950f8d7c9ac4d87448ea7e049a970e5bf5` ; modifications locales : False.
Dérogation au budget : User explicitly requested continuing fixes until all tests pass; see e2e/evidence/issue55/README.md.
Android API 35 ; émulateur `emulator-5580` ; données synthétiques.

| Critère | Statut |
| --- | --- |
| Tâches Gradle build/lint et instrumentation Compose | NOT_RUN |
| Tests JVM réellement exécutés | NOT_RUN |
| Revue visuelle native des états normaux | NOT_RUN |
| Détection du défaut volontaire | NOT_RUN |
| Comparaison aux références Eta sélectionnées par l’utilisateur | NOT_RUN |
| Appareil Samsung / retour tactile physique | NOT_RUN |

Références : Eta, commit `4101bc700be7c13b7b72f85a59c1c320f2183c04`,
chat_home.jpg et settings.jpg (sources et empreintes dans references/manifest.json).
La comparaison porte sur les états communs du prototype ; elle ne prouve pas
la parité fonctionnelle avec Eta. La CI ne juge pas l'UX.

Observation comparative : NOT_RUN

| État | Capture et assertions | Revue visuelle | Observation |
| --- | --- | --- | --- |
| empty | NOT_RUN | NOT_RUN | Image non examinée |
| message | NOT_RUN | NOT_RUN | Image non examinée |
| reset | NOT_RUN | NOT_RUN | Image non examinée |
| settings | NOT_RUN | NOT_RUN | Image non examinée |
| dark | NOT_RUN | NOT_RUN | Image non examinée |
| small_screen | NOT_RUN | NOT_RUN | Image non examinée |
| large_text | NOT_RUN | NOT_RUN | Image non examinée |
| keyboard | NOT_RUN | NOT_RUN | Image non examinée |
| reduced_motion | NOT_RUN | NOT_RUN | Image non examinée |
| menu | NOT_RUN | NOT_RUN | Image non examinée |
| gallery | NOT_RUN | NOT_RUN | Image non examinée |
| dialog | NOT_RUN | NOT_RUN | Image non examinée |
| regression_fixture | NOT_RUN | NOT_RUN | Image non examinée |

Erreur : Emulator exited; see logs/emulator-local.txt
