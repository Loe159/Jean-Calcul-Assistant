# Issue #55 — tentative 11/11

Commit : `be39f79bf9b1b7218c9de02ca411595c31d34542` ; modifications locales : False.
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

Observation comparative : Références Eta chat_home et settings ouvertes. Fond neutre, capsule et groupes arrondis communs ; V2 ajoute une navigation basse et omet les fonctions non raccordées. États incomplets : en-tête caché avec clavier et indication du champ parfois absente. Pas de référence exacte sombre/IME/police 200 %, ni preuve haptique ou fluidité physique.

| État | Capture et assertions | Revue visuelle | Observation |
| --- | --- | --- | --- |
| empty | PASS | FAIL | Accueil et navigation lisibles ; la zone de saisie ne montre pas son indication de champ vide. |
| message | PASS | FAIL | Message, navigation et nouvelle conversation visibles ; indication absente dans le champ vide après envoi. |
| reset | PASS | PASS | Accueil restauré sans message, indication de saisie et destinations complètes visibles. |
| settings | PASS | PASS | Préférences et résumés lisibles ; onglet Réglages et icônes complets. |
| dark | PASS | PASS | Palette sombre lisible, indication du champ et navigation complètes, statut contrasté. |
| small_screen | PASS | PASS | 360×640 dp : titre sur deux lignes, champ avec indication et navigation visibles sans chevauchement. |
| large_text | PASS | PASS | Police 200 % : texte et onglets agrandis sans coupure, action d’envoi accessible ; titres fixes COUI documentés. |
| keyboard | PASS | FAIL | Saisie et envoi au-dessus du clavier mais en-tête et ses actions masqués, malgré des coordonnées sémantiques inchangées. |
| reduced_motion | PASS | PASS | Rendu final lisible, indication de saisie et navigation présentes ; pas de preuve de fluidité physique. |
| menu | PASS | PASS | Menu natif et arrière-plan lisibles, deux entrées accessibles, navigation et en-tête complets. |
| gallery | PASS | PASS | Textes, bulles illustratives et navigation complets ; blanc de la palette peu distinct sur blanc. |
| dialog | PASS | PASS | Titre, description et Fermer lisibles avec marge système ; fond correctement assombri. |
| regression_fixture | PASS | FAIL | Surcouche rouge basse masque navigation et Envoyer, défaut volontaire détecté. |
