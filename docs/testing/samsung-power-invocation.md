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

## Résultats relevés les 2026-07-16 et 2026-07-17

| Scénario | Méthode | Résultat | Preuve expurgée |
| --- | --- | --- | --- |
| DM-01 — accueil déverrouillé | `KEYCODE_ASSIST`, puis Retour et nouvelle invocation | Validé : Jean-Calcul est l'assistant ciblé et la session isolée apparaît deux fois sans crash. | One UI : `DigitalAssistantApp=fr.loevan.jeancalcul.debug`, `LockState=Unlocked`; processus `:assistant_session` présent. |
| DM-02 — Chrome au premier plan | Même séquence depuis `com.android.chrome` | Validé : la session apparaît sans faire tomber Chrome ni le processus assistant. | One UI : `ForegroundApp=com.android.chrome`, `ASSIST_VISIBLE`; aucune `FATAL EXCEPTION` ni `AndroidRuntime`. |
| DM-03 — écran verrouillé | `KEYCODE_ASSIST` le 2026-07-16, puis appui long Power physique le 2026-07-17 | Validé pour l'ouverture : One UI affiche l'assistant sélectionné avec l'état verrouillé. L'appui physique réveille l'écran et autorise aussi la commande locale de volume; aucune donnée personnelle n'est affichée. | One UI : `DigitalAssistantApp=fr.loevan.jeancalcul.debug`, `LockState=Locked`, `ASSIST_VISIBLE`; comportement documenté ci-dessous. |
| Retour / réinvocation | Retour, puis nouvelle invocation dans DM-01 et DM-02 | Validé : la session est recréée ou réutilisée sans crash Compose. | Aucune exception `AndroidRuntime` ou `FATAL EXCEPTION` dans les traces filtrées. |
| DM-04 — après redémarrage | `adb reboot`, déverrouillage puis reconnexion du débogage Wi-Fi ; nouvel appui long physique et commande vocale | Validé : `ROLE_ASSISTANT` est conservé, le service est de nouveau lié et la session répond après le boot. | Rôle `fr.loevan.jeancalcul.debug`, service et session déclarés par `dumpsys voiceinteraction`; aucune `AndroidRuntime`. |
| DM-05 — économie d'énergie | Mode économie d'énergie One UI activé temporairement, puis appui long physique et commande vocale | Validé : première frame, microphone, STT, résultat final et volume sont observés ; le mode a été restauré à désactivé. | Trace `JeanCalculPerf` expurgée : 39 ms vers frame, 757 ms frame-micro, 1 ms vers volume observé. |
| DM-06 — optimisation batterie | App en mode Optimisée, puis temporairement Sans restriction ; appui long physique et commande vocale dans chaque mode | Validé dans les deux modes. Le mode Optimisée a été rétabli à la fin. | Sans restriction confirmée par la liste `deviceidle`; aucune exception `AndroidRuntime`. |

### Validation physique complémentaire du 2026-07-17

- DM-01 : l'appui long physique depuis l'accueil ouvre Jean-Calcul, la transcription progresse, la réponse TTS est audible et le geste Retour ferme la session.
- DM-02 : depuis Chrome, l'appui long physique conserve Chrome visible sous la session. Le geste Retour prédictif ferme la session sans artefact observé.
- DM-03 : depuis l'écran verrouillé, One UI réveille l'écran et accepte la commande vocale locale de volume; la réponse vocale est jouée puis l'écran s'éteint selon son délai normal. Aucune donnée personnelle n'est affichée. Ce comportement est une limitation One UI à conserver : la phase 1 devra appliquer sa politique explicite d'actions disponibles écran verrouillé.
- Une veille continue de 15 minutes en mode Dozing, écran éteint, a conservé le rôle. L'invocation physique et le parcours vocal ont réussi après déverrouillage.

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
