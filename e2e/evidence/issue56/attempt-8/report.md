# Issue #56 — tentative 8/8

Commit : `14674ce83eec5f574aa136f61ce3a03ab8d3aced` ; modifications locales : False.
Dérogation au budget : Oui, autoriser la tentative 8.
Android API 35 ; émulateur `emulator-5580` ; données synthétiques.

| Critère | Statut |
| --- | --- |
| Tâches Gradle build/lint et instrumentation Compose | PASS |
| Tests JVM réellement exécutés | PASS |
| Revue visuelle native du périmètre ciblé | PASS |
| Détection du défaut volontaire | PASS |
| Comparaison aux références Eta sélectionnées par l’utilisateur | PASS |
| Appareil Samsung / retour tactile physique | NOT_RUN |

Références : Eta, commit `4101bc700be7c13b7b72f85a59c1c320f2183c04`,
chat_home.jpg et settings.jpg (sources et empreintes dans references/manifest.json).
La comparaison porte sur les états communs du prototype ; elle ne prouve pas
la parité fonctionnelle avec Eta. La CI ne juge pas l'UX.

Observation comparative : Références Eta chat_home/settings ouvertes : composition claire, saisie flottante, menu gauche/⋯ droit, titre des options et cartes arrondies à icônes bleues repris. Eta montre des suggestions et outils non raccordés à V2 ; ces contrôles ne sont pas affichés. Comparaison des états communs, sans parité pixel ni fonctionnelle.

Périmètre visuel : empty, message, reset, settings, provider_selection, model_selection, dark, small_screen, large_text, keyboard, reduced_motion, menu, gallery, dialog, chatgpt_dark, chatgpt_small_screen, chatgpt_large_text, provider_dark, provider_small_screen, provider_large_text.
Motif : Refonte de l’en-tête global, de la saisie, des options et des écrans fournisseur/modèle ; revue étendue au chat, navigation, galerie, clavier, sombre, petit écran, police 200 % et animations réduites.

| État | Capture et assertions | Revue visuelle | Observation |
| --- | --- | --- | --- |
| empty | PASS | PASS | Chat clair : menu gauche, modèle centré et menu ⋯ alignés ; saisie flottante au bas, sans navigation basse ni chevauchement des barres système. |
| message | PASS | PASS | Bulle utilisateur, erreur de connexion réelle et action Réessayer lisibles ; saisie et en-tête restent dégagés. |
| reset | PASS | PASS | Après confirmation, ancien message absent ; accueil, modèle et saisie vide reviennent sans navigation basse. |
| settings | PASS | PASS | Options : titre large, icônes bleues, groupes blancs arrondis, résumés et chevrons lisibles ; marges et insets dégagés. |
| provider_selection | PASS | PASS | Fournisseurs : ChatGPT et API distincts, sélection indiquée, accès modèle et connexion visibles dans des cartes lisibles. |
| model_selection | PASS | PASS | Modèles : état sans catalogue explicite et lisible ; aucune liste fictive présentée comme disponible. |
| dark | PASS | PASS | Chat sombre : en-tête, texte secondaire et capsule contrastés ; système et saisie dégagés. |
| small_screen | PASS | PASS | À 360×640 dp, titre central et consigne reviennent à la ligne ; boutons et saisie restent visibles sans chevauchement. |
| large_text | PASS | PASS | Police 200 % : titre, consigne et destination du composeur reviennent à la ligne ; aucune superposition visible. |
| keyboard | PASS | PASS | IME visible : brouillon, curseur, envoi et en-tête restent entièrement visibles ; capsule au-dessus du clavier. |
| reduced_motion | PASS | PASS | État statique avec animations réduites : disposition et cibles de la conversation préservées ; fluidité et haptique non mesurées. |
| menu | PASS | PASS | Menu ⋯ ancré à droite : Nouvelle conversation et Galerie COUI lisibles, sans recouvrir la saisie. |
| gallery | PASS | PASS | Galerie : titre large, bulles illustratives et palette visibles ; pas de réponse IA simulée. |
| dialog | PASS | PASS | Dialogue COUI lisible au-dessus de la galerie, action Fermer visible et fond correctement atténué. |
| chatgpt_dark | PASS | PASS | Sélection sombre : cartes anthracite, icônes bleues, résumé de connexion et Continue with ChatGPT contrastés. |
| chatgpt_small_screen | PASS | PASS | Sélection à 360×640 dp : fournisseur, modèle et connexion visibles dans des cartes sans troncature. |
| chatgpt_large_text | PASS | PASS | Police 200 % après défilement : choix du modèle et connexion ChatGPT lisibles ; la section précédente est hors viewport. |
| provider_dark | PASS | PASS | Formulaire API sombre : URL, modèle et clé ont des contours visibles ; bouton Enregistrer et texte de destination lisibles. |
| provider_small_screen | PASS | PASS | Formulaire à 360×640 dp : champs et bouton Enregistrer visibles, sans débordement horizontal ; pied sous le pli. |
| provider_large_text | PASS | PASS | Police 200 % : champs, labels et destination se réorganisent sans chevauchement ; Enregistrer reste sous le pli dans la page défilante. |
| regression_fixture | PASS | FAIL | FAIL attendu : la surcouche rouge de test masque entièrement la capsule de saisie au bas de l’écran. |
