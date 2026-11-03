# Domaine JVM

Aucune dépendance Android/Compose/OkHttp/Room/Hilt. Réutiliser les interfaces existantes.
ModelProvider/AgentBackend et leurs événements restent typés séparément. Les IDs de backend
ne sont ni une politique ni des références de secret exploitables par le domaine.

Préserver les invariants des data classes, schémas stricts et planchers PolicyEngine. Tester
le comportement avec horloge/fakes indépendants de la plateforme : `./gradlew :core-domain:test`.
Ne pas rendre une propriété nullable uniquement pour contourner un appelant incorrect.
