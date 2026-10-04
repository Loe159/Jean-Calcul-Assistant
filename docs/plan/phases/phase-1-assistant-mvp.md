# Phase 1 — Assistant vocal minimal et configurable

Epic GitHub : #17  
Issues : #18 à #33

> **Statut : implémentation logicielle terminée.** Les issues enfants sont fermées. L’epic #17 reste ouverte uniquement pour quatre validations physiques strictes consignées dans `docs/observability/phase_1_mvp_validation.md`. Le développement courant se poursuit dans la phase 1B (#39).

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

Hermes n’est pas connecté dans cette phase. Le contrat `AgentBackend` prépare les backends agents, désormais exploités en priorité par la phase 1B.

## Implémentation livrée

Les issues #18 à #33 couvrent le design system, la machine d’états, la voix, les contrats fournisseurs, OpenAI-compatible, Anthropic, OpenRouter, Ollama, les conversations, le registre d’outils, le Policy Engine, Android Keystore, les paramètres, les outils Android MVP, l’audit et la validation finale.

Les décisions détaillées et les tests de régression sont conservés dans `docs/architecture/`, `docs/testing/` et le code de chaque module. Ce document reste un contexte de phase et ne duplique plus l’état détaillé de chaque issue.

## Contraintes toujours applicables

- aucune action ne contourne le Policy Engine ;
- aucun secret en clair ;
- aucun fournisseur imposé ;
- aucune dépendance du domaine vers Android ;
- mode texte toujours disponible ;
- actions locales utilisables hors connexion ;
- pas de Gateway obligatoire pour le socle Android.

## Validation résiduelle de #17

La clôture physique stricte attend encore :

1. VP-02 avec un vrai appel entrant ;
2. les sept scénarios Ollama avec un serveur LAN configuré ;
3. une surface ou des scénarios instrumentés interactifs pour les outils Android hors volume ;
4. une entrée d’audit `Échec` issue d’une défaillance Android réelle et récupérable.

Ces points ne doivent pas être considérés comme validés par des fakes ou des tests JVM. Le rapport canonique est `docs/observability/phase_1_mvp_validation.md`.
