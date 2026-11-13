# Issue #55 — tentative 12/12

Commit : `23d9529ad37dc48313405c6b9c5adcb5a38061bc` ; modifications locales : False.
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

Observation comparative : Les deux JPG Eta ont été ouverts et comparés : fond gris neutre, capsule blanche flottante à deux niveaux, accent bleu et groupes de réglages blancs arrondis repris. V2 conserve un en-tête nommé et une navigation inférieure propres, sans cartes outils ni réglages non raccordés ; prototype IA non connectée explicitement annoncé. Les états clavier, sombre, petit écran et texte agrandi restent lisibles ; absence de références Eta pour ces variantes, aucune parité pixel ou fonctionnelle revendiquée. Haptique physique, Samsung et fluidité NOT_RUN.

| État | Capture et assertions | Revue visuelle | Observation |
| --- | --- | --- | --- |
| empty | PASS | PASS | En-tête complet, invitation centrée, indication de saisie visible dans la capsule blanche ; les deux destinations restent dégagées de la barre système. |
| message | PASS | PASS | Bulle sortante bleutée et avertissement mémoire lisibles ; champ vidé avec indication visible, navigation complète et action nouvelle conversation bleue. |
| reset | PASS | PASS | Invitation restaurée après effacement ; indication du champ et destinations Chat/Réglages visibles, aucun ancien message. |
| settings | PASS | PASS | Deux groupes blancs et leurs résumés sont lisibles ; retour, menu et sélection Réglages visibles, aucun chevauchement des insets. |
| dark | PASS | PASS | Fond noir, surfaces anthracite, texte clair et sélection bleu sombre restent distinguables ; indication du champ et icônes présentes. |
| small_screen | PASS | PASS | Invitation répartie sur deux lignes sans coupe ; capsule, envoi, navigation et en-tête tiennent dans le petit écran. |
| large_text | PASS | PASS | Corps, indication de saisie et destinations agrandis restent lisibles sans recouvrement ; titre Toolbar/PageTitle reste fixe, limite alpha7 documentée. |
| keyboard | PASS | PASS | Clavier réellement visible ; en-tête Jean-Calcul et ses trois actions restent peints. Texte saisi et envoi bleu au-dessus du clavier, sans recouvrement. |
| reduced_motion | PASS | PASS | État final complet avec indication de saisie et navigation lorsque les animations Android sont désactivées ; une image statique ne juge pas la fluidité. |
| menu | PASS | PASS | Menu flottant COUI blanc lisible, deux entrées distinctes ; champ et navigation restent présents en arrière-plan. |
| gallery | PASS | PASS | Palette, typographie, deux bulles explicitement illustratives et action dialogue lisibles ; icônes de navigation présentes. Nuancier blanc peu distinct du groupe blanc, limite mineure. |
| dialog | PASS | PASS | Dialogue COUI au-dessus du fond assombri, titre et description lisibles ; bouton Fermer large, marges inférieures respectées. |
| regression_fixture | PASS | FAIL | Défaut volontaire détecté : grand rectangle rouge opaque en bas, masquant le bas de la capsule, envoi et navigation. État inutilisable. |
