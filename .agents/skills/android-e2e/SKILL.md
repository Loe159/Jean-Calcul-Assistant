---
name: android-e2e
description: Lancer les tests et captures de Jean-Calcul V2 sur un émulateur Android isolé, puis recueillir les preuves reproductibles.
---
Lire `e2e/README.md` et `docs/QUALITY.md` depuis la racine du dépôt.
Pendant l'itération, compiler et livrer directement la preview au téléphone avec
`python3 scripts/install-preview.py [--serial SERIAL]`. Demander un retour sur
la fonctionnalité en cours sans attendre GitHub Actions ni le run E2E complet.
Cette variante possède un `applicationId` distinct : V2 et ses données restent
intactes. `adb install -r` conserve les données de Preview si sa signature est
identique ; ne jamais désinstaller automatiquement en cas de signature incompatible.

Avant la livraison de la PR, utiliser
`python3 e2e/run.py --run-id <issue> --attempt 1` dans un environnement JDK 25 /
SDK Android (ou `nix develop`). Le lanceur possède son AVD synthétique ;
ne pas lui substituer un téléphone personnel ou un émulateur déjà utilisé.
Les APK, logs expurgés, captures et `report.json` sont sous
`e2e-artifacts/<issue>/attempt-1/`. Les tests ne prouvent pas la qualité visuelle.

Après collecte, charger `.agents/skills/android-visual-review/SKILL.md` et ouvrir
avec la vision native les captures liées à la fonctionnalité modifiée et ses
régressions plausibles. Déclarer explicitement les autres états NOT_RUN pour la
revue visuelle ; les tests techniques continuent à couvrir toute la suite.
Corriger puis
retester au besoin, au plus trois tentatives pour la modification. Ne pas
changer de run-id pour contourner cette limite. Conserver échecs et NOT_RUN.
Aucun appel de modèle vision externe, aucune approbation ou fusion automatique.
