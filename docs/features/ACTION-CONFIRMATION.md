# Confirmation des actions sensibles

Avant qu'une action sensible de l'agent ne s'exécute, Jean-Calcul affiche une fenêtre native COUI
(`ActionConfirmationActivity`). Le modèle ne peut ni la déclencher à sa place, ni y répondre : la décision ne vient
que de la fenêtre (`ActionConfirmationBroker.resolve`).

## Niveaux

| Niveau | Fenêtre | Actions |
| --- | --- | --- |
| Aucun | aucune | bascules réversibles (lampe, luminosité, mise en veille, son, ne pas déranger), lectures, outils d'interface, terminal |
| Bas | « Accepter » / « Refuser » | création ou modification d'agenda, d'alarme, de note ; `set_setting`, `set_device_state`, `set_hotspot` ; `app_state_control` `unfreeze` |
| Haut | « Accepter » + empreinte (`BiometricPrompt`, repli code de l'appareil) | suppression d'agenda, d'alarme, de note ; `app_state_control` `force_stop` / `freeze` ; écritures Majordome à haut risque |

La classification est dans `SensitiveActionPolicy` (pure, testée). Aucun outil dédié à l'envoi de message n'existe ;
les taps dans une application de messagerie ne sont pas filtrés.

## Fonctionnement

- Outils locaux : `AgentLocalTools.executeLocal` appelle `confirmSensitiveAction(nom, arguments)` après les contrôles
  de permission. Un refus renvoie `USER_DECLINED`, `CONFIRMATION_TIMED_OUT` ou `CONFIRMATION_UNAVAILABLE` et l'outil
  n'est pas exécuté.
- Majordome : voir `MAJORDOME.md` (section Approbation).
- Une seule demande à la fois ; délai de 60 s, puis refus. Téléphone verrouillé ou fenêtre impossible à ouvrir
  (par ex. Android Auto) : l'action est refusée, jamais approuvée par défaut.
- La fenêtre est `FLAG_SECURE`, non exportée, hors récents ; fermer la fenêtre vaut refus.

## Vérification

`SensitiveActionPolicyTest`, `ActionConfirmationBrokerTest`, `AgentLocalToolsPermissionTest`, `MajordomeToolTest`.
Rendu : `scripts/preview render ActionConfirmation`.
