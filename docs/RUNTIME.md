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

La politique explicite n’autorise que `current_time` sans argument : lecture
réelle de l’horloge UTC, sans permission Android ni service tiers. Son schéma
est annoncé au fournisseur. Toute autre demande (Android, shell, etc.) est
refusée avant exécution, le run échoue en TOOL_POLICY et le journal le conserve.
Aucun résultat d’outil inventé n’est renvoyé au modèle. Le client assemble les
fragments `tool_calls`, conserve les identifiants et les résultats dans
l’historique, puis demande la suite au modèle. La boucle est bornée à quatre
tours, huit appels par tour et 16 Kio d’arguments par appel. Les serveurs/modèles
doivent accepter le paramètre OpenAI `tools`. Les confirmations et outils Android
sensibles restent dans la tranche outils ; aucun accès appareil n’est permis ici.
Une seule conversation courante est persistée ; « Nouveau » l’efface après
confirmation. Il n’y a pas encore de sélecteur de conversations archivées.

## Authentification ChatGPT

L’extension documentée dans `CHATGPT-AUTH.md` ajoute le parcours officiel
local/open source : inscription dynamique, navigateur système et PKCE, OIDC
signé, Keystore, comptes séparés et refresh/révocation. Il n’est pas nécessaire
de préconfigurer un client secret ou une clé partenaire pour ce parcours.
La précédente conclusion d’indisponibilité faute d’un client OAuth enregistré
était trop générale : elle concernait le parcours partenaire.

Le fournisseur `ChatGptProvider` appelle le catalogue du compte et l’API
Responses publique avec `store=false`, `stream=true`. Il réutilise le runtime
conversationnel. Aucun moteur Codex app-server ni endpoint backend-api n’est
embarqué. Le consentement utilisateur réel et l’inférence réelle restent
NOT_RUN jusqu’au test manuel ; la validation locale de la tentative 3 précède
cette extension et ne la valide pas. Voir `CHATGPT-AUTH.md` pour les preuves,
le budget E2E et les étapes d’utilisation sur le téléphone.

## Vérifications

Les tests JVM du runtime couvrent flux, stockage, annulation, événements tardifs,
retry, concurrence et reprise d’un run interrompu. Les tests transport utilisent
un serveur HTTPS de test et des données explicitement synthétiques : requête,
SSE, erreurs HTTP, troncature, assemblage d’appels d’outils, renvoi de résultats, fermeture effective
d’une socket en attente et validation de configuration. La boucle teste la lecture
d’horloge autorisée, le refus des autres noms et la borne de quatre tours.
Les tests Android vérifient round-trip Keystore, absence de clé en clair dans les
préférences, suppression de clé, relecture du stockage et recréation de l’activité.
Les scénarios de captures démarrent avec un historique synthétique effacé via
run-as ; ils ne supposent plus qu’un force-stop supprime les messages.
