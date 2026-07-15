# Phase 1 — Assistant vocal minimal et configurable

Epic GitHub : #17  
Issues : #18 à #33

## Objectif

Transformer le PoC en première version utilisable : conversation vocale et texte, fournisseurs interchangeables, outils Android sécurisés, stockage local, configuration et journal d’audit.

## Résultat utilisateur attendu

L’utilisateur peut :

- invoquer l’assistant par Power ;
- parler ou écrire ;
- choisir un profil de modèle ;
- recevoir une réponse en streaming ;
- demander une action Android ;
- comprendre et confirmer l’action ;
- consulter l’historique et l’audit ;
- continuer à utiliser les actions locales sans réseau.

## Architecture de la phase

Composants principaux :

- machine d’états de l’assistant ;
- pipeline STT/TTS remplaçable ;
- design system ;
- `ModelProvider` et `AgentBackend` ;
- profils de fournisseurs ;
- conversation Room ;
- registre d’outils ;
- Policy Engine ;
- SecretStore ;
- audit ;
- paramètres.

Hermes n’est pas encore connecté. L’interface `AgentBackend` est définie afin de préparer la phase 3.

## Technologies

- Kotlin, Compose, Coroutines/Flow ;
- Room et DataStore ;
- Android Keystore ;
- BiometricPrompt ;
- OkHttp/Ktor ;
- JSON Schema ;
- MockWebServer ou serveur simulé ;
- Macrobenchmark et Baseline Profiles.

## Travaux détaillés

### Design system — #18

Créer :

- palette gris, bleu et vert ;
- thèmes clair et sombre ;
- surfaces translucides ;
- `GradientOrb` ;
- `VoiceWave` ;
- `AssistantBubble` ;
- `ActionCard` ;
- `ApprovalSheet` ;
- indicateurs microphone et confidentialité ;
- mode animations/effets réduits.

Contraintes : aucun composant UI ne dépend directement d’un fournisseur LLM.

### Machine d’états — #19

États minimum :

```text
Idle
Invoked
Listening
Transcribing
Thinking
ProposingAction
WaitingApproval
Executing
Speaking
Completed
Cancelled
Error
```

Définir transitions, timeouts, effets, annulation et récupération. La logique doit être testable sans Android UI.

### Pipeline vocal — #20

- finaliser les interfaces STT/TTS ;
- encapsuler les implémentations Android ;
- gérer Bluetooth, focus audio, appels entrants et changements de langue ;
- relier l’amplitude réelle à l’animation ;
- fournir saisie texte de secours ;
- préparer la sélection future de moteurs.

### Contrats modèles et agents — #21

`ModelProvider` doit couvrir :

- envoi de messages ;
- streaming ;
- annulation ;
- tool calling ;
- capacités ;
- erreurs normalisées.

`AgentBackend` doit couvrir :

- sessions ;
- reprise ;
- événements ;
- approbations ;
- tools/skills ;
- tâches longues.

Créer des fakes pour les tests.

### Fournisseurs — #22, #23, #24, #25

#### OpenAI-compatible — #22

- URL personnalisée ;
- modèle ;
- clé ;
- streaming ;
- tools ;
- erreurs 401/429/5xx/timeout ;
- annulation.

#### Anthropic — #23

- format natif ;
- tool use ;
- streaming ;
- capacités ;
- erreurs normalisées.

#### OpenRouter — #24

- configuration spécialisée ;
- modèles multiples ;
- fallback ;
- métadonnées de coût lorsque disponibles.

#### Ollama — #25

- détection serveur ;
- liste des modèles ;
- réseau local ;
- streaming ;
- capacités variables ;
- avertissement HTTP non sécurisé.

### Conversations — #26

Modèles :

- `Conversation` ;
- `Message` ;
- `AssistantSession` ;
- `ProviderUsage` ;
- référence de session agent.

Fonctions :

- persistance ;
- streaming UI ;
- annulation ;
- réessai ;
- nouvelle conversation ;
- suppression ;
- export ;
- reprise après redémarrage.

### Registre d’outils — #27

Chaque outil définit :

- nom et version ;
- description destinée au modèle ;
- schéma d’entrée et de sortie ;
- niveau de risque ;
- permissions Android ;
- contraintes écran verrouillé ;
- disponibilité ;
- exécuteur ;
- politique par défaut.

Le registre refuse les propriétés inconnues et les outils non enregistrés.

### Policy Engine — #28

Entrées :

- outil et version ;
- paramètres ;
- profil ;
- permission ;
- état verrouillé ;
- visibilité de l’application ;
- préférence utilisateur ;
- origine de la demande.

Décisions :

```text
allow
confirm
biometric
open_system_panel
deny
```

Chaque décision possède une justification déterministe.

### Secrets — #29

- `SecretStore` basé sur Keystore ;
- chiffrement des valeurs persistées ;
- ajout, remplacement, suppression et invalidation ;
- redaction des logs ;
- gestion d’une restauration ou d’un changement de verrouillage.

### Configuration — #30

Écrans :

- fournisseurs ;
- modèles ;
- agents ;
- voix ;
- permissions ;
- apparence ;
- confidentialité ;
- diagnostic.

Fonctions : création, duplication, test de connexion, activation, suppression et validation.

### Outils Android complémentaires — #31

À ajouter au registre :

- `media.play_pause` ;
- `device.open_settings` ;
- `apps.launch` ;
- `device.toggle_flashlight` ;
- `device.get_battery` ;
- `device.get_local_time` ;
- `tasks.create_local`.

Les réglages protégés ouvrent un panneau système au lieu de contourner Android.

### Audit — #32

Enregistrer :

- origine ;
- outil/version ;
- paramètres expurgés ;
- risque ;
- décision ;
- confirmation ;
- résultat ;
- latence ;
- erreur.

Ajouter pagination, filtres, rétention et export expurgé.

### Validation finale — #33

Scénarios critiques :

- voix → modèle → outil → confirmation → résultat → TTS ;
- texte hors connexion → action locale ;
- changement de fournisseur ;
- clé invalide ;
- permission révoquée ;
- timeout ;
- rotation ;
- redémarrage ;
- 100 invocations sans crash.

## Difficultés

### Formats fournisseurs

Les structures de messages, outils, streaming et erreurs diffèrent. Toutes les implémentations doivent converger vers les contrats internes sans perdre les informations utiles.

### Latence

Le temps total combine invocation, STT, réseau, modèle, outil et TTS. L’UI doit fournir un retour immédiat et permettre l’interruption.

### Hallucination d’outils

Un modèle peut appeler un outil inexistant ou produire des paramètres invalides. Le registre et les schémas sont l’unique autorité.

### Cycle de conversation

Une annulation ou un réessai ne doit pas dupliquer les messages ni laisser un flux réseau actif.

### Secrets et logs

Les SDK et erreurs HTTP peuvent inclure des informations sensibles. La redaction est requise avant toute écriture.

## Contraintes

- aucune action ne contourne le Policy Engine ;
- aucun secret en clair ;
- aucun fournisseur imposé ;
- aucune dépendance du domaine vers Android ;
- mode texte toujours disponible ;
- actions locales utilisables hors connexion ;
- pas de Gateway obligatoire dans cette phase.

## Critères de sortie

- assistant stable en voix et texte ;
- profils configurables ;
- OpenAI-compatible, Anthropic et Ollama fonctionnels ;
- OpenRouter au minimum prêt comme P1 ;
- conversation persistée ;
- registre et Policy Engine obligatoires ;
- secrets protégés ;
- audit consultable ;
- actions locales hors connexion ;
- budgets de performance respectés ou documentés ;
- validation #33 terminée.
