# Tester et voir Jean-Calcul avec un agent

Lire `AGENTS.md`, `docs/QUALITY.md` et les skills `android-e2e` et
`android-visual-review`. Aucune clé API vision n'est nécessaire : l'agent qui
implémente ouvre lui-même les PNG avec son outil vision natif.

## Préparer une fois

Linux x86_64 avec KVM accessible, JDK 25, Python 3.10+, Android SDK.
Placer `java`, `adb`, `emulator` et `avdmanager` dans PATH, ou définir
`ANDROID_HOME`. Le wrapper Gradle est inclus. Installer les paquets SDK :

```sh
sdkmanager --channel=3 'platforms;android-37.0' 'build-tools;37.0.0' \
  'platform-tools' 'emulator' 'system-images;android-35;google_apis;x86_64'
```

Lire et accepter les licences Android avec `sdkmanager --licenses`.
Sous NixOS, utiliser `nix develop` (voir `flake.nix`) : le SDK, le JDK,
Python et les binaires Android adaptés à NixOS sont fournis ensemble.
Le SDK Nix inclut les licences acceptées par sa configuration explicite.

## Une commande par tentative

```sh
python3 e2e/run.py --run-id issue54 --attempt 1
python3 e2e/report.py e2e-artifacts/issue54/attempt-1
```

Le lanceur crée **son propre AVD vierge**, sans compte, sans snapshot, API 35,
et ne réutilise jamais un appareil connecté. Port ADB par défaut 5580 (réglable
avec `--port`). Toutes les commandes ADB et Gradle ciblent ce numéro de série.
Le réseau Android est coupé avant les tests. Seuls les téléchargements de
build se font sur le réseau de l'hôte. L'app ne reçoit que du texte synthétique.
Ne jamais installer des données, comptes, secrets ou conversations personnels
dans cet AVD.

Les étapes : compilation, JVM/lint, tests instrumentés Compose, installation des
APK, actions système UI Automator/ADB, captures plein écran et hashes SHA-256.
Les erreurs instrumentées sont détectées même si `am instrument` renvoie zéro.
Les PNG restent dans le stockage privé de cette app debug et sont lus via
`adb exec-out run-as` ; pas de dépendance au nettoyage asynchrone du stockage partagé.
Chaque scénario repart d’une activité neuve après force-stop, puis le lanceur efface uniquement le fichier d’historique privé synthétique
via run-as. La persistance réelle est testée séparément dans SmokeTest.
Un échec arrête la tentative et conserve les preuves partielles avec NOT_RUN.
Boot limité à 240 s, build/tests à 1500 s, scénario à 120 s. Les logs enregistrés
sont expurgés (chemin personnel, jetons usuels, emails) ; les inspecter avant partage.
Pas de dump global des logs Android ni de données d'autres applications.

La compression et les dimensions du PNG sont vérifiées dans le test, puis le
fichier est écrit atomiquement et synchronisé. Les captures et lectures ADB
ont chacune au plus trois essais techniques bornés ; un scénario en échec
n’est jamais relancé silencieusement. Un PNG vide ou tronqué est refusé.

Captures : chat vide, message, reset, réglages, sombre, 360×640 dp, police 200 %,
clavier réellement visible, animations réduites et défaut visuel volontaire.
Ce dernier est une surcouche rouge **dans le test uniquement**, absente de l'APK
applicatif. Sa revue doit être FAIL et le critère de détection PASS.

## Ouvrir et interagir

`index.html` est une galerie locale consultable dans un navigateur ; chaque PNG
est également ouvrable directement par l'agent (ex. `view_image`).
Pour manipuler l'application avec la fenêtre d'émulateur :

```sh
python3 e2e/run.py --run-id interactive --show-window --keep-emulator
adb -s emulator-5580 shell am start -n fr.loevan.jeancalcul.v2/.MainActivity
adb -s emulator-5580 exec-out screencap -p > /tmp/jean-calcul.png
# Après inspection :
adb -s emulator-5580 emu kill
```

L'émulateur conservé ne contient toujours que les données synthétiques. Les
captures, APK et logs restent sous `e2e-artifacts/<run>/attempt-N/`, ignoré par Git.
Le rapport lie commit, modifications locales, APK et empreintes des captures.

## Boucle de revue, au plus trois tentatives

1. Lancer la tentative 1, ouvrir **chaque** image avec la vision native.
2. Dans `visual-review.json`, remplir chaque état : `status` PASS/FAIL/NOT_RUN,
   `observation` concrète, `opened_with` outil utilisé. Conserver `sha256`.
   Une image non ouverte ou une référence manquante reste NOT_RUN.
