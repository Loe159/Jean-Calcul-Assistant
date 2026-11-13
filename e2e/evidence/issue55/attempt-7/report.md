# Issue #55 — tentative 7/7

Commit : `1e34e259990c9f28ba87ca3b8814e11bacba9f9c` ; modifications locales : False.
Dérogation au budget : User explicitly requested continuing fixes until all tests pass; see e2e/evidence/issue55/README.md.
Android API 35 ; émulateur `emulator-5580` ; données synthétiques.

| Critère | Statut |
| --- | --- |
| Tâches Gradle build/lint et instrumentation Compose | PASS |
| Tests JVM réellement exécutés | NOT_RUN |
| Revue visuelle native des états normaux | FAIL |
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
| empty | PASS | PASS | Accueil, saisie et navigation complets, barres système contrastées. |
| message | PASS | PASS | Bulle lisible, navigation et nouveau bouton bleu visibles après fermeture IME. |
| reset | PASS | PASS | Accueil restauré ; capsule de navigation et icônes complètes après reset. |
| settings | PASS | FAIL | Groupes lisibles mais la navigation basse ne montre que ses libellés, sans icônes ni surface. |
| dark | PASS | PASS | Texte clair lisible, surfaces sombres distinctes et navigation complète. |
| small_screen | PASS | PASS | 360×640 dp, titre replié sans coupure, saisie et navigation accessibles. |
| large_text | PASS | PASS | Corps agrandi et navigation à hauteur libre sans coupure ; titres COUI fixes documentés. |
| keyboard | PASS | PASS | En-tête et ses actions désormais visibles ; saisie et Envoyer au-dessus du clavier, aucun recouvrement. |
| reduced_motion | NOT_RUN | NOT_RUN | Image non examinée |
| menu | NOT_RUN | NOT_RUN | Image non examinée |
| gallery | NOT_RUN | NOT_RUN | Image non examinée |
| dialog | NOT_RUN | NOT_RUN | Image non examinée |
| regression_fixture | NOT_RUN | NOT_RUN | Image non examinée |

Erreur : Execution interrupted after keyboard capture; runner/emulator processes unavailable. Partial evidence retained, no full-run success claimed.
