# Notifications — décisions Mastra (V2)

Mastra (dépôt `homelab-ai`, workflow `notification-intelligence`) est l'unique moteur de
classification métier. Jean-Calcul ne classe plus rien en fonctionnement normal : il écoute,
conserve, envoie, puis **vérifie et exécute**. Référence serveur : `homelab-ai/docs/notifications.md`.

## Chaîne

1. `AgentNotificationHistoryService.onNotificationPosted` copie la notification (thread dédié, jamais
   le thread principal) dans `NotificationHistoryRepository` avec un identifiant opaque (`event_id`)
   et une **révision** qui augmente à chaque changement de contenu. La clé Android ne quitte jamais le téléphone.
2. Si le homelab est associé, la ligne passe `sync_state=QUEUED` et `MastraNotificationSync.request`
   planifie un job `JobScheduler` (réseau requis, regroupement 3 s, backoff exponentiel). Pas de polling.
3. Le job envoie des lots ≤ 50 signés HMAC (`MastraNotificationClient`), après `NotificationPrivacyFilter`
   (codes numériques masqués, `redactions: ["NUMERIC_CODE"]`). Ni icône ni pièce jointe.
   Le homelab applique d’abord ses règles. Le mode OpenAI est activable côté serveur avec l’accord
   de l’utilisateur pour un appareil précis. Il transmet à l’API OpenAI le texte filtré et les
   métadonnées utiles à la classification des notifications sans règle applicable.
4. Chaque décision valide est enregistrée **uniquement** si `event_id`, révision et empreinte
   correspondent. La priorité de Now Brief suit la correction utilisateur ou, en son absence, Mastra.
5. Quand « Retrait automatique » est activé, Jean-Calcul garde `NOW` dans le panneau et retire
   `TODAY` et `LOW`. La règle couvre toutes les catégories. Sur le thread principal, la notification
   doit toujours être active, effaçable et avoir la même empreinte. Un résumé de groupe attend le
   départ de ses notifications enfants. Jean-Calcul réexamine les notifications classées encore
   actives à la reconnexion, à la réactivation du retrait et après correction de priorité.
6. Seul `onNotificationRemoved(REASON_LISTENER_CANCEL)` marque `android_action_state=CONFIRMED`.
   Archiver dans Now Brief reste une action locale qui n'efface rien sur Android.

États distincts : `sync_state` (LOCAL, QUEUED, SENT, DECIDED, ERROR, REJECTED, EXPIRED après 24 h),
`android_action_state` (NONE, REQUESTED, CONFIRMED, SKIPPED_PROTECTED, SKIPPED_STALE, SKIPPED_NO_CONSENT, FAILED)
et `filter_state` historique pour l'affichage.

## Hors connexion

Homelab injoignable : la notification reste dans l'historique (« Analyse en attente »), rien n'est
retiré, le job réessaie avec backoff. Sans homelab associé, Now Brief l'indique et range tout dans
« À voir aujourd'hui » sans classification.

## Now Brief

`priorityForBrief` lit la priorité corrigée par l'utilisateur, sinon celle de Mastra, sinon TODAY.
Les cartes affichent la catégorie, la priorité, la raison de Mastra, l'état réel du filtrage et les
suggestions (menu COUI). « Corriger la priorité », « Changer la catégorie », « Intéressante »,
« Indésirable », « Suppression incorrecte », ouverture, archivage et restauration alimentent l'outbox
de feedback (`notification_remote_feedback_outbox`) envoyée au homelab.

Actions : `PREPARE` → résumé exact (calendrier : appel suspendu du Secrétaire, approbation native) →
`APPROVE` avec l'empreinte affichée, ou `REJECT`. Les brouillons sont seulement copiables.

## Réglages

Notifications intelligentes → Homelab : association par chaîne `jeancalcul-mastra:v1?...`
(secret chiffré par Android Keystore via `McpSecretStore`), test, synchronisation, consentement
d'apprentissage (désactivé par défaut), dissociation ; préférences du homelab consultables et supprimables.

## Ancien moteur local

`LocalNotificationModel`, `NotificationObservation` et les politiques Kotlin restent dans le code pour
les tests de régression et le diagnostic, mais le listener ne les appelle plus et
`requestSafeFilter` ne peut plus agir. Les règles validées vivent désormais côté Mastra (`rules.ts`).

## Tests

- `MastraContractTest` : fixtures v1 partagées (copie de `homelab-ai/tests/fixtures/notification-contract/v1`),
  vecteur de signature, validation, appairage, filtre de confidentialité, priorité Now Brief.
- `MastraNotificationSyncTest` : faux homelab (intercepteur OkHttp) : hors connexion, décision tardive,
  NOW+DISMISS rejeté, rejeu Android, feedback dédupliqué, confirmation par callback uniquement.
- `NotificationHistoryRepositoryTest` : migration v6 → v7 sans perte, révisions.
- Scénario visuel `brief_mastra`.

Limites : la suppression réelle sur un Samsung n'est pas prouvée par ces tests ; l'émulateur valide le
chemin Android mais pas un homelab réel sur Tailscale.