3. Exécuter `report.py` pour produire `report.md` et `index.html`. Une preuve
   modifiée invalide la revue correspondante. Le script ne fait aucun jugement UX.
4. Corriger les défauts constatés puis relancer avec le même `--run-id` et
   `--attempt 2`, puis 3 si nécessaire. Chaque dossier est immuable pour le lanceur.
   Après trois tentatives, arrêter et signaler les défauts restants ; ne pas
   contourner le budget en renommant le run. Une quatrième validation exige
   un accord utilisateur explicite, consigné dans le rapport, puis
   `--attempt 4 --extra-validation-authorized`. Ce drapeau n'est pas une
   autorisation en soi ; ne jamais l'ajouter automatiquement.
5. Fournir rapport, APK et preuves dans une PR vers
   `feature/jean-calcul-v2-foundation`. Aucune fusion ni approbation automatique.

## Références Eta sélectionnées par l’utilisateur

Le dossier [Eta/docs/Screenshots](https://github.com/Mangi-11/Eta/tree/main/docs/Screenshots)
a été fourni par l’utilisateur pour cette revue. `references.json` épingle le
commit, les fichiers chat_home.jpg / settings.jpg et leurs hashes. Le lanceur
les télécharge dans le dossier `references/` de la tentative et vérifie les
empreintes ; pas de copie du code Eta, ni de redistribution des JPG dans Git.
Un échec réseau n'empêche pas les tests techniques : comparaison NOT_RUN.

L'agent ouvre les références **et** les captures V2, puis remplit
`_reference_comparison` dans `visual-review.json` : statut, outil, observation
comparative et `reference_sha256` (nom → hash du manifeste). Le rapport vérifie
les fichiers et les empreintes. PASS signifie comparaison exécutée et motivée
pour les états communs du prototype, pas parité fonctionnelle avec Eta.
Les écarts et états sans référence exacte doivent être précisés. Les captures
V2 restent des preuves candidates, jamais des références auto-approuvées.

## CI technique

GitHub Actions exécute build, JVM/lint, instrumentation et publie APK/preuves.
Aucun appel vision, aucun secret `OPENAI_API_KEY`, aucun jugement UX en CI.
Vérification des garde-fous du rapport :

```sh
python3 -m unittest discover -s e2e -p 'test_*.py'
```

## Galerie #55

Les scénarios incluent aussi le menu natif, la galerie (bulles entrante/sortante
explicitement illustratives) et le dialogue COUI. Le numéro du rapport suit les
chiffres du run-id (`issue55` → #55). Les rapports historiques conservent leur
liste propre de captures. Les tests vérifient l'annulation de l'effacement,
la conservation du brouillon en navigation et le retour système depuis la galerie.

Une instruction utilisateur explicite peut autoriser une poursuite après la
quatrième tentative. Conserver le même run-id et les preuves précédentes ;
utiliser `--extra-validation-authorized --authorization-note '<instruction>'`
pour enregistrer l'autorisation dans chaque rapport. Aucun dépassement implicite.
Les échecs Smoke sont conservés en FAIL mais n'empêchent plus la collecte des
scénarios visuels indépendants si les APK sont disponibles. Un build ou scénario
inexploitable arrête toujours la collecte avec les preuves partielles.

La partition de données du nouvel AVD synthétique est limitée à 4 Gio. Le profil
Pixel 6 reste identique pour l'écran, l'API et la mémoire ; aucun compte ou
contenu personnel n'y est installé. Les anciens AVD ne sont pas modifiés.

## Fournisseur #56

Les variantes `provider_dark`, `provider_small_screen` et `provider_large_text`
inspectent le formulaire de connexion. Aucun secret n’est saisi pour les captures.
Si le disque est insuffisant, `--avd-root /dev/shm` crée un répertoire neuf par
tentative pour le seul AVD synthétique ; `avd/` dans les preuves pointe vers lui.
Le stockage de l’AVD est jetable ; les APK, captures, logs et rapports restent
dans `e2e-artifacts/`. Aucun AVD existant n’est réutilisé ou déplacé.

Les variantes `chatgpt_dark`, `chatgpt_small_screen` et `chatgpt_large_text`
capturent l’entrée déconnectée Continue with ChatGPT sans ouvrir le navigateur
ni employer de compte réel. Les variantes provider défilent jusqu’au champ URL
après ouverture du formulaire API. Ces états ne valident pas un consentement
OpenAI réel. Le run local #56 reste soumis au même budget de tentatives.
