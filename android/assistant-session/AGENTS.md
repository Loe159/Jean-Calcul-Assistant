# Session assistant Android

Lire spec voix, skill jc-voice et les propriétaires de lifecycle existants. Le processus
assistant_session est distinct de l'app : ne pas supposer ViewModel/Singleton/configuration partagés.

Une transcription finale rejoint le runtime commun une fois ; partiels n'exécutent rien. Éviter
double écriture avec VoiceConversationRecorder. Garder le mode volume local déterministe explicite.

Stop, fermeture, perte focus ou appel entrant libèrent STT, TTS, focus, coroutine et réseau possédés.
Propager CancellationException. Aucun microphone actif en arrière-plan ou faux succès de biométrie.
Les tests fake sont distincts des preuves Power/One UI/appel réel consignées sur appareil.
