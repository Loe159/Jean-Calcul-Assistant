# Performance et stabilite - phase 0

Issue associee : #15
Appareil cible : Samsung Galaxy S26 5G Dual SIM (`SM-S942B/DS`), Android 16,
One UI 8.5. Consulter aussi la [matrice d'appareils](../testing/device-matrix.md).

## Instrumentation

Le tag Logcat `JeanCalculPerf` produit des lignes `key=value` sans contenu vocal
ni donnees personnelles. Chaque session possede un `invocation_id` aleatoire.

| Jalons | Mesure obtenue |
| --- | --- |
| `invocation_received` -> `first_frame` | Power vers premiere frame de la session |
| `first_frame` -> `microphone_ready` | Premiere frame vers microphone pret |
| `speech_started` -> `first_transcription` | Parole vers premiere transcription |
| `speech_started` -> `final_result` | Parole vers resultat final |
| `volume_requested` -> `volume_applied` | Demande locale vers volume Android relu |

Les lignes `performance_memory` relevent `total_pss_kb`, le PID et le processus
pour `service_ready`, `session_invocation`, `session_first_frame` et
`session_destroy`. Le service et la session sont donc mesures separement.

## Procedure reproductible Samsung

1. Installer la variante `coreDebug`, selectionner Jean-Calcul comme assistant,
   accorder le microphone et suivre la configuration One UI de
   `docs/testing/samsung-power-invocation.md`.
2. Demarrer une capture avant chaque serie :

   ```bash
   adb logcat -c
   adb logcat -v epoch JeanCalculPerf:I '*:S' > phase-0-performance.log
   ```

3. Depuis l'accueil deverrouille, effectuer un appui long physique sur Power,
   dire `Mets le volume a 30 %`, puis attendre la reponse. Repeter dix fois
   apres un redemarrage a froid et dix fois sans tuer l'application.
4. Verrouiller ensuite le telephone pendant au moins 30 minutes, le deverrouiller,
   puis repeter dix invocations. Ne pas modifier les reglages batterie pendant
   une serie ; noter leur etat.
5. Arreter la capture, joindre le fichier Logcat expurge a l'issue et reporter
   mediane, P95, maximum et echec eventuel dans le tableau suivant. Verifier que
   les deux processus ont une ligne `performance_memory` et qu'aucune ligne
   `AndroidRuntime` ou `FATAL EXCEPTION` n'est presente.

Les commandes de developpement suivantes couvrent aussi le parcours determine
trente fois dans le test instrumente :

```bash
./gradlew :assistant-session:connectedCoreDebugAndroidTest
```

## Resultats Samsung a renseigner

Les valeurs ci-dessous sont volontairement vides tant qu'une capture issue de
l'appareil cible n'a pas ete produite. Les remplir a partir de la meme serie de
traces, sans substituer l'emulateur AOSP au Samsung.

| Serie et build | Power -> frame (P50/P95) | Frame -> micro (P50/P95) | Parole -> transcription (P50/P95) | Parole -> final (P50/P95) | Volume applique (P50/P95) | PSS service / session | Crashes / 30 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 2026-07-16, `coreDebug`, 30 `KEYCODE_ASSIST` consecutifs | 51 / 66 ms, 29 traces frame | 78 / 134 ms, 24 paires frame-micro | Non mesure : aucune parole injectee | Non mesure : aucune parole injectee | Non mesure : aucune commande volume injectee | 43.1 MiB / 50.5 MiB (PSS) | 0 crash Jean-Calcul ; 30 invocations recues |
| 2026-07-17, `coreDebug`, 6 commandes vocales physiques completes | 36 / 159 ms | 619 / 1 038 ms | 1 177 / 1 251 ms | 2 354 / 2 502 ms | 1 / 6 ms | 15.0 MiB / 31.9 MiB apres reboot (PSS) | 0 crash; accueil, reprise apres 15 min de veille, economie d'energie, Optimisee, Sans restriction et reboot couverts |

La premiere invocation apres installation a aussi produit 322 ms jusqu'a la
premiere frame et 192 ms entre la frame et le microphone pret. La serie de 30
invocations a ete declenchee par `KEYCODE_ASSIST`, qui valide la chaine Android
selectionnee mais ne remplace ni l'appui long physique Power ni une parole
reelle. Une frame et cinq disponibilites microphone n'ont pas ete observees
avant la fermeture automatisee de leur session ; elles restent a analyser lors
d'une serie manuelle avec parole, sans les comptabiliser comme des succes de
latence.

Les P50 de la seconde ligne sont les medianes de six mesures completes. Avec six echantillons, les P95 sont le maximum observe; ils ne constituent pas une estimation statistique de production. Une invocation ecran verrouille a aussi mesure le microphone, la transcription, le resultat et le volume, mais sans evenement de premiere frame, et n'est donc pas incluse dans ces calculs.

La stabilite a ete reexecutee le 2026-07-17 apres le reboot : 30 ouvertures et fermetures automatisees via `KEYCODE_ASSIST` ont produit 30 `invocation_received`, 30 `invocation_finished` et zero ligne `AndroidRuntime`. Le test instrumente `:assistant-session:connectedDebugAndroidTest` a egalement reussi (2 tests, dont 30 sessions deterministes). Ces essais ne remplacent pas les six mesures vocales reelles rapportees ci-dessus.

## Budgets initiaux de la phase 1

Ces budgets sont des seuils de depart a confirmer apres la premiere serie
Samsung. Ils distinguent la plateforme Android, le recognizer installe et
l'execution locale de volume.

| Indicateur | P50 cible | P95 cible |
| --- | ---: | ---: |
| Power -> premiere frame | <= 700 ms | <= 1 200 ms |
| Premiere frame -> microphone pret | <= 500 ms | <= 1 500 ms |
| Parole -> premiere transcription | <= 750 ms | <= 2 000 ms |
| Parole -> resultat final | <= 2 000 ms | <= 4 000 ms |
| Demande locale -> volume observe | <= 100 ms | <= 250 ms |
| PSS processus service | <= 80 MiB | <= 100 MiB |
| PSS processus session | <= 140 MiB | <= 180 MiB |

Un depassement doit conserver la trace expurgee et indiquer si la cause est
l'invocation One UI, la reconnaissance Android ou l'execution locale. Aucun
budget ne justifie de conserver de l'audio ou une transcription dans les logs.

## Etat de validation

- Instrumentation, rapport et tests automatises : fournis par l'issue #15.
- Premiere frame, microphone et PSS : releves sur le Samsung le 2026-07-16.
- Parole, resultat final et volume observe : mesures avec commande vocale reelle le 2026-07-17.
- Appui long Power physique, reprise apres 15 minutes de veille, economie d'energie et les modes Optimisee/Sans restriction : mesures le 2026-07-17.
