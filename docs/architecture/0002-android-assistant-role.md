# ADR 0002 — Rôle assistant Android

## Décision

L’application expose `JeanCalculVoiceInteractionService` depuis `assistant-service` et demande
`ROLE_ASSISTANT` avec `RoleManager` lorsque l’API est disponible. L’état est relu au démarrage et à
chaque reprise de `MainActivity`; le parcours propose aussi les paramètres système de saisie vocale.

Les métadonnées Android exigent un service de session et un service de reconnaissance. Ils sont donc
déclarés dans une forme minimale : la session est isolée dans le processus `:assistant_session` et le
reconnaisseur renvoie explicitement une erreur sans ouvrir le microphone. `supportsAssist` reste activé :
Android ne retient sinon pas le `VoiceInteractionService` pour activer le rôle Assistant. Leur
implémentation réelle reste strictement réservée aux issues #11 et #13.

## Conséquences

- Le `VoiceInteractionService` ne crée ni UI, ni client réseau, ni base Room.
- Android peut conserver le service sélectionné après ses redémarrages prévus.
- Les événements d’activation, désactivation et les erreurs de parcours sont journalisés sans donnée
  utilisateur.
