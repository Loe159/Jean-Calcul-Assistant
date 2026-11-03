# Spécifications exécutables

Ces documents complètent les ADR et le plan. Sauf section `OBSERVÉ`, les exigences identifiées
sont des **CIBLES de remise en état**, pas des fonctionnalités attestées. Le [registre des écarts](../agents/current-state.md)
donne les observations et leur commit. Une mission précise quel sous-ensemble implémenter.

| Document | Contenu | Exigences |
| --- | --- | --- |
| [00 Produit](00-product.md) | Usages, périmètre, vocabulaire, jalons | PROD-01 à PROD-06 |
| [01 Conversation](01-conversation-runtime.md) | Routage, états, concurrence, streaming, retry | CONV-01 à CONV-14 |
| [02 Outils et politique](02-tools-policy-and-approvals.md) | Permissions, reçus, confirmation, idempotence | POL-01 à POL-12 |
| [03 Backends/auth/protocole](03-backends-auth-and-protocol.md) | Auth, transports, sessions, erreurs | AUTH-01 à AUTH-08, NET-01 à NET-08 |
| [04 Données](04-data-and-privacy.md) | Room, configuration, export, rétention | DATA-01 à DATA-10 |
| [05 Voix](05-voice-and-android-lifecycle.md) | Cycle de vie, continuité, audio | VOICE-01 à VOICE-10 |
| [06 UX/UI](06-ux-and-design.md) | Écrans, navigation, états, accessibilité | UI-01 à UI-12 |
| [07 Qualité](07-quality-and-acceptance.md) | Scénarios, risques, release | QA-01 à QA-08 |
| [08 Phases futures](08-future-phases.md) | Notifications à distribution, décisions restantes | DIFFÉRÉ |

## Lire et modifier une spec

Un « doit » dans une exigence identifiée est normatif pour la mission liée. Une proposition de
structure interne reste remplaçable si les mêmes invariants et tests sont satisfaits.
Les exemples utilisent des données synthétiques ; ils ne sont pas des informations réelles de l'utilisateur.

Modifier une exigence suppose de mettre à jour sa mission, ses tests et le compte rendu. Conserver
son identifiant si le sens reste le même ; créer un nouvel identifiant pour un autre comportement.
Ne pas réaffecter un ancien ID. Ajouter une ADR pour un changement de stockage, transport,
authentification ou frontière, plutôt que laisser deux documents se contredire.

Les sources techniques réellement implémentées restent les fichiers Kotlin/Python et schémas
Room versionnés. Les présentes specs n'ajoutent ni un serveur ni un contrat distant inexistant.
