# Phase 1B — Première boucle Codex utilisable

Epic GitHub : #39  
Issues : #40 à #46

## Objectif

Transformer le socle de phase 1 en parcours réellement utilisable avec Codex authentifié via l’abonnement ChatGPT, sans clé API OpenAI, tout en gardant Android comme autorité pour les outils, les politiques, les confirmations et l’audit.

## Parcours de référence

1. Authentifier un compagnon Codex privé avec ChatGPT.
2. Appairer explicitement le Samsung avec ce compagnon.
3. Envoyer une demande texte et recevoir la réponse Codex en streaming.
4. Faire utiliser `device.get_battery` et renvoyer sa valeur réelle à Codex.
5. Faire proposer `device.toggle_flashlight`, appliquer le Policy Engine et la confirmation Android, puis renvoyer le résultat.
6. Rejouer le même parcours par la voix.
7. Vérifier historique, reprise de session et audit expurgé.

## Architecture

- Android reste la source de vérité des conversations, du registre d’outils et des politiques.
- Le compagnon conserve l’authentification ChatGPT ; aucun identifiant ChatGPT n’est transmis au téléphone.
- `AgentBackend` est le contrat entre l’application et le compagnon.
- Les propositions d’outils sont des `ActionProposal` : le backend ne peut jamais exécuter directement une API Android.
- Le canal Android-compagnon est authentifié, révocable et refuse les transports distants non protégés.
- Texte et voix utilisent le même orchestrateur conversationnel.

## Ordre d’exécution

- #40 et #41 peuvent démarrer en parallèle.
- #42 dépend de #41.
- #43 dépend de #40, #41 et #42.
- #44 dépend de #43 et réutilise #27, #28, #31 et #32.
- #45 dépend de #40, #43 et #44.
- #46 valide l’ensemble sur le Samsung de référence.

## Critères de sortie

- boucle texte Codex réelle en streaming ;
- boucle voix identique au chemin texte ;
- appairage révocable et reprise après coupure ;
- un outil de lecture et un outil à effet validés de bout en bout ;
- refus, annulation, compagnon hors ligne, coupure réseau et session expirée récupérables ;
- vingt tours Codex et vingt appels d’outils sans crash, duplication ni corruption ;
- aucun identifiant ChatGPT ou secret d’appairage brut dans les logs, stockages ou exports ;
- rapport de validation Samsung de #46 versionné.

## Hors périmètre

Hermes complet, multi-agent, jobs longs, cron, exposition Internet, notifications, calendrier, mails, skills, mémoire, TELOS, LifeOS et distribution grand public restent dans les phases suivantes.
