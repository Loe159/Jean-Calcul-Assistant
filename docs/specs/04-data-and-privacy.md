# Données, persistance et confidentialité

## Sources et stockage actuel

[Database v2](../../android/core-data/src/main/kotlin/fr/loevan/jeancalcul/data/JeanCalculDatabase.kt),
[entités](../../android/core-data/src/main/kotlin/fr/loevan/jeancalcul/data/conversation/ConversationEntities.kt),
[repository](../../android/core-data/src/main/kotlin/fr/loevan/jeancalcul/data/conversation/RoomConversationRepository.kt),
[configuration](../../android/core-data/src/main/kotlin/fr/loevan/jeancalcul/data/settings/DataStoreAssistantSettingsRepository.kt),
[SecretStore](../../android/core-security/src/main/kotlin/fr/loevan/jeancalcul/security/SecretStore.kt).

| Donnée | Propriétaire | Durée cible | Suppression/export |
| --- | --- | --- | --- |
| Conversation/messages | Room Android | Jusqu'à décision utilisateur | Cascade locale, export expurgé |
| Session/référence backend | Room Android | Avec conversation | Cascade ; n'est pas un token d'auth |
| Profils/voix/apparence | DataStore Android | Jusqu'au changement | Suppression cohérente des sélections |
| Tokens/clé/appairage | SecretStore/Keystore | Jusqu'à rotation/révocation | Jamais d'export de secret |
| Audit | Room + préférences de rétention | Selon réglage existant | Purge et export expurgé |
| Tâches MVP | LocalTaskStore actuel | Jusqu'à suppression explicite | Ne pas prétendre une synchro calendrier |
| Audio | Pipeline en mémoire | Durée de capture/lecture | Aucun archivage brut par défaut |
| Auth Codex compagnon | CLI sur PC | Politique CLI | Android ne la lit pas |

**DATA-01 — Identité.** UUID locaux pour conversation/message/session ; IDs distants restent
des métadonnées de session. Ne pas utiliser un ID backend comme clé primaire du message ou comme
preuve d'autorisation. Timestamps en epoch millis ; fuseau seulement pour présentation/données calendaires.

**DATA-02 — Séquences et transactions.** Séquence locale ordonnée et allocation atomique pour
les producteurs texte/voix. Préserver l'association message→session/conversation. Si plusieurs écritures
forment une opération logique, tester rollback et invariants après interruption. Un simple mutex dans
un ViewModel ne protège pas les deux processus Android.

**DATA-03 — Terminal et reprise.** Les messages partiels sont conservés. Au démarrage, détecter
STREAMING sans propriétaire reprenable et le marquer INTERRUPTED une fois. Ne pas effacer l'historique
pour supprimer un spinner. L'état d'exécution volatile ne doit pas être confondu avec l'état durable.

**DATA-04 — Migration.** La base actuelle est v2 ; MIGRATION_1_2 ajoute l'audit. Ajouter une
migration explicite pour toute évolution, exporter le nouveau schéma, tester 1→courant et 2→courant
si la version change. Ne pas réécrire les anciens schémas exportés ni activer fallbackToDestructiveMigration.
Un changement du JSON DataStore exige aussi lecture des anciennes valeurs, valeurs absentes et champs inconnus.

**DATA-05 — Sélection cohérente.** Supprimer/désactiver une connexion invalide les profils
dépendants et la sélection effective. Normaliser les deux sélections héritées selon CONV-01 dans
une seule logique testée. Révocation de secret et suppression d'un profil ne laissent pas un backend
réutilisable par une session cachée. La migration de sélection conserve les préférences voix/apparence.

**DATA-06 — Export.** Un snapshot ConversationExport conserve sa version publique et contient
uniquement les données prévues. Expurger secrets connus, en-têtes/tokens, URLs avec identifiants et
messages d'erreur sensibles, y compris s'ils ont été collés dans un texte. Si un champ est retiré,
documenter la décision et la compatibilité. Ne pas appeler « export chiffré » le JSON actuel partagé
par Intent. L'utilisateur choisit le destinataire du partage Android.

**DATA-07 — Suppression.** Supprimer conversation/messages/sessions en cascade après arrêt du
tour. Aucun écrivain tardif ne la recrée. La suppression locale ne garantit pas une suppression côté
fournisseur : l'UI doit préciser la portée réelle et n'annoncer une suppression distante que si implémentée.

**DATA-08 — Secrets et traces.** Uniquement références secretId dans les profils. Redaction avant
persistance dans l'audit, avant UI d'erreur et avant export. Ne pas journaliser prompts, transcriptions,
audio, headers, callback OAuth ni payloads authentifiés. Les tests utilisent des marqueurs synthétiques
reconnaissables, jamais les vrais tokens de la machine de développement.

**DATA-09 — Audit et rétention.** Les mises à jour d'un même actionId ne fabriquent pas plusieurs
succès. Conserver décision/refus/expiration/échec/rejeu avec leur chronologie utile. Un export et une
purge concurrents ont une sémantique snapshot testée ; ne pas suspendre la politique en attendant Room.
Les rapports de diagnostic indiquent versions, codes et temps, pas le contenu privé des demandes.

**DATA-10 — Sauvegarde et multi-processus.** allowBackup=false est présent et reste respecté.
Ne pas annoncer que Room est chiffré intégralement : seul le chiffrement réellement implémenté est
documentable. Vérifier la cohérence entre processus app et assistant_session lors des changements
de sélection/révocation ; DataStore et caches ne deviennent pas multiprocessus par leur seule annotation Hilt.
Si nécessaire, choisir un mécanisme de partage existant/explicite et le tester avant de promettre cette cohérence.

## Cas de test obligatoires selon la modification

Ancien JSON avec deux profils actifs ; profil supprimé ; secret absent ; séquence attribuée depuis
deux producteurs ; annulation entre placeholder et terminal ; cascade avec run actif ; export d'un
texte contenant un faux Bearer token ; purge audit ; ouverture v1/v2 et conservation des messages.

Les procédures de tests Room sur appareil sont dans [la validation](../agents/verification.md).
