# Issue #54 — tentative 1/3

Commit : `b95a1fa2d265ae0a0a355fc20c9a69e4c69e9a0b` ; modifications locales : True.
Android API 35 ; émulateur `emulator-5580` ; données synthétiques.

| Critère | Statut |
| --- | --- |
| Tâches Gradle build/lint et instrumentation Compose | FAIL |
| Tests JVM réellement exécutés | NOT_RUN |
| Revue visuelle native des états normaux | FAIL |
| Détection du défaut volontaire | PASS |
| Comparaison aux références Eta sélectionnées par l’utilisateur | PASS |
| Appareil Samsung / retour tactile physique | NOT_RUN |

Références : Eta, commit `4101bc700be7c13b7b72f85a59c1c320f2183c04`,
chat_home.jpg et settings.jpg (sources et empreintes dans references/manifest.json).
La comparaison porte sur les états communs du prototype ; elle ne prouve pas
la parité fonctionnelle avec Eta. La CI ne juge pas l'UX.

Observation comparative : Comparaison réellement effectuée avec chat_home.jpg et settings.jpg Eta. V2 conserve fond clair neutre, surfaces arrondies et saisie basse. Contrairement à Eta, contraste des barres système et agrandissement sont défectueux ; à corriger. Cartes de raccourcis, paramètres fournisseurs et outils Eta absents par périmètre du prototype, aucune commande fictive ajoutée. Pas de référence sombre/clavier/200 % exacte.

| État | Capture et assertions | Revue visuelle | Observation |
| --- | --- | --- | --- |
| empty | PASS | FAIL | État vide lisible et saisie présente ; FAIL : icônes système blanches presque invisibles sur fond clair, saisie collée au geste de navigation. |
| message | PASS | FAIL | Bulle synthétique et avertissement mémoire visibles ; FAIL : barre de statut sans contraste et marge de navigation insuffisante. |
| reset | PASS | FAIL | Message effacé et accueil restauré ; FAIL : mêmes défauts des barres système en clair. |
| settings | PASS | FAIL | Préférences et mention sans moteur IA visibles ; FAIL : statut blanc sur fond clair. Réglages volontairement limités au prototype. |
| dark | PASS | FAIL | Textes, puce et actions lisibles en sombre ; FAIL : champ trop proche de la barre de geste en bas. |
| small_screen | PASS | FAIL | 360×640 dp : actions et texte lisibles, placeholder sur deux lignes ; FAIL : barre système sans contraste et saisie collée au geste. |
| large_text | PASS | FAIL | FAIL : à 200 %, titre et sous-titre empilés sur plusieurs lignes, avertissement « IA non connectée » coupé verticalement ; barre système claire illisible. |
| keyboard | PASS | FAIL | Clavier réellement visible et envoi accessible au-dessus ; FAIL : statut sans contraste en clair. |
| reduced_motion | PASS | FAIL | État statique stable avec animations système désactivées ; FAIL : défauts de statut/navigation identiques à l’accueil clair. Fluidité non déduite du PNG. |
| regression_fixture | PASS | FAIL | FAIL volontaire détecté : rectangle rouge opaque sur toute la largeur basse, masque entièrement champ de saisie et bouton Envoyer. |
