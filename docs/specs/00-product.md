# Produit et périmètre

## Utilisateur et scène d'usage

Assistant personnel Android utilisé à une main, souvent par appui long Power, parfois clavier
ou mains occupées. Le propriétaire choisit son moteur et doit comprendre où vont ses données
et quelles actions sont proposées. L'app doit rester utile sans agent configuré : historique,
réglages, audit et commandes locales explicitement prises en charge.

**PROD-01 — Contrat principal.** Une demande texte ou transcription finale produit soit une
réponse progressive, soit un état d'action à confirmer, soit une erreur compréhensible et récupérable.
Enregistrer silencieusement le texte seul ne satisfait pas « Envoyer ».

**PROD-02 — Autorité locale.** Conversations, sélection de profil, permissions, politique et
audit sont contrôlés par Android. Le backend peut proposer, jamais s'autoriser lui-même.

**PROD-03 — Destination visible.** Avant l'envoi, afficher le profil et le caractère local/distant
du traitement. « Historique conservé sur cet appareil » ne signifie pas « inférence locale ».
La destination choisie ne change pas automatiquement à la suite d'un échec.

**PROD-04 — Mise en service progressive.** Lire l'historique et saisir du texte n'exige ni rôle
d'assistant système ni microphone. Demander le rôle lors de l'activation Power, le micro lors
de la première demande vocale, une permission d'outil seulement quand elle est nécessaire.

**PROD-05 — Refonte compatible.** Réparer dans les modules existants et conserver les données.
Une réécriture totale doit avoir une mission distincte avec migration, comparaison fonctionnelle
et stratégie de retour ; elle n'est pas le prérequis des missions JC.

**PROD-06 — Qualité honnête.** Ne pas annoncer accès ChatGPT, hors-ligne général, biométrie,
reprise durable ou outils disponibles sur la seule base d'une enum, d'une preview ou d'un mock.
Afficher ce qui a été configuré, testé et réellement accessible.

## Vocabulaire

| Terme | Sens |
| --- | --- |
| Connexion | Endpoint et référence de secret ; ProviderConnection |
| Profil modèle | ModelProfile ; choisit un modèle direct via ModelProvider |
| Profil agent | AgentProfile ; choisit un AgentBackend avec sessions/capacités |
| Conversation | Historique local durable identifié par Conversation.id |
| Session locale | AssistantSession ; associe une conversation à un profil et une éventuelle référence distante |
| Run/tour | Une exécution backend pour un message, distincte de l'historique |
| Proposition | ActionProposal non fiable, à valider localement |
| Approbation | Décision utilisateur liée à une proposition exacte |
| Reçu | Preuve émise par PolicyEngine, consommée par le registre |
| Skill de développement | Instructions dans .agents/skills pour l'agent qui code ce dépôt |
| Skill produit | Extension future de phase 4 ; aucun droit Android dérivé des skills de développement |

## Jalons à prouver

1. **Conversation fiable** : profil unique, modèle/agent réels, streaming, stop, retry, aucune duplication.
2. **Action contrôlée** : lecture batterie puis lampe après confirmation, résultat et audit.
3. **Voix équivalente** : même conversation, même politique, interruption propre, voie locale explicite.
4. **Usage quotidien** : navigation, onboarding, réglages et audit lisibles avec IME/grande police.
5. **Qualification** : vingt tours et vingt outils sur l'appareil de référence, échecs récupérables,
   confidentialité et preuves associées au commit.

Les phases notifications/calendrier/Gateway/mémoire/email restent différées jusqu'à la porte de
sortie 1B. La documentation de leurs contraintes n'autorise pas leur implémentation anticipée.
