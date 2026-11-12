# Issue #55 — tentative 1/3

Commit : `e6f3c5b6accaac1142bf268b5aa11510b2826d7e` ; modifications locales : False.
Dérogation au budget : aucune.
Android API 35 ; émulateur `emulator-5580` ; données synthétiques.

| Critère | Statut |
| --- | --- |
| Tâches Gradle build/lint et instrumentation Compose | FAIL |
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

Erreur : [HOME]/Projects/Jean-Calcul-Assistant/gradlew failed (1); see logs/gradle.txt
