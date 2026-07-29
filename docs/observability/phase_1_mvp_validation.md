# Validation de sortie du MVP - phase 1

Date : 2026-07-29

Issue : #33
Appareil de reference : Samsung Galaxy S26 5G Dual SIM (`SM-S942B/DS`), Android 16, One UI 8.5.

## Verdict

Le socle logiciel et les parcours physiques executables avec le materiel disponible sont valides sur le Samsung de
reference. Le verdict de sortie physique reste conditionnel : VP-02 exige un second appelant, les scenarios Ollama
exigent un serveur local absent de l'environnement, et plusieurs outils Android ne sont pas declenchables depuis
l'interface actuelle. Ces limites ne sont pas remplacees par des resultats deduits des tests unitaires.

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

## Campagne physique du 29 juillet 2026

Reference : Samsung `SM-S942B`, Android 16, One UI 8.5, build `S942BXXS4AZG5`, correctif de securite
du 5 juillet 2026. Variante `coreDebug`, role assistant attribue a `fr.loevan.jeancalcul.debug`.

| Scenario | Resultat physique | Observation |
| --- | --- | --- |
| DM-01 | Reussi | Accueil deverrouille, voix, transcription, confirmation, volume, TTS et Retour. Une regression de focus audio a ete corrigee avant le second passage. |
| DM-02 | Reussi | Chrome est reste visible sous la surface transparente ; aucune perte de contexte ni crash. |
| DM-03 | Reussi | En mode Verrouillage One UI, la transcription a abouti mais la politique a bloque `audio.set_volume` avant toute execution. Extend Unlock avait rendu le premier essai invalide. |
| DM-04 | Reussi | Apres redemarrage complet, le role assistant et le service sont restes actifs. Un crash Samsung Cell Broadcast sans lien avec Jean-Calcul a ete exclu. |
| DM-05 | Reussi | Invocation, confirmation, action et TTS fonctionnels avec Economie d'energie active. Le mode a ensuite ete desactive. |
| DM-06 | Reussi | Reprise apres veille en modes Optimisee et Sans restriction. Aucun ecart de fiabilite sur un essai par mode ; Optimisee a ete retablie. |
| VP-01 | Reussi | Samsung Galaxy Buds3 Pro : onde Bluetooth violette/bleue, deconnexion sans blocage, puis reconnexion et nouvelle invocation fonctionnelles. |
| VP-02 | Reporte | Aucun second telephone ou compte appelant distinct n'etait disponible. Un appel PC avec le meme compte ne constitue pas une preuve equivalente. |
| VP-03 | Reussi | En `fr-FR`, le texte partiel est apparu avant la fin et le silence a produit le resultat final. |
| VP-04 | Reussi avec limite | Le passage dynamique a `en-GB` a ete pris en compte par le STT. Le TTS Samsung est reste sur une voix francaise et a prononce l'anglais avec un accent inadapte. `fr-FR` a ete retabli. |
| VP-05 | Reussi avec limite | Service de reconnaissance indisponible, erreur recuperable et commande texte effective. Le premier essai texte n'a pas produit de TTS ; un essai hors ligne ulterieur a bien produit texte et TTS. |
| VP-06 | Reussi | Retour pendant l'ecoute, destruction de la premiere session, puis reprise immediate du microphone et action complete. |
| Hors ligne | Reussi | Mode Avion avec Wi-Fi coupe : reconnaissance embarquee fonctionnelle, puis repli texte force avec microphone revoque. Action locale, confirmation, resultat ecrit, TTS et audit sans reseau. |
| Permission microphone | Reussi apres correction | Le bouton d'autorisation faisait planter la session via `startVoiceActivity`. Le lancement normal de `MainActivity` a ete implemente ; la fenetre One UI s'ouvre desormais et la voix reprend apres accord. |
| Audit | Reussi partiellement | Reussite, refus et annulation visibles avec arguments expurges et sans dictee en clair. Une defaillance Android d'outil n'est pas injectable depuis l'interface actuelle. |

La camera est restee refusee pendant la campagne. Aucune donnee personnelle, transcription complete, adresse reseau
ou secret n'est conserve dans les preuves du rapport.

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

AndroidX Benchmark et le plugin Baseline Profile ont ete alignes de `1.3.3` vers `1.4.1`. La premiere collecte
echouait sur Android API 36 malgre un affichage reel de `MainActivity` en 203 ms ; la collecte a reussi apres la
mise a niveau compatible API 36.

Resultats Macrobenchmark, dix demarrages a froid par configuration :

| Configuration | Affichage initial min / mediane / max | Frame CPU P50 / P95 / P99 |
| --- | ---: | ---: |
| Sans compilation | 221,1 / 259,2 / 272,5 ms | 4,1 / 7,3 / 27,3 ms |
| Avec Baseline Profile | 233,2 / 242,7 / 256,8 ms | 3,0 / 5,9 / 15,8 ms |

