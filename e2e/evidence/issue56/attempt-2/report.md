# Issue #56 — tentative 2/3

Commit : `af75f18ecc6eac4605baa911b0abba9a5b709669` ; modifications locales : False.
Dérogation au budget : aucune.
Android API 35 ; émulateur `emulator-5580` ; données synthétiques.

| Critère | Statut |
| --- | --- |
| Tâches Gradle build/lint et instrumentation Compose | FAIL |
| Tests JVM réellement exécutés | PASS |
| Revue visuelle native des états normaux | FAIL |
| Détection du défaut volontaire | PASS |
| Comparaison aux références Eta sélectionnées par l’utilisateur | PASS |
| Appareil Samsung / retour tactile physique | NOT_RUN |

Références : Eta, commit `4101bc700be7c13b7b72f85a59c1c320f2183c04`,
chat_home.jpg et settings.jpg (sources et empreintes dans references/manifest.json).
La comparaison porte sur les états communs du prototype ; elle ne prouve pas
la parité fonctionnelle avec Eta. La CI ne juge pas l'UX.

Observation comparative : Références Eta chat_home.jpg et settings.jpg ouvertes. Même fond neutre, capsule basse et hiérarchie de groupes arrondis. V2 ajoute navigation Chat/Réglages et indication de configuration, ne reprend pas les raccourcis/outils Eta non connectés. Le formulaire fournisseur est plus dense et ses champs vides manquent de délimitation (FAIL à corriger). Sombre, IME, petit écran et 200 % sans référence exacte ; aucune parité pixel ni fonctionnelle revendiquée.

| État | Capture et assertions | Revue visuelle | Observation |
| --- | --- | --- | --- |
| empty | PASS | PASS | Titre, consigne de configuration et stockage local lisibles ; capsule et navigation restent dans les insets. |
| message | PASS | PASS | Bulle sortante synthétique, erreur de configuration réelle et action Réessayer lisibles. Aucune réponse IA inventée. |
| reset | PASS | PASS | Accueil vide rétabli après effacement ; saisie et navigation visibles sans ancien message. |
| settings | PASS | FAIL | FAIL : les trois champs vides se fondent dans la carte blanche, sans contour ni repère de zone de saisie. Le formulaire monopolise les réglages. |
| dark | PASS | PASS | Texte clair et secondaire lisibles sur fond sombre ; capsule et onglet actif identifiables. |
| small_screen | PASS | PASS | 360×640 dp : titre et description reviennent à la ligne ; saisie et navigation restent entièrement accessibles. |
| large_text | PASS | PASS | Police 200 % : corps et onglets agrandis sans chevauchement ; indication de saisie abrégée avec ellipse. Titre COUI fixe, limite alpha7. |
| keyboard | PASS | PASS | Clavier visible, texte et envoi au-dessus de l’IME. En-tête Jean-Calcul et trois actions confirmés par ouverture native du recadrage inspection/keyboard-header.png ; aucune disparition. |
| reduced_motion | PASS | PASS | État stable lisible avec échelle animation à zéro ; capture ne prouve pas la fluidité ni l’haptique. |
| menu | PASS | PASS | Menu COUI ancré sous les options, deux entrées lisibles sans recouvrir la saisie. |
| gallery | PASS | PASS | Palette et bulles illustratives clairement présentées ; bouton dialogue visible et navigation dégagée. |
| dialog | PASS | PASS | Dialogue COUI au-dessus du fond assombri ; titre, message et bouton Fermer lisibles dans les insets. |
| provider_dark | PASS | FAIL | FAIL : champs vides sans contours sur fond de carte sombre, zones éditables peu identifiables. Bouton Enregistrer contrasté. |
| provider_small_screen | PASS | FAIL | FAIL : les champs vides sans bordure sont indiscernables sur la carte. Formulaire défilable ; bouton Enregistrer plus bas, non inspecté dans cette capture. |
| provider_large_text | PASS | FAIL | FAIL : champs vides sans délimitation ; texte agrandi lisible et non superposé, suite sous le pli défilable. |
| regression_fixture | PASS | FAIL | FAIL volontaire : grand rectangle rouge opaque au bas masque la majorité de la capsule, l’envoi et la navigation. |

Erreur : Instrumentation XML contains a failure: TEST-jc-e2e-issue56-2(AVD) - 15.xml
