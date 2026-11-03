---
name: jc-voice
description: "Raccorder la voix Jean Calcul au runtime commun et corriger son cycle de vie Android : transcription, TTS, audio focus, interruption et continuité. Ne sert pas à ajouter une écoute continue."
---

# jc-voice

## Entrée

Lire JC-06, [la spec voix](../../../docs/specs/05-voice-and-android-lifecycle.md), les instructions
assistant-session et les contrats de runtime/données déjà livrés. Inspecter VoiceSessionController,
VoiceAgentCommandProcessor, recorder et VoicePipeline ; identifier leurs propriétaires de ressources.

## Intégration

Une transcription finale correspond à un tour, jamais chaque partiel. Préserver origine voix,
conversation/session et historique du runtime texte. Supprimer les écritures redondantes du
recorder dans le chemin unifié. Le partage d'une factory ne prouve pas le partage du contexte.
Le processus assistant_session demande une coordination de données explicite ; réutiliser JC-09.

Les commandes volume locales déterministes restent un mode explicite hors réseau. Une demande
générale ne devient pas une erreur de volume. L'UI indique clairement écoute, réflexion, attente,
parole et erreur. Ne pas déclarer le micro actif à partir de la seule animation d'orbe.

## Ressources et décisions

Propager CancellationException hors des runCatching. Stop/fermeture/appel entrant/perte focus
ferment capture, TTS, coroutine et run possédés, une seule fois. TTS peut attendre la fin du
texte pour ce jalon ; un échec TTS ne détruit pas la réponse affichée.

La confirmation utilise le même coordinateur que le texte. Oui ne vaut que pour la proposition
exacte en attente, jamais biométrie ni approbation globale. Tester partiel dupliqué, final répété,
refus, expiration, interruption pendant stream et micro absent.

## Sortie

Tests contrôlés du cycle de vie, parité des outils et absence de doublons. Garder
VoiceInteractionService léger. Les scénarios Power/One UI/appel réel/route Bluetooth sont consignés
séparément par [la procédure appareil](../../../docs/agents/verification.md), sans simulation annoncée réelle.
