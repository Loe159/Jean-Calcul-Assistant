# Abonnement ChatGPT direct — Sign in with ChatGPT

## Décision

Jean Calcul utilise **Sign in with ChatGPT** comme chemin principal pour les profils `openai-chatgpt-plan`.
L'application Android s'authentifie directement auprès d'OpenAI avec OAuth 2.0 + PKCE et appelle ensuite
`https://api.openai.com/v1/responses` sans clé API, sans PC et sans serveur intermédiaire.

Le backend `codex-companion` reste supporté pour compatibilité, mais n'est plus requis.

## Authentification

Le flux suit les contraintes OpenAI pour les applications open source locales :

- client dynamique `dynamic_agent_client` lors de la première connexion ;
- callback loopback `http://127.0.0.1:<port>/auth/callback` ;
- PKCE S256, `state` et `nonce` aléatoires ;
- scopes `openid profile email offline_access resource.invoke chatgpt.tokens.use.direct` ;
- ressource `https://api.openai.com/v1` ;
- validation RS256 de l'ID token avec le JWKS OpenAI ;
- validation de l'émetteur, de l'audience, de l'expiration, du nonce et du sujet ;
- rotation automatique du refresh token.

Le `client_id` délivré et le `ext_agent_host_id` stable sont des métadonnées non secrètes. Les access,
refresh et ID tokens sont sérialisés dans un seul secret chiffré par `SecretStore`/Android Keystore.
Ils ne sont jamais affichés ni journalisés.

## Inférence

Le backend `ChatGptPlanAgentBackend` utilise Responses avec :

- `store: false` ;
- `stream: true` ;
- l'historique nécessaire renvoyé à chaque tour ;
- le modèle configuré dans `AgentProfile.agentId` ;
- les définitions JSON Schema des outils Android disponibles.

Le backend n'accepte comme base de production que `https://api.openai.com/v1`, afin d'éviter qu'un jeton
ChatGPT soit transmis à un hôte arbitraire.

## Outils Android

OpenAI ne reçoit que les descriptions des outils actuellement disponibles. Un `function_call` est converti
en `ToolCall` non fiable puis suit exactement le chemin local existant :

```text
Responses API
  -> ToolCall
  -> LocalAgentToolRuntime
  -> ToolRegistry + validation de schéma
  -> PolicyEngine
  -> exécuteur Android
  -> audit
  -> function_call_output
  -> Responses API
```

Le modèle n'accède donc jamais directement aux API Android.

## Surfaces

La conversation et le bouton Power utilisent déjà la même abstraction `AgentBackendFactory`. Le profil
créé par **Continuer avec ChatGPT** devient actif pour les deux surfaces, ce qui garantit les mêmes outils,
politiques et règles d'audit.

## Compatibilité

Les profils existants `codex-companion` continuent de fonctionner avec `companion/`. Cette voie peut être
supprimée ultérieurement après migration explicite des anciennes installations.
