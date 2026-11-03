# Conversation, routage et machine d'états

## Sources et unités

Lire ConversationViewModel, ConversationOrchestrator, ConversationScreenActions dans
[la feature](../../android/feature-conversation/src/main/kotlin/fr/loevan/jeancalcul/feature/conversation/),
[les entités de domaine](../../android/core-domain/src/main/kotlin/fr/loevan/jeancalcul/domain/Conversation.kt)
et [les tests existants](../../android/feature-conversation/src/test/kotlin/fr/loevan/jeancalcul/feature/conversation/ConversationOrchestratorTest.kt).

La conversation est durable ; le draft et le travail actif sont des états distincts. Le cycle
du réseau ne doit pas dépendre du nombre de collecteurs Compose.

## Sélection et création

**CONV-01 — Un profil actif.** L'état effectif est Aucun, Modèle(id) ou Agent(id). Pour réparer
le schéma actuel sans migration spéculative, conserver les deux champs mais rendre leur activation
mutuellement exclusive. Si une configuration héritée contient les deux, la normalisation conserve
l'agent valide (compatibilité de la priorité actuelle), efface l'autre et expose le profil choisi.
Si l'agent est invalide, conserver le modèle valide. Aucune sélection valide : Aucun.
Tester cette normalisation indépendamment du ViewModel. Une future union sérialisée exige sa migration.

**CONV-02 — Validation avant effet.** Vérifier existence/enabled de la connexion et du profil,
type cohérent et secret disponible avant toute requête. Aucun profil : garder le draft et proposer
« Configurer un assistant ». Ne pas ajouter un faux message COMPLETED. Un AGENT_BACKEND est
refusé sur le chemin direct et réciproquement, y compris avec une configuration DataStore corrompue.

**CONV-03 — Nouvelle conversation.** « Nouvelle » sélectionne un espace vide local, vide les
erreurs du précédent et conserve les anciens historiques. La session distante est créée au premier
envoi utile. Le collecteur de conversations ne remplace pas ce choix par l'ancien premier élément.
Le titre initial vient du premier texte non vide, limité à 48 caractères selon la convention existante.
Un draft saisi pendant un envoi n'est jamais effacé par la fin de cet envoi.

**CONV-04 — Reprise et changement de profil.** Une session appartient à un seul profil et type.
Reprendre utilise cette identité ; sélectionner un autre profil s'applique aux nouveaux tours/sessions
selon une action explicite. Pour ce jalon, si le profil de la conversation diffère du profil actif,
proposer une nouvelle conversation plutôt qu'envoyer silencieusement l'historique au nouveau moteur.
Un profil supprimé/désactivé interdit un nouvel envoi, même si une référence backend est persistée.

## États de l'exécution

**CONV-05 — Exclusion et identité.** Un seul tour actif pour le runtime partagé du MVP, toutes
surfaces confondues. Réserver ce travail avant la première suspension, pas après le chargement des
réglages. Double clic ou appui clavier simultané ne crée pas deux messages/runs. Une autre surface
affiche « Une réponse est en cours » avec accès au tour ou action d'arrêt. La concurrence multi-session
est différée. Un changement de conversation affichée ne doit pas réattribuer les deltas.

L'état UI cible ci-dessous est conceptuel ; il peut être représenté par un sealed type ou des champs
cohérents. Ne pas multiplier les booléens contradictoires.

| État | Entrée | Interface | Sortie autorisée |
| --- | --- | --- | --- |
| Repos | Pas de travail actif | Draft et Envoyer si valide | Préparation |
| Préparation | Envoi réservé | Destination, indicateur, Arrêter | Streaming, erreur, interruption |
| Streaming | Premier événement | Texte progressif, Arrêter | Confirmation, fin, erreur, interruption |
| Confirmation | Proposition exige une décision | Action exacte, Confirmer/Refuser | Exécution, refus, expiration, interruption |
| Exécution | Reçu valide | État d'outil, aucune double action | Streaming, fin, erreur, interruption |
| Arrêt demandé | Stop utilisateur | Bouton désactivé, retour rapide | Interrompu |
| Terminé | Événement terminal valide | Réponse persistée, draft utilisable | Repos |
| Échec | Erreur récupérable ou définitive | Cause et action adaptée | Retry explicite ou Repos |
| Interrompu | Annulation/fin de propriétaire | Texte partiel marqué | Retry explicite ou Repos |

**CONV-06 — Écriture unique.** Après validation locale, créer une seule fois le message utilisateur
et le placeholder assistant associés à un requestId. Mettre à jour ce même assistant à chaque delta.
Les séquences locales sont strictement ordonnées dans la conversation ; l'opération doit résister à
deux producteurs texte/voix, pas seulement au thread UI. Persister un terminal une seule fois.

