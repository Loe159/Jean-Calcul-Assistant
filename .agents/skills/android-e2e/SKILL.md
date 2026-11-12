---
name: android-e2e
description: Lancer les tests et captures de Jean-Calcul V2 sur un émulateur Android isolé, puis recueillir les preuves reproductibles.
---
Lire `e2e/README.md` et `docs/QUALITY.md` depuis la racine du dépôt.
Utiliser `python3 e2e/run.py --run-id <issue> --attempt 1` dans un environnement
JDK 25 / SDK Android (ou `nix develop`). Le lanceur possède son AVD synthétique ;
ne pas lui substituer un téléphone personnel ou un émulateur déjà utilisé.
Les APK, logs expurgés, captures et `report.json` sont sous
`e2e-artifacts/<issue>/attempt-1/`. Les tests ne prouvent pas la qualité visuelle.

Après collecte, charger `.agents/skills/android-visual-review/SKILL.md` et ouvrir
les images avec la vision native de l'agent qui implémente. Corriger puis
retester au besoin, au plus trois tentatives pour la modification. Ne pas
changer de run-id pour contourner cette limite. Conserver échecs et NOT_RUN.
Aucun appel de modèle vision externe, aucune approbation ou fusion automatique.
