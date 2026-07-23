# ADR 0006 — Fondation modulaire de la phase 1

## Décision

La phase 1 ajoute uniquement les six frontières requises par les issues déjà planifiées :
`core-network`, `core-security`, `feature-conversation`, `feature-voice`, `feature-settings` et
`feature-tasks`. Ce sont des bibliothèques Android sans logique métier initiale. `core-domain` reste une
bibliothèque Kotlin/JVM indépendante d’Android.

La direction des dépendances ajoutées est la suivante :

```text
app → core-network / core-security / feature-*
assistant-session → feature-conversation / feature-voice
core-network → core-domain / core-security
core-security → core-domain
feature-conversation → core-domain / core-data / core-ui
feature-settings → core-domain / core-data / core-network / core-security / core-ui
feature-tasks → core-domain / core-data
feature-voice → core-domain / core-observability
tool-bridge → core-domain / feature-tasks
```

`assistant-service` ne dépend d’aucun nouveau module. Il reste indépendant de Room, DataStore, du client
HTTP, de la biométrie et de Media3.

## Configuration technique

Le catalogue de versions centralise les dépendances de phase 1 afin que les issues enfants n’aient pas à
restructurer Gradle :

- Room et DataStore sont configurés dans `core-data` ;
- OkHttp et MockWebServer sont configurés dans `core-network` ;
- BiometricPrompt est configuré dans `core-security` ;
- Media3 est disponible dans `feature-voice` et `tool-bridge` ;
- le validateur JSON Schema est configuré dans `tool-bridge` ;
- Turbine et MockK sont disponibles pour les tests des nouveaux modules ;
- Macrobenchmark, Profile Installer et le plugin Baseline Profile sont catalogués pour #33, sans créer
  prématurément un module de benchmark.

Hilt et KSP sont activés dans les modules qui accueilleront des implémentations injectables. Kotlin
Serialization est activé uniquement pour les frontières qui porteront des contrats sérialisés. Les modules
Compose sont limités à `feature-conversation` et `feature-settings`.

## Ownership

| Module | Issue qui remplit le module |
| --- | --- |
| `feature-voice` | #20 — pipeline vocal Android |
| `core-network` | #22, #23, #24 et #25 — fournisseurs de modèles |
| `feature-conversation` | #26 — conversations locales et streaming UI |
| `core-security` | #29 — stockage sécurisé des secrets |
| `feature-settings` | #30 — écrans et profils de configuration |
| `feature-tasks` | #31 — création de tâches locales via le registre d’outils |

`core-data` reste possédé initialement par #26 pour la base Room. `tool-bridge` est rempli par #27 puis #31.
Les outils de benchmark catalogués sont consommés par #33.

## Sécurité et données

Cette fondation n’ajoute aucune permission Android, aucune donnée persistée et aucun stockage de secret.
Les manifests sont volontairement minimaux. Les futures implémentations restent soumises à la validation de
schéma, au Policy Engine et au journal d’audit avant toute action Android.
