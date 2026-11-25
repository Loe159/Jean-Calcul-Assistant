# Cuisine sur Android

Cuisine s’ouvre depuis le menu principal, dans le groupe des raccourcis au-dessus des conversations. Ce point d’entrée reste disponible quand aucune carte Cuisine n’est présente dans le Brief. Les routes typées utilisent la transition COUI et le geste de retour habituel. La notification d’un minuteur ouvre directement la même session Room que le bouton « Reprendre » du Brief.

## Écrans

- **Cuisine** regroupe Recettes et Garde-manger. La bibliothèque trie les favoris en tête et accepte une recherche par nom ou tag. La suggestion « Que cuisiner avec ce que j’ai ? » utilise le classement déterministe de Mastra. Les recettes proposées sont regroupées selon les achats requis. Les lignes visibles chargent le détail des manques à la demande.
- **Garde-manger** regroupe les produits par emplacement et propose un filtre de péremption. Le relevé daté et les quantités estimées restent identifiables hors ligne. L’ajout recherche le catalogue ou crée un produit. La correction peut ajouter ou retirer une quantité et signaler un produit abîmé. Une tentative répétée avec la même saisie réutilise son identifiant d’opération tant que l’éditeur reste ouvert.
- **Recette** recalcule immédiatement les portions, la checklist, les instructions et les manques à partir du dernier relevé connu. Elle demande en parallèle la disponibilité serveur pour le nombre de portions choisi. Le favori et les options de bibliothèque sont dans la barre d’action. L’adaptation ouvre les mêmes contrôles de suivi et d’annulation que la génération.
- **Création** accepte le garde-manger, une envie, une surprise ou un texte collé. Le formulaire pilote les paramètres de génération et l’identifiant du job est conservé dans l’état de navigation. Une erreur réseau, l’absence d’appairage ou l’IA désactivée produit un état explicite. Les résultats reçus sont mis en cache avant l’ouverture de la fiche.
- **Session de cuisine** garde la progression au-dessus de l’instruction et propose des contrôles explicites d’étape. Les feuilles donnent accès aux ingrédients, au sommaire, aux options de lecture et à l’assistant. Chaque minuteur peut être ajusté indépendamment et affiche un décompte à partir de son échéance Room. Les propositions significatives demandent une confirmation distincte.
- **Récapitulatif** permet de corriger la consommation, le nombre de portions, les restes et la note. La confirmation déclenche l’outbox C1. Le statut du résultat et les détails par ligne restent disponibles dans la destination de session. Les préférences culinaires, accessibles dans Cuisine, permettent de modifier les déclarations et de supprimer des inférences.

## Now Brief

La session active la plus récente est lue dans Room et passe avant les cartes réelles, y compris pendant une panne réseau. Une extension avec priorité de placement conserve le rendu et les animations du flux Brief sans donner au shell la responsabilité de la logique Cuisine. Les deux cartes serveur éventuelles gardent leur durée de validité et leur ordre de priorité. La personnalisation permet de masquer ces recommandations et péremptions tout en gardant l’accès à la session active.

L’ouverture envoie `OPENED` et route vers la recette ou le garde-manger filtré. Masquer une carte envoie `DISMISSED`. Une recette ouverte depuis le Brief conserve l’identifiant de sa recommandation dans la session. L’envoi de fin de recette émet `COOKED` avec un identifiant stable, y compris lors d’un rejeu de consommation. Les événements d’ouverture et de masquage sont envoyés au mieux à partir du cache courant.

## Contrat et intégration

