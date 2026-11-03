# Environnement et vérifications

## Préparer sans changer arbitrairement la stack

Sources : [catalogue de versions](../../gradle/libs.versions.toml),
[wrapper](../../gradle/wrapper/gradle-wrapper.properties), [CI Android](../../.github/workflows/android.yml).
Actuellement : JDK/toolchain 17, Gradle 8.11.1, AGP 8.7.3, Kotlin 2.0.21, compileSdk 35,
build-tools 35.0.0 dans la CI, Python 3.11+ pour le compagnon. Ne pas mettre à jour ces versions
pour résoudre un simple manque d'installation locale.

Depuis la racine du dépôt :

```bash
git status --short --branch
java -version
python3 --version
./gradlew --version
adb devices -l
```

Le JDK de lancement et la toolchain doivent être compatibles avec la configuration. SDK dans
ANDROID_HOME ou local.properties non versionné. Installation SDK, téléchargement de dépendances,
émulateur et accès ADB suivent les permissions de l'environnement. Ne pas fabriquer un résultat
si l'outil est absent. Aucune clé API ou compte ChatGPT n'est nécessaire aux tests unitaires fake.

## Documentation seule

```bash
python3 scripts/check_agent_docs.py
python3 -m unittest discover -s tests/docs -v
git diff --check
```

Ce contrôle vérifie liens locaux, chemins sources, identifiants d'exigence, dépendances et structure
des skills. Il ne compile pas Kotlin et ne valide pas les services externes. Les anciennes pages
hors périmètre ne sont pas réécrites automatiquement pour faire disparaître leurs liens historiques.

## Tests ciblés selon la mission

| Domaine | Commande depuis la racine | Ce que cela prouve |
| --- | --- | --- |
| Contrats/politique/sélection | `./gradlew :core-domain:test` | Logique JVM sans Android |
| Orchestrateur/ViewModel | `./gradlew :feature-conversation:testDebugUnitTest` | États, persistance fake, concurrence |
| Réglages | `./gradlew :feature-settings:testDebugUnitTest` | Validation et transformations |
| Réseau | `./gradlew :core-network:testDebugUnitTest` | Parsing/MockWebServer, pas auth réelle |
| Outils | `./gradlew :tool-bridge:testDebugUnitTest` | Politique/registre avec adaptateurs fake |
| Session voix | `./gradlew :assistant-session:testDebugUnitTest :feature-voice:testDebugUnitTest` | Cycle de vie contrôlé et signal |
| Secrets/audit | `./gradlew :core-security:testDebugUnitTest :core-observability:testDebugUnitTest` | Redaction et erreurs fake |
| Tokens UI | `./gradlew :core-ui:testDebugUnitTest` | Contrats de design, pas capture app |
| Compagnon | `PYTHONWARNINGS=error::ResourceWarning python3 -m unittest -v tests.companion.test_codex_companion` | Protocole fake, cleanup, pas compte réel |

Les modules Android bibliothèque n'ont pas les flavors de l'app : utiliser `testDebugUnitTest`
ou `connectedDebugAndroidTest`, pas `connectedCoreDebugAndroidTest` pour ces bibliothèques.
Si un futur build ajoute des flavors, vérifier `./gradlew :module:tasks --all` puis actualiser ce guide.

## Vérification d'intégration avant merge

```bash
python3 -m py_compile companion/codex_companion.py
PYTHONWARNINGS=error::ResourceWarning python3 -m unittest -v tests.companion.test_codex_companion
./gradlew --no-daemon build assembleCoreDebug assemblePowerUserDebug
./gradlew --no-daemon test
./gradlew --no-daemon :baseline-profile:assembleCoreBenchmarkRelease :baseline-profile:assemblePowerUserBenchmarkRelease
./gradlew --no-daemon ktlintCheck detekt lint lintCoreDebug lintPowerUserDebug
python3 scripts/check_agent_docs.py
git diff --check
```

Reprendre les commandes du workflow si elles évoluent. Les rapports sont sous les répertoires
build/reports et build/test-results de chaque module ; les APKs app sous android/app/build/outputs/apk.
Une commande bloquée au téléchargement ou à la toolchain n'a exécuté aucun test pertinent.

## Tests instrumentés

Avec un appareil/émulateur sélectionné et autorisé :

```bash
./gradlew :core-data:connectedDebugAndroidTest
./gradlew :core-security:connectedDebugAndroidTest
./gradlew :tool-bridge:connectedDebugAndroidTest
./gradlew :assistant-session:connectedDebugAndroidTest
./gradlew :core-ui:connectedDebugAndroidTest
```

Séparer les tests qui changent des paramètres réels et annoncer leur effet avant exécution sur
appareil personnel. Restaurer volume/lampe/thème/font-scale à leur valeur initiale. Si plusieurs
devices existent, fixer la cible ; ne pas exécuter aveuglément sur tous les téléphones connectés.

## Parcours Samsung de qualification

Appareil historique : voir [matrice](../testing/device-matrix.md). Noter modèle/Android/One UI réels
du nouvel essai, commit, variante et backend/version. Ne pas recopier les mesures anciennes.

1. Installer le build autorisé et configurer le backend avec consentement réel, sans capturer les secrets.
2. Tester modèle direct puis agent en texte : deltas visibles, Stop et Retry, double clic, changement d'écran.
3. Demander batterie réelle puis lampe en STRICT ; confirmer un cas et refuser un autre ; consulter audit.
4. Rejouer la conversation par Power/voix, y compris continuité « et maintenant ? » avec contexte connu.
5. Couper le réseau pendant réponse et pendant outil ; arrêter/redémarrer le compagnon ; vérifier récupération.
6. Révoquer permission/profil pendant attente ; verrouiller l'écran ; confirmer que l'action reste refusée.
7. Rotation, mort processus et relance : historique cohérent et aucun spinner infini.
8. Vingt tours et vingt outils avec tableau cas par cas ; appel entrant réel pour l'audio.
9. Capturer états utiles, sombre/clair, IME, police 1,3× et contrôle 2× ; vérifier TalkBack.
10. Export expurgé et suppression ; restaurer réglages de test ; remplir [le rapport](templates/device-report.md).

Une capture Android peut être produite avec `adb exec-out screencap -p` vers un fichier choisi.
Éviter les écrans de compte, jetons et conversations privées. Fournir uniquement des captures synthétiques.

## Diagnostic rapide

| Symptôme | Vérification utile | Éviter |
| --- | --- | --- |
| Gradle ne trouve pas JDK 17 | Toolchains/installations locales | Passer le projet en JDK 25 sans mission |
| SDK absent | ANDROID_HOME/local.properties/platform 35 | Déclarer tests PASS après une compilation bloquée |
| Aucun appareil | adb devices, câble/débogage autorisé | Simuler une mesure Samsung |
| 401 compagnon | Type d'auth et appairage, sans afficher le token | Copier des credentials ChatGPT vers Android |
| 426 compagnon | Header protocole 2 et version des deux côtés | Ignorer la validation de version |
| Texte seul enregistré | Profil/routage runtime, GAP-01 | Ajouter une fausse réponse locale |
| Lampe sans confirmation | Contexte réel de politique, GAP-06 | Baisser le risque pour faire passer le test |
| OAuth direct refusé | Source officielle, état auth et code expurgé | Inventer des scopes/client IDs ou promettre l'accès |
