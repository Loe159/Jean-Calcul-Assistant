# Issue #56 — tentative 3/3

Commit : `b41453484e7edeb51decac23a230ce5d9068e3b4` ; modifications locales : False.
Dérogation au budget : aucune.
Android API 35 ; émulateur `emulator-5580` ; données synthétiques.

| Critère | Statut |
| --- | --- |
| Tâches Gradle build/lint et instrumentation Compose | PASS |
| Tests JVM réellement exécutés | PASS |
| Revue visuelle native des états normaux | PASS |
| Détection du défaut volontaire | PASS |
| Comparaison aux références Eta sélectionnées par l’utilisateur | PASS |
| Appareil Samsung / retour tactile physique | NOT_RUN |

Références : Eta, commit `4101bc700be7c13b7b72f85a59c1c320f2183c04`,
chat_home.jpg et settings.jpg (sources et empreintes dans references/manifest.json).
La comparaison porte sur les états communs du prototype ; elle ne prouve pas
la parité fonctionnelle avec Eta. La CI ne juge pas l'UX.

Observation comparative : Les JPG Eta chat_home et settings de cette tentative ont été ouverts avec les captures V2. Continuité du fond neutre, de la capsule basse et des groupes arrondis avec titres/résumés. V2 conserve sa navigation Chat/Réglages, affiche la configuration réelle et ajoute des champs bordés ; aucun raccourci Eta non connecté ni réglage factice. Pas de parité pixel/fonctionnelle revendiquée. Les états sombre, clavier, 360×640 et 200 % ont été inspectés pour lisibilité/insets sans référence Eta exacte.

| État | Capture et assertions | Revue visuelle | Observation |
| --- | --- | --- | --- |
| empty | PASS | PASS | Accueil clair : titre, consigne fournisseur et stockage local visibles ; capsule et onglets entièrement au-dessus du geste système. |
| message | PASS | PASS | Message utilisateur synthétique affiché ; état non configuré et Réessayer explicites, aucune bulle entrante inventée. Capsule vide conservée. |
| reset | PASS | PASS | Effacement confirmé : ancien message absent, accueil et indication de saisie restaurés ; navigation Chat active. |
| settings | PASS | PASS | Réglages compacts : action Configurer le fournisseur, état IA non configurée, politique horloge et apparence lisibles. Le formulaire est replié, galerie visible. |
| dark | PASS | PASS | Fond quasi noir, carte de saisie sombre, textes et icônes clairs contrastés ; onglet Chat bleu sombre distinct. |
| small_screen | PASS | PASS | Écran 360×640 dp : titre sur deux lignes et description lisibles, capsule/envoi/navigation sans chevauchement ni débordement horizontal. |
| large_text | PASS | PASS | 200 % : corps et navigation agrandis, retours à la ligne sans superposition ; placeholder abrégé, titre COUI encore visible et à taille fixe (alpha7). |
| keyboard | PASS | PASS | Clavier présent ; brouillon, curseur et envoi restent dégagés au-dessus de l’IME. En-tête et trois actions vérifiés aussi dans inspection/keyboard-header.png ouvert nativement. |
| reduced_motion | PASS | PASS | Capture stable avec animations désactivées : accueil, capsule et onglets lisibles ; la capture ne mesure ni fluidité ni retour tactile. |
| menu | PASS | PASS | Menu natif COUI avec Galerie COUI et Préférences lisibles, ancré en haut à droite, saisie non recouverte. |
| gallery | PASS | PASS | Carte typographique, palette et deux bulles illustratives distinctes ; dialogue accessible dans la zone visible, navigation séparée. |
| dialog | PASS | PASS | Dialogue au bas, titre/message/Fermer lisibles sur fond blanc ; voile de fond et barre système distincts, aucune action sous le geste système. |
| provider_dark | PASS | PASS | Correction visible : les trois champs vides sont bordés en gris clair sur surface sombre. Libellés et avertissement HTTPS lisibles ; suite et Enregistrer sous le pli non validés par ce PNG. |
| provider_small_screen | PASS | PASS | Correction visible en clair sur 360×640 : URL, modèle et clé ont des contours nets. Éléments visibles sans débordement horizontal ; bas de formulaire hors viewport défilable, non inspecté ici. |
| provider_large_text | PASS | PASS | À 200 % les champs URL et modèle conservent des bordures visibles, les libellés se réorganisent sans se chevaucher. Le champ clé et la suite sont sous le pli ; leur affichage après défilement reste NOT_RUN. |
| regression_fixture | PASS | FAIL | Défaut volontaire détecté : rectangle rouge opaque sur toute la largeur basse masque statut, envoi et navigation, et une partie de la capsule. |
