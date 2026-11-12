# Produit V2 — périmètre

Objectif : assistant Android moderne, très fortement inspiré de l'UX Eta/COUI,
utilisable en chat, voix et appui long Power sur Samsung sans root obligatoire,
et capable de travailler avec un homelab Mastra sans en dépendre.

## Tranches verticales
1. Build Android isolé, APK téléchargeable, émulateur et preuves E2E.
2. Design system COUI et interface conversation soignée.
3. Chat persistant, Codex direct et endpoints LLM locaux, streaming/stop/retry.
4. Voix et rôle d'assistant Android raccordés à la même session.
5. Registre d'outils, confirmations, audit, contrôles Android.
6. GUI Agent au premier plan : accessibilité, capture, action, vérification.
7. MCP/skills/mémoire/automatisations via Mastra si nécessaires.

Exclus du socle : root, LSPosed, hacks constructeur, terminal Linux complet,
système de personnages et copie du runtime Eta.

Le prototype actuel n'a pas de connexion IA, ne persiste pas les conversations,
et ne doit pas être annoncé comme un assistant fonctionnel.
