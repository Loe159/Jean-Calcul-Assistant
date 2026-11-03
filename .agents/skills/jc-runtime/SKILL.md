---
name: jc-runtime
description: "Implémenter ou corriger le routage modèle/agent et le cycle de conversation Jean Calcul, y compris streaming, annulation, réessai et ViewModel. Pour les permissions outils, utiliser jc-tools-policy."
---

# jc-runtime

## Entrée

Une fiche JC-01, JC-02 ou JC-03. Depuis la racine, lire AGENTS.md, la fiche indiquée par
[le manifest](../../../docs/agents/tasks/manifest.json), puis uniquement ses exigences dans
[conversation](../../../docs/specs/01-conversation-runtime.md). Lire les sources citées et tests.

## Décisions propres au dépôt

- Deux préférences actives existent aujourd'hui ; appliquer la normalisation CONV-01 sans
  fabriquer une troisième source de vérité. Activer un type désactive l'autre.
- ModelProvider et AgentBackend sont deux contrats, pas deux implémentations interchangeables
  par cast. Les factories directes existent ; leur sélection manque dans le parcours UI actuel.
- Conversation.id est local ; backend session/run et requestId ont des rôles différents.
- Un seul tour propriétaire pour le runtime MVP. Réserver avant suspension ; ne pas confondre
  la conversation affichée et celle dont le tour s'exécute.
- Le draft suivant appartient à l'utilisateur. Une fin de requête ne doit pas le remplacer.

## Exécution

Reproduire le défaut avec un fake contrôlé. Lire ConversationOrchestratorTest pour réutiliser
les fixtures. Tester les deltas intermédiaires, effets de persistance, nombres d'appels et terminaux.
Pour cancel, conserver la propagation de CancellationException et cleanup borné même si le
cancel distant échoue. Pour retry, conserver les IDs de messages, nouveau requestId, contexte
borné et outils ; ne jamais répéter automatiquement un effet dont le résultat est inconnu.

Pour le ViewModel, isoler les décisions du Context Android et contrôler dispatcher main et
scheduler. Raccorder les états/actions aux appelants dans la même mission. Ne pas ajouter de
réponse fictive pour faire paraître Envoyer fonctionnel.

## Sortie

Exécuter les checks de la fiche et [la matrice](../../../docs/agents/verification.md).
Remplir [le résultat](../../../docs/agents/templates/result.md), en distinguant fake et service réel.
Ne pas modifier politique, transport ou stockage hors ajustement indispensable à la fiche.
