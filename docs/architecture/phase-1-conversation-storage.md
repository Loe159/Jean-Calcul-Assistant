# Stockage local des conversations - phase 1

Issue de reference : #26.

## Decision

Les conversations, messages et references de sessions sont stockes dans la base Room
`jean_calcul.db`, schema version 1. Le schema exporte est versionne dans
`android/core-data/schemas/`.

Les messages d'un modele direct restent un historique local. Une session d'agent conserve sa
reference distante dans la table `assistant_sessions`; cette reference n'est jamais incorporee au
texte des messages. Le resume de contexte et la derniere sequence resumee sont reserves dans la
table `conversations` afin qu'une future compression ne reecrive pas l'historique brut.

## Cycle de vie des donnees

- Finalite : reprise locale, affichage progressif, reessai et export d'une conversation.
- Conservation : jusqu'a suppression explicite par l'utilisateur.
- Suppression : suppression en cascade de la conversation, de ses messages et de ses sessions.
- Export : JSON versionne contenant une conversation, ses messages et ses metadonnees de session.
- Fournisseurs autorises : le profil selectionne pour la conversation uniquement; aucun fournisseur
  n'accede directement a Room.
- Sensibilite : donnees personnelles locales. Elles ne doivent pas contenir de cle fournisseur et ne
  sont pas incluses dans les sauvegardes Android (`allowBackup=false`).

## Migrations

La version 1 est le schema initial. Toute evolution ajoute une migration explicite dans
`JeanCalculDatabase.MIGRATIONS`; aucun fallback destructif n'est autorise. L'issue #32 ajoutera les
tables d'audit par migration depuis ce schema.
