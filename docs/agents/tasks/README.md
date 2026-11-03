# Catalogue des missions

Les fiches découpent la remise en état du MVP, sans créer de nouvelles issues ni prétendre fermer
les issues parentes. Le [manifest](manifest.json) est la source de leurs dépendances et chemins.
Avant de choisir une mission, vérifier les commits réellement intégrés dans la branche.

| Mission | Résultat | Dépendances | Issues parentes |
| --- | --- | --- | --- |
| [JC-01](jc-01-active-profile.md) | Rendre la sélection du profil unique et vérifiable | Aucune | #40, #43 |
| [JC-02](jc-02-turn-lifecycle.md) | Garantir la fin, l’annulation et le réessai des tours | JC-01 | #40, #43 |
| [JC-03](jc-03-conversation-routing.md) | Raccorder le parcours texte aux modèles et agents | JC-01, JC-02 | #40, #43 |
| [JC-04](jc-04-configured-policy.md) | Appliquer les permissions et politiques réellement configurées | JC-01 | #44 |
| [JC-05](jc-05-approval-round-trip.md) | Faire aboutir les confirmations jusqu’au résultat outil | JC-02, JC-04 | #44 |
| [JC-06](jc-06-voice-parity.md) | Unifier les tours texte et voix et libérer les ressources | JC-03, JC-05, JC-09 | #45 |
| [JC-07](jc-07-conversation-ui.md) | Recomposer conversation et navigation pour un usage Android | JC-03, JC-05 | #40, #39 |
| [JC-08](jc-08-settings-audit-ui.md) | Clarifier mise en service, réglages et audit | JC-01, JC-04, JC-09 | #39, #43, #44 |
| [JC-09](jc-09-data-recovery-privacy.md) | Sécuriser persistance, récupération et exports | JC-02 | #40, #44 |
| [JC-10](jc-10-backend-compatibility.md) | Qualifier auth et reprise des backends sans promesse fictive | JC-01, JC-02 | #41, #42, #43 |
| [JC-11](jc-11-integration-qualification.md) | Exécuter la qualification automatisée et assembler les preuves | JC-06, JC-07, JC-08, JC-09, JC-10 | #39, #46 |
| [JC-12](jc-12-samsung-validation.md) | Valider réellement le parcours sur le Samsung de référence | JC-11 | #46, #17 |

## Ordre conseillé

Commencer par JC-01, puis JC-02 et JC-04. JC-03 raccorde le texte ; JC-05 fournit la confirmation.
JC-09 sécurise les données et la coordination nécessaire à JC-06 (voix). JC-07/08 portent les écrans.
JC-10 qualifie les backends ; JC-11 assemble les preuves automatisées ; JC-12 exige un essai réel.

Une dépendance signifie contrat et tests disponibles, pas nécessairement issue parent clôturée.
Un agent peut préparer une lecture indépendante, mais ne code pas contre un contrat imaginé.
Les travaux pouvant avancer indépendamment ne constituent pas une instruction de lancer des subagents.

## Éligibilité et taille

Une fiche est une mission bornée, parfois à livrer en plusieurs petits commits cohérents. Si son diff
devient trop large, découper en sous-tâches dans la même issue en conservant ses exigences ; ne pas
supprimer les critères difficiles. Une preuve physique manquante n’autorise pas une fausse clôture.
Les phases futures restent dans [leur cadrage](../../specs/08-future-phases.md) jusqu’à une issue prête.

Utiliser [le prompt prêt à transmettre](../templates/delegation.md) et [le modèle de résultat](../templates/result.md).
Le statut de réalisation reste dans GitHub ; ne pas ajouter ici une seconde roadmap de cases terminées.
