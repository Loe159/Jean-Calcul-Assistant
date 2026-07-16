# Validation Samsung — invocation par bouton latéral

Issue associée : #12
Appareil : Samsung Galaxy S26 5G Dual SIM (`SM-S942B/DS`), Android 16,
One UI 8.5. Les caractéristiques complètes sont dans
[`device-matrix.md`](device-matrix.md).

## Configuration One UI

1. Installer la variante `coreDebug` de Jean-Calcul Assistant.
2. Dans l'application, demander le rôle Assistant puis vérifier que
   **Jean-Calcul Assistant** est l'assistant numérique par défaut. En cas de
   refus du dialogue système, ouvrir les paramètres de saisie vocale depuis
   l'onboarding et effectuer le même choix.
3. Dans **Paramètres > Fonctions avancées > Bouton latéral**, choisir
   **Appui long > Assistant numérique**. Les intitulés peuvent légèrement
   varier selon la version de One UI ; le réglage doit invoquer l'assistant
   numérique système, pas le menu d'alimentation ni Bixby.
4. Laisser le **double appui** configuré indépendamment : il n'intervient pas
   dans cette validation.

Le rôle réellement détenu est contrôlable sans donnée personnelle :

```bash
adb shell cmd role get-role-holders android.app.role.ASSISTANT
adb shell dumpsys voiceinteraction
```

Sur l'appareil cible, le premier renvoie
`fr.loevan.jeancalcul.debug` et le second associe
`JeanCalculVoiceInteractionService` à
`JeanCalculVoiceInteractionSessionService`.

## Résultats relevés le 2026-07-16

| Scénario | Méthode | Résultat | Preuve expurgée |
| --- | --- | --- | --- |
| DM-01 — accueil déverrouillé | `KEYCODE_ASSIST`, puis Retour et nouvelle invocation | Validé : Jean-Calcul est l'assistant ciblé et la session isolée apparaît deux fois sans crash. | One UI : `DigitalAssistantApp=fr.loevan.jeancalcul.debug`, `LockState=Unlocked`; processus `:assistant_session` présent. |
| DM-02 — Chrome au premier plan | Même séquence depuis `com.android.chrome` | Validé : la session apparaît sans faire tomber Chrome ni le processus assistant. | One UI : `ForegroundApp=com.android.chrome`, `ASSIST_VISIBLE`; aucune `FATAL EXCEPTION` ni `AndroidRuntime`. |
| DM-03 — écran verrouillé | Écran verrouillé, puis `KEYCODE_ASSIST` | Validé pour l'ouverture : One UI affiche l'assistant sélectionné avec l'état verrouillé. La session actuelle ne présente aucune donnée personnelle ni action. | One UI : `DigitalAssistantApp=fr.loevan.jeancalcul.debug`, `LockState=Locked`, `ASSIST_VISIBLE`. |
| Retour / réinvocation | Retour, puis nouvelle invocation dans DM-01 et DM-02 | Validé : la session est recréée ou réutilisée sans crash Compose. | Aucune exception `AndroidRuntime` ou `FATAL EXCEPTION` dans les traces filtrées. |
| DM-04 — après redémarrage | Redémarrage ADB demandé alors que le rôle était détenu | À compléter : le Galaxy coupe la session de débogage Wi-Fi au redémarrage ; il faut le déverrouiller puis reconnecter ADB pour relire le rôle et refaire DM-01. | Avant redémarrage : `fr.loevan.jeancalcul.debug` détenait `ROLE_ASSISTANT`. |
| DM-05 / DM-06 — économie d'énergie et optimisation | Non exécuté | À compléter après retour de la connexion ADB. Aucun réglage batterie n'a été désactivé pour les essais ci-dessus. | S/O |

## Limites et protocole de clôture

- `adb shell input keyevent KEYCODE_ASSIST` traverse le même rôle et le même
  service de session que l'invocation One UI, mais il ne peut pas prouver que
  le bouton latéral physique est bien paramétré. Après la configuration
  ci-dessus, effectuer un appui long physique depuis l'accueil, Chrome et
  l'écran verrouillé, puis consigner le même résultat dans ce tableau.
- L'accès vocal et les actions sensibles ne sont volontairement pas testés ici
  : la reconnaissance vocale relève de #13 et les outils soumis à politique de
  #14. Sur l'écran verrouillé, l'issue #12 vérifie uniquement l'invocation et
  l'absence de divulgation.
- Le redémarrage rompt le débogage Wi-Fi sur ce Samsung. C'est une limitation
  de la procédure de collecte, non une perte observée du rôle. Reconnecter le
  téléphone (ou utiliser l'USB), le déverrouiller, puis exécuter les deux
  commandes de contrôle ci-dessus et DM-01.
- Les réglages du bouton latéral sont propres à One UI et peuvent changer de
  libellé ou d'emplacement après une mise à jour système ; l'application ne
  peut ni les modifier ni contourner l'écran verrouillé.
