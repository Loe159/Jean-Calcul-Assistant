# Qualité V2

Boucle : issue -> PR -> APK -> émulateur -> tests E2E -> captures ->
analyse visuelle agentique -> corrections bornées -> APK -> décision utilisateur.

## Déjà en place
GitHub Actions compile l'APK Debug, exécute tests JVM et lint, lance
l'émulateur Android 35 et produit des captures. Les jobs peuvent échouer;
consulter le résultat réel d'un commit avant toute revendication.

## Non disponible à ce jour
Agent visuel autonome analysant les captures et corrigeant les régressions,
appareil Samsung automatisé, validation dans l'APK en un clic. Ces fonctions
restent des issues prioritaires, ne pas annoncer leur existence.

## Rapport minimal
Issue, commit, liste des tests, API Android, captures, erreurs expurgées,
statuts PASS/FAIL/NOT_RUN, vérification visuelle si vraiment exécutée.
Si un critère n'a pas été testé, indiquer NOT_RUN.
