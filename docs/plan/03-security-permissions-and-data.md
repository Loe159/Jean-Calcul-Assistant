# Sécurité, permissions et données

## Modèle de confiance

Les éléments suivants sont considérés comme non fiables :

- sortie d’un LLM ;
- sortie d’un agent ;
- notification ;
- mail ;
- page web ;
- pièce jointe ;
- résultat d’un skill externe ;
- contenu provenant d’un appareil distant.

Un contenu non fiable peut être résumé ou classé, mais ne peut pas modifier les instructions système ni exécuter directement une action.

## Chaîne d’autorisation obligatoire

Toute action suit :

1. proposition structurée ;
2. validation du schéma ;
3. vérification de l’outil et de sa version ;
4. vérification des permissions Android ;
5. décision du Policy Engine ;
6. confirmation ou biométrie éventuelle ;
7. exécution déterministe ;
8. vérification du résultat ;
9. écriture d’un événement d’audit.

## Niveaux de risque

### R0 — lecture non personnelle

Exemples : heure locale, batterie, volume courant.

Politique par défaut : autorisation automatique.

### R1 — lecture personnelle limitée

Exemples : tâches internes, prochain événement, nom d’une application installée.

Politique : autorisation selon profil et état de verrouillage.

### R2 — modification locale réversible

Exemples : volume, lecture multimédia, création d’une tâche.

Politique : automatique seulement si la règle est claire et configurée ; sinon confirmation simple.

### R3 — modification personnelle ou communication

Exemples : créer/modifier un événement, répondre à une notification, lire un mail complet.

Politique : confirmation détaillée.

### R4 — action sensible

Exemples : envoyer un mail, supprimer plusieurs éléments, ajouter une permission à un skill, appairer un appareil.

Politique : confirmation et biométrie.

### R5 — action interdite par défaut

Exemples : saisir un mot de passe, lire un OTP, réaliser un paiement, modifier la sécurité Android, installer une application, exécuter un shell non sandboxé.

## Écran verrouillé

Lorsque le téléphone est verrouillé :

- aucune donnée sensible complète ne doit être affichée ;
- aucune communication externe ne doit être envoyée ;
- aucune modification importante ne doit être exécutée ;
- seules des commandes explicitement autorisées et non sensibles peuvent fonctionner ;
- le Policy Engine doit réduire dynamiquement la liste des outils annoncés.

## Secrets

- Stockage via Android Keystore ou secret store du Gateway.
- Jamais de clé API dans Room, DataStore, fichiers de configuration versionnés ou logs.
- Jamais de secret transmis à un skill.
- Les écrans ne réaffichent pas une clé complète.
- La rotation et la suppression doivent être possibles.
- Les exports et rapports de crash sont expurgés.

## Données locales

Catégories :

- configuration ;
- conversations ;
- tâches ;
- événements référencés ;
- notifications normalisées ;
- mails indexés ;
- mémoire ;
- audit ;
- secrets.

Chaque catégorie doit définir :

- finalité ;
- durée de conservation ;
- mode de suppression ;
- possibilité d’export ;
- fournisseurs autorisés ;
- sensibilité.

## Minimisation des données

Avant un appel distant :

- supprimer les identifiants techniques inutiles ;
- masquer les OTP, tokens et secrets ;
- éviter les pièces jointes par défaut ;
- transmettre uniquement les champs nécessaires ;
- indiquer le fournisseur ciblé ;
- respecter les exclusions d’applications et de contacts.

## Notifications et mails

- Le texte est encapsulé comme donnée externe.
- Les instructions présentes dans le contenu sont ignorées.
- Une classification ne déclenche pas directement une action sensible.
- Une notification bancaire, d’authentification ou de sécurité bénéficie d’une politique protectrice par défaut.
- Les réponses et envois exigent une confirmation.

## Permissions des skills

Permissions prévues :

```text
notifications.read
notifications.dismiss
calendar.read
calendar.write
tasks.read
tasks.write
contacts.read
email.read
email.send
device.basic_control
device.accessibility
network.public
network.private
filesystem.workspace
shell.sandboxed
memory.read
memory.write
```

Un skill déclare ses permissions. L’utilisateur accorde un sous-ensemble. Toute élévation exige une nouvelle validation.

## Accessibilité Android

L’accessibilité :

- n’est pas utilisée dans la variante Core ;
- est isolée dans la variante Power User ;
- ne reçoit pas de plan de clic arbitraire généré par un LLM ;
- exécute uniquement des automatisations déterministes déclarées ;
- reste désactivable immédiatement.

Cette séparation est nécessaire pour la sécurité et pour les contraintes de distribution Google Play.

## Appairage Gateway

- QR code ou code temporaire ;
- échange de clé ;
- identité d’appareil ;
- token ou certificat révocable ;
- expiration et rotation ;
- liste des appareils ;
- protection anti-rejeu ;
- TLS obligatoire hors environnement local explicitement signalé.

## Audit

Chaque tentative d’outil enregistre :

- origine ;
- session ;
- outil et version ;
- paramètres expurgés ;
- niveau de risque ;
- décision ;
- confirmation ;
- résultat ;
- durée ;
- erreur expurgée.

Les refus, expirations et annulations sont également journalisés.

## Menaces à tester

- prompt injection dans une notification ou un mail ;
- faux message système ;
- outil inconnu ;
- paramètres supplémentaires non déclarés ;
- rejeu d’une action ;
- agent demandant un secret ;
- skill demandant une élévation ;
- appareil révoqué ;
- action depuis écran verrouillé ;
- contenu sensible présent dans les logs.

## Références officielles

- VoiceInteractionService : https://developer.android.com/reference/android/service/voice/VoiceInteractionService
- RoleManager : https://developer.android.com/reference/android/app/role/RoleManager
- NotificationListenerService : https://developer.android.com/reference/android/service/notification/NotificationListenerService
- Politique AccessibilityService : https://support.google.com/googleplay/android-developer/answer/10964491
