# JC-10 — Qualifier auth et reprise des backends sans promesse fictive

## Contrat de mission

- Issues parentes : [#41](https://github.com/Loe159/Jean-Calcul-Assistant/issues/41), [#42](https://github.com/Loe159/Jean-Calcul-Assistant/issues/42), [#43](https://github.com/Loe159/Jean-Calcul-Assistant/issues/43). Leurs critères plus larges restent ouverts si non couverts ici.
- Dépendances à vérifier dans la branche : JC-01, JC-02.
- Skill : [$jc-backend](../../../.agents/skills/jc-backend/SKILL.md).
- Exigences : AUTH-01, AUTH-02, AUTH-03, AUTH-04, AUTH-05, AUTH-06, AUTH-07, AUTH-08, NET-01, NET-02, NET-03, NET-04, NET-05, NET-06, NET-07, NET-08, PROD-06.
- Statut de cette fiche : **CIBLE**, aucune implémentation/prévalidation déduite de sa présence.

## Problème et résultat

Le dépôt contient deux chemins ChatGPT aux garanties historiques divergentes. Les capacités de reprise v2 et les mocks ne prouvent pas auth/inférence/continuité réelles.

Livrer : Des erreurs récupérables et capacités exactes ; tests contractuels wire/cancel/reprise ; statut documenté de l’accès direct et des limites du compagnon.

## Lecture minimale

- [03-backends-auth-and-protocol.md](../../specs/03-backends-auth-and-protocol.md)

- [00-product.md](../../specs/00-product.md)

Sources à inspecter, avec leurs tests de module :

- [android/core-network/src/main/kotlin/fr/loevan/jeancalcul/network/codex/ChatGptPlanAuthManager.kt](../../../android/core-network/src/main/kotlin/fr/loevan/jeancalcul/network/codex/ChatGptPlanAuthManager.kt)
- [android/core-network/src/main/kotlin/fr/loevan/jeancalcul/network/codex/ChatGptPlanAgentBackend.kt](../../../android/core-network/src/main/kotlin/fr/loevan/jeancalcul/network/codex/ChatGptPlanAgentBackend.kt)
- [android/core-network/src/main/kotlin/fr/loevan/jeancalcul/network/codex/CodexCompanionAgentBackend.kt](../../../android/core-network/src/main/kotlin/fr/loevan/jeancalcul/network/codex/CodexCompanionAgentBackend.kt)
- [companion/codex_companion.py](../../../companion/codex_companion.py)
- [companion/README.md](../../../companion/README.md)

Lire aussi les AGENTS.md applicables et [l'état réel](../current-state.md).

## Implémentation guidée

1. Lire les ADR et contrôler les DTO/headers v2 côté Python/Kotlin. Actualiser la documentation observée sans supprimer les limites historiques.
2. Tester NDJSON fragmenté, Unicode, EOF, 401/426, session incorrecte, collision de noms outils, cancel et résultats corrélés.
3. Tester interruption avant terminal, reprise après terminal, redémarrage compagnon et scope des curseurs ; ne pas relancer un effet incertain.
4. Pour le direct, vérifier source officielle actuelle, PKCE/state/claims, rotation concurrente, révocation et fermeture callback. Ne pas inventer endpoints/scopes.
5. Exécuter consentement/inférence avec compte autorisé si disponible. Sinon livrer statut non vérifié/indisponible et instructions de récupération, sans prétendre valider AUTH-01.
6. Préserver loopback privé et isolation hôte ; l’appairage LAN/TLS complet de #42 demande son propre découpage si nécessaire.

## Critères d'acceptation

- [ ] Protocole v2 vérifié de bout en bout fake ; mauvais header/token/session refusé proprement.
- [ ] Annulation libère processus/appels/attentes sans ResourceWarning.
- [ ] Limites de reprise exactes, aucun rejeu silencieux d’un outil après crash.
- [ ] Tokens jamais envoyés à un endpoint arbitraire ni présents dans logs/rapports.
- [ ] Le résultat distingue explicitement support externe prouvé et preuve manquante ; #42 n’est pas fermée pour le seul loopback.

## Vérification

Suites Python companion, CodexCompanionAgentBackendTest, ChatGptPlanAgentBackendTest et nouveaux cas auth. MockWebServer pour les mécanismes ; vrai compte pour éligibilité externe.

```bash
PYTHONWARNINGS=error::ResourceWarning python3 -m unittest -v tests.companion.test_codex_companion
./gradlew :core-network:testDebugUnitTest
```

Ces commandes sont à exécuter, pas des résultats acquis. Ajouter les analyses de module et la
[validation d'intégration](../verification.md) selon le diff. L'absence d'environnement est
consignée comme NON EXÉCUTÉ. L'instrumentation ne se lance que sur appareil autorisé.

## Hors périmètre

Pas de copie des tokens Codex PC vers Android, exposition Internet, désactivation TLS ou achat de crédits API automatique.

## Sortie et handoff

Remplir [le résultat](../templates/result.md) avec le mapping exigence→preuve, les fichiers,
commandes exactes, limites et critères non exécutés. Fournir les références officielles datées et les preuves manquantes à JC-11/12. Garder les critères #42 non réalisés ouverts.
