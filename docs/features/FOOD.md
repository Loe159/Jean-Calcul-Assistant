# Cuisine sur Android

Cuisine s’ouvre depuis le menu principal, dans le groupe des raccourcis au-dessus des conversations. Ce point d’entrée reste disponible quand aucune carte Cuisine n’est présente dans le Brief. Les routes typées utilisent la transition COUI et le geste de retour habituel. La notification d’un minuteur ouvre directement la même session Room que le bouton « Reprendre » du Brief.

## Écrans

L’interface suit le prototype du propriétaire (`docs/design/cuisine.md`, captures dans `docs/design/references/cuisine/`).

- **Cuisine** a quatre onglets : Recettes, Frigo, Courses, Planning. Chaque onglet garde sa position de défilement ; le retour depuis un autre onglet revient à Recettes. La route `FoodHome(tab)` ouvre directement un onglet (cartes du Brief).
- **Recettes** affiche les tuiles stock, courses et repas du soir, une recherche qui lit une durée (« 20 min pâtes ») sans IA, des filtres (durée, difficulté, favoris), le repas prévu ce soir, « Avec ce que j’ai » (classement déterministe de Mastra), « À cuisiner bientôt » (raisons `USES_EXPIRING`) et toute la bibliothèque (favoris, puis cuisinées récemment).
- **Frigo** filtre par emplacement (`locationKind` de Mastra, sinon déduit du nom Grocy), affiche « À consommer bientôt » et une ligne par produit et emplacement datée par son lot le plus proche. L’ajout recherche le catalogue ou crée un produit ; la correction ajoute, retire ou signale un produit abîmé avec un identifiant d’opération réutilisé.
- **Courses** regroupe les lignes Grocy par rayon puis par produit et unité, avec leur origine (recette ou repas prévu). Le mode magasin ne montre que ce qui reste à trouver, puis le panier. Cocher place dans le panier ; « Ranger au stock » passe par une feuille de confirmation et réutilise le même `operationId` à chaque tentative.
- **Planning** montre la semaine (déjeuner, dîner), déplace un repas en deux touchers (`MOVE` ou `SWAP`), génère les créneaux futurs vides en aperçu puis les applique, et ajoute le résumé des courses de la semaine. Une révision périmée recharge la semaine.
- **Recette** : photo pleine largeur, temps et difficulté, bandeau des manques, portions 1 à 24, actions Adapter, Planifier et Options, checklist avec « Ajouter les manquants aux courses » et annulation, aperçu des étapes, bouton fixe Commencer ou Reprendre.
- **Création** accepte le garde-manger, une envie, une surprise ou un texte collé. Le formulaire pilote les paramètres de génération et l’identifiant du job est conservé dans l’état de navigation. Une erreur réseau, l’absence d’appairage ou l’IA désactivée produit un état explicite.
- **Cuisson** : une seule session active par appareil (même recette : reprise ; autre recette : feuille de conflit). Le retour réduit la session, un bandeau « Cuisine en cours » apparaît sur les pages Cuisine. Progression segmentée, minuteurs avec tiroir et alarme à l’écran, feuilles ingrédients, étapes, « Voir comment faire » et assistant.
- **Fin de recette** : « Mettre à jour le stock » (outbox C1) ou « Terminer sans modifier le stock » (même route avec des consommations vides) ; le statut réel du rejeu s’affiche ensuite. Les préférences culinaires restent accessibles depuis Cuisine.

## Now Brief

La session active la plus récente est lue dans Room et passe avant les cartes réelles, y compris pendant une panne réseau. Une extension avec priorité de placement conserve le rendu et les animations du flux Brief sans donner au shell la responsabilité de la logique Cuisine. Les cartes serveur viennent de la route opt-in `POST /brief/cards` (repas prévu, courses, péremptions, recette suggérée), avec repli sur la route historique pour un serveur plus ancien. La sélection garde au plus deux cartes Cuisine, session active comprise, sans doublon de recette : repas imminent, péremption urgente, courses utiles, puis suggestion. La personnalisation permet de masquer ces recommandations et péremptions tout en gardant l’accès à la session active.

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
