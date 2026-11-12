# Issue #55 — preuves locales

- Tentative 1 : FAIL, cinq tests Android échouent sur l'assertion du renderer
  COUI à une surface ; correction par le renderer natif à trois surfaces.
- Tentative 2 : NOT_RUN, l'émulateur refuse de créer sa partition userdata
  faute d'espace disque. Les disques temporaires synthétiques de cette tâche
  sont supprimés après arrêt ; rapports, logs et XML conservés.
- Tentative 3 : FAIL, trois tests Android réussis, deux échecs au retour système
  après interaction IME/dialogue. Aucune capture produite, revue NOT_RUN.
- Le correctif `a51f595` masque explicitement l'IME lors de la navigation et
  synchronise les tests sur la fermeture des fenêtres Android. Build, APK et
  lint passent. Tests JVM : NO-SOURCE ; neuf tests Python du rapport passent.

## Autorisation de validation supplémentaire

Le 12 novembre 2026, l'utilisateur a répondu explicitement **« Oui, autoriser
la tentative 4 »** à la question : « Autorisez-vous une quatrième tentative
locale pour valider le correctif de la PR #60 et produire les captures puis
leur revue visuelle ? Le budget de trois tentatives imposé par e2e/README.md
est atteint. » Cette autorisation permet le drapeau
`--extra-validation-authorized` pour ce run uniquement.

La tentative 4 complète sera ajoutée après exécution réelle. La PR reste en
brouillon jusqu'aux résultats. Aucune fusion automatique.
