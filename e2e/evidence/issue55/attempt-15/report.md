# Issue #55 — tentative 15/15

Commit : `7914e229fd781f1acb5ad782600fbeab9d20f7e8` ; modifications locales : False.
Dérogation au budget : User explicitly requested continuing fixes until all tests pass; see e2e/evidence/issue55/README.md.
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

Observation comparative : Comparaison réelle des deux références Eta : fond neutre clair, capsule blanche flottante à deux niveaux, accents bleus et groupes blancs arrondis conservés. V2 ajoute son en-tête nommé et les destinations inférieures ; les cartes outils et réglages non raccordés sont omis, le prototype annonce IA non connectée. Ce contrôle valide une direction visuelle commune, sans parité pixel ni fonctionnelle. Pas de référence Eta pour sombre/clavier/texte agrandi ; ces variantes restent lisibles et sans recouvrement observé. Samsung et haptique physique NOT_RUN.

| État | Capture et assertions | Revue visuelle | Observation |
| --- | --- | --- | --- |
| empty | PASS | PASS | En-tête et trois actions présents ; invitation centrée, indication de saisie dans une capsule blanche distincte, destinations et barre système dégagées. |
| message | PASS | PASS | Bulle sortante asymétrique lisible, avertissement mémoire explicite ; champ vidé avec indication, curseur visible, navigation et plus bleu complets. En-tête confirmé par extraction des pixels supérieurs. |
| reset | PASS | PASS | Accueil rétabli sans ancien message ; indication du champ, en-tête et les deux destinations présents. |
| settings | PASS | PASS | Préférences avec retour, deux groupes blancs et séparateur lisibles ; résumés non tronqués, Réglages sélectionné, insets respectés. |
| dark | PASS | PASS | Hiérarchie conservée sur fond noir et capsules anthracite ; textes clairs, état sélectionné bleu sombre et icônes visibles. |
| small_screen | PASS | PASS | Invitation sur deux lignes sans coupe ; saisie, envoi et destinations tiennent dans la hauteur réduite avec marges système. |
| large_text | PASS | PASS | Texte de description et saisie agrandis sans recouvrement ; destinations restent distinctes. Toolbar/PageTitle fixes : limite alpha7 documentée. |
| keyboard | PASS | PASS | Clavier visible et champ saisi au-dessus avec envoi bleu accessible. En-tête Jean-Calcul et ses trois actions effectivement présents dans le PNG : contrôle natif du recadrage supérieur et de la planche des en-têtes. L’affichage initial de l’image entière avait conduit à une fausse interprétation. |
| reduced_motion | PASS | PASS | État final complet, invitation, indication du champ et navigation lisibles ; en-tête confirmé dans les pixels supérieurs. Fluidité non évaluée par une capture. |
| menu | PASS | PASS | Menu COUI blanc avec deux entrées espacées, superposé sans masquer la saisie ni la navigation ; actions supérieures présentes. |
| gallery | PASS | PASS | Palette bleue et typographie, deux directions de bulles explicitement illustratives et action dialogue lisibles. Navigation et retour présents ; nuancier blanc faiblement distinct du fond blanc, limite mineure. |
| dialog | PASS | PASS | Fond assombri et dialogue COUI blanc lisible ; titre, explication et large bouton Fermer sans coupe, barre système séparée. |
| regression_fixture | PASS | FAIL | Défaut volontaire détecté : rectangle rouge opaque couvrant le bas de la capsule, la commande envoi et la navigation. Interaction bloquée. |
