---
name: jc-tools-policy
description: "Corriger ou étendre le circuit des outils Android Jean Calcul : permissions configurées, PolicyEngine, confirmations, reçus, idempotence et audit. Ne sert pas à une simple retouche Compose."
---

# jc-tools-policy

## Entrée

Lire la fiche JC-04/JC-05, [la spec outils](../../../docs/specs/02-tools-policy-and-approvals.md),
AGENTS.md et les instructions de tool-bridge. Inspecter LocalAgentToolRuntime, PolicyEngine,
ToolRegistry et les tests existants avant de changer leurs signatures.

## Invariants opérationnels

- Un ToolCall est non fiable. Le modèle ne confirme pas sa propre proposition.
- Appliquer le profil réel : pas de préférence ALLOW synthétique ou de flag R2 automatique
  imposé à tous. Tester le mapping de permissions du catalogue et de l'exécution.
- CONFIRM/BIOMETRIC sont des états d'attente. Un bouton d'UI ne remplace pas le reçu produit
  et vérifié par les contrats de politique existants.
- Le reçu correspond à l'action, version, arguments et expiration exacts. Revérifier contexte
  et permissions juste avant effet ; une ancienne annonce de disponibilité n'est pas une autorisation.
- Refus/fermeture/expiration/stop libèrent l'attente sans effet et restent auditables.
- Un résultat incertain ne permet pas de rejouer une modification sous une nouvelle clé.

## Méthode

Utiliser une horloge et des adaptateurs à compteurs. Prouver zéro appel avant consentement,
zéro appel sur refus/expiration/révocation et un seul appel après autorisation valide. Tester
rejeu identique et clé réutilisée avec arguments différents. Conserver les planchers R4/R5.

La coordination d'attente peut être suspendable, mais tool-bridge ne dépend pas de Compose.
Le host présente la décision et la biométrie ; le domaine évalue et émet le reçu. Renvoyer
ToolResult au callId/run exact et distinguer output/error. Vérifier les refus UNKNOWN_TOOL et
indisponibilités dans l'audit, pas seulement les succès d'adaptateur.

## Sortie

Tests domain/registry/runtime et sink fake, puis instrumentation si disponible. [Résultat](../../../docs/agents/templates/result.md)
avec décisions et preuves. Les effets réels lampe/volume/biométrie exigent appareil autorisé et
restauration des réglages ; leur absence reste NON EXÉCUTÉ.
