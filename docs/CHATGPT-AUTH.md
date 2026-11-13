# Connexion ChatGPT — issue #56

L’action principale des préférences est désormais **Continue with ChatGPT**.
Le parcours utilise l’inscription dynamique documentée pour les clients locaux /
open source. Il ne demande ni clé API partenaire ni client secret à configurer.
La page « Request a client ID » concerne un autre parcours, destiné aux
partenaires commerciaux ; la précédente conclusion qui bloquait toute connexion
sur un enregistrement préalable était trop générale.

## Utilisation sur le téléphone

1. Ouvrir Réglages → Continue with ChatGPT. Android ouvre le navigateur système.
2. Se connecter chez OpenAI, choisir le compte/espace et autoriser Jean-Calcul
   à utiliser les capacités éligibles de l’abonnement ChatGPT.
3. Après le retour local, revenir dans Jean-Calcul. L’app vérifie le jeton signé
   et le droit d’utiliser l’abonnement ; elle affiche ensuite le compte autorisé.
4. Accepter le message expliquant l’utilisation du plan, choisir un modèle dans
   le catalogue du compte, puis revenir au chat pour envoyer un message.
5. « Gérer l’utilisation ChatGPT » ouvre les réglages d’usage OpenAI. La
   déconnexion tente la révocation puis efface les jetons de cet appareil.

Aucun identifiant ni mot de passe ChatGPT n’est saisi dans Jean-Calcul.
Les jetons ne sont jamais demandés à l’utilisateur ni copiés depuis Codex.
Le navigateur affiche une page locale statique après le callback ; elle ne
contient ni code ni jeton et invite à revenir dans l’application. Un arrêt du
processus pendant l’autorisation impose de recommencer, sans réutiliser le code.
L’éligibilité du compte et les limites d’usage sont décidées par OpenAI.

## Protocole implémenté

- Un identifiant opaque `urn:uuid:…` est sauvegardé avant la première connexion
  pour cette installation. Il reste stable après redémarrage et déconnexion.
- Premier consentement : `dynamic_agent_client` et le nom réel Jean-Calcul.
  Le `client_id` délivré dans le callback est utilisé pour l’échange de code,
  puis conservé avec l’identité vérifiée. Les connexions suivantes le réutilisent.
- `state`, `nonce` et vérificateur PKCE aléatoires nouveaux pour chaque tentative.
  Défi S256 ; callback exact `http://127.0.0.1:<port>/auth/callback`, écouteur
  ouvert avant le navigateur, lié seulement à IPv4 loopback, limité à cinq minutes.
- Rejet des retours expirés, état incorrect/dupliqué, codes ambigus et changement
  de client. L’écouteur ferme après le premier callback accepté ou refusé.
- Échange HTTPS form-encoded avec le même callback, vérificateur et resource.
  Validation OIDC RS256 avec Nimbus JOSE + JWT et les JWKS publiées par OpenAI :
  signature, issuer, audience/azp, exp, iat/nbf, nonce et identité attendue.
- Vérification du scope `chatgpt.tokens.use.direct` avant toute inférence. Une
  identité valide sans ce droit reste enregistrée mais ne peut pas utiliser le
  plan. Le prochain consentement explicite emploie `prompt=consent`.
- Chaque compte/espace conserve son client, identité, jetons, modèle et état
  d’accueil séparément. Le sélecteur distingue les inscriptions, même si leurs
  emails sont identiques. Un nouveau compte n’écrase pas les autres.
- Les comptes et jetons sont dans une enveloppe AES-GCM atomique sous
  `noBackupFilesDir`, protégée par une clé Android Keystore non exportable.
  Aucun jeton dans Compose, SavedState, SharedPreferences, URL applicative,
  journal du runtime ou logs. Les classes internes masquent leur `toString`.
- Renouvellements sérialisés, remplacement atomique des jetons tournants.
  Seuls les échecs de refresh explicitement terminaux suppriment les jetons.
  Une panne temporaire garde les credentials et ne change pas la facturation.