**CONV-07 — Streaming terminal.** Un flux n'est terminé que sur Completed/Failed normalisé.
EOF sans terminal → FAILED récupérable (« Réponse interrompue par la connexion »), texte partiel conservé.
Un Completed avec réponse vide sans résultat utile ne doit pas construire un Message COMPLETED vide,
interdit par le domaine. Retourner un échec de protocole compréhensible. Ignorer événements de mauvais
requestId et doublons de séquence sans corrompre le contenu. Un delta après terminal n'ouvre pas un tour.

**CONV-08 — Erreurs.** ProviderException est normalisée. Les exceptions de transport/protocole
inattendues ferment aussi le placeholder avec erreur expurgée. Ne pas afficher la stack ou le corps HTTP.
Échec avant acceptation : préserver/restaurer le draft. Échec après création des messages : proposer
un retry du tour, sans recopier automatiquement le même texte dans un second envoi.

**CONV-09 — Annulation.** Stop annule le Job propriétaire et le run/appel réseau exact, y compris
pendant création/reprise/session et attente d'approbation. Propager CancellationException, terminer
le message partiel INTERRUPTED avec une section de cleanup bornée non annulable pour la persistance.
L'échec du cancel distant ne doit pas empêcher cleanup local. Les événements tardifs sont ignorés.

**CONV-10 — Retry sans duplication.** Autoriser le retry uniquement du dernier tour assistant
FAILED/INTERRUPTED de la conversation, sans tour postérieur. Réutiliser IDs utilisateur/assistant et
leur séquence, mais créer un nouveau requestId. Effacer ancien texte/erreur/usage de l'assistant.
Revalider profil, permission et outils ; conserver le runtime d'outils. Un résultat d'outil à effet
incertain ne doit jamais être rejoué automatiquement sous une nouvelle clé d'idempotence.

**CONV-11 — Contexte envoyé.** Inclure uniquement les messages du contexte autorisé jusqu'au
message utilisateur courant ; exclure le placeholder et les assistants FAILED/INTERRUPTED du contexte
de génération. Conserver ces derniers dans l'historique visible. Ne pas transformer une sortie d'outil
en message système. Pour le compagnon actuel, le dernier texte est envoyé au thread distant : ne pas
supposer que son transport rejoue la même liste que le modèle direct.

**CONV-12 — Rotation et mort de processus.** Une rotation ne démarre pas un second run et conserve
sélection/draft via l'état adapté. La mort du processus ne laisse pas un STREAMING perpétuel : marquer
interrompu à la reprise si aucun run propriétaire ne peut être repris avec preuve. Ne pas prétendre
reprendre un flux réseau en recréant uniquement son ViewModel.

**CONV-13 — Suppression/export.** Supprimer exige une décision utilisateur. S'il s'agit du tour actif,
arrêter et attendre le cleanup avant la suppression en cascade ; aucun callback tardif ne recrée les
messages. Export utilise un snapshot cohérent et la redaction de la spec données. Un échec d'export
ne doit ni effacer le draft ni masquer une erreur de conversation.

**CONV-14 — Options de modèle.** Le chemin direct transmet temperature/maxOutputTokens configurés
dans GenerationOptions et respecte les capacités effectives. Échouer explicitement si une option
indispensable n'est pas disponible ; ne pas annoncer une option appliquée alors qu'elle est ignorée.

## Scénarios de référence

| Cas | Given / When | Then |
| --- | --- | --- |
| Texte direct | Modèle actif ; envoyer « Bonjour » ; fake émet deux deltas et Completed | 1 utilisateur, 1 assistant final ; les deux états intermédiaires sont observés |
| Agent | Agent actif ; remote session créée ; second tour | Bonne référence locale/distante, pas de session modèle |
| Double clic | Deux appels send avant reprise du scheduler | 1 tour et un seul appel backend |
| Arrêt | Stream suspendu ; Stop ; événement tardif | INTERRUPTED, réseau annulé, texte tardif absent, nouvel envoi possible |
| Retry | Dernière réponse FAILED ; Retry | Même nombre de lignes, nouveau requestId, outils présents |
| Navigation | Tour A actif ; afficher B | Deltas uniquement dans A ; stop cible A |
| Processus | Base contient ancien STREAMING, aucun run reprenable | Historique INTERRUPTED, aucun spinner infini |
| Absence de profil | Draft non vide ; Envoyer | Aucun réseau, draft intact et action réglages |
