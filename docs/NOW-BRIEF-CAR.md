# Now Brief · Voiture Peugeot 208 thermique

État au **21 novembre 2026**. La recherche s’appuie sur Connected Car B2C v4.17.2, `psa_car_controller` v3.7.10 (`c50a5c87`), Home Assistant `stellantis_vehicles` (`11fa3e55`, branche `develop` du 17 novembre, manifeste `2026.11.1-beta.3`, stable publiée 2026.10.3) et l’inspection statique de MyPeugeot Android 1.56.0 (2695). La requête stations a utilisé un point public. Aucun compte ni véhicule privé n’a été interrogé. [Spécification v4](https://developer.groupe-psa.io/assets/openapi/api-b2c.yaml) · [Version flobz](https://github.com/flobz/psa_car_controller/releases/tag/v3.7.10) · [Code Home Assistant](https://github.com/andreadegiovine/homeassistant-stellantis-vehicles/tree/11fa3e55640e9b84544d6ba7856baf47fd1a3f71) · [Versions Home Assistant](https://github.com/andreadegiovine/homeassistant-stellantis-vehicles/releases)

**Conclusion.** La voie MyPeugeot utilisée par Home Assistant fournit des chemins de lecture pour la jauge, l’autonomie, le compteur, l’entretien restant et la dernière position. La référence v4 ajoute les alertes. La disponibilité effective dépend de la 208, du service connecté et des droits du compte. L’intervalle et le dernier entretien relèvent d’un autre jeu de données. L’accès partenaire avec ses propres identifiants est la voie recommandée pour distribuer la carte. Les prix des stations sont déjà accessibles par une API publique.

## 1. Authentification et transport

### Client MyPeugeot utilisé par les projets communautaires

| Étape | Comportement relevé dans le code |
| --- | --- |
| Autoriser | `GET https://idpcvs.peugeot.com/am/oauth2/authorize` avec `client_id`, `response_type=code` et le `redirect_uri` du profil. Pour la France, `mymap://oauth2redirect/fr`. Le suffixe est le pays en minuscules, par exemple `/gb`, et Home Assistant envoie `locale=fr-FR` pour la France. `id-dcr.peugeot.com` peut intervenir dans la connexion Web après la redirection. |
| Scopes et PKCE | Flobz demande `openid profile`, génère un `state` aléatoire et utilise PKCE S256 (`code_challenge`, `code_challenge_method=S256`, puis `code_verifier`). Home Assistant demande `openid profile email` et son URL et son échange PSA actuels omettent PKCE et `state`. Les deux implémentations indiquent que l’obligation de PKCE ne peut pas être déduite du seul code public. Le manifeste MyPeugeot contient un récepteur AppAuth pour `mymap`, et le DEX contient `oauth2redirect` ainsi que des noms de paramètres PKCE. Cette inspection statique ne vérifie pas les paramètres effectivement transmis pour le profil France. |
| Échanger le code | `POST https://idpcvs.peugeot.com/am/oauth2/access_token`, `Authorization: Basic` avec le couple `client_id:client_secret` encodé en Base64. Paramètres `grant_type=authorization_code`, `code` et le même `redirect_uri`, avec `code_verifier` si le code a été émis avec PKCE. Flobz envoie un formulaire via sa bibliothèque OAuth. Home Assistant place les paramètres en query et annonce `application/x-www-form-urlencoded`. Le code est éphémère et à usage unique. |
| Renouveler | Même endpoint et même authentification Basic, avec `grant_type=refresh_token` et `refresh_token`. La bibliothèque de flobz ajoute `scope=openid profile` au formulaire de renouvellement. Utiliser `expires_in` en secondes pour calculer l’expiration de l’access token. Des retours publics montrent `3599` s, soit environ 1 h. La durée de validité du refresh OAuth pour le profil français n’est pas publiée dans les sources consultées. Home Assistant anticipe de 5 min, sérialise les renouvellements et enregistre le nouveau couple de jetons après rotation. `invalid_grant` persistant déclenche une nouvelle autorisation. |
| Lire les données | `https://api.groupe-psa.com/connectedcar/v4` avec `Authorization: Bearer <access_token>`, `x-introspect-realm: clientsB2CPeugeot` et `client_id` en query. Home Assistant ajoute `locale=fr-FR`. Son `aiohttp.ClientSession` et la configuration Connected Car de flobz utilisent la vérification TLS serveur, sans certificat client configuré pour ces lectures. Les lectures de la 208 française restent à vérifier. |
| OTP SMS | Dans Home Assistant, SMS + PIN servent à l’activation des commandes à distance et à l’obtention d’un jeton MQTT distinct. Les GET de liste, statut et entretien fonctionnent dans le flux de lecture seul. Peugeot peut toutefois demander une vérification lors de la connexion au compte ou l’association préalable du téléphone/véhicule pour activer un service. Les trois jours codés dans Home Assistant concernent le refresh MQTT. |

[Constantes et requêtes Home Assistant](https://github.com/andreadegiovine/homeassistant-stellantis-vehicles/blob/11fa3e55/custom_components/stellantis_vehicles/client/psa/const.py) · [OAuth, renouvellement et lectures Home Assistant](https://github.com/andreadegiovine/homeassistant-stellantis-vehicles/blob/11fa3e55/custom_components/stellantis_vehicles/client/psa/client.py) · [Session HTTP Home Assistant](https://github.com/andreadegiovine/homeassistant-stellantis-vehicles/blob/11fa3e55/custom_components/stellantis_vehicles/client_base.py#L54) · [OAuth et PKCE flobz](https://github.com/flobz/psa_car_controller/blob/c50a5c87/psa_car_controller/psa/oauth.py) · [Bibliothèque OAuth2Client](https://github.com/antechrestos/OAuth2Client/blob/master/oauth2_client/credentials_manager.py) · [Exemple de durée observée](https://github.com/35develr/stellantis-evcc-homeassistant-psa-token-refresh)

**Callback Android.** L’URI est enregistrée pour le client MyPeugeot. MyPeugeot revendique déjà le schéma `mymap` via `RedirectUriReceiverActivity`. La visibilité du package permet de découvrir l’appli, mais Android peut lui livrer le callback ou présenter un choix si Jean-Calcul revendique aussi ce schéma. Un callback HTTPS vérifié ou un schéma propre à Jean-Calcul nécessite un client OAuth dont Stellantis a enregistré l’URI. Pour le client partenaire, privilégier le navigateur système, valider le `state` et convenir du support de PKCE S256 lors de l’enregistrement. Le mode manuel Home Assistant récupère le code côté navigateur. Son mode automatique transmet l’e-mail et le mot de passe au worker qu’il utilise pour ouvrir une session. [Documentation PSA Home Assistant](https://github.com/andreadegiovine/homeassistant-stellantis-vehicles/blob/11fa3e55/docs/PSA.md#oauth2-code) · [RFC 8252](https://datatracker.ietf.org/doc/html/rfc8252)

### Accès officiel pour une application tierce

Stellantis présente *Accessing Party for End-Users* comme disponible **sur demande**. Il faut enregistrer son application, son URI de retour et les droits nécessaires, puis recueillir le consentement du propriétaire via le flux Authorization Code. Le guide utilise les mêmes familles d’URL `idpcvs.{brand.tld}/am/oauth2/authorize` et `/access_token`. Les exemples d’échange et de renouvellement utilisent Basic, un formulaire et `realm`. La documentation de lecture publie pour Peugeot `clientsB2CPeugot/OTPPeugeot` (orthographe du tableau à confirmer avec Stellantis). Les GET sur `https://api-cert.groupe-psa.com/connectedcar/v4` exigent **mTLS** avec certificat client et clé privée de l’application partenaire, `client_id` en query, `Authorization: Bearer` et `x-introspect-realm`. Le quickstart OAuth prime sur la section `securitySchemes` de l’OpenAPI, qui contient une référence à l’ancien flux implicite v2. Les scopes métier `data:telemetry`, `data:position` et `data:alert` gouvernent l’accès aux données. Leur traduction en scopes de consentement doit être confirmée à l’enregistrement. Le guide illustre `vehicle_read,remote_write` et renvoie au contrat de l’application. [Enregistrement et certificat](https://developer.groupe-psa.io/webapi/b2c/quickstart/app-registration/) · [Consentement, jetons et révocation](https://developer.groupe-psa.io/webapi/b2c/quickstart/enroll-users/) · [Accès aux données et realms](https://developer.groupe-psa.io/webapi/b2c/quickstart/access-user-data/) · [Disponibilité et scopes](https://developer.groupe-psa.io/webapi/b2c/quickstart/data-availability-scopes/)

## 2. Identifiants et lecture de l’APK Android

| Élément | Provenance |
| --- | --- |
| Pays et langue | `res/raw/cultures.json` → `FR.languages[0]` vaut `fr_FR`. Flobz et le générateur Home Assistant utilisent la première langue du pays pour former `res/raw-<langue>-r<PAYS>/parameters.json`. Le pays du compte est un paramètre explicite. |
| Identifiant et secret du client | `res/raw-fr-rFR/parameters.json` → `cvsClientId`, `cvsSecret`. Les deux clés sont présentes dans le `base.apk` 1.56.0. Une comparaison locale a confirmé que les deux valeurs concordent avec le profil français de `stellantis_vehicles`. Les deux projets obtiennent les APK depuis le miroir `flobz/psa_apk`. Flobz lit le fichier au démarrage de sa configuration. Home Assistant génère `client/psa/configs.json` à l’avance, y embarque les valeurs par pays et le charge au démarrage. |
| Realm, code marque et schéma | Les tables des projets associent le package à `clientsB2CPeugeot`, à `AP` dans flobz, et à `mymap`. `AP` est le code marque interne utilisé par flobz, par exemple pour construire le `siteCode` `AP_FR_ESP` dans son bootstrap. `motorization` et la marque renvoyée par `/vehicles` ont un autre usage. |
| Bootstrap flobz | `apk_parser.py` lit aussi `string/HOST_BRANDID_PROD` via `resources.arsc` et `assets/MWPMYMA1.pfx`. La chaîne `/GetAccessToken` puis `/api/v1/user` côté middleware MYM sert à récupérer les infos du compte et le `short_label`. Le PFX est utilisé comme certificat client pour cette requête middleware. C’est une chaîne distincte des GET Connected Car et de l’accès mTLS partenaire. |

[Parseur APK flobz](https://github.com/flobz/psa_car_controller/blob/c50a5c87/psa_car_controller/psa/setup/apk_parser.py) · [Bootstrap flobz](https://github.com/flobz/psa_car_controller/blob/c50a5c87/psa_car_controller/psa/setup/app_decoder.py) · [Tables marque/realm flobz](https://github.com/flobz/psa_car_controller/blob/c50a5c87/psa_car_controller/psa/constants.py) · [Générateur Home Assistant](https://github.com/andreadegiovine/homeassistant-stellantis-vehicles/blob/11fa3e55/custom_components/stellantis_vehicles/client/psa/configs_updater/__init__.py)

**Vérification du binaire.** APKMirror a refusé le téléchargement direct (403). Le XAPK APKPure 1.56.0 a été téléchargé dans le dossier temporaire demandé et traité comme une archive non fiable. Le manifeste du `base.apk` confirme le package et la version, et la signature APK v3 vérifiée par `apksigner` correspond à l’empreinte publiée par APKMirror. `raw/cultures`, `raw/parameters`, `string/HOST_BRANDID_PROD` et le PFX sont dans la base. Les deux splits examinés (`config.arm64_v8a.apk`, `config.mdpi.apk`) ne contiennent pas ces fichiers. L’inspection était statique, sans installation, exécution ou lecture du contenu du PFX. [Version APKPure](https://apkpure.net/mypeugeot-app/com.psa.mym.mypeugeot/versions) · [Version et signature publiées par APKMirror](https://www.apkmirror.com/apk/peugeot-automobiles/mypeugeot-app/mypeugeot-app-1-56-0-release/mypeugeot-app-1-56-0-android-apk-download/#safeDownload)

**Faisabilité à l’exécution.** Android expose `ApplicationInfo.sourceDir` pour l’APK de base, `publicSourceDir` pour la partie publique et `splitSourceDirs` pour les splits. Une déclaration `<queries><package android:name="com.psa.mym.mypeugeot" /></queries>` suffit à cibler ce package à partir d’Android 11. Le manifeste actuel dispose aussi de `QUERY_ALL_PACKAGES` pour les fonctions de l’assistant. Sur un APK installé classiquement et dans le même profil Android, la lecture du ZIP via `sourceDir` est plausible sans root. Tester la lecture depuis l’UID Jean-Calcul sur l’appareil. Pour les ressources `raw`, `PackageManager.getResourcesForApplication` avec une configuration de locale explicite est une autre voie qui évite de dépendre du nom de l’entrée ZIP. La visibilité ne donne aucun accès à la session ni au stockage privé de MyPeugeot. Les ressources du client restent accessibles à qui possède le binaire. La lecture à la demande évite leur présence dans le dépôt, sans conférer de droits sur l’API. [Visibilité des packages](https://developer.android.com/training/package-visibility/declaring) · [ApplicationInfo](https://developer.android.com/reference/android/content/pm/ApplicationInfo) · [PackageManager](https://developer.android.com/reference/android/content/pm/PackageManager)

## 3. Contrat de lecture et mapping de la carte

Les chemins ci-dessous sont relatifs à `/connectedcar/v4`. Utiliser l’`id` opaque de la liste comme `{id}`. La liste et les alertes sont des collections HAL, avec pagination et liens potentiellement absents. Les colonnes scopes reprennent la référence partenaire. La présence d’un champ dans le schéma n’établit pas sa disponibilité avec le client mobile.

| GET | Réponse / accès |
| --- | --- |
| `/user/vehicles` et `/user/vehicles/{id}` | Liste `_embedded.vehicles[]` ou véhicule seul. `extension=branding` enrichit le libellé dans la référence et `extension=onboardCapabilities` sert à vérifier les capacités. `data:vehicle:devices:pnc` est marqué facultatif pour la liste et la fiche. Liste utilisée par les deux projets. |
| `/user/vehicles/{id}/status` | Dernier état connu. `data:telemetry` et `data:position` selon les blocs autorisés. Lecture utilisée par les deux projets. |
| `/user/vehicles/{id}/maintenance` | Dernière échéance connue, `data:telemetry`. Home Assistant suit `_links.maintenance.href` lorsque la liste l’annonce, puis lit les jours et les kilomètres restants. Résoudre les liens HAL sur l’origine attendue avant de leur joindre un jeton. |
| `/user/vehicles/{id}/alerts` et `/user/vehicles/{id}/alerts/{aid}` | Collection `_embedded.alerts[]` ou détail. `data:alert` requis et `data:position` facultatif pour les positions d’événement. La v4.17.2 expose `timestamps`, `indexRange`, `pageSize`, `pageToken` pour la liste. Le quickstart illustre aussi `locale` sans le lister dans l’opération OpenAPI actuelle. Confirmer son effet. Le client généré de flobz expose les chemins d’alertes. Le coordinateur PSA de Home Assistant ne les utilise pas pour ses capteurs de statut. |
| `/user/vehicles/{id}/lastPosition` | GeoJSON, `data:position`. Opération marquée obsolète en v4.17.2. Employer d’abord `status.lastPosition`. |
| `/user/vehicles/{id}/trips` | Historique des trajets réalisés, `data:trip`. Ces données n’indiquent pas à quelle heure partir pour un rendez-vous futur. |

[OpenAPI v4.17.2](https://developer.groupe-psa.io/assets/openapi/api-b2c.yaml) · [Exemples de requêtes](https://developer.groupe-psa.io/webapi/b2c/quickstart/request_examples/) · [Code HTTP Home Assistant](https://github.com/andreadegiovine/homeassistant-stellantis-vehicles/blob/11fa3e55/custom_components/stellantis_vehicles/client/psa/client.py#L607) · [Client généré flobz](https://github.com/flobz/psa_car_controller/blob/c50a5c87/psa_car_controller/psa/connected_car_api/api/vehicles_api.py)

| Champ de la carte | Champ JSON et interprétation |
| --- | --- |
| Modèle / libellé | `_embedded.vehicles[i]._embedded.extension.branding.{brand,label}`. Repli sur `vehicles[i].{brand,label}` dépréciés. Garder un seul préfixe « Peugeot » si le libellé l’inclut. Si le libellé manque, proposer un nom choisi par l’utilisateur. Le `short_label` de flobz est une donnée de son bootstrap middleware. La finition et la couleur du libellé de démo ne sont pas garanties. |
| Autonomie | `status.energies[].autonomy` avec `type == "Fuel"`, en km. Tester l’ancien objet `status.energy` seulement si sa forme est confirmée et son `type` vaut `Fuel`. |
| Carburant | Même entrée `Fuel`, `.level` en 0–100 %, précision de 0,1 %. `0` est une mesure valide. `subType: "FossilEnergy"` n’identifie pas l’essence ou le diesel ni l’indice recommandé. |
| Compteur | `status.odometer.mileage`, nombre décimal en km, et `status.odometer.createdAt` pour la mesure. |
| Prochain entretien | `maintenance.mileageBeforeMaintenance` en km et `maintenance.daysBeforeMaintenance` en jours. Un négatif représente une échéance dépassée. Pour une date indicative, ajouter les jours à la date locale du relevé d’entretien (`updatedAt` ou, à défaut, `createdAt`, à vérifier sur les réponses). Ne pas ajouter à la date de chaque téléchargement d’une réponse identique. |
| Intervalle et dernier entretien | Aucun champ correspondant dans `Maintenance`. La date de mise à jour de cette ressource décrit le relevé. Le carnet Maintenance e-book de Peugeot couvre l’historique et le plan d’entretien selon l’éligibilité, sans endpoint public correspondant identifié. Pour la carte, omettre la date du dernier entretien et la jauge d’intervalle quand ces données manquent. |
| Alertes véhicule | `_embedded.alerts[].{type,active,severity,startedAt,updatedAt,endAt}`. Afficher les événements actifs et traduire par exemple `LeftFrontTireUnderInflated` en « Pneu avant gauche sous-gonflé ». Le schéma ne fournit ici aucune mesure de pression en bar. Une requête sans accès et une collecte d’alertes vide correspondent à des états d’interface distincts. |
| Dernière position | `status.lastPosition.geometry.coordinates` au format GeoJSON `[longitude, latitude, altitude facultative]`, en degrés pour les deux premières composantes. Horodatage dans `status.lastPosition.properties.createdAt`. Une position absente, ancienne ou `Estimated` doit être signalée comme telle. Le champ fournit des coordonnées, sans adresse. |
| Fraîcheur | Préférer `energies[Fuel].createdAt` (éventuellement `updatedAt` sur une réponse ancienne), `odometer.createdAt`, `lastPosition.properties.createdAt` et `maintenance.updatedAt` ou `createdAt` pour leurs blocs. Afficher `status.updatedAt` ou `status.createdAt` pour l’instant global du statut, et conserver séparément l’instant de récupération local. Un succès HTTP peut renvoyer un relevé ancien. |

Sur un thermique sans chaîne de traction électrique, attendre `motorization: "Thermic"` et l’énergie `Fuel`. Les blocs de traction `Electric`, `extension.electric.*`, charge et autonomie électriques sont hors usage pour cette carte. `status.battery`, s’il existe, décrit la batterie de service du thermique. Tous les champs métier ci-dessus restent facultatifs du point de vue de l’application. Peugeot peut disposer d’un historique réseau ou de données synchronisées en Bluetooth dans MyPeugeot que ce contrat cloud ne reproduit pas. [Schémas v4](https://developer.groupe-psa.io/assets/openapi/api-b2c.yaml) · [Capteurs Home Assistant](https://github.com/andreadegiovine/homeassistant-stellantis-vehicles/blob/11fa3e55/custom_components/stellantis_vehicles/const.py) · [Carnet d’entretien Peugeot](https://services-store.peugeot.fr/store/maintenance-e-book) · [FAQ Peugeot sur les origines du kilométrage](https://www.peugeot.fr/electrique-et-hybride/rouler-en-electrique/faq-electrique.html)

### Exemple JSON anonymisé

Extraits synthétiques de quatre réponses, rassemblés sous `vehicles`, `status`, `maintenance` et `alerts` pour la lecture. Ces quatre clés de regroupement n’existent pas dans l’API. Liens et pagination sans incidence sur le mapping omis. La position est un point public d’exemple.

```json
{
  "vehicles": {
    "_embedded": {
      "vehicles": [{
        "id": "veh-exemple",
        "motorization": "Thermic",
        "_embedded": {"extension": {"branding": {"brand": "Peugeot", "label": "208"}}}
      }]
    }
  },
  "status": {
    "createdAt": "2026-11-20T17:12:00Z",
    "updatedAt": "2026-11-20T17:12:04Z",
    "energies": [{"type": "Fuel", "subType": "FossilEnergy", "level": 24.5, "autonomy": 168, "createdAt": "2026-11-20T17:12:00Z"}],
    "odometer": {"mileage": 38542.7, "createdAt": "2026-11-20T17:12:00Z"},
    "lastPosition": {
      "type": "Feature",
      "geometry": {"type": "Point", "coordinates": [2.3522, 48.8566]},
      "properties": {"type": "Acquire", "createdAt": "2026-11-20T17:11:57Z"}
    }
  },
  "maintenance": {
    "createdAt": "2026-11-20T17:12:00Z",
    "mileageBeforeMaintenance": 1250,
    "daysBeforeMaintenance": 45
  },
  "alerts": {
    "_embedded": {
      "alerts": [{
        "id": "alerte-exemple",
        "type": "LeftFrontTireUnderInflated",
        "active": true,
        "severity": "Warning",
        "startedAt": "2026-11-20T16:55:00Z",
        "updatedAt": "2026-11-20T17:12:00Z"
      }]
    }
  }
}
```

## 4. Limites et alternatives

- **Quotas.** Le contrat partenaire prévoit des limites quotidiennes et par rafale selon la souscription. Lire `X-RateLimit-Limit-*`, `X-RateLimit-Remaining-*`, puis sur `429` respecter `Retry-After` ou `X-RateLimit-Reset`. Aucun quota chiffré fiable pour le client mobile. Les 6 renouvellements OAuth par 30 min dans flobz et Home Assistant sont des garde-fous locaux. Leurs cadences de lecture du statut sont respectivement 120 s et 60 s par défaut. Pour le Brief, commencer avec un cache de 15 à 30 min pour statut et alertes en consultation, un entretien rafraîchi au plus une fois par jour et la liste actualisée à l’association ou sur demande. Regrouper les lectures par véhicule, espacer les erreurs réseau avec temporisation progressive, respecter le délai en cas de `429` et limiter le rejeu après `401` à un renouvellement sérialisé. [Quotas Stellantis](https://developer.groupe-psa.io/webapi/b2c/overview/errors/) · [Cadence flobz](https://github.com/flobz/psa_car_controller/blob/c50a5c87/psa_car_controller/psacc/application/psa_client.py#L66) · [Cadence Home Assistant](https://github.com/andreadegiovine/homeassistant-stellantis-vehicles/blob/11fa3e55/custom_components/stellantis_vehicles/const.py#L67)
- **Disponibilité.** Confirmer la génération et la télématique de la 208, la souscription Connect ONE/PLUS ou le service applicable à son millésime, le partage des données et leur fraîcheur après un vrai trajet. À l’arrêt, le calculateur peut cesser d’envoyer des données. Un retour 2026 décrit un `40400` avec véhicule visible dans MyPeugeot, résolu après passage à un « compte sécurisé » côté Stellantis. D’autres signalements décrivent un délai dépassé du worker en France et `invalid_grant` suivi d’un souci de connexion à `id-dcr` en Grande-Bretagne. Ce sont des observations individuelles. [Éligibilité des données](https://developer.groupe-psa.io/webapi/b2c/quickstart/data-availability-scopes/) · [Retour 40400](https://github.com/andreadegiovine/homeassistant-stellantis-vehicles/issues/464) · [Connexion via worker en France](https://github.com/andreadegiovine/homeassistant-stellantis-vehicles/issues/689) · [Connexion en Grande-Bretagne](https://github.com/andreadegiovine/homeassistant-stellantis-vehicles/issues/691)
- **CGU et blocage.** Le client mobile est une intégration non publiée pour des tiers. Un changement d’identifiants, de consentement, de droits ou d’hôte peut interrompre la lecture. Les conditions Connect PLUS prévoient des suspensions, notamment pour sécurité ou usage contraire aux conditions. Le droit de consulter ses données ne vaut pas autorisation de redistribuer le client MyPeugeot. Les sources consultées ne chiffrent pas le risque d’un blocage permanent du compte pour des GET et n’établissent pas la conformité d’un tel usage. Valider les conditions du service français applicable et obtenir l’accord du fournisseur avant de distribuer une intégration construite sur ce client. [CGU Connect PLUS France, §§ 6 et 7](https://services-store.peugeot.fr/sites/peugeot/files/2025-09/FR_ConnectPLUS_NCS_2_0_0.pdf)
- **Alternatives officielles.** Demander l’onboarding Accessing Party et la couverture réelle de la 208. Mobilisights fournit des offres de télématique professionnelles avec un schéma distinct et des signaux de carburant, kilométrage, localisation, maintenance et alertes selon le pack et l’éligibilité. Demander les conditions d’accès pour un seul véhicule et les signaux disponibles. Pour un particulier, Peugeot indique le portail de confidentialité Stellantis, section « Droit d’accès », et la possibilité d’autoriser un tiers au titre du Data Act. La page ne documente pas d’API personnelle de rafraîchissement à partir de ce portail. [Accès partenaire](https://developer.groupe-psa.io/webapi/b2c/overview/about/) · [Signaux Mobilisights](https://www.mobilisights.com/content/dam/stellantis/mobilisights/pdf/FMDP_signals_20241209.pdf) · [Peugeot Data Act](https://www.peugeot.fr/tools/peugeot-data-act.html)

## 5. Station-service et prix en France

Le jeu officiel « Prix des carburants en France – Flux instantané – v2 » est en **Licence Ouverte / Open Licence 2.0**. Le flux est mis à jour toutes les 10 minutes. La date du prix reste celle déclarée par la station. Requête publique sans authentification, vérifiée en HTTP 200 le 21 novembre 2026 pour un point public à Paris et un rayon de 5 km. [Jeu de données et licence](https://www.data.gouv.fr/datasets/prix-des-carburants-en-france-flux-instantane-v2-amelioree) · [API du ministère](https://data.economie.gouv.fr/explore/assets/prix-des-carburants-en-france-flux-instantane-v2/api/) · [Explore API v2.1 et ODSQL](https://help.opendatasoft.com/apis/ods-explore-v2/explore_v2.1.html)

```sh
curl --get 'https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/prix-des-carburants-en-france-flux-instantane-v2/records' \
  --data-urlencode "where=within_distance(geom, geom'POINT(2.3522 48.8566)', 5km) AND e10_prix IS NOT NULL AND e10_rupture_type IS NULL" \
  --data-urlencode "order_by=distance(geom, geom'POINT(2.3522 48.8566)') ASC" \
  --data-urlencode "select=id,adresse,ville,geom,e10_prix,e10_maj,e10_rupture_type,horaires,distance(geom, geom'POINT(2.3522 48.8566)') AS distance_m" \
  --data-urlencode 'limit=1'
```

- `where` encode le point **longitude latitude**, le rayon et le carburant. Remplacer `e10` par `gazole`, `sp95`, `sp98`, `e85` ou `gplc` selon le carburant explicitement choisi. Exclure prix nuls et ruptures déclarées de ce carburant. Le filtre ne garantit pas la disponibilité à l’instant de l’arrivée.
- Réponse JSON `total_count` et `results[]`. Utiliser `id`, `adresse`, `ville`, `geom.{lat,lon}`, `*_prix` numérique en €/L et `*_maj` ISO 8601 avec décalage. `distance_m` est une distance à vol d’oiseau, à convertir en km. Les horaires sont un JSON imbriqué sous forme de chaîne et peuvent être incomplets. Ne déduire une heure de fermeture que des horaires présents. Afficher la source ministère de l’Économie et la date de mise à jour du prix.
- Pour conseiller un plein, partir de la dernière position du véhicule si elle est suffisamment fraîche et autorisée pour cet usage. À défaut, la position du téléphone peut servir de centre distinct, si elle est disponible et indiquée comme telle. Rafraîchir les résultats selon la zone, le carburant et un cache de 10 à 30 min. L’appel géographique transmet le centre de recherche au fournisseur des prix. Le client stations n’a besoin ni du VIN ni du jeton Stellantis.

## 6. Implémentation

La carte est intégrée au Brief avec le client communautaire MyPeugeot pour usage personnel.

- `data/car/CarSource.kt` expose la source injectable. `MyPeugeotCarSource.kt`, `CarMapping.kt` et `CarHttp.kt` gèrent les GET, les données facultatives, les liens HAL de même origine, les délais de quota et les caches en mémoire. Statut et alertes restent valides 15 min, entretien et liste 24 h. En cas d’échec temporaire, un relevé en cache peut rester affiché avec la mention « Données partielles ». La date affichée vient du relevé, même après une nouvelle requête.
- `MyPeugeotParameters.kt` lit les paramètres du paquet installé en français, avec repli sur le ZIP de l’APK. `PeugeotLoginSheet.kt` héberge le site Peugeot et intercepte le retour dans la WebView. `PeugeotSession.kt` vérifie le `state`, échange le code et sérialise les renouvellements. La session atomique dans `KeystoreSecretStore.kt` conserve le refresh token, l’expiration, le véhicule et le carburant. Le jeton d’accès reste en mémoire.
- `FuelStations.kt` interroge le flux public dans un rayon de 5 km, avec un cache de 20 min pour le point et le carburant courants. Le point du véhicule doit être mesuré depuis moins de 24 h. À défaut, la carte peut utiliser une dernière position du téléphone âgée de moins de 24 h si la permission de premier plan est déjà accordée. La requête publique reçoit le centre de recherche et le carburant.
- `BriefCarCard.kt`, `NowBriefScreen.kt` et `CarSettingsSection.kt` affichent le chargement, MyPeugeot absent ou illisible, la connexion à établir, la reconnexion, les erreurs et les relevés disponibles ou partiels. Une session fait remonter la clé `car` au-dessus d’Aperçus et conserve la préférence de visibilité et la densité. Sans session, l’action de connexion reste dans Aperçus. `DemoCarSource.kt` permet d’injecter les valeurs de `BriefDemoData` dans un aperçu ou un test.
- Le trajet, les heures de départ, la jauge d’intervalle et la réservation de révision ont été retirés de la carte. La barre reprend les prochains événements de l’agenda fictif aux moments où elle recommandait le trajet.

**Compatibilité PKCE.** `usePeugeotPkce` dans `PeugeotSession.kt` est activé par défaut. Si l’IdP français renvoie HTTP 400 pendant l’échange, vérifier le parcours sur l’appareil. Pour essayer le mode de compatibilité, mettre la constante à `false`, reconstruire l’APK et démarrer une nouvelle connexion. La vérification du `state` s’applique dans les deux modes.

### Essai sur appareil

1. Avec MyPeugeot installé dans le même profil Android, ouvrir Now Brief → Aperçus → Voiture → Connecter MyPeugeot, ou Réglages → Voiture. Vérifier la saisie avec le clavier, la redirection interne et la remontée de la carte après connexion.
2. Dans Réglages → Voiture, sélectionner le véhicule si le compte en comporte plusieurs, puis le carburant (E10 par défaut). Comparer les relevés du Brief à ceux de MyPeugeot après un trajet, notamment la date du statut, l’autonomie, le niveau de carburant, le compteur, l’entretien et les alertes. Vérifier aussi la visibilité de la carte après réouverture de l’app.
3. Vérifier la station, son prix et sa date, les horaires en cours et l’ouverture de la carte via « Y aller ». Contrôler le repli sur le téléphone quand le relevé véhicule est trop ancien, puis le comportement avec la permission de localisation retirée.
4. Réouvrir le Brief après expiration du jeton d’accès et vérifier le renouvellement. Tester « Déconnecter », le retour dans Aperçus et la prochaine connexion. La déconnexion efface la session, les relevés et les cookies et le cache WebView de Jean-Calcul, partagés avec son navigateur intégré.

## Inconnues / à confirmer sur l’appareil

- Millésime, motorisation et carburant exacts, équipement télématique, service souscrit, état d’association et de partage des données. Déterminer quelles données visibles dans MyPeugeot arrivent du cloud, du Bluetooth ou du carnet d’entretien.
- Version et signature de l’APK Play réellement installé, profil Android, présence des ressources FR et succès de lecture via `sourceDir` et `getResourcesForApplication` depuis l’UID Jean-Calcul. L’inspection du miroir ne vérifie pas cette étape d’exécution.
- Avec un accès autorisé, vérifier le callback enregistré, le chemin de connexion français, le support effectif de PKCE et du `state`, les scopes réellement accordés, l’expiration et la rotation des jetons. Pour un client partenaire, confirmer le realm exact, le certificat client et le domaine réel de production auprès de Stellantis.
- Comparer sur la 208 les réponses `vehicles`, `status`, `maintenance` et `alerts` au relevé MyPeugeot après un trajet. Confirmer `branding`, jauge et autonomie `Fuel`, base temporelle des jours restants, alertes actives et position. Identifier séparément la disponibilité de l’intervalle et de l’historique d’entretien.
