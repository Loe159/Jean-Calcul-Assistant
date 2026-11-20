# Jean-Calcul — périmètre sélectionné

Reprise fidèle des composants COUI et des comportements ETA retenus, sous identité Jean-Calcul.
Source : ETA 83a25e71eed1e5d8dadbd330362e3999b46595d9 (3.4.4).

Conversations : ChatGPT/Codex via abonnement et serveurs vLLM auto-hébergés via API OpenAI compatible.
La clé vLLM est facultative ; l’adresse doit être celle du serveur accessible depuis le téléphone, suivie de `/v1`.
`127.0.0.1` désigne le téléphone, pas le PC. Android 17 demande l’accès réseau local pour le homelab.

Voix (conversation et assistant du bouton marche) : trois moteurs seulement, au choix pour la reconnaissance
et pour la lecture — Android local (défaut, sans compte), OpenAI (clé API personnelle) et vLLM
(`/v1/audio/transcriptions`, `/v1/audio/speech`). Adresse vLLM vocale vide = celle du fournisseur vLLM de conversation.
Les fournisseurs vocaux ETA (Qwen, Doubao, MiMo, DeepSeek, Gemini, MiniMax, stockage OSS) sont retirés.

Voir [la sélection](eta/SELECTION.md) pour chaque bloc. Root et pont LSPosed restent optionnels pour les capacités choisies.
Les compatibilités One UI et services vocaux se vérifient sur appareil ; aucune promesse de fonctionnement universel.
