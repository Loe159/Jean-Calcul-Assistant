# Issue #56 — preuves locales

**Attention :** la tentative 3 ci-dessous précède l’extension ChatGPT. Elle
ne valide pas la nouvelle connexion ou son écran. Voir
[CHATGPT-AUTH](../../../../docs/CHATGPT-AUTH.md) pour l’état actuel et les limites.
La connexion dynamique officielle est maintenant implémentée ; aucune création
préalable de client partenaire n’est requise pour ce parcours local/open source.
Consentement réel et inférence réelle restent NOT_RUN. Les nouveaux tests
Android passent dans la tentative 4 ; l’ensemble reste FAIL (9/10), voir plus bas.

Validation du commit **`b41453484e7edeb51decac23a230ce5d9068e3b4`**, arbre propre :
[rapport tentative 3](attempt-3/report.md) · [galerie](attempt-3/index.html) ·
[captures](attempt-3/screenshots) · [revue par image](attempt-3/visual-review.json).

- Build Debug, lint, 14 tests JVM et 8 tests Android API 35 : PASS.
- Seize PNG ouverts par l’agent implémenteur avec la vision native : quinze
  états normaux PASS ; défaut rouge volontaire FAIL, donc détection PASS.
- Références Eta chat_home.jpg et settings.jpg ouvertes et comparées : PASS
  pour les états communs, sans parité pixel ni fonctionnelle.
- Revue indépendante : défaut des champs sans contours confirmé sur tentative 2,
  puis correction **RESOLVED / SHIP dans ce périmètre** sur les quatre vues
  settings/provider de la tentative 3. Aucune validation des contrôles hors viewport.

APK exact testé, hors Git :
`e2e-artifacts/issue56/attempt-3/app-debug.apk` (36 Mio environ).
SHA-256 : `a71ad2b1b8dfc2fbd23759549ca7f6159cc71968afb246e2742bca47fda567be`.
Le commit suivant ajoute uniquement les preuves ; il ne modifie pas l’application.

