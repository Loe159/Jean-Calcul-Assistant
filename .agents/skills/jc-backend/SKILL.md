---
name: jc-backend
description: "Implémenter ou diagnostiquer les adaptateurs réseau, le protocole compagnon et les mécanismes auth de Jean Calcul. Distingue les tests fake des preuves de support externe ChatGPT."
---

# jc-backend

## Entrée

Lire JC-10 ou la fiche réseau assignée, [backends/auth](../../../docs/specs/03-backends-auth-and-protocol.md)
et [l'état réel](../../../docs/agents/current-state.md). Identifier quel chemin est concerné :
modèle direct, codex-companion ou openai-chatgpt-plan. Ne pas fusionner leurs credentials.

## Règles propres au dépôt

Le compagnon actuel parle HTTP/NDJSON v2, loopback, avec Bearer Jean Calcul et header de version.
Ses runs sont en mémoire et libérés après terminal ; resume ne signifie pas durabilité illimitée.
L'accès direct OAuth existe dans le code mais nécessite une preuve officielle et un compte réel
pour déclarer son éligibilité. Ni un ADR ni MockWebServer ne constituent cette preuve.

Avant une modification spécifique à un service externe, vérifier sa documentation officielle
actuelle via les outils disponibles. Si indisponible, noter l'hypothèse non validée et ne pas
inventer endpoint/client ID/scope. Le client direct refuse d'envoyer un token ChatGPT à un hôte
arbitraire ; les injections d'URL de test restent internes.

## Implémenter et vérifier

- Conserver DTO Python/Kotlin et version wire cohérents ; tester fragmentation, Unicode, EOF,
  version absente, 401/426, mauvaise session et collisions de noms outils.
- Corréler sessionId/runId/requestId/callId ; ne pas traiter un curseur d'un autre run comme acquis.
- Fermer appels, streams et processus en cleanup. Une annulation ne devient pas une erreur auth.
- OAuth : mauvais state/claims, timeout callback, rotation concurrente, révocation et secret absent.
- Ne pas logger bodies/headers/tokens. Tests avec faux secrets ; rapport uniquement expurgé.
- Ne pas désactiver TLS, ouvrir le serveur sur Internet ou copier les identifiants du CLI vers Android.

## Sortie

Lancer les suites de la fiche, noter compatibilité prouvée et limites de reprise. L'essai auth
réel utilise uniquement un compte et consentement autorisés. Sans cela, documenter NON EXÉCUTÉ
et un état produit honnête. Ne pas fermer #42 sur la seule démonstration adb reverse.
