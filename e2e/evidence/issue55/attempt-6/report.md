# Issue #55 — tentative 6/6

Commit : `f586bc0928172137d06efe4c12faa50b326bf352` ; modifications locales : False.
Dérogation au budget : User explicitly requested continuing fixes until all tests pass; see e2e/evidence/issue55/README.md.
Android API 35 ; émulateur `emulator-5580` ; données synthétiques.

| Critère | Statut |
| --- | --- |
| Tâches Gradle build/lint et instrumentation Compose | PASS |
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
| empty | PASS | PASS | Accueil, capsule et navigation complets ; contraste et marges système lisibles. |
| message | PASS | PASS | Message et avertissement mémoire lisibles ; navigation et bouton bleu de nouvelle conversation sont désormais visibles après fermeture du clavier. |
| reset | PASS | FAIL | Accueil restauré sans message ; la capsule de navigation est vide, icônes et libellés absents. |
| settings | PASS | PASS | Groupes et résumés lisibles, actions et navigation présentes, sans texte coupé. |
| dark | PASS | PASS | Surfaces sombres distinctes, textes et icônes clairs, navigation et statut lisibles. |
| small_screen | PASS | PASS | 360×640 dp : titre replié sur deux lignes, saisie et navigation visibles sans recouvrement. |
| large_text | PASS | PASS | Police 200 % : corps agrandi, navigation à hauteur libre, aucune coupure ; titre COUI fixe documenté. |
| keyboard | PASS | FAIL | Texte saisi et Envoyer au-dessus du clavier ; l’en-tête et ses actions sont toujours absents. |
| reduced_motion | PASS | PASS | État statique avec animations désactivées lisible et navigation complète ; fluidité non prouvée. |
| menu | PASS | PASS | Deux entrées natives lisibles, menu ancré à droite ; saisie et navigation restent visibles. |
| gallery | PASS | FAIL | Exemples et textes lisibles ; seuls les libellés Chat/Réglages sont visibles dans la navigation, sans icônes ni surface. |
| dialog | PASS | PASS | Titre, message et bouton Fermer lisibles, dialogue au-dessus du geste, fond assombri. |
| regression_fixture | PASS | FAIL | Surcouche rouge basse masquant navigation et action d’envoi, défaut volontaire correctement identifié. |
