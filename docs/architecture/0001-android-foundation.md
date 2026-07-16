# ADR 0001 — Fondation Android modulaire

## Décision

La phase 0 initialise uniquement sept modules Android/Kotlin : `app`, `assistant-service`,
`assistant-session`, `core-domain`, `core-data`, `core-ui` et `tool-bridge`.

Les modules suivent cette direction :

```text
app → assistant-service / assistant-session / core-data / core-ui / tool-bridge
assistant-service → core-domain
assistant-session → core-domain + core-ui
core-data → core-domain
tool-bridge → core-domain
```

`core-domain` reste une bibliothèque Kotlin JVM sans dépendance Android. Les bibliothèques Android
n’ajoutent encore ni permission ni service Android. `assistant-service` et `assistant-session` restent
séparés afin que les prochaines issues puissent isoler le `VoiceInteractionService` de la session UI.

## Variantes

Le module `app` porte la dimension de produit `distribution` avec les variantes `core` et `powerUser`.
La variante `core` est le binaire par défaut. `powerUser` reçoit son propre identifiant d’application ;
elle ne contient encore aucune fonctionnalité d’accessibilité. Cette frontière empêche son ajout futur
dans le binaire Core.

## Conséquences

- La compilation de chaque variante est vérifiée par Gradle et GitHub Actions.
- Aucun ancien prototype ni aucune fonctionnalité d’assistant n’est repris.
- Les versions et plugins sont centralisés dans `gradle/libs.versions.toml`.
- Les règles partagées de SDK, Java 17 et qualité sont centralisées dans les scripts Gradle racine.
