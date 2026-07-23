# ADR 0007 - Stockage des secrets avec Android Keystore

## Decision

Les secrets fournisseurs sont accessibles uniquement par le contrat public `SecretStore` de
`core-security`. Son implementation Android chiffre chaque valeur en AES-256-GCM avec une cle generee et
conservee par Android Keystore sous l'alias versionne
`fr.loevan.jeancalcul.secret_store.aes_gcm.v1`.

La cle est non exportable. Le stockage persistant prive ne contient que :

- la version du format ;
- le vecteur d'initialisation aleatoire ;
- le texte chiffre et son tag GCM.

L'identifiant du secret est utilise comme donnee authentifiee additionnelle. Un texte chiffre ne peut donc
pas etre deplace silencieusement vers un autre profil. Les valeurs chiffrees sont conservees dans un fichier
`SharedPreferences` dedie. Room et DataStore ne recoivent jamais une cle fournisseur. L'application conserve
egalement `android:allowBackup="false"`.

## Contrat et cycle de vie en memoire

`SecretStore.put` cree ou remplace atomiquement une valeur. `get` renvoie un `SecretValue` fermable et non une
chaine persistante. Le consommateur utilise `SecretValue.useChars`, construit la requete fournisseur puis
ferme immediatement la valeur. Les buffers temporaires sont remis a zero apres utilisation et `toString`
renvoie toujours `[REDACTED]`.

Les futurs adaptateurs de `core-network` ne doivent jamais lire le fichier chiffre ni Android Keystore
directement. Ils recoivent uniquement `SecretStore`, fourni comme singleton Hilt par `SecurityModule`.

## Redaction

`SecretRedactor` retire les en-tetes d'autorisation, cles API, tokens, mots de passe et valeurs sensibles
explicitement fournies. Il produit aussi un `RedactedErrorReport` qui ne conserve ni le `Throwable` original
ni sa chaine de causes. Toute frontiere Logcat ou rapport de crash qui manipule une erreur fournisseur doit
utiliser cette representation expurgee. L'UI utilise le masque constant `********` et ne reaffiche jamais une
valeur lue.

## Invalidation et restauration

Une cle absente, invalidee ou incompatible avec le texte chiffre retourne
`SecretStoreFailureReason.INVALIDATED`. Cette erreur est recuperable et ne contient ni cause technique ni
secret. Le stockage ne supprime rien automatiquement lors de la lecture.

Procedure de recuperation :

1. informer l'utilisateur que les identifiants doivent etre saisis de nouveau ;
2. appeler `SecretStore.reset` apres confirmation pour supprimer les textes chiffres et l'alias Keystore ;
3. demander chaque cle fournisseur a nouveau ;
4. enregistrer les nouvelles valeurs avec `put` ;
5. tester la connexion dans le parcours de configuration de l'issue #30.

Ce chemin couvre une restauration OEM inattendue, un transfert d'appareil et une invalidation liee au
verrouillage securise.

## Rotation d'une cle fournisseur

1. conserver la nouvelle valeur dans un `CharArray` temporaire ;
2. appeler `SecretStore.put` avec le meme `SecretId` ;
3. effacer le buffer fourni par l'appelant ;
4. verifier la connexion quand le provider correspondant sera implemente ;
5. revoquer l'ancienne cle chez le fournisseur ;
6. ne jamais journaliser les deux valeurs ni les conserver pour un retour arriere.

La rotation de la cle de chiffrement Keystore utilisera un nouvel alias versionne et une migration dediee si
elle devient necessaire. Elle n'est pas anticipee dans l'issue #29.

## Validation

Les tests JVM couvrent la creation, la lecture, le remplacement, la suppression, l'absence de texte clair,
l'invalidation recuperable, la remise a zero logique et la redaction. Les tests instrumentes verifient sur
Android que la cle Keystore n'est pas exportable, que la persistance reste chiffree et qu'une cle supprimee
produit l'erreur de restauration attendue.

Le 23 juillet 2026, les deux tests instrumentes ont passe sur l'AVD Android 16 / API 36
`Medium_Phone_API_36.0`.
