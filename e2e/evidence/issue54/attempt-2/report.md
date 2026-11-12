# Issue #54 — tentative 2/3

Commit : `3b73a468c483475f194239aa9dfb331182edecbb` ; modifications locales : False.
Android API 35 ; émulateur `emulator-5580` ; données synthétiques.

| Critère | Statut |
| --- | --- |
| Tâches Gradle build/lint et instrumentation Compose | PASS |
| Tests JVM réellement exécutés | NOT_RUN |
| Revue visuelle native des états normaux | PASS |
| Détection du défaut volontaire | PASS |
| Comparaison aux références Eta sélectionnées par l’utilisateur | PASS |
| Appareil Samsung / retour tactile physique | NOT_RUN |

Références : Eta, commit `4101bc700be7c13b7b72f85a59c1c320f2183c04`,
chat_home.jpg et settings.jpg (sources et empreintes dans references/manifest.json).
La comparaison porte sur les états communs du prototype ; elle ne prouve pas
la parité fonctionnelle avec Eta. La CI ne juge pas l'UX.

Observation comparative : Comparé aux captures Eta chat_home et settings au commit épinglé : fond neutre, espaces généreux, surfaces arrondies, saisie basse et navigation par icônes cohérents pour les états communs. V2 conserve sa marque et son avertissement de prototype. Cartes de raccourcis et paramètres fournisseurs/outils Eta absents par périmètre actuel ; aucune fonctionnalité fictive ajoutée. Pas de référence exacte sombre, clavier ou police 200 % : ces variantes sont contrôlées pour lisibilité/insets, sans parité pixel à pixel.

| État | Capture et assertions | Revue visuelle | Observation |
| --- | --- | --- | --- |
| empty | PASS | PASS | Accueil et mention prototype entièrement lisibles. Statut sombre sur fond clair ; saisie séparée du geste Android. Titre et actions compacts visibles. |
| message | PASS | PASS | Bulle synthétique à droite et avertissement mémoire lisibles, champ vide et bouton désactivé. Barres système distinctes du contenu. |
| reset | PASS | PASS | Accueil vide restauré, bulle supprimée et champ remis à zéro ; contraste système et marges conservés. |
| settings | PASS | PASS | Préférences et avertissement sans moteur IA lisibles ; retour au chat visible et retour système testé par SmokeTest. Pas de réglages fictifs. |
| dark | PASS | PASS | Textes clairs et puce contrastés sur surfaces sombres, actions visibles, champ au-dessus de la navigation Android. |
| small_screen | PASS | PASS | 360×640 dp : titre/actions et avertissement visibles, placeholder sur deux lignes sans débordement, navigation dégagée. |
| large_text | PASS | PASS | À 200 %, titre sur une ligne et avertissement complet sur deux lignes, aucun texte tronqué. Champ/envoi accessibles et séparés du geste Android. |
| keyboard | PASS | PASS | Clavier système visible ; champ saisi et envoi actif au-dessus de l’IME. En-tête vérifié également par agrandissement diagnostic de la capture : titre, Nouveau et Réglages visibles. |
| reduced_motion | PASS | PASS | Rendu statique lisible, statut contrasté et marges système correctes avec animations désactivées. Le PNG ne valide pas la fluidité physique. |
| regression_fixture | PASS | FAIL | FAIL volontaire : bande rouge opaque masque entièrement le champ et Envoyer sur la partie basse ; défaut visuel effectivement détecté. |
