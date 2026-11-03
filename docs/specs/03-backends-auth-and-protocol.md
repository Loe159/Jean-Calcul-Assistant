# Backends, authentification et protocole

## Trois chemins à distinguer

| Chemin | Contrat | Identifiants | Situation |
| --- | --- | --- | --- |
| Modèle direct | ModelProvider / ChatRequest | Secret API du fournisseur ou aucun pour Ollama local | Adaptateurs présents ; routage UI à compléter |
| codex-companion | AgentBackend / AgentRequest | Token Jean Calcul sur Android ; compte ChatGPT dans CLI PC | Serveur Python et adaptateur v2 présents |
| openai-chatgpt-plan | AgentBackend / AgentRequest | OAuth chiffré sur Android | Implémenté dans le dépôt ; support externe réel à établir |

[L'état réel](../agents/current-state.md) décrit le conflit des ADR 0016/0017.
Cette spec ne constitue pas une documentation officielle d'un fournisseur externe.

## Authentification

**AUTH-01 — Preuve externe.** Avant de déclarer le chemin direct utilisable, relever les sources
officielles datées autorisant ce client, ses scopes, le compte, le modèle et l'endpoint. Faire un
consentement puis une réponse réelle avec compte autorisé et consigner des métadonnées expurgées.
Ne pas confondre abonnement ChatGPT et crédit API ; ne pas déduire leur interchangeabilité d'un mock.
Si le support n'est pas établi, indiquer « Connexion directe non vérifiée » ou indisponible avec
action adaptée. Ne pas modifier scopes/identifiants au hasard pour contourner un refus.

**AUTH-02 — Comptes séparés.** Aucun secret ChatGPT transmis au compagnon par le téléphone.
Pour le compagnon, le CLI gère login et tokens ; Android possède uniquement son token d'appairage.
Pour le direct, les tokens restent dans SecretStore/Keystore. Aucun export de compte brut.

**AUTH-03 — Cycle OAuth direct.** Les contrôles présents dans ChatGptPlanAuthManager sont à
préserver et tester : state/PKCE/nonce, signature et claims attendus, callback loopback, expiration,
rotation. Le navigateur est lancé par une action utilisateur. Annulation, timeout, mauvais state,
callback répété et fermeture activité libèrent le listener et affichent une récupération.
Ces contrôles ne prouvent pas que le serveur reconnaît le client ou les scopes.

**AUTH-04 — Secret et rotation.** Pas de token dans les drafts, SavedStateHandle, exceptions,
URL ou logs. Une rotation de refresh token remplace atomiquement l'ancien secret ; tests de deux
requêtes simultanées. Une suppression de connexion supprime son secret si aucune autre référence
n'en dépend. Une erreur Keystore expose une reconnexion, pas un fallback en clair.

**AUTH-05 — Destination.** Le direct reste limité à l'endpoint de production explicitement
autorisé par la factory ; une URL modifiée ne reçoit jamais un token ChatGPT. Vérifier redirections
et hôtes lors de l'authentification, pas seulement la chaîne initiale. Les URLs MockWebServer restent
des paramètres de test internes, pas une préférence utilisateur contournant cette règle.

**AUTH-06 — Révocation.** Profil/connexion supprimé, disabled ou secret révoqué : pas de nouveau
tour ; l'activité en cours est annulée proprement. Ne pas conserver un backend authentifié caché
comme moyen de contourner la révocation. Le test de connexion doit être distingué d'un droit durable.

**AUTH-07 — Dégradation.** Auth invalide → reconnecter ; manque de permission/compte → expliquer ;
réseau indisponible → retry explicite. Aucun basculement automatique vers un autre fournisseur ou
une facturation API. Les commandes locales restent accessibles par leur chemin explicitement choisi.

**AUTH-08 — Compagnon privé.** HTTP uniquement loopback pour le compagnon actuel, via adb reverse
si nécessaire. Le serveur refuse l'écoute distante non protégée. Rotation/révocation liée à un appareil,
LAN/TLS et QR restent le travail de #42, pas une capacité obtenue en changeant 127.0.0.1 en 0.0.0.0.
Ne pas exposer le port sur Internet pour résoudre un problème de connexion.

## Protocole compagnon v2 observé

Source : [Handler et StreamEvent Python](../../companion/codex_companion.py) et
[adaptateur Kotlin](../../android/core-network/src/main/kotlin/fr/loevan/jeancalcul/network/codex/CodexCompanionAgentBackend.kt).
Chaque requête exige `Authorization: Bearer` et `X-Jean-Calcul-Protocol: 2`.
Le contenu JSON est limité à 1 000 000 octets dans le serveur actuel.

| Route | Corps/paramètres utiles | Succès |
| --- | --- | --- |
| GET /v1/status | Aucun | 200 : protocolVersion, state, auth |
| POST /v1/sessions | tools optionnel | 201 : sessionId, resumable |
| POST /v1/sessions/{id}/resume | tools optionnel | 200 : même sessionId, resumable |
| POST /v1/sessions/{id}/runs | requestId, text, tools | 202 : runId, status |
| GET /v1/sessions/{id}/runs/{runId}/events?after=N | Curseur exclusif | 200 NDJSON ordonné |
| POST /v1/sessions/{id}/runs/{runId}/tool-results | callId, success, output ou error | 202 accepted |
| POST /v1/sessions/{id}/runs/{runId}/cancel | Aucun corps requis par Handler actuel | 202 cancelling |

401 correspond à l'appairage invalide ; 426 à une version absente/incompatible ; 404 à un run
inconnu ; 400 couvre notamment request/after invalide ; certaines erreurs de parsing sont actuellement
normalisées en 503 par Handler. Ne pas documenter un futur 400 strict comme le comportement actuel.
502 codex_protocol et 503 codex_unavailable sont distincts d'une authentification échouée.

**NET-01 — Version et parsing.** Tester version correcte/absente/incompatible. Chaque ligne NDJSON
est un objet complet ; gérer les frontières de lecture réseau, Unicode et EOF au milieu d'une ligne.
Un événement invalide ne devient pas un delta vide silencieux. Les champs d'autorité inconnus ne
sont pas appliqués même si ignoreUnknownKeys permet la compatibilité des métadonnées.

**NET-02 — Corrélation.** Conserver requestId, sessionId, runId et callId séparément. Vérifier
qu'une réponse/reprise concerne la session attendue. Le curseur de reprise ne peut être réutilisé
pour un autre flux sans preuve de sa portée. La séquence actuelle Python est initialisée avec
time.time_ns par run ; elle n'est pas une garantie de séquence persistante après redémarrage.

**NET-03 — Reprise limitée.** Le compagnon conserve les sessions/runs en mémoire et relâche un run
après terminal consommé. Tester déconnexion avant terminal, reprise après terminal et redémarrage.
Si la reprise est impossible, marquer le tour interrompu et proposer un nouveau tour explicite.
Ne pas rejouer automatiquement une demande qui pourrait avoir déjà exécuté un outil.

**NET-04 — Outils.** tools contient nom, version, description, inputSchema. Le compagnon convertit
en noms dynamiques et refuse les collisions de normalisation. L'appel revient à Android, qui valide
et décide. Le retour est corrélé au callId exact. Un changement de catalogue lors d'une session doit
avoir un test vérifiant les définitions réellement exposées au thread, pas seulement stockées localement.

**NET-05 — Annulation/timeouts.** Annuler ferme l'appel de streaming et envoie l'interruption au
bon run. Distinguer attente première réponse, attente outil et silence du stream. Le serveur actuel
limite la connexion événements à 300 s ; ce timeout n'est ni une fin de réponse ni un job long.
Les threads/processus du compagnon doivent être fermés en cleanup dans les tests.

**NET-06 — Modèles directs.** Utiliser les factories OpenAiCompatible/Anthropic/OpenRouter/Ollama
existantes. Leur construction doit partir de la connexion choisie et du SecretStore, en conservant
leurs erreurs normalisées, options et capacités. Un probe réussi ne crée pas une réponse de chat.
Le test du routage couvre chaque kind sans dépendre d'une clé réelle.

**NET-07 — Capacités honnêtes.** Afficher les capacités obtenues du backend ou connues par son
adaptateur, pas une liste universelle. supportsToolApprovals distant=false n'interdit pas une
confirmation locale Android, mais n'autorise pas à appeler approveTool distant. Skills/jobs longs
ne sont pas disponibles pour ces backends simplement parce que l'interface a des méthodes.

**NET-08 — Erreurs normalisées.** Conserver ProviderErrorCategory et retryAfterMillis ; pas de
corps brut dans l'UI. NETWORK/TIMEOUT/SERVICE_UNAVAILABLE → réessai ; AUTHENTICATION → reconnexion ;
RATE_LIMITED → attente affichée ; MODEL_NOT_FOUND → réglages ; PROTOCOL → diagnostic/version ;
CANCELLED → interruption. Aucune boucle de retry non bornée ni fallback de destination implicite.

Exemple synthétique de statut, sans secret :

```json
{"protocolVersion":"2","state":"ready","auth":"chatgpt"}
```

Pour une évolution du wire, mettre à jour Python, DTO Kotlin et tests contractuels ensemble.
Le protocole Gateway futur de phase 3 ne remplace pas silencieusement ce protocole v2.
