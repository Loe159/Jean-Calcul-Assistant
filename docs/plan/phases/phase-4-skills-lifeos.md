# Phase 4 — Skills, mémoire et compatibilité LifeOS

## Objectif

Créer un système d’extensions contrôlé et réutiliser les concepts utiles de LifeOS sans embarquer l’intégralité de son environnement dans l’application Android.

## Éléments LifeOS à réutiliser

- TELOS : identité, valeurs, objectifs, stratégies et état souhaité ;
- skills composables ;
- mémoire textuelle et traçable ;
- hooks ;
- dashboard Pulse comme inspiration ;
- diagnostic Doctor ;
- boucle état actuel → état idéal.

LifeOS reste un backend ou une source de conventions. Il ne doit pas devenir une dépendance obligatoire du client Android.

## Résultat utilisateur attendu

- installer ou désactiver un skill ;
- voir ses permissions ;
- importer un skill LifeOS compatible ;
- définir ses objectifs et préférences ;
- contrôler ce que chaque agent peut mémoriser ;
- comprendre pourquoi un workflow a été déclenché ;
- diagnostiquer les intégrations.

## Architecture

Composants :

- format de skill versionné ;
- registre de skills ;
- moteur de permissions ;
- sandbox Gateway ;
- importeur LifeOS ;
- TELOS local ;
- mémoire structurée ;
- moteur de hooks ;
- Doctor.

## Format de skill

```text
skill-name/
├── skill.yaml
├── SKILL.md
├── tools/
├── workflows/
├── schemas/
├── tests/
└── assets/
```

`skill.yaml` contient :

- identifiant et version ;
- auteur et licence ;
- description ;
- moteurs compatibles ;
- permissions ;
- outils ;
- entrées/sorties ;
- secrets nécessaires ;
- accès réseau ;
- politique d’approbation ;
- hash ou signature.

## Travaux

### Registre de skills

- installation locale ou depuis Git ;
- désinstallation ;
- activation/désactivation ;
- mise à jour ;
- version pinning ;
- historique ;
- vérification de signature ;
- affichage des permissions ;
- restauration d’une version.

### Permissions

Un skill déclare les permissions listées dans `03-security-permissions-and-data.md`. L’utilisateur peut accorder un sous-ensemble et ajouter des restrictions horaires, de sources ou d’appareils.

Toute élévation exige une nouvelle confirmation.

### Sandbox

Pour le code exécuté sur Gateway :

- conteneur ou environnement isolé ;
- filesystem temporaire ;
- CPU, mémoire et timeout ;
- réseau coupé par défaut ;
- domaines autorisés explicitement ;
- aucun secret hérité ;
- logs et nettoyage.

### Import LifeOS

L’importeur doit :

- lire `SKILL.md` ;
- détecter scripts et dépendances CLI ;
- extraire les outils ;
- traduire les permissions ;
- signaler les incompatibilités ;
- produire un rapport ;
- ne rien installer sans validation.

Classifications : compatible directement, Gateway uniquement, adaptation requise, incompatible Android, dangereux.

### TELOS

Modèle minimum :

- identité ;
- valeurs ;
- objectifs ;
- priorités ;
- projets ;
- contraintes ;
- habitudes ;
- stratégies ;
- état actuel ;
- état souhaité.

Fonctions : onboarding, édition, versionnement, export Markdown, champs privés, contrôle de visibilité par agent.

### Mémoire

Types :

- profil ;
- préférence ;
- fait durable ;
- épisode ;
- décision ;
- projet ;
- mémoire temporaire ;
- donnée sensible.

Chaque élément possède source, date, confiance, portée, rétention, agents autorisés, preuve et statut confirmé/inféré.

Une inférence LLM ne devient pas automatiquement un fait durable.

### Hooks

Événements possibles : notification reçue, début de journée, événement proche, tâche échue, mail important, téléphone en charge, réseau, localisation, fin de session, objectif modifié.

Un hook peut déclencher une règle, une proposition, un résumé ou un workflow. Il ne déclenche pas une action critique non confirmée.

### Doctor

Vérifier :

- rôle assistant ;
- microphone ;
- notifications ;
- calendrier ;
- batterie ;
- Gateway/TLS ;
- Hermes ;
- fournisseurs ;
- clés ;
- workers/jobs ;
- skills ;
- permissions incohérentes ;
- versions incompatibles.

États : OK, dégradé, action utilisateur requise, erreur, désactivé.

### Évaluations

- jeux de données ;
- sorties attendues ;
- permissions ;
- injection ;
- coût ;
- durée ;
- régressions ;
- fiabilité.

## Difficultés

- skills conçus pour un autre environnement ;
- permissions implicites ;
- scripts dangereux ;
- dépendances CLI absentes ;
- mémoire incorrecte ou trop intrusive ;
- contexte personnel trop volumineux ;
- objectifs vagues ;
- migrations de format.

## Contraintes

- aucune installation silencieuse ;
- permissions visibles ;
- code en sandbox ;
- mémoire exportable et supprimable ;
- informations inférées signalées ;
- LifeOS optionnel ;
- Doctor ne modifie rien sans confirmation.

## Critères de sortie

- format versionné ;
- registre utilisable ;
- permissions appliquées ;
- sandbox testée ;
- import LifeOS avec rapport ;
- TELOS éditable ;
- mémoire traçable ;
- hooks contrôlés ;
- Doctor opérationnel.
