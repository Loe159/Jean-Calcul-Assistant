# Phase 1 - Configuration locale de l'assistant

L'issue #30 introduit un parcours de configuration local pour les fournisseurs, les profils modeles,
les profils agents, la voix, les permissions, l'apparence et le diagnostic.

## Stockage et frontieres

- `core-domain` porte les valeurs configurables et les regles d'activation, sans dependance Android.
- `core-data` persiste un instantane versionne dans Preferences DataStore.
- `core-security` reste l'unique proprietaire des cles API. DataStore ne conserve qu'un `secretId`.
- `core-network` fournit une sonde HTTP generique et annulable pour le bouton de test de connexion.
- `feature-settings` porte l'etat UI, le CRUD des profils et les sept sections de reglages.
- `app` integre la navigation et applique immediatement les preferences visuelles.

`ModelProfile` et `AgentProfile` restent deux types distincts. Un profil agent ne peut etre active
qu'avec une connexion de type `AGENT_BACKEND`.

## Validation et activation

Une URL doit etre HTTP(S), absolue et sans identifiants, requete ou fragment. Une configuration peut
etre preparee puis laissee inactive, mais elle ne peut devenir active que si sa connexion existe, est
activee et respecte ces regles. Les parametres numeriques des modeles sont verifies avant persistance.

Les connexions HTTP sont autorisees pour les serveurs locaux comme Ollama, avec un avertissement
explicite. Elles ne sont pas considerees equivalentes a une connexion distante chiffree.

## Test de connexion

Le test verifie la joignabilite de l'URL configuree et normalise les erreurs d'authentification, de
quota, de route, de serveur et de reseau. Il ajoute la cle depuis `SecretStore` uniquement pendant la
requete et ne journalise ni en-tete ni contenu sensible. Le test ne remplace pas les adaptateurs
fonctionnels des issues #22 a #25 et n'envoie aucune conversation.

## Suppression

La suppression d'une connexion supprime sa reference Keystore et les profils qui en dependent. Toute
selection active devenue orpheline est retiree dans la meme mise a jour DataStore.
