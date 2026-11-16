# Architecture

Un module Android `app`, Kotlin/Compose. Namespace interne `io.github.mangi.eta`, applicationId `fr.loevan.jeancalcul.v2`.

- `ui/` : composants COUI, navigation et stores des écrans. Miuix reste une dépendance interne utilisée par les thèmes/navigation partagés ; le style alternatif n’est plus sélectionnable.
- `agent/runtime`, `agent/model` : boucle, streaming, sessions, arrêt, providers et outils.
- `agent/tool`, `agent/accessibility`, `agent/virtualdisplay` : exécution Android avec contrôles de disponibilité/permissions.
- `agent/voice`, `agent/auth`, `agent/mcp`, `agent/skill`, `agent/subagent`, `agent/terminal` : moteurs amont conservés pour les blocs retenus.
- `data/` : Room, DataStore et secrets chiffrés. Les tables/types de compatibilité amont ne constituent pas des fonctionnalités exposées.
- `product/JeanCalculProfile.kt` : sélection immuable ; filtrage des providers, du catalogue et refus des appels d’outils exclus.
- Module LSPosed : seul `system` est déclaré, pour accessibilité/écran virtuel. Les hooks constructeurs/Google ne sont plus installés.

La documentation technique amont est conservée dans `docs/upstream/eta/` comme référence, sans valoir périmètre produit.

`agent/development` réutilise la capture Power, le runtime/modèle configuré, les secrets chiffrés et le canal de mises à jour pour créer des PR et publier des versions de test. Voir [SELF-IMPROVEMENT.md](SELF-IMPROVEMENT.md).