[APK CI téléchargeable](https://github.com/Loe159/Jean-Calcul-Assistant/actions/runs/43410651676/artifacts/14029258003)
construit pour la PR avec le head `b414534`, commit de fusion technique
`5ea2590b6d3f6f37bcc1da92ce362425a8bb3526`. Ce build CI a son propre binaire et
sa signature ; il n’est pas annoncé identique à l’APK local ci-dessus. Le job
Build/tests/lint est PASS ; le job émulateur CI était encore en cours lors de
la rédaction. La CI n’exécute aucune revue visuelle.

## Historique conservé

1. [Tentative 1](attempt-1/report.md) : émulateur NOT_RUN, partition impossible
   avec 6,8 Go libres alors que l’émulateur en demandait 7,4. Aucune capture.
2. [Tentative 2](attempt-2/report.md), `af75f18` : build/lint et dix tests JVM
   passent ; 7/8 Android, retour système après saisie/navigation FAIL. Revue
   visuelle FAIL : champs vides sans contours en clair/sombre/petit écran/200 %.
   Détection du masque rouge et comparaison Eta PASS. Les échecs sont conservés.
3. Tentative 3 : formulaire replié par défaut, champs bordés, retour système
   repasse. Boucle tool calling ajoutée, fragmentée, bornée ; seule l’horloge UTC
   réelle est autorisée. Quatorze JVM et huit Android passent ; revue finale ci-dessus.

L’AVD de la tentative 2, créé dans `/dev/shm` par cette tâche, a été supprimé
après son arrêt pour libérer la mémoire temporaire. Rapports, logs, XML, APK et
captures sont conservés. Aucun AVD préexistant ni aucune donnée personnelle
n’est utilisé. Aucun dépassement du budget de trois tentatives.

## Limites et reste de #56

L’utilisateur confirme ne pas encore disposer de son OAuth. Codex direct :
**non intégré / NOT_RUN** jusqu’à une inscription autorisée et une preuve de
connexion/rafraîchissement/révocation. Aucun échange avec un vrai modèle local
ou distant n’a été validé ; les tests HTTPS et fournisseurs sont explicitement
synthétiques. Le test d’annulation ferme bien une socket HTTPS de test réelle.

Le runtime persiste une conversation courante et ses événements ; Nouveau
efface après confirmation. Pas de sélecteur d’archives. Aucun accès Android
n’est autorisé par le modèle ; seule `current_time` lit l’horloge système.
La future voix pourra appeler le même runtime, mais aucune voix n’est ajoutée.

Samsung, Android 13, haptique physique et fluidité réelle : NOT_RUN.
Le bas du formulaire après défilement, notamment la clé à 200 %, n’est pas
validé par les captures initiales. Elles ne prouvent pas un envoi IA réel.
La PR #61 reste en brouillon, l’issue #56 reste ouverte ; l’utilisateur décide
s’il accepte cette tranche avant le raccordement OAuth. Aucune fusion automatique.

## Extension ChatGPT — autorisation de la quatrième tentative

Le 13 novembre 2026, après présentation du code, de l’APK et des 27 tests JVM
PASS, l’utilisateur répond explicitement **« Oui, autoriser la tentative 4 »**
à la demande de quatrième validation locale de cette extension. Le même run-id
`issue56` est conservé, avec `--attempt 4 --extra-validation-authorized` et
cette instruction enregistrée dans le rapport. Les trois tentatives précédentes
ne sont ni remplacées ni renommées. Le résultat sera publié après exécution.

Les vérifications JVM/build seules sont archivées dans `chatgpt-preflight/` ;
ce dossier n’est pas un run E2E et ne contient aucune validation visuelle.

## Résultat réel de la tentative 4 autorisée

Commit testé `51c1b53`, arbre propre : build/lint et 27 JVM PASS, Android
**9/10 PASS, ensemble FAIL**. Les deux nouveaux tests ChatGPT passent : entrée
de connexion visible et stockage/rotation de l’enveloppe Keystore. L’échec
est `resetCanBeCancelledAndDraftSurvivesNavigation` : Retour après saisie puis
réglages laisse l’activité RESUMED sur Préférences. Aucune validation verte
globale n’est revendiquée.

[Rapport 4](attempt-4/report.md) : dix-neuf captures réellement ouvertes par
l’agent implémenteur, dix-huit vues normales PASS pour leur viewport, masque
rouge volontaire FAIL donc détection PASS. Les références Eta sont ouvertes
et comparées. Aucun compte réel, aucun jeton réel, aucun consentement OpenAI
ni réponse d’abonnement réel dans ces preuves.

Le correctif préparé ensuite renouvelle la connexion de saisie Android après
disparition du champ et synchronise le test sur `isAcceptingText=false` ainsi
que les insets IME masqués. Build/lint/27 JVM et compilation instrumentation
PASS après ce correctif ; sa validation Android/visuelle reste NOT_RUN tant
qu’une tentative supplémentaire n’est pas autorisée. Pas de tentative 5 implicite.

APK CI avec l’authentification ChatGPT, avant ce correctif de Retour :
https://github.com/Loe159/Jean-Calcul-Assistant/actions/runs/43423322349/artifacts/14034454574
Il peut servir au test manuel Réglages → Continue with ChatGPT ; il ne valide
pas la connexion utilisateur ni l’éligibilité du compte sans ce test réel.

## Tentatives 5 et 6 autorisées

Instruction utilisateur : « Oui, autoriser les tentatives 5 et 6 ». La tentative 5
(commit `11bf644`) reste FAIL : 9/10 Android et collecte arrêtée sur message
(attente du bouton de reprise pendant le clavier). Seule empty est produite
et réellement ouverte : PASS pour cette vue ; revue globale, références et
détection NOT_RUN. [Rapport 5](attempt-5/report.md).
Le correctif restartInput ne suffit donc pas à valider Retour. La tentative 6
attend une IME réellement visible avant navigation et libère le viewport avant
l’inspection du bouton de reprise ; les assertions fonctionnelles restent présentes.

## Refonte visuelle Eta — validation de la tentative 8

La refonte demandée ensuite a été testée sur le commit applicatif `14674ce`.
Les tentatives locales 6 et 7 de cette refonte ont été conservées hors Git :
la première s'est arrêtée sur un AVD temporaire déjà présent, la seconde
avant le démarrage Android faute d'espace dans `/dev/shm`. L'utilisateur a
autorisé explicitement les tentatives 7 puis 8. Après suppression des seuls
AVD synthétiques inactifs, la tentative 8 a utilisé un AVD neuf.

[Rapport 8](attempt-8/report.md) · [galerie](attempt-8/index.html) ·
[revue par image](attempt-8/visual-review.json).

- Build/lint, tests JVM et instrumentation Android API 35 : **PASS**.
- Vingt captures du périmètre visuel ouvertes par l'agent implémenteur :
  **PASS**. Le défaut rouge volontaire ouvert est **FAIL**, détection **PASS**.
- Références Eta `chat_home.jpg` et `settings.jpg` ouvertes et comparées aux
  états communs : **PASS**, avec écarts fonctionnels explicités dans le rapport.
- APK Debug exact du commit testé, hors Git :
  `e2e-artifacts/issue56/attempt-8/app-debug.apk`, SHA-256
  `714f4aeb39495555092bd329d875e4de9e087a6479eac30199a595d66c1de13c`.
- La variante Preview a été installée sur le Samsung autorisé ; l'utilisateur
  a confirmé que l'interface lui convient visuellement. Haptique physique,
  fluidité et connexion réelle dans cet AVD : **NOT_RUN**.

Les JPG Eta et les APK ne sont pas redistribués dans Git. Les captures et logs
du dossier `attempt-8` ne contiennent que des données de test synthétiques.
