# Cuisine sur Android

Le lot C1 met en place le client Mastra, les caches Room, les sessions locales et les minuteurs. La route typée `CookingSession` ouverte depuis une notification affiche la recette de la session. Le parcours interactif et les cartes Now Brief arrivent en C2.

## Contrat et intégration

- Le code métier vit dans `io.github.mangi.eta.food`, un paquet fonctionnel de `:app`. Les entités, le DAO et la migration restent dans `data/db`, avec les autres schémas Room. `EtaApp` fournit les dépendances communes à l’activité, aux receivers et au job.
- `FoodClient` couvre les routes signées de `/mobile/v1/food`. Il réutilise `MastraConnection` et `MastraSignedTransport` avec le client Notifications. Le transport signe le chemin encodé et le corps exact, refuse les query strings et désactive les redirections. Les délais sont de 10 secondes par requête et 30 secondes pour l’assistant.
- Le décodeur accepte les champs inconnus et rejette les valeurs d’énumération inconnues. Le retour de feedback Brief attend un accusé de réception HTTP. Les fixtures sont copiées telles quelles et les calculs Kotlin exécutent tous les cas partagés. [Contrat Cuisine v1](https://github.com/Loe159/homelab-ai/blob/feat/food-module/docs/food-contract.md), [schémas Zod](https://github.com/Loe159/homelab-ai/tree/feat/food-module/src/mastra/food/schemas)

## Données hors ligne et sessions

Room expose les recettes versionnées, l’index et les métadonnées de bibliothèque, les disponibilités, le garde-manger et les cartes Brief sous forme de `Flow`. Chaque cache est rattaché à l’origine Mastra et à l’identifiant d’appareil de l’appairage. Le résultat du rafraîchissement distingue `NOT_PAIRED`, `OFFLINE`, `SERVER_ERROR`, `GROCY_UNAVAILABLE` et `AI_DISABLED`. Les données en cache gardent leur date de récupération, et les stocks gardent leur `stockAsOf`. Les cartes Brief expirent à `validUntil`.

La disponibilité hors ligne utilise le dernier stock connu pour la même version de recette. Elle recalcule les quantités exactes et les manques dans l’unité de la recette. Un stock inconnu reste inconnu et un ingrédient sans produit lié reste neutre. Les étapes affichent les fractions des quantités mises à l’échelle. Une quantité redéfinie pendant la session s’applique à la session entière, puis se répartit entre les étapes.

Une session fige le JSON de la recette dès sa création. Les changements de portions, d’étape, d’ingrédient ou de minuteur sont écrits localement avec une révision croissante pour la session active. Le serveur reçoit une copie complète par PUT quand l’appairage est disponible. Un échec de ce PUT laisse la session locale utilisable et la modification suivante envoie la révision courante. La migration 26 → 27 ajoute les tables Cuisine et préserve les données existantes.

## Fin de recette et rejeu

La confirmation enregistre en une transaction la session terminée, l’état `PENDING` et la ligne d’outbox avec un `operationId` unique. Chaque tentative poste les mêmes octets. Android tente l’envoi immédiatement et un `JobService` persistant exige le réseau et applique un délai de reprise exponentiel. Une réponse incertaine ou une réponse perdue laisse l’opération en file pour réconciliation par Mastra. Les états `SUCCEEDED`, `PARTIAL` et `FAILED` terminent le rejeu. Une opération associée à un appairage attend cet appairage avant tout envoi. Les opérations créées sans appairage sont associées au premier appareil utilisé pour l’envoi.

## Minuteurs et notifications

Les minuteurs indépendants conservent leur état et leur échéance absolue dans Room. Un réveil vérifie l’état et l’échéance avant d’afficher l’alerte. Android reprogramme les minuteurs au démarrage, après une mise à jour de l’app, à l’ouverture et après l’octroi de l’accès aux alarmes exactes.

Avec cet accès, le planificateur utilise `setExactAndAllowWhileIdle`. Sinon il demande une fenêtre d’une minute de part et d’autre de l’échéance et expose `exactAlarmsAvailable` à l’interface. Si le système livre l’alarme avant l’échéance, le réveil la reprogramme à partir de l’échéance. Android peut élargir la fenêtre des alarmes approximatives. [Planification des alarmes Android](https://developer.android.com/develop/background-work/services/alarms)

Le canal « Minuteurs cuisine » utilise le son et la vibration, avec les actions « +1 min » et « Arrêter ». Une notification continue ouvre la session active la plus récente. Une notification standard suffit, car Room conserve l’état et AlarmManager réveille l’app à l’échéance sans service de premier plan. Le listener et le dépôt Notifications excluent les deux canaux Cuisine de Jean-Calcul de l’historique et du retrait automatique.
