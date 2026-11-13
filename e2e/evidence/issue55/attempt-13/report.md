# Issue #55 — tentative 13/13

Commit : `0c63424ea1dc84393a477f6ccd7b825b702c3277` ; modifications locales : False.
Dérogation au budget : User explicitly requested continuing fixes until all tests pass; see e2e/evidence/issue55/README.md.
Android API 35 ; émulateur `emulator-5580` ; données synthétiques.

| Critère | Statut |
| --- | --- |
| Tâches Gradle build/lint et instrumentation Compose | FAIL |
| Tests JVM réellement exécutés | NOT_RUN |
| Revue visuelle native des états normaux | FAIL |
| Détection du défaut volontaire | PASS |
| Comparaison aux références Eta sélectionnées par l’utilisateur | NOT_RUN |
| Appareil Samsung / retour tactile physique | NOT_RUN |

Références : Eta, commit `4101bc700be7c13b7b72f85a59c1c320f2183c04`,
chat_home.jpg et settings.jpg (sources et empreintes dans references/manifest.json).
La comparaison porte sur les états communs du prototype ; elle ne prouve pas
la parité fonctionnelle avec Eta. La CI ne juge pas l'UX.

Observation comparative : 

| État | Capture et assertions | Revue visuelle | Observation |
| --- | --- | --- | --- |
| empty | PASS | PASS | Invitation, en-tête, indication du champ et deux destinations complets. |
| message | PASS | PASS | Message sortant lisible, indication de saisie restaurée et action plus bleue visible. |
| reset | PASS | PASS | État vide restauré, champ et navigation complets. |
| settings | PASS | PASS | Groupes et résumés complets, retour et sélection Réglages visibles. |
| dark | PASS | PASS | Texte clair sur surfaces sombres, indication du champ et navigation lisibles. |
| small_screen | PASS | PASS | Invitation sur deux lignes, capsule et navigation sans chevauchement. |
| large_text | PASS | PASS | Texte agrandi lisible, destinations et champ non recouverts ; titres fixes alpha7. |
| keyboard | PASS | FAIL | FAIL : texte saisi et envoi accessibles, mais en-tête Jean-Calcul et actions absents des pixels sous clavier malgré les sémantiques. |
| reduced_motion | PASS | PASS | État final complet avec capsule, indication et destinations ; fluidité non jugeable sur image. |
| menu | PASS | PASS | Deux entrées COUI lisibles dans le menu blanc, navigation et champ présents. |
| gallery | PASS | PASS | Deux bulles illustratives, palette et dialogue lisibles ; navigation complète. Nuancier blanc faiblement distinct. |
| dialog | PASS | PASS | Titre, explication et action Fermer lisibles sur fond assombri, marges respectées. |
| regression_fixture | PASS | FAIL | Rectangle rouge volontaire masquant envoi et navigation, défaut bloquant détecté. |

Erreur : Instrumentation XML contains a failure: TEST-jc-e2e-issue55-13(AVD) - 15.xml