Le profil reduit la mediane d'affichage de 16,5 ms, soit 6,4 %. Les fichiers `baseline-prof.txt` et
`startup-prof.txt` ont ete generes pour `coreRelease`.

Pour limiter une execution au generateur ou aux benchmarks, ajouter respectivement :

```powershell
-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile
-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark
```

## Budgets

Les mesures suivantes agregent DM-01, DM-02, DM-04, DM-05 et les deux passages DM-06. DM-03 est exclu des
latences d'action car la politique doit precisement refuser l'execution.

| Indicateur | Mesure P50/P95 | Budget P50/P95 | Etat |
| --- | ---: | ---: | --- |
| Power -> premiere frame | 76 / 235 ms | 700 / 1 200 ms | Respecte. |
| Premiere frame -> microphone pret | 192 / 348 ms | 500 / 1 500 ms | Respecte. |
| Parole -> premiere transcription | 1 106 / 1 167 ms | 750 / 2 000 ms | P95 respecte ; P50 reste dependant du recognizer Android. |
| Parole -> resultat final | 2 415 / 2 895 ms | 2 000 / 4 000 ms | P95 respecte ; echantillon physique de six passages. |
| Action locale -> volume observe | 21 / 31 ms | 100 / 250 ms | Respecte. |
| Premier token fournisseur | Jalon automatise, pas de mesure materielle dans cette execution | Aucun budget phase 0 | A mesurer par fournisseur configure. |

Les depassements P50 de reconnaissance sont acceptables pour le MVP car les P95 restent sous budget, l'interface
reste interruptible et l'echantillon physique est trop petit pour un percentile de production. Les traces
expurgees ne contiennent ni audio, ni transcription, ni secret.

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

Les etapes 1 a 6 ont ete executees sur le Samsung. Les 100 sessions deterministes et les trois tests instrumentes
`assistant-session` ont termine sans crash apres les deux corrections de campagne.

### Checklist outils Android

Le registre versionne contient les outils du MVP, mais la surface conversationnelle de phase 1 ne route que les
commandes de volume via `DeterministicVolumeCommandInterpreter`. Etat de la checklist physique :

| Verification | Etat |
| --- | --- |
| Volume, politique, confirmation, refus et annulation | Valide physiquement. |
| Batterie, heure locale et capacites | Tests instrumentes connectes reussis ; comparaison UI manuelle partielle. |
| Persistance/rejeu des taches locales | Test `feature-tasks` connecte reussi ; aucun declencheur manuel dans l'UI principale. |
| Media, panneaux Reglages, lancement d'application et torche | Non declenchables de bout en bout depuis l'interface actuelle. |
| Audit succes, refus et annulation | Valide physiquement et expurge. |
| Audit defaillance Android | Non injectable depuis l'interface actuelle. |
| Decouverte sous verrouillage | Politique automatisee et refus physique du volume valides ; liste complete non visible dans l'UI. |

Une surface de validation dediee ou un scenario instrumente interactif est requis pour terminer cette checklist sans
etendre artificiellement l'interpreteur conversationnel du MVP.

## Permissions, donnees et limitations

- Aucune permission Android supplementaire n'est introduite par #33.
- Le module de benchmark est un APK de test separe ; il n'est pas inclus dans l'application distribuee.
- Les tests utilisent des fakes sans reseau et sans secret.
- La campagne a utilise le Samsung de reference connecte en USB. Les resultats restent specifiques a ce build One UI
  et ne remplacent pas une matrice multi-appareils.
- Le premier token depend du fournisseur et du reseau choisis. Le jalon est present, mais aucun budget universel
  n'est defini.
- Aucun serveur Ollama n'etait installe ou actif sur l'hote. Les sept scenarios physiques de
  `docs/testing/phase-1-ollama-network.md` restent reportes.
- VP-02 reste reporte faute de second appelant.
- Le TTS doit encore suivre la locale dynamique du STT et rester actif dans tous les chemins de repli texte.
- La checklist manuelle des outils Android reste incomplete tant que les outils hors volume ne sont pas exposables
  par une surface de validation.

## Criteres d'acceptation #33

- [x] Parcours critiques automatises ou documentes reproductiblement.
- [x] Aucun outil sensible ne s'execute sans decision du Policy Engine.
- [x] Mode texte hors connexion valide pour une action locale.
- [x] Budgets respectes ou ecarts justifies.
- [x] 100 invocations deterministes sans crash.
- [x] Rapport de validation versionne.

## Conditions restantes avant cloture physique stricte

- [ ] Executer VP-02 avec un vrai appel entrant.
- [ ] Executer les sept scenarios Ollama avec un serveur LAN configure.
- [ ] Fournir une surface de validation ou des scenarios instrumentes interactifs pour les outils Android hors volume.
- [ ] Verifier une entree d'audit `Echec` issue d'une defaillance Android reelle et recuperable.
