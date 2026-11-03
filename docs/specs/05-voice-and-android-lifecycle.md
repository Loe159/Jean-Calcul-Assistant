# Voix et cycle de vie Android

## Architecture observée

[VoiceSessionController](../../android/assistant-session/src/main/kotlin/fr/loevan/jeancalcul/assistant/session/VoiceSessionController.kt)
pilote le pipeline, les commandes et l'état. [VoiceAgentCommandProcessor](../../android/assistant-session/src/main/kotlin/fr/loevan/jeancalcul/assistant/session/VoiceAgentCommandProcessor.kt)
crée actuellement ses propres sessions. [VoiceConversationRecorder](../../android/feature-conversation/src/main/kotlin/fr/loevan/jeancalcul/feature/conversation/VoiceConversationRecorder.kt)
persiste un historique séparément. Le service de session utilise le processus `:assistant_session`.
Références : [ADR voix](../architecture/0009-replaceable-android-voice-pipeline.md),
[machine d'états](../architecture/0008-assistant-state-machine.md).

## Contrat cible

**VOICE-01 — Invocation légère.** Le VoiceInteractionService sélectionné garde un démarrage
minimal. L'UI/session gère les ressources de capture et la coordination ; pas de client auth lourd,
de DB ou de LLM dans le callback de service. Préserver les propriétaires de lifecycle de la fenêtre Compose.

**VOICE-02 — Entrée équivalente.** Une transcription finale devient une entrée du même runtime
que le texte, avec origine USER_VOICE et bonne conversation/session. Les résultats partiels ne
déclenchent ni backend ni outil. Un résultat final répété du recognizer ne crée pas deux tours.
L'UI texte voit les messages vocaux et leurs réponses sans duplication par le recorder.

**VOICE-03 — Continuité réelle.** Conserver le contexte entre deux demandes liées ; ne pas
créer un thread distant sans lien à chaque transcription. Une action « Nouvelle conversation »
est explicite. La session Power peut être un autre host du runtime, mais partager seulement
AgentBackendFactory n'est pas suffisant. Réutiliser les contrats domain/repository et définir
comment la sélection et le tour actif sont coordonnés entre processus (DATA-10).

**VOICE-04 — États visibles.** Distinguer repos, écoute, transcription, réflexion, streaming,
confirmation, exécution, parole, interruption, erreur et mode local. Le micro est annoncé actif
seulement quand une capture existe. Les animations/amplitudes ne lisent jamais directement le micro.

**VOICE-05 — Réponse et TTS.** Le texte arrive progressivement à la même conversation. Pour ce
jalon, TTS peut attendre la réponse finale afin de ne pas prononcer deux fois les deltas ni une
phrase annulée. Arrêter le tour arrête aussi TTS. Une erreur TTS conserve la réponse lisible et propose
le texte ; aucune réponse réussie ne devient un échec de conversation seulement faute de voix.

**VOICE-06 — Confirmation.** Les outils suivent exactement POL-01 à POL-12. Présenter et
vocaliser l'action avant toute exécution ; un « oui » se rapporte uniquement à la proposition en
attente, non expirée, et ne vaut jamais biométrie. Sans proposition, « oui » n'exécute rien.
Reconnaissance ambiguë → demander de confirmer via l'UI, pas deviner. Refus/stop/cancel restent distincts.

**VOICE-07 — Interruptions.** Fermeture overlay, appel entrant, perte audio focus ou arrêt
utilisateur annulent capture, TTS, coroutine et run réseau possédés. Propager CancellationException,
cleanup idempotent, pas de « L'agent n'a pas pu répondre » à la place d'un arrêt volontaire.
Ne pas laisser deux recognizers en parallèle ni retenir le micro en arrière-plan.

**VOICE-08 — Voie locale déterministe.** Conserver les commandes prises en charge par
DeterministicVolumeCommandInterpreter et VolumeCommandProcessor. Afficher le mode local et sa
limite de vocabulaire. Une demande générale sans backend produit une aide à configurer, pas une
fausse commande de volume. Ne pas transmettre au distant une demande choisie comme locale.

**VOICE-09 — Permissions et matériel.** Sans RECORD_AUDIO, proposer demande OS ou texte ; refus
durable mène aux réglages officiels. Sans moteur STT/TTS ou Bluetooth disponible, état explicite et
fallback texte. La langue vient de VoiceSettings. Restaurer audio focus et route après fin de session.
Les limites One UI/écran verrouillé sont testées physiquement, pas supposées à partir d'un émulateur.

**VOICE-10 — Confidentialité/performance.** Pas d'enregistrement brut ni transcription dans
les logs. Traces de jalons sans contenu selon les budgets existants ; pas d'écoute continue ajoutée.
Le processeur service reste isolé d'un crash UI/voix. Les effets réduits ne suppriment aucun contrôle.

## Matrice de parité texte/voix

| Demande synthétique | Résultat attendu sur les deux surfaces |
| --- | --- |
| « Quelle batterie reste-t-il ? » | Même outil R0 et vraie valeur, bon historique/audit |
| « Active la lampe » en STRICT | Même attente, aucun effet avant confirmation |
| Refuser la lampe | Zéro effet, refus audité, réponse finale claire |
| Arrêter pendant réponse | Tour interrompu, plus de son/capture/réseau propriétaire |
| Deux transcriptions finales identiques | Un seul tour pour la même capture |
| Réseau coupé | Même erreur récupérable, pas de commande locale inventée |
| Volume en mode local | Interpréteur déterministe, aucune requête distante |

Les vingt tours et appels d'outils ainsi que l'appel entrant réel restent des preuves appareil.
