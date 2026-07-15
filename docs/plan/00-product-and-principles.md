# Vision produit et principes

## Vision

Jean-Calcul Assistant est un assistant personnel Android open source servant d’interface entre l’utilisateur, le système Android, ses données personnelles et plusieurs moteurs d’intelligence artificielle.

Le produit doit progressivement permettre de :

- être invoqué comme assistant Android depuis le bouton Power ;
- fonctionner à la voix et au texte ;
- utiliser un modèle ou un agent choisi par l’utilisateur ;
- exposer des capacités Android limitées et contrôlées ;
- comprendre les notifications et proposer des actions ;
- gérer tâches, calendrier, mails et résumés ;
- afficher un dashboard personnel ;
- intégrer Hermes et certains concepts ou skills de LifeOS ;
- conserver une visibilité complète sur les données, permissions et actions.

## Ce que le produit n’est pas

- Ce n’est pas un agent disposant d’un accès général au téléphone.
- Ce n’est pas un clone visuel de Gemini sans architecture de sécurité.
- Ce n’est pas un unique backend imposé.
- Ce n’est pas une automatisation fondée uniquement sur des clics d’accessibilité.
- Ce n’est pas un stockage opaque de toute la vie de l’utilisateur.

## Principes structurants

### Local-first

Les règles, tâches, préférences, audits et données sensibles sont conservés localement par défaut. Les informations envoyées à un fournisseur distant sont minimisées et leur destination doit être visible.

### Modèles interchangeables

Le choix du LLM ne doit pas imposer l’architecture. Les profils de modèles doivent pouvoir cibler OpenAI, Anthropic, OpenRouter, Ollama ou un endpoint compatible.

### Agents interchangeables

Hermes, Codex, Claude Code et d’autres backends possèdent leurs propres sessions, outils et mémoires. Ils sont intégrés via `AgentBackend`, séparément des appels directs à un modèle.

### Code déterministe avant autonomie

Le modèle interprète et propose. Le code valide, autorise et exécute. Une action Android est toujours implémentée par un exécuteur déterministe connu.

### Moindre privilège

Chaque outil et chaque agent reçoit uniquement les capacités nécessaires. Les permissions sont visibles, révocables et limitées par contexte.

### Confirmation proportionnelle au risque

Une lecture non sensible peut être automatique. Une communication externe, une suppression ou un changement de sécurité exige une confirmation renforcée ou est interdite.

### Explicabilité

Une proposition doit indiquer sa source, l’action prévue, les données utilisées et la raison de la confirmation. Le journal d’audit doit permettre de comprendre ce qui s’est produit.

### Fonctionnement dégradé

Sans réseau ou sans Gateway, l’application conserve les fonctions locales : assistant texte, commandes Android déterministes, tâches, calendrier local, règles et historique.

### Extensibilité contrôlée

Les futurs skills utilisent un format versionné, déclarent leurs permissions et passent par une sandbox lorsqu’ils exécutent du code.

## Expérience utilisateur cible

### Invocation

L’appui long sur Power affiche une surface transparente au-dessus de l’application courante. Une animation bleu-vert réagit à la voix et représente clairement l’état de l’assistant.

### États visibles

L’utilisateur doit toujours distinguer :

- écoute ;
- transcription ;
- réflexion ;
- proposition d’action ;
- attente de confirmation ;
- exécution ;
- parole ;
- erreur ou mode hors connexion.

### Dashboard

Le dashboard ne doit pas être un simple agrégateur. Il doit présenter les éléments demandant une décision : tâche à créer, événement à ajouter, mail à traiter, notification à résumer, conflit à résoudre.

## Périmètre MVP

Le premier MVP inclut :

- rôle d’assistant Android ;
- invocation Power validée sur Samsung ;
- interface vocale transparente ;
- entrée texte ;
- fournisseurs OpenAI-compatible, Anthropic, OpenRouter et Ollama ;
- registre d’outils Android ;
- Policy Engine et confirmations ;
- volume, multimédia, lampe, lancement d’applications et paramètres ;
- stockage local des conversations ;
- clés protégées par Android Keystore ;
- journal d’audit ;
- diagnostic minimal.

Les notifications, Hermes, LifeOS, mails et dashboard complet sont développés dans les phases suivantes.
