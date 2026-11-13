---
name: issue-to-preview
description: Transformer une issue GitHub en PR V2 avec APK et preuves.
---
Lire `AGENTS.md` et l'issue V2. Implémenter uniquement les critères validés.
Pour un retour rapide, proposer l'APK local via `scripts/install-preview.py`
sur un téléphone ADB autorisé et recueillir le verdict utilisateur avant la CI.
À la livraison, tester, livrer APK associé au SHA, preuves E2E et PR vers
`feature/jean-calcul-v2-foundation`. L'utilisateur prend la décision finale.
