# ADR 0003 — Session d’assistant transparente

## Décision

`JeanCalculVoiceInteractionSessionService` crée une `VoiceInteractionSession` dans le processus
`:assistant_session`, distinct de `VoiceInteractionService`. La session affiche une vue `ComposeView`
plein écran. La fenêtre est transparente, non atténuée et tactile afin de conserver l’application
courante visible tout en empêchant que ses interactions reçoivent les gestes de la session.

Un scrim translucide Compose est systématiquement rendu : il est la solution de repli lorsque le flou
système n’est pas disponible. Sur Android 12 et versions ultérieures, la session demande aussi un blur
d’arrière-plan ; son absence ne modifie pas la lisibilité ni le comportement de fermeture.

La fermeture passe par un unique chemin pour le bouton, Retour, le geste prédictif et la fermeture des
dialogues système. La recréation de configuration réapplique la configuration de fenêtre sans recréer
de graphe applicatif ou de ressource audio.

## Conséquences

- Aucun Hilt, accès réseau, Room, microphone ou TTS n’est initialisé dans le processus de session.
- Les états visuels sont limités à invoqué, écoute et erreur jusqu’à l’issue de reconnaissance vocale.
- La compatibilité One UI du blur reste à confirmer sur appareil ; le fallback translucide est le rendu
  de référence lorsqu’il est indisponible.
