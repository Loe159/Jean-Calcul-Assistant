# Issue #54 — tentative 4/4

Commit : `941334bb1a4133a8a1dc385e2fc251e917fc5b56` ; modifications locales : False.
Dérogation au budget : Explicit user authorization, 2026-11-12.
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

Observation comparative : Références Eta chat_home.jpg et settings.jpg rouvertes à côté des nouvelles captures. Les états communs de V2 conservent fond neutre, espace central, surfaces arrondies, navigation par icônes et saisie basse. Contraste système et police 200 % corrigés par rapport à la première tentative. V2 conserve sa marque et son avertissement de prototype ; elle ne possède pas encore les cartes de raccourcis ni les paramètres fournisseurs/outils d’Eta. La comparaison est exécutée, sans revendiquer parité fonctionnelle ou pixel à pixel ; pas de référence exacte sombre/clavier/texte 200 % fournie.

| État | Capture et assertions | Revue visuelle | Observation |
| --- | --- | --- | --- |
| empty | PASS | PASS | Capture finale ouverte : titre/actions compacts, accueil et avertissement complets, champ de saisie au-dessus du geste système ; barre de statut lisible en clair. |
| message | PASS | PASS | Bulle Bonjour V2 et avertissement mémoire visibles ; champ vidé après envoi. Aucun chevauchement des contrôles ou barres système. |
| reset | PASS | PASS | Accueil restauré, bulle synthétique absente et champ vide ; PNG complet, mention prototype entièrement lisible. |
| settings | PASS | PASS | Préférences et mention sans moteur IA visibles, retour au chat identifiable. Le retour système a également passé le test connecté. |
| dark | PASS | PASS | Fond sombre cohérent, texte et actions clairs lisibles ; surface de saisie et geste système bien séparés. |
| small_screen | PASS | PASS | À 360×640 dp, navigation, texte d’accueil, avertissement et saisie restent dans la fenêtre. Placeholder sur deux lignes sans texte coupé. |
| large_text | PASS | PASS | À 200 %, Jean-Calcul et le sous-titre restent lisibles ; avertissement IA non connectée entièrement affiché sur deux lignes. Envoi et saisie ne débordent pas. |
| keyboard | PASS | PASS | Clavier Android visible, texte synthétique et bouton Envoyer actif au-dessus de l’IME. Le contenu reste lisible sans chevauchement du clavier. |
| reduced_motion | PASS | PASS | Capture stable et complète, thème clair lisible et insets corrects avec animations système à zéro. Fluidité et haptique physiques non déduites de cette image. |
| regression_fixture | PASS | FAIL | FAIL volontaire détecté dans cette nouvelle capture : rectangle rouge plein masquant toute la saisie et le bouton Envoyer. Aucun défaut analogue sur les neuf états normaux. |