- Le code métier vit dans `io.github.mangi.eta.food`, un paquet fonctionnel de `:app`. Les entités, le DAO et la migration restent dans `data/db`, avec les autres schémas Room. `EtaApp` fournit les dépendances communes à l’activité, aux receivers et au job.
- `FoodClient` couvre les routes signées de `/mobile/v1/food`. Il réutilise `MastraConnection` et `MastraSignedTransport` avec le client Notifications. Le transport signe le chemin encodé et le corps exact, refuse les query strings et désactive les redirections. Les délais sont de 10 secondes par requête et 30 secondes pour l’assistant.
- Courses utilise `GET /shopping`, `POST /shopping/items`, `PATCH|DELETE /shopping/items/{id}`, `POST /shopping/undo`, `POST /shopping/put-away` et `POST /recipes/{id}/shopping-items`.
- Planning lit `GET /planning/weeks/{weekStart}` et écrit avec `PUT|DELETE /planning/slots/{date}/{meal}` et `POST /planning/move`. Sous la route de semaine, `POST /generate` accepte `PREVIEW` et `APPLY`. `GET /shopping-summary` fournit le jeton de stock et de panier à transmettre à `POST /shopping-items`.
- Les lots enrichis portent un `entryId`, une révision et un `locationKind` quand Mastra les fournit. `PATCH /pantry/entries/{entryId}` couvre quantité, emplacement et péremption. Une date omise conserve la péremption, une date `null` l’efface. `POST /brief/cards` sélectionne les types demandés, dont `MEAL_PLANNED` et `SHOPPING_LIST`. Le GET historique reste disponible.
- Le décodeur accepte les champs inconnus et rejette les valeurs d’énumération inconnues. Le retour de feedback Brief attend un accusé de réception HTTP. Les fixtures sont copiées telles quelles et les calculs Kotlin exécutent tous les cas partagés. [Contrat Cuisine v1](https://github.com/Loe159/homelab-ai/blob/feat/food-module/docs/food-contract.md), [schémas Zod](https://github.com/Loe159/homelab-ai/tree/feat/food-module/src/mastra/food/schemas)

## Données hors ligne et sessions

Room expose les recettes versionnées, l’index et les métadonnées de bibliothèque, les disponibilités, le garde-manger et les cartes Brief sous forme de `Flow`. Chaque cache est rattaché à l’origine Mastra et à l’identifiant d’appareil de l’appairage. Le résultat du rafraîchissement distingue `NOT_PAIRED`, `OFFLINE`, `SERVER_ERROR`, `GROCY_UNAVAILABLE` et `AI_DISABLED`. Les données en cache gardent leur date de récupération, et les stocks gardent leur `stockAsOf`. Les cartes Brief expirent à `validUntil`.

La migration 28 → 29 ajoute le cache Courses, le cache Planning par lundi ISO et le cache Brief v2 par ensemble de types. Les trois sont datés et partitionnés par appairage. Le cache Brief v2 a sa propre table. La lecture des Courses et des lots applique les opérations locales au dernier relevé disponible. Les articles ajoutés localement ont une clé provisoire jusqu’à l’arrivée du relevé suivant.

La disponibilité hors ligne utilise le dernier stock connu pour la même version de recette. Elle recalcule les quantités exactes et les manques dans l’unité de la recette. Un stock inconnu reste inconnu et un ingrédient sans produit lié reste neutre. Les étapes affichent les fractions des quantités mises à l’échelle. Une quantité redéfinie pendant la session s’applique à la session entière, puis se répartit entre les étapes.

Une session fige le JSON de la recette dès sa création. Les changements de portions, d’étape, d’ingrédient ou de minuteur sont écrits localement avec une révision croissante pour la session active. Le serveur reçoit une copie complète par PUT quand l’appairage est disponible. Un échec de ce PUT laisse la session locale utilisable et la modification suivante envoie la révision courante. La migration 26 → 27 ajoute les tables Cuisine. La migration 27 → 28 conserve les propositions locales confirmées, la référence Brief et le détail du résultat de fin de recette. Les métadonnées de remplacement, les notes et les ajustements d’étape sont projetés dans la session locale à partir de la recette figée.

## Fin de recette et rejeu

La confirmation enregistre en une transaction la session terminée, l’état `PENDING` et la ligne d’outbox avec un `operationId` unique. Chaque tentative poste les mêmes octets. Android tente l’envoi immédiatement et un `JobService` persistant exige le réseau et applique un délai de reprise exponentiel. Une réponse incertaine ou une réponse perdue laisse l’opération en file pour réconciliation par Mastra. Les états `SUCCEEDED`, `PARTIAL` et `FAILED` terminent le rejeu. Une opération associée à un appairage attend cet appairage avant tout envoi. Les opérations créées sans appairage sont associées au premier appareil utilisé pour l’envoi.

La file `food_cuisine_operations` conserve séparément les ajouts, l’état panier, les éditions, suppressions et annulations Courses, l’ajout des ingrédients manquants d’une recette et l’édition des lots. Elle exige un appairage connu et conserve le corps JSON signé, l’état, le résultat par opération et le nombre de tentatives. Le même job rejoue les réponses incertaines et recharge les relevés à réconcilier. Après une réponse définitive, un échec de lecture relance seulement la lecture. Le rangement et les écritures Planning, y compris l’ajout des manquants d’une semaine, sont réservés à une connexion active. L’appelant conserve le même `operationId` et le même corps pour réessayer.

## Minuteurs et notifications

Les minuteurs indépendants conservent leur état et leur échéance absolue dans Room. Un réveil vérifie l’état et l’échéance avant d’afficher l’alerte. Android reprogramme les minuteurs au démarrage, après une mise à jour de l’app, à l’ouverture et après l’octroi de l’accès aux alarmes exactes.

Avec cet accès, le planificateur utilise `setExactAndAllowWhileIdle`. Sinon il demande une fenêtre d’une minute de part et d’autre de l’échéance et expose `exactAlarmsAvailable` à l’interface. Si le système livre l’alarme avant l’échéance, le réveil la reprogramme à partir de l’échéance. Android peut élargir la fenêtre des alarmes approximatives. [Planification des alarmes Android](https://developer.android.com/develop/background-work/services/alarms)

Le canal « Minuteurs cuisine » utilise le son et la vibration, avec les actions « +1 min » et « Arrêter ». Une notification continue ouvre la session active la plus récente. Une notification standard suffit, car Room conserve l’état et AlarmManager réveille l’app à l’échéance sans service de premier plan. Le listener et le dépôt Notifications excluent les deux canaux Cuisine de Jean-Calcul de l’historique et du retrait automatique.


## Vérification

Le scénario instrumenté `food_cooking` utilise un homelab synthétique sur la boucle locale. Il ouvre une recette depuis le Brief, passe à quatre portions, lance deux minuteurs, recrée l’activité et confirme la consommation. Le lanceur recueille les vues de recette, de minuteurs, de récapitulatif et le résultat de synchronisation. Les tests JVM couvrent les projections de portions et de disponibilité, l’ordre du Brief, le décompte, la navigation et les modifications de session, ainsi que la reprise du résultat partiel.
