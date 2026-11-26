# Majordome — délégation de l’assistant au homelab

L’assistant de Jean-Calcul (conversation et bouton Power) n’accède pas directement aux données du foyer. Il délègue au
**Majordome** Mastra (dépôt `homelab-ai`, agent `majordome`), qui délègue à son tour à ses agents spécialisés :
le **Cuisinier** (recettes, frigo, placards, congélateur, liste de courses, planning des repas) et le **Secrétaire**
(agenda du homelab). Référence serveur : `homelab-ai/docs/food.md`.

## Outil `ask_majordome`

- Déclaré dans `agent/majordome/MajordomeTool.kt`, ajouté aux outils du run par `AgentRuntimeRunExecutor`, comme les
  outils de conversation ou de développement. Il n’apparaît que pour une conversation (`OP_CHAT`), quand l’appairage
  homelab existe et est activé (`MastraConnection.loadEnabled`) et que l’outil n’est pas désactivé dans Outils > Homelab.
- Un seul argument : `request`, la demande complète en français (4 000 caractères au plus). Le prompt système demande
  d’envoyer au Majordome les demandes du foyer plutôt que de piloter des applications.
- Requête signée `POST /mobile/v1/assistant/messages` (`MastraSignedTransport`, sans query string ni redirection),
  corps `{ threadId, message }`. Le fil Mastra est `jc-` suivi d’un condensé SHA-256 de l’identité de la conversation :
  une conversation Jean-Calcul garde le même fil Majordome, et l’identifiant local ne quitte pas le téléphone.
- Délai d’appel 170 s (le serveur coupe à 150 s). Le résultat est marqué sensible : il sert au tour courant mais
  n’entre pas brut dans la conversation enregistrée ; le Majordome garde le contexte dans son propre fil.
- Réponses : `COMPLETED` (réponse relayée telle quelle) ; `APPROVAL_REQUIRED` (écriture soumise à approbation
  native : suppression de recette, création d’agenda). Erreurs explicites : `NOT_PAIRED`, `NOT_AUTHORIZED`, `OFFLINE`,
  `TIMEOUT` (l’action a pu être partiellement appliquée), `RATE_LIMITED`, `UNSUPPORTED` (serveur sans la route),
  `AI_UNAVAILABLE`, `INVALID_RESPONSE`.

## Approbation

1. Sur `APPROVAL_REQUIRED`, rien n’est encore fait : le Majordome garde l’appel suspendu (15 min). L’outil renvoie
   `pending_action` (résumé lisible) et l’assistant demande confirmation à l’utilisateur, puis termine son tour.
2. L’identifiant d’approbation reste sur le téléphone (`MajordomePendingApprovals`, par fil Majordome) : le résultat
   de l’outil est sensible et n’entre pas dans la conversation enregistrée, le tour suivant ne pourrait pas le relire.
3. Au tour suivant, si l’utilisateur accepte, l’assistant appelle `ask_majordome` avec `decision=approve` (ou `decline`).
   L’outil envoie `POST /mobile/v1/assistant/approvals` `{threadId, approvalId, decision}` ; le Majordome reprend l’appel
   exact et la réponse est relayée comme un message (`COMPLETED` ou nouvel `APPROVAL_REQUIRED`).
4. Garde-fou : une approbation envoyée dans le même run que la demande est refusée (`USER_CONFIRMATION_REQUIRED`) ; il
   faut un nouveau message de l’utilisateur. Un refus est accepté à tout moment. Une nouvelle demande sur la
   conversation abandonne l’approbation en attente (le serveur la décline).
5. Erreurs : `NO_PENDING_ACTION` (rien en attente sur ce téléphone, par exemple après la mort du processus),
   `APPROVAL_NOT_FOUND` / `APPROVAL_EXPIRED` (expirée ou service homelab redémarré) : rien n’a été fait.

## Vérification

`MajordomeToolTest` couvre le schéma, la signature, le fil stable, le relais de la réponse, l’approbation (refus
de l’approbation dans le même run, envoi au tour suivant, usage unique, refus) et
la correspondance des erreurs. Le comportement réel du Majordome (délégation au Cuisinier, écritures Grocy) est testé
côté `homelab-ai`.
