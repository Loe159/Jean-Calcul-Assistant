# Architecture

Un module Android `app`, Kotlin/Compose. Namespace interne `io.github.mangi.eta`, applicationId `fr.loevan.jeancalcul.v2`.

- `ui/` : écrans et stores, construits sur COUI 2.0 (`io.github.suqi8.coui.kmp` : ui, preference, blur, nav, icons) avec un seul `COUITheme` et la palette ColorOS native. `ui/components/coui/` ne contient que les compositions d’app recommandées par COUI : échafaudage de page (barre repliable, lueur COUI 17, fond enregistré pour le flou), pont de flou, dialogues d’app et éléments de préférence absents de la bibliothèque (valeur de ligne, note de section).
- `agent/runtime`, `agent/model` : boucle, streaming, sessions, arrêt, providers et outils.
- `agent/tool`, `agent/accessibility`, `agent/virtualdisplay` : exécution Android avec contrôles de disponibilité/permissions.
- `agent/voice`, `agent/auth`, `agent/mcp`, `agent/skill`, `agent/subagent`, `agent/terminal` : moteurs amont conservés pour les blocs retenus.
- `data/` : Room, DataStore et secrets chiffrés. Les tables/types de compatibilité amont ne constituent pas des fonctionnalités exposées.
- `data/car/` : source du Now Brief Voiture. La session MyPeugeot est chiffrée via Android Keystore et les relevés sont mis en cache en mémoire. Le Brief injecte une source de démonstration pour les tests. Voir [NOW-BRIEF-CAR.md](NOW-BRIEF-CAR.md).
- `food/` regroupe le client Mastra, les sessions de cuisine locales, les caches Room et les minuteurs Android. Voir [FOOD.md](FOOD.md).
- `product/JeanCalculProfile.kt` : sélection immuable ; filtrage des providers, du catalogue et refus des appels d’outils exclus.
- Module LSPosed : seul `system` est déclaré, pour accessibilité/écran virtuel. Les hooks constructeurs/Google ne sont plus installés.

La documentation technique amont est conservée dans `docs/upstream/eta/` comme référence, sans valoir périmètre produit.

`agent/development` réutilise la capture Power, le runtime/modèle configuré, les secrets chiffrés et le canal de mises à jour pour créer des PR et publier des versions de test. Voir [SELF-IMPROVEMENT.md](SELF-IMPROVEMENT.md).
