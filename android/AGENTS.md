# Instructions Android

Lire AGENTS.md racine et la fiche assignée. Les commandes Gradle partent de la racine du dépôt,
avec projets `:app`, `:core-domain`, etc. Ne pas créer un second projet Gradle sous android.
Versions et toolchain sont dans gradle/libs.versions.toml et les builds existants.

core-domain est JVM pur ; adaptateurs Android dans leurs modules ; UI sans politique cachée.
Privilégier les contrats existants et Flow/Coroutines/Hilt. Ne pas ajouter un framework réseau,
navigation ou DI pour corriger un défaut qui se résout dans la stack actuelle.

Libraries : testDebugUnitTest/connectedDebugAndroidTest. Flavors core/powerUser dans app et
baseline-profile selon leurs builds ; ne pas supposer ces flavors présents dans les libraries.
Préserver le service assistant léger et le processus de session séparé. Inspecter les accès
multi-processus avant de supposer un cache ou Singleton Hilt partagé.

Une permission nouvelle doit avoir usage, refus/révocation et preuve ; aucun manifeste permissif
global. Core ne reçoit pas une dépendance d'accessibilité par commodité.
