---
name: android-visual-review
description: Revue visuelle obligatoire par l'agent qui code Jean-Calcul V2 : inspection des PNG, notation motivée de chaque critère, correction et re-test bornés.
---

## Règle centrale
**Tu es le relecteur visuel.** Utilise ton outil natif de visualisation d'images (ou outil image disponible dans ton environnement) pour **voir** chaque capture produite par `android-e2e`. Ne fais pas appel à un endpoint OpenAI, à un service vision externe ou à GitHub Actions pour juger les images. Si tu ne peux pas les ouvrir, marque NOT_RUN, ne simule pas une inspection.

## Cycle (max 3 tentatives)
1. Après `bash e2e/agentic_loop.sh run N`, lis `e2e/visual-criteria.json` et les captures dans `e2e-artifacts/attempt-N/captures/`.
2. Ouvre **chaque PNG**; vérifie que Jean-Calcul est au premier plan, sans erreur système (les dialogues « Process system isn't responding », « Settings isn't responding » rendent la capture **FAIL**, pas PASS). Distingue problème app, test ou émulateur.
3. Pour chaque critère fonctionnel/UX, constate par observation : disposition/insets, navigation, lisibilité, contrastes, petits écrans, champ de saisie et clavier, apparence COUI / inspirations Eta **sans revendiquer une parité non observée**. Compare aux références explicites approuvées si elles existent. Un fichier PNG valide n'établit aucun verdict UX.
4. Renseigne **tous** les critères via l'outil d'enregistrement (un verdict = observations vérifiables) :
   ```bash
   python3 e2e/record_verdict.py --out e2e-artifacts/attempt-1 --criterion ux.layout --status FAIL --reason "Le compositeur est masqué par le clavier sur la capture 08, à corriger."
   ```
   Consulte la liste des critères avec `python3 e2e/record_verdict.py --list`. Pour des preuves absentes ou incertaines, utilise NOT_RUN.
5. Génère le rapport (code de sortie non nul si échec ou test non exécuté) :
   ```bash
   bash e2e/agentic_loop.sh report 1
   ```
6. Si aucun FAIL ni NOT_RUN : terminé. Sinon corrige le code ou la procédure de test, puis `bash e2e/agentic_loop.sh run 2` et recommence la revue entière. Idem pour l'itération 3 si nécessaire. **Ne relance pas de tentative 4.** Pas de commit/push automatique par les scripts.
7. Joins `report.md`, verdicts et captures non privées à la PR. Mentionne honnêtement les scénarios NOT_RUN et les défauts restants; n'approuve ni ne fusionne la PR.

## Politique des preuves
- Appareil : émulateur neuf dédié, aucun compte utilisateur ou message réel.
- Un PASS exige une description concrète et les captures attendues visibles, ainsi que les assertions instrumentées pour les critères fonctionnels.
- Les comparaisons de pixels/baselines constituent des alarmes, pas une preuve esthétique ni une substitution à la vision de l'agent.
- Les scripts ne contiennent ni clé API vision, ni prompts envoyés à un service distant.
