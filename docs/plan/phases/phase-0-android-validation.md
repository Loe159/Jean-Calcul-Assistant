# Phase 0 — Validation technique Android et Samsung

Epic GitHub : #7  
Issues : #8 à #16

## Objectif

Valider les risques techniques pouvant rendre le produit impossible ou dégrader fortement l’expérience avant d’investir dans les fournisseurs LLM et le dashboard.

La phase doit démontrer le parcours suivant sur le Samsung cible :

```text
Appui long Power
→ ouverture de la session transparente
→ microphone actif
→ transcription d’une commande de volume
→ validation locale
→ modification du volume
→ réponse visuelle et vocale
```

## Hors périmètre

- véritable conversation LLM ;
- Hermes ;
- notifications ;
- dashboard ;
- accès par accessibilité ;
- wake word permanent.

## Architecture de la phase

Modules minimum :

- `app` ;
- `assistant-service` ;
- `assistant-session` ;
- `core-domain` ;
- `core-ui` ;
- `feature-voice` ;
- `tool-bridge`.

Le `VoiceInteractionService` doit rester séparé de la session et ne contenir aucune initialisation lourde.

## Technologies

- Kotlin ;
- Jetpack Compose ;
- `VoiceInteractionService` ;
- `VoiceInteractionSessionService` ;
- `RoleManager` ;
- `SpeechRecognizer` ;
- Android `TextToSpeech` ;
- `AudioManager` ;
- Coroutines/Flow ;
- tests instrumentés ;
- traces Android et Macrobenchmark exploratoire.

## Ordre d’implémentation

### 1. Matrice des appareils — #8

À documenter :

- modèle Samsung ;
- version Android ;
- version One UI ;
- résolution, RAM et paramètres batterie ;
- comportement du bouton latéral ;
- environnement AOSP de référence ;
- scénarios écran verrouillé, veille et redémarrage.

Livrable : `docs/testing/device-matrix.md`.

### 2. Projet Android et CI — #9

- initialiser Gradle et le catalogue de versions ;
- configurer Compose, Hilt, Coroutines et serialization ;
- créer uniquement les modules nécessaires ;
- ajouter variantes Core/Power User ;
- ajouter lint, Detekt, ktlint et tests ;
- ajouter CI.

Critique : le projet doit être reproductible sur un environnement neuf.

### 3. Rôle assistant Android — #10

- déclarer le service et ses métadonnées ;
- demander `ROLE_ASSISTANT` ;
- afficher l’état du rôle ;
- guider l’utilisateur vers les paramètres ;
- vérifier le cycle de vie après redémarrage.

### 4. Session transparente — #11

- créer `VoiceInteractionSessionService` ;
- afficher une vue Compose transparente ;
- garder l’application courante visible ;
- fournir un fallback sans blur ;
- gérer fermeture, Retour, rotation et erreurs ;
- isoler le processus.

### 5. Invocation Power Samsung — #12

- configurer l’assistant et le bouton latéral ;
- tester depuis accueil, application tierce et verrouillage ;
- tester après veille et redémarrage ;
- mesurer la fiabilité ;
- documenter les limites One UI.

### 6. Prototype STT/TTS — #13

- permission microphone ;
- résultats partiels ;
- détection de fin de parole ;
- timeouts ;
- TTS Android ;
- interruption et annulation ;
- libération des ressources ;
- interfaces abstraites minimales.

### 7. Outils de volume — #14

- modèles `ToolDefinition`, `ActionProposal`, `ToolResult` ;
- schémas stricts ;
- lecture du volume ;
- modification 0–100 ;
- conversion vers les valeurs réelles d’Android ;
- résultat vérifié ;
- audit minimal.

Implementation note: the phase-0 tools expose only `MUSIC`, `ALARM` and `NOTIFICATION`.
They reject unknown JSON properties and percentages outside 0-100 before calling Android,
then return the volume reread from `AudioManager`. The general versioned registry remains
the responsibility of phase-1 issue #27.

### 8. Parcours de bout en bout — #16

Créer un interpréteur déterministe limité aux commandes de volume. Aucun LLM n’est nécessaire dans cette phase.

Scénarios :

- « Mets le volume à 30 % » ;
- « Baisse le volume » ;
- commande ambiguë ;
- commande invalide ;
- annulation avant action.

### 9. Performance et stabilité — #15

Mesurer :

- Power → première frame ;
- première frame → microphone prêt ;
- parole → première transcription ;
- parole → résultat final ;
- demande → volume appliqué ;
- mémoire du service et de la session ;
- 30 invocations successives ;
- reprise après veille.

Livrable : rapport dans `docs/observability/`.

## Difficultés

### Invocation constructeur

Samsung peut imposer ses propres écrans, animations ou restrictions. La phase doit documenter la réalité et non supposer une équivalence parfaite avec Gemini.

### Transparence et blur

Le blur de la fenêtre située derrière n’est pas garanti. Le design doit rester correct avec une surface translucide sans blur.

### Écran verrouillé

Certaines actions et informations devront être bloquées. La phase valide le comportement, sans chercher à contourner Android.

### Reconnaissance vocale

Le service disponible peut être distant, lent ou absent. Une saisie texte de secours est obligatoire dès que possible.

### Cycle de vie

Android peut recréer la session ou tuer les processus. Les ressources audio ne doivent jamais rester bloquées.

## Contraintes

- pas d’écoute permanente ;
- pas de réseau dans le service assistant ;
- pas de LLM pour interpréter la commande de démonstration ;
- aucune action hors registre ;
- aucune valeur de volume non validée ;
- aucune dépendance à l’accessibilité.

## Critères de sortie

- rôle assistant sélectionnable ;
- invocation Power reproductible ;
- UI transparente stable ;
- STT et TTS fonctionnels ;
- outil volume déterministe ;
- parcours complet validé ;
- 30 invocations sans crash ;
- budgets de performance définis ;
- limitations Samsung documentées.

## Passage à la phase 1

La phase 1 peut commencer lorsque les risques critiques #10, #11, #12, #13, #14 et #16 sont validés. L’instrumentation #15 doit être terminée avant la validation finale du MVP.
