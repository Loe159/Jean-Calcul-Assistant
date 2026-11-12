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

## Résultat de la tentative 4/4

Commit testé : `01c1d381dff34e044342eb79488f280fd7471d4c` (arbre propre).
Build et lint passent ; instrumentation **FAIL : 4/5 réussis**. Le parcours
menu → galerie → dialogue → retour passe désormais. Le seul échec restant
est `resetCanBeCancelledAndDraftSurvivesNavigation` : après saisie puis réglages,
Retour système termine l'activité au lieu de restaurer le brouillon. Le log de
cycle de vie montre PAUSED → STOPPED → DESTROYED. La fermeture explicite du
clavier et l'attente UI Automator ne suffisent donc pas à résoudre ce défaut.

L'arrêt technique empêche les treize scénarios de capture : captures, revue
visuelle et comparaison Eta **NOT_RUN**. Les références JPG ont été ouvertes,
mais aucune conformité visuelle n'est revendiquée sans images de l'application.
Le défaut volontaire ne peut pas être évalué : NOT_RUN.

[Rapport final](attempt-4/report.md), XML et logs expurgés sont conservés.
Les APK réellement construits sont dans `e2e-artifacts/issue55/attempt-4/` local,
hors Git. Empreinte APK dans `report.json`. Ils sont candidats, non validés.
La PR #60 reste en brouillon. Aucun nouveau run n'est lancé : le budget de
quatre tentatives accordé est épuisé. Samsung, haptique physique, fluidité,
Android 13 et scénarios de capture restent non validés.

## Reprise autorisée après la tentative 4

L'utilisateur demande explicitement : « Il faut corriger le problème jusqu'à
ce que tu puisses passer tous les tests ». Cette instruction autorise la
poursuite des corrections et validations au-delà de la dérogation précédente,
sous le même run-id `issue55`, sans écraser les résultats antérieurs. Chaque
nouvelle tentative consigne cette instruction avec `--authorization-note`.

Les logs de la tentative 4 signalent que `OnBackInvokedCallback` n'est pas
activé. La reprise active le chemin de retour Android moderne dans le manifeste
et attend explicitement la disparition de l'IME avant d'injecter Retour.
Les captures sont maintenant collectées même en cas d'échec des tests Smoke,
tout en conservant le statut technique FAIL et le code de sortie non nul.
