# Qualité V2

Boucle : issue -> PR -> APK -> émulateur -> tests E2E -> captures ->
analyse visuelle agentique -> corrections bornées -> APK -> décision utilisateur.

## Déjà en place
GitHub Actions compile l'APK Debug, exécute tests JVM et lint, lance
l'émulateur Android 35 et produit des captures. Les jobs peuvent échouer;
consulter le résultat réel d'un commit avant toute revendication.

## Parcours local agentique
Lire `e2e/README.md`. `e2e/run.py` prépare son propre émulateur sans données
privées, teste et capture les états. L'agent implémenteur ouvre les PNG avec sa
vision native, motive PASS/FAIL/NOT_RUN dans `visual-review.json`, corrige et
reteste (maximum trois tentatives). `e2e/report.py` vérifie les empreintes et
produit rapport et galerie, sans décider de l'UX. Un défaut visuel volontaire
existe uniquement dans l'instrumentation pour vérifier sa détection.

GitHub Actions reste technique : aucun modèle vision ni correcteur agentique,
aucun secret OPENAI_API_KEY. Pas d'approbation/fusion automatique.

## Limites explicites
Les références Eta sélectionnées par l’utilisateur sont épinglées dans
`e2e/references.json` ; elles doivent être ouvertes et comparées réellement.
Les variantes sans référence exacte (sombre, clavier, police agrandie) sont
inspectées pour lisibilité/insets, sans revendiquer une conformité pixel à pixel.
Appareil Samsung automatisé et validation APK en un clic restent
hors du parcours disponible. Le succès réel dépend du rapport de chaque run ;
ne pas déduire une validation de la présence des scripts ou des PNG.

## Rapport minimal
Issue, commit, liste des tests, API Android, captures, erreurs expurgées,
statuts PASS/FAIL/NOT_RUN, vérification visuelle si vraiment exécutée.
Si un critère n'a pas été testé, indiquer NOT_RUN.
