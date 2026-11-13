# Qualité V2

Boucle courte : issue -> modification -> APK local installé par ADB -> retour
utilisateur -> correction. Boucle de livraison : tests locaux et E2E isolé ->
captures ciblées -> revue visuelle -> PR + APK du commit -> décision utilisateur.

## Déjà en place
GitHub Actions compile l'APK Debug, exécute tests JVM et lint, lance
l'émulateur Android 35 et produit des captures. Les jobs peuvent échouer;
consulter le résultat réel d'un commit avant toute revendication.

## Parcours local agentique
Lire `e2e/README.md`. `e2e/run.py` prépare son propre émulateur sans données
privées, teste et capture les états. L'agent implémenteur ouvre les PNG avec sa
vision native pour les états modifiés et leurs régressions plausibles, motive
PASS/FAIL/NOT_RUN dans `visual-review.json`, corrige et
reteste (maximum trois tentatives). `e2e/report.py` vérifie les empreintes et
produit rapport et galerie, sans décider de l'UX. Un défaut visuel volontaire
existe uniquement dans l'instrumentation pour vérifier sa détection.

GitHub Actions reste technique : aucun modèle vision ni correcteur agentique,
aucun secret OPENAI_API_KEY. Pas d'approbation/fusion automatique.

## Limites explicites
Les références Eta sélectionnées par l’utilisateur sont épinglées dans
`e2e/references.json` ; ouvrir et comparer celles pertinentes pour le périmètre
de la modification. Les autres comparaisons restent NOT_RUN.
Les variantes sans référence exacte (sombre, clavier, police agrandie) sont
inspectées pour lisibilité/insets, sans revendiquer une conformité pixel à pixel.
L'installation directe utilise ADB sur un téléphone autorisé ; son résultat et
le retour utilisateur sont distincts des preuves de l'émulateur. L'installation
depuis un APK signé par une autre clé peut exiger une décision explicite sur les
données V2. Le succès réel dépend du rapport de chaque run ;
ne pas déduire une validation de la présence des scripts ou des PNG.

## Rapport minimal
Issue, commit, liste des tests, API Android, captures, erreurs expurgées,
statuts PASS/FAIL/NOT_RUN, vérification visuelle si vraiment exécutée.
Si un critère n'a pas été testé, indiquer NOT_RUN.
