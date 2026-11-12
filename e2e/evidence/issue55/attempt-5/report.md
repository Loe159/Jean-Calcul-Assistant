# Issue #55 — tentative 5/5

Commit : `46b0346c07de4345dc61a63942131bbf0fcbfc78` ; modifications locales : False.
Dérogation au budget : User explicitly requested continuing fixes until all tests pass; see e2e/evidence/issue55/README.md.
Android API 35 ; émulateur `emulator-5580` ; données synthétiques.

| Critère | Statut |
| --- | --- |
| Tâches Gradle build/lint et instrumentation Compose | PASS |
| Tests JVM réellement exécutés | NOT_RUN |
| Revue visuelle native des états normaux | FAIL |
| Détection du défaut volontaire | PASS |
| Comparaison aux références Eta sélectionnées par l’utilisateur | FAIL |
| Appareil Samsung / retour tactile physique | NOT_RUN |

Références : Eta, commit `4101bc700be7c13b7b72f85a59c1c320f2183c04`,
chat_home.jpg et settings.jpg (sources et empreintes dans references/manifest.json).
La comparaison porte sur les états communs du prototype ; elle ne prouve pas
la parité fonctionnelle avec Eta. La CI ne juge pas l'UX.

Observation comparative : Eta chat_home et settings ouverts. Fond neutre, capsule flottante et groupes arrondis communs ; V2 ajoute une navigation basse et omet les suggestions/outils non raccordés. Défauts V2 : navigation absente après message, en-tête absent avec IME et icônes manquantes en galerie. Pas de référence exacte sombre, IME ou police 200 %. Ni haptique ni fluidité physique prouvées.

| État | Capture et assertions | Revue visuelle | Observation |
| --- | --- | --- | --- |
| empty | PASS | PASS | Accueil lisible, capsule et navigation présentes ; statut et barre de geste contrastés, marges système respectées. |
| message | PASS | FAIL | Message et avertissement mémoire lisibles ; clavier absent mais barre de navigation attendue absente, seul le champ reste en bas. |
| reset | PASS | PASS | Accueil restauré sans message ; capsule, navigation et actions supérieures visibles. |
| settings | PASS | PASS | Groupes COUI, résumés et séparation lisibles ; retour et navigation visibles, aucun chevauchement. |
| dark | PASS | PASS | Texte et icônes clairs sur fond sombre, capsule distincte, barre système lisible et navigation présente. |
| small_screen | PASS | PASS | 360×640 dp : titre sur deux lignes sans coupure, capsule et navigation accessibles avec marge sous le geste. |
| large_text | PASS | PASS | Police 200 % : corps et libellés agrandis non coupés, navigation à hauteur libre ; titres fixes COUI documentés. |
| keyboard | PASS | FAIL | Clavier et bouton Envoyer visibles, saisie lisible ; en-tête et ses actions absents au-dessus du contenu. |
| reduced_motion | PASS | PASS | État final lisible avec animations désactivées, capsule et navigation visibles ; une capture statique ne prouve pas la fluidité. |
| menu | PASS | PASS | Menu natif ancré à droite, deux choix lisibles sans recouvrement de la saisie ; reste de la page visible. |
| gallery | PASS | FAIL | Typographie et bulles lisibles, exemple entrant explicitement illustratif ; icônes et surface de la navigation manquantes, seuls les libellés restent visibles. Le blanc sur blanc de la palette est une limite de présentation. |
| dialog | PASS | PASS | Dialogue natif en bas, titre, message et action Fermer lisibles ; marge au-dessus de la barre de geste, fond assombri. |
| regression_fixture | PASS | FAIL | Grande surcouche rouge en bas qui masque la navigation, le bouton Envoyer et une partie de la capsule ; défaut volontaire détecté. |