- Déconnexion : arrêt du run, découverte de l’endpoint de révocation OpenAI,
  deux tentatives bornées avec délai. Si la révocation n’est pas confirmée,
  les jetons sont quand même supprimés localement et l’interface le précise.
  L’inscription et l’identifiant d’hôte restent pour une reconnexion ultérieure.

Les destinations OAuth et API ne sont pas éditables par l’utilisateur. Les
redirections HTTP sont désactivées. Les URLs découvertes pour JWKS et révocation
restent HTTPS sur le même hôte/port que l’issuer. Les réponses d’erreur brutes,
codes OAuth et URLs d’autorisation ne sont pas journalisés.

## Inférence liée au même runtime

Le catalogue vient de `GET https://api.openai.com/v1/models` avec le jeton du
compte sélectionné. L’UI respecte son ordre, filtre les modèles affichables et
utilise leur slug réel ; elle n’invente pas un modèle par défaut.

Les messages utilisent `POST https://api.openai.com/v1/responses`, avec
`store=false` et `stream=true`, via HTTP/SSE. Aucun endpoint ChatGPT `backend-api`
ni runtime Codex exécutant des outils sur Android n’est utilisé. L’historique
persistant, stop/retry et la politique d’outils restent ceux du runtime commun.
`response.completed` est obligatoire ; une troncature, un résultat incomplet ou
un échec de quota ne devient pas un succès. Les résultats d’outils et éléments
chiffrés de raisonnement sont conservés pour les tours sans stockage serveur.
Les éléments opaques ne sont réutilisés que pour le même compte et le même modèle.
Seule l’horloge UTC est autorisée ; aucun accès Android ou shell n’est ajouté.

Le fournisseur API configurable reste une option explicite. Aucun échec ChatGPT
ne bascule automatiquement vers une clé API ou une autre facturation. Changer
le compte ou le fournisseur affecte les prochains envois de l’historique courant,
comme indiqué dans les réglages ; « Nouveau » permet de l’effacer auparavant.

## Preuves et limites de cette modification

Build Debug, lint, compilation instrumentation et tests JVM sont exécutés avant
livraison. Les tests d’authentification utilisent un vrai serveur HTTPS local de
fixture, de vraies clés RSA synthétiques et un écouteur loopback réel. Ils ne
prouvent pas un consentement OpenAI ni une réponse d’un abonnement réel.

La tentative locale #56 n°3 valide le commit **antérieur** `b414534`, avant cette
connexion ChatGPT. Elle ne doit pas être citée comme preuve du nouvel écran,
du nouveau stockage OAuth ou d’une autorisation utilisateur réelle.

Pour cette extension : consentement réel, catalogue réel et réponse réelle
**NOT_RUN**, en attendant la connexion manuelle de l’utilisateur. Les nouveaux
tests Android (entrée de connexion et enveloppe Keystore) et la revue visuelle
locale sont **NOT_RUN** tant qu’une quatrième tentative n’est pas explicitement
autorisée. Le budget n’est pas contourné par un nouveau run-id. Aucune capture
personnelle et aucun jeton réel ne doivent être ajoutés aux preuves.

## Sources officielles consultées le 13 novembre 2026

- https://developers.openai.com/siwc/token-sharing-open-source/sign-in
- https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions
- https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference
- https://developers.openai.com/siwc/token-sharing-open-source/errors-and-recovery
- https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations
- https://developers.openai.com/siwc/ui-ux-guidelines
- https://auth.openai.com/.well-known/openid-configuration

La découverte publique a été vérifiée : issuer auth.openai.com, endpoints
accounts/authorize et accounts/oauth/token, JWKS OpenAI, révocation
accounts/oauth/revoke, signatures ID-token RS256. Ce contrôle ne crée pas de
connexion utilisateur et n’est pas une preuve de fonctionnement de bout en bout.
