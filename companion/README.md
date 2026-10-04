# Jean Calcul — compagnon ChatGPT

Le compagnon conserve l'authentification **Sign in with ChatGPT** sur une machine de confiance et expose à l'application Android un protocole local minimal. L'application Android ne reçoit jamais l'access token, le refresh token ni l'ID token ChatGPT.

## Prérequis

- Node.js 20+ ;
- `openssl` pour générer le certificat TLS local ;
- un abonnement ChatGPT autorisant l'usage par des applications participantes.

## Première installation

```bash
cd companion
npm run login
npm run pairing-reset
npm start -- --host 0.0.0.0 --port 43120
```

`login` ouvre le navigateur système et utilise OAuth/OIDC + PKCE. Les jetons sont stockés dans `~/.config/jean-calcul-companion/` avec permissions utilisateur uniquement.

`pairing-reset` affiche **une seule fois** un jeton d'appairage local. Ce jeton n'est pas une clé API OpenAI. Saisissez-le dans Jean Calcul ; Android le chiffre via Android Keystore.

Au démarrage, le compagnon affiche aussi l'empreinte TLS `sha256/...`. Copiez cette empreinte dans la configuration du backend afin que l'application n'accepte que ce certificat auto-signé précis.

## Commandes

```bash
npm run login          # Continue with ChatGPT
npm run status         # état local, sans afficher de jeton
npm run models         # modèles accessibles au compte ChatGPT
npm run pairing-reset  # révoque l'ancien appareil et crée un nouveau jeton local
npm run logout         # révoque la session OAuth quand possible puis efface les jetons
npm start -- --host 0.0.0.0 --port 43120
npm test
```

Par défaut, le serveur écoute uniquement sur `127.0.0.1`. Pour un téléphone physique sur le LAN, utilisez explicitement `--host 0.0.0.0`, conservez TLS et l'épinglage de certificat, puis autorisez uniquement le port choisi dans le pare-feu local.

## Garanties de sécurité

- aucune clé API OpenAI n'est utilisée ;
- les jetons ChatGPT ne quittent jamais le compagnon ;
- l'API Android-compagnon exige TLS et un jeton d'appairage révocable ;
- le certificat auto-signé est épinglé par son SPKI SHA-256 côté Android ;
- les requêtes Responses utilisent `store: false` et `stream: true` ;
- aucun secret n'est écrit dans les logs ou renvoyé par `/v1/status`.
