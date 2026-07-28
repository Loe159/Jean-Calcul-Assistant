# Validation de sortie du MVP - phase 1

Date : 2026-07-28

Issue : #33
Appareil de reference : Samsung Galaxy S26 5G Dual SIM (`SM-S942B/DS`), Android 16, One UI 8.5.

## Verdict

Le socle logiciel de la phase 1 est valide. Les parcours critiques sont couverts par des tests deterministes et les
essais qui dependent d'Android sont documentes de facon reproductible. La collecte Macrobenchmark et la generation
du Baseline Profile restent des operations sur appareil : elles ne sont pas executees par la CI sans materiel.

## Couverture automatisee

| Scenario | Preuve automatisee | Resultat |
| --- | --- | --- |
| Voix -> modele -> outil -> confirmation -> resultat -> TTS | `PhaseOneValidationTest.model tool call is confirmed by policy before registry execution and tts` | Le registre refuse l'appel sans recu ; la confirmation autorise ensuite l'execution et la reponse TTS. |
| Texte hors connexion -> action locale | `PhaseOneValidationTest.text local action remains available without any network provider` | L'action locale aboutit sans construire ni appeler de fournisseur reseau. |
| 100 invocations sans crash | `PhaseOneValidationTest.one hundred deterministic voice invocations finish without crash` | 100 sessions deterministes terminees dans la JVM. Le meme volume est aussi defini dans le test instrumente. |
| Annulation et reessai | `ConversationOrchestratorTest` et `VoiceSessionControllerTest` | Le flux est annule en amont, le message est interrompu et le reessai ne duplique rien. |
| Changement ou repli de fournisseur | `ProviderSelectionTest` | Seuls les profils compatibles sont selectionnes ; les erreurs d'authentification ne provoquent pas de repli non sur. |
| Fournisseur absent, timeout et cle invalide | Tests `core-network` OpenAI-compatible, Anthropic, Ollama et `ProviderConnectionProbeTest` | Les erreurs sont normalisees et recuperables ; le secret manquant bloque avant tout envoi. |
| Permission revoquee et ecran verrouille | `MvpToolPolicyTest`, `PolicyEngineTest` et `ToolRegistryTest` | La politique ouvre le panneau systeme ou refuse ; les outils sensibles ne sont pas annonces sur ecran verrouille. |
| Rotation et redemarrage | Tests Room/DataStore de `core-data` et procedure DM-04 ci-dessous | Conversations, sessions et reglages sont persistants ; la reprise Android reste verifiee sur appareil. |

## Performance et Baseline Profile

Le module `baseline-profile` cible les variantes `core` et `powerUser`. Il fournit :

- un generateur `BaselineProfileRule` sur le demarrage et la navigation principale ;
- un Macrobenchmark de demarrage a froid avec `StartupTimingMetric` et `FrameTimingMetric` ;
- une comparaison sans compilation et avec Baseline Profile ;
- dix iterations par configuration ;
- un jalon `first_token` emis sur le premier delta texte non vide d'un fournisseur.

Les executions sur appareil sont volontairement separees du build normal. La propriete
`phase1DeviceValidation` evite qu'une CI sans appareil tente une collecte materielle.

```powershell
./gradlew.bat :app:generateCoreBaselineProfile -Pphase1DeviceValidation
./gradlew.bat :baseline-profile:connectedCoreBenchmarkReleaseAndroidTest -Pphase1DeviceValidation
./gradlew.bat :assistant-session:connectedDebugAndroidTest
```

Pour limiter une execution au generateur ou aux benchmarks, ajouter respectivement :

```powershell
-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile
-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark
```

## Budgets

Les dernieres mesures physiques de la phase 0 restent la reference d'entree de la phase 1.

| Indicateur | Mesure P50/P95 | Budget P50/P95 | Etat |
| --- | ---: | ---: | --- |
| Power -> premiere frame | 36 / 159 ms | 700 / 1 200 ms | Respecte |
| Premiere frame -> microphone pret | 619 / 1 038 ms | 500 / 1 500 ms | P95 respecte ; P50 depasse de 119 ms, attribue au recognizer Android et deja documente. |
| Parole -> premiere transcription | 1 177 / 1 251 ms | 750 / 2 000 ms | P95 respecte ; P50 depasse de 427 ms, attribue au recognizer Android et deja documente. |
| Parole -> resultat final | 2 354 / 2 502 ms | 2 000 / 4 000 ms | P95 respecte ; P50 depasse de 354 ms avec seulement six echantillons. |
| Action locale -> volume observe | 1 / 6 ms | 100 / 250 ms | Respecte |
| Premier token fournisseur | Jalon automatise, pas de mesure materielle dans cette execution | Aucun budget phase 0 | A mesurer par fournisseur configure. |

Les depassements P50 sont acceptes pour la sortie du MVP car les P95 restent sous budget, l'interface reste
interruptible et l'echantillon physique est trop petit pour un percentile de production. Les nouvelles traces ne
contiennent ni audio, ni transcription, ni secret.

## Procedure appareil

1. Installer `coreDebug`, attribuer le role assistant et accorder le microphone.
2. Executer DM-01 a DM-06 de `docs/testing/device-matrix.md` : accueil, application tierce, ecran verrouille,
   redemarrage, economie d'energie et modes de batterie.
3. Couper le Wi-Fi et les donnees mobiles, saisir une commande locale en texte, puis verifier son resultat et
   l'evenement d'audit.
4. Revoquer le microphone et la camera, puis verifier qu'aucune action protegee ne s'execute silencieusement.
5. Lancer 100 invocations, rechercher `AndroidRuntime` et `FATAL EXCEPTION`, puis joindre la trace expurgee.
6. Generer le Baseline Profile et executer le Macrobenchmark avec les commandes ci-dessus. Reporter mediane,
   minimum et maximum pour le demarrage et les frames.

## Permissions, donnees et limitations

- Aucune permission Android supplementaire n'est introduite par #33.
- Le module de benchmark est un APK de test separe ; il n'est pas inclus dans l'application distribuee.
- Les tests utilisent des fakes sans reseau et sans secret.
- Aucun appareil n'etait connecte a l'environnement de cette implementation. Les tests instrumentes, la collecte
  du profil et les valeurs Macrobenchmark doivent donc etre rejoues sur le Samsung avant une publication externe.
- Le premier token depend du fournisseur et du reseau choisis. Le jalon est present, mais aucun budget universel
  n'est defini.

## Criteres d'acceptation #33

- [x] Parcours critiques automatises ou documentes reproductiblement.
- [x] Aucun outil sensible ne s'execute sans decision du Policy Engine.
- [x] Mode texte hors connexion valide pour une action locale.
- [x] Budgets respectes ou ecarts justifies.
- [x] 100 invocations deterministes sans crash.
- [x] Rapport de validation versionne.
