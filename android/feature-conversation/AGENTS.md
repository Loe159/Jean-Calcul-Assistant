# Conversation

Lire la spec conversation et le skill runtime. Orchestrateur : persistance et événements du tour ;
ViewModel/host : état et intentions UI. Aucun secret dans UiState, draft persistant ou erreur brute.

Réserver le tour avant suspension, conserver son identité malgré sélection/navigation, propager
CancellationException. Toute réponse atteint un état terminal ; le cleanup n'est pas annulé avant
son écriture finale. Retry conserve les messages et le circuit d'outils.

Les tests contrôlent deltas, compteurs, terminaux et annulation avec scheduler. Ne pas dormir pour
espérer reproduire une race. L'UI reçoit callbacks stop/retry/configuration explicites ; ne pas
stocker silencieusement un texte en prétendant avoir contacté un modèle.
