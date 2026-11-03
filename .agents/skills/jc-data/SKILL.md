---
name: jc-data
description: "Modifier les données locales Jean Calcul : Room, séquences de messages, récupération, DataStore, migrations, redaction et exports. Ne traite pas le design visuel ni un futur Gateway."
---

# jc-data

## Entrée

Lire JC-09 ou la mission données, [la spec données](../../../docs/specs/04-data-and-privacy.md),
les instructions core-data et les schémas versionnés. La base observée est v2 ; vérifier le
code actuel avant de choisir le prochain numéro. Ne pas modifier les anciens JSON de schéma.

## Points fragiles

Les séquences sont locales à une conversation. Leur allocation et l'écriture doivent conserver
les invariants avec producteurs texte/voix et processus app/assistant_session. Un mutex de
ViewModel ou un Singleton Hilt ne constitue pas une transaction ni un singleton interprocessus.
Les messages STREAMING sans propriétaire doivent être récupérés en INTERRUPTED sans supprimer
leur texte. Supprimer un parent n'autorise pas un callback tardif à le recréer.

## Travail

Lire les DAO et repositories réels. Définir le plus petit changement conservant les identités,
références et historiques. S'il change le schéma : version, migration explicite, export nouveau
schéma et tests depuis les versions supportées. S'il change DataStore : tester ancien JSON,
valeurs absentes, champs inconnus et conservation voix/apparence.

Tester snapshot d'export et cascade avec activité concurrente. Utiliser de faux secrets dans
texte/erreur/métadonnées pour vérifier la redaction avant partage. Ne pas appeler JSON partagé
une sauvegarde chiffrée. SecretStore contient les secrets, les profils seulement leurs IDs.

Inspecter comment la configuration est partagée entre processus et documenter la stratégie
réellement testée. Ne pas créer de provider IPC exporté sans protection pour contourner un cache.

## Sortie

Tests JVM pour transformations/redaction ; instrumentation Room/DataStore pour transactions et
migrations réelles. Sans appareil, assembler le code et consigner l'instrumentation non exécutée.
Donner à JC-06 la sémantique de partage/révocation et remplir [le résultat](../../../docs/agents/templates/result.md).
