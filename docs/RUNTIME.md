# Runtime de conversation — #56

`ConversationRuntime` est indépendant d’Android et de Compose. Une session
persistée contient les messages, le run courant et un journal borné à 200 événements
sans contenu, arguments d’outil, clé ni corps d’erreur HTTP. Le ViewModel conserve
le run pendant la rotation. Après mort du processus, RUNNING devient INTERRUPTED ;
la reprise est explicite et régénère la réponse au dernier message utilisateur.
Retry remplace la réponse partielle sans dupliquer la question. Stop ferme la
requête réseau et invalide les événements tardifs ; reset arrête puis efface.

Le stockage privé utilise AtomicFile. Il n’est pas chiffré ; Android assure
l’isolation et le chiffrement du stockage de l’appareil. Les sauvegardes Android
sont désactivées. Les clés API sont chiffrées AES-GCM avec une clé non exportable
Android Keystore ; elles ne sont jamais préremplies dans l’interface. Enregistrer
avec un champ clé vide supprime la clé précédente, comme indiqué dans le formulaire.

## Fournisseur

Configurer une URL **de base** HTTPS (par exemple `https://hote/v1`) et un
identifiant de modèle accepté par ce serveur. Le client POSTe vers
`/chat/completions` et reçoit le SSE OpenAI-compatible. Les serveurs de modèles
locaux, par exemple llama.cpp derrière un frontal TLS, utilisent le même protocole.
Le certificat doit être reconnu par Android ; aucun bypass TLS ni HTTP clair.
Le libellé « local déclaré » est une déclaration de l’utilisateur, pas une
attestation réseau. L’hôte réel est toujours affiché. Chaque envoi transmet
l’historique courant à cet hôte. Aucun fallback ou choix implicite de modèle.

Les redirections sont désactivées pour ne pas transférer une clé à un autre hôte.
Les corps d’erreurs restent hors du journal et de l’interface. Les statuts distinguent
configuration, authentification, quota, réseau, protocole, fournisseur et stockage.
Une réponse coupée, vide, filtrée ou limitée par le fournisseur n’est pas annoncée
comme terminée. Les délais réseau sont bornés à 15 s connexion / 60 s lecture /
5 min appel. Aucun log HTTP de requête/réponse n’est activé.

## Outils et limites de la tranche

Aucun outil Android n’est enregistré ni annoncé. La politique explicite refuse
toute demande, y compris `tool_calls` / `function_call` non sollicités ; le run
échoue en TOOL_POLICY et le journal le conserve. Aucun résultat d’outil inventé
n’est renvoyé au modèle. Le registre d’outils autorisés, les confirmations et la
boucle après exécution relèvent de la tranche outils, pas d’un faux outil de démo.
Il n’y a donc pas encore de boucle de tool calling avec exécution autorisée.
Une seule conversation courante est persistée ; « Nouveau » l’efface après
confirmation. Il n’y a pas encore de sélecteur de conversations archivées.

## Codex direct : NOT_RUN / non intégré

La condition de l’issue « après preuve de fonctionnement et conformité » n’est
pas satisfaite par des tests SSE synthétiques. La documentation officielle décrit
l’authentification Codex et l’intégration app-server ; cela ne constitue pas une
preuve OAuth Android pour cette application. Aucun client OAuth appartenant à
une autre application, fichier auth.json de l’hôte, cookie ou jeton de session
ChatGPT n’est réutilisé. L’interface indique cette indisponibilité.

Sources consultées le 13 novembre 2026 :
- https://developers.openai.com/codex/auth/
- https://learn.chatgpt.com/docs/app-server
- https://developers.openai.com/api/reference/resources/chat

Pour terminer ce critère, fournir une voie d’authentification officiellement
autorisée pour Jean-Calcul, vérifier le cycle connexion/rafraîchissement/révocation
et effectuer un échange réel autorisé. Aucune réponse d’un modèle réel distant
ou local n’a été validée par les fixtures. Ne pas clore #56 sur cette seule PR.

## Vérifications

Les tests JVM du runtime couvrent flux, stockage, annulation, événements tardifs,
retry, concurrence et reprise d’un run interrompu. Les tests transport utilisent
un serveur HTTPS de test et des données explicitement synthétiques : requête,
SSE, erreurs HTTP, troncature, refus d’outil et validation de configuration.
Les tests Android vérifient round-trip Keystore, absence de clé en clair dans les
préférences, suppression de clé, relecture du stockage et recréation de l’activité.
Les scénarios de captures démarrent avec un historique synthétique effacé via
run-as ; ils ne supposent plus qu’un force-stop supprime les messages.
