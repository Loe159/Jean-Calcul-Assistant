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
  par fenêtre native : suppression de recette, création d’agenda). Erreurs explicites : `NOT_PAIRED`, `NOT_AUTHORIZED`, `OFFLINE`,
  `TIMEOUT` (l’action a pu être partiellement appliquée), `RATE_LIMITED`, `UNSUPPORTED` (serveur sans la route),
  `AI_UNAVAILABLE`, `INVALID_RESPONSE`.

## Approbation

Sur `APPROVAL_REQUIRED`, rien n’est encore fait : le Majordome garde l’appel suspendu (15 min). L’application ouvre
elle-même la fenêtre de confirmation native décrite dans `ACTION-CONFIRMATION.md` (résumé du serveur ; « Accepter »
seul, ou « Accepter » + empreinte si l’action est à haut risque), puis envoie la réponse
`POST /mobile/v1/assistant/approvals` `{threadId, approvalId, decision}`. Le modèle ne voit ni l’identifiant ni la
décision et ne peut pas approuver : il n’y a plus d’échange de confirmation dans le chat ni de paramètre `decision`.

- Accepté : le Majordome reprend l’appel exact et la réponse est relayée comme un message.
- Refusé, délai de 60 s dépassé ou fenêtre impossible à afficher : `DECLINE` est envoyé au serveur et l’outil renvoie
  `USER_DECLINED`, `CONFIRMATION_TIMED_OUT` ou `CONFIRMATION_UNAVAILABLE` ; rien n’a été fait.
- Risque : champ optionnel `approval.risk` du serveur (toute valeur non vide = haut risque) ; à défaut, détecté
  depuis le résumé (suppression, envoi…) ; résumé vide = haut risque.
- Erreurs serveur `APPROVAL_NOT_FOUND` / `APPROVAL_EXPIRED` : rien n’a été fait.

## Vérification

`MajordomeToolTest` couvre le schéma, la signature, le fil stable, le relais de la réponse, l’approbation via la
fenêtre (acceptée, refusée, expirée, indisponible, risque serveur) et
la correspondance des erreurs. Le comportement réel du Majordome (délégation au Cuisinier, écritures Grocy) est testé
côté `homelab-ai`.
