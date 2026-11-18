# Classification locale des notifications — POC Android V2

Statut : **phase A inachevée, GO/NO-GO encore indéterminé**.
Aucune suppression, aucun classement de notifications réelles et aucun nouveau
listener n'ont été activés.

## Portage et architecture

Branche V2 `feature/jean-calcul-v2-foundation` (ETA/COUI, minSdk 33, JDK 25,
SDK 37, package `fr.loevan.jeancalcul.v2`). V2 possède déjà un
`AgentNotificationHistoryService` et une fonction distincte
`search_notification_history`. **Ne pas ajouter de second listener**.
Important : ce service historique écrit déjà des titres/corps en SQLite ;
réviser cette persistance et éviter toute fuite vers un LLM dans la future
classification.

Modèle retenu : `Horizon-Labs/multilingual-zeroshot-small`.
Révision immuable `17af5f8a728d101ce33a9bffad227b0cd005e95e`.
Poids quantifiés ONNX (gather-only) : 268 409 234 octets.
SHA-256 : `0ac394942a086fb1c02beb0fde0357bf7f3bd739ca07af7fa6c9fa11c9c05512`.
Tokenizer SHA-256 :
`aebee76d0312011b0d73b08a255d4d11d25c83e9639324870c7b916ea102e13e`.
Configuration :
`3e5f7eebf8ddee141f092672ca5865b7741e64df974fbc68223c91da6cd74222`.
Tokenizer configuration :
`ea349495764b24570b4560fe2e4a557e5c0a47e6bce782c2409b1d6445cf8633`.

Tokenizer réel : **BPE avec Metaspace**, TemplateProcessing
`<bos> A <eos> B <eos>`, pas BERT WordPiece.
I/O ONNX vérifiés sur Linux :
`input_ids` int64 [batch,seq],
`attention_mask` int64 [batch,seq],
`logits` float [batch,2].
Classes : 0 `not_entailment`, 1 `entailment`.

Labels et template versionnés dans `NotificationNliScores.kt` et
`scripts/notification_onnx_reference.py`.
**Android n'a pas encore de tokenizer BPE embarqué.**
Le test instrumenté exerce les poids ONNX avec les IDs BPE préencodés
depuis le même tokenizer sur le PC. Ce n'est **pas une preuve de tokenizer
Android en production**. Ne pas déployer le classifieur dans le listener
avant une intégration paritaire ONNX Runtime Extensions ou Rust tokenizers JNI.

## Mesures du banc Linux

Protocole : ONNX Runtime 1.32.0, tokenizers 0.23.3, CPU, exécution séquentielle,
spinning désactivé, longueur maximale 128 tokens, 7 catégories,
10 notifications synthétiques, 3 passages par exemple.

| Threads | Médiane approximative p50 sur les exemples | RSS maximale du processus |
| --- | ---: | ---: |
| 1 | ~330 ms | ~951 MiB |
| 2 | ~195 ms | ~949 MiB |
| 4 | ~160 ms | ~937 MiB |

Temps de création d'une session Linux : 1,2–1,6 s.
Ces mesures incluent un batch de 7 paires / notification.
Le RSS inclut le buffer Python de modèle et les bibliothèques ;
**ce n'est pas la RAM native Android ni une estimation de batterie**.

Cette charge mémoire est élevée pour un service Android permanent :
tester déchargement immédiat, cache court et conservation en mémoire
seulement si les mesures réelles montrent un bénéfice. Ne jamais garder
une inférence active ni un wakelock à l'idle.

## Exécuter le banc local

Dépendances de développement uniquement : `numpy`, `onnxruntime`,
`tokenizers`. Les fichiers du modèle doivent être téléchargés
**séparément** depuis la révision épinglée et vérifiés avant chargement.
Les parties du modèle sont nécessaires uniquement si l'environnement
interdit les fichiers de plus de 64 Mio. Sur un environnement standard
réunir les parties dans `model_quantized.onnx` et adapter le script
pour charger le fichier unique.

```sh
python3 scripts/notification_onnx_smoke.py \
  --model-dir /CHEMIN/PRIVE --output /CHEMIN/PRIVE/result.json \
  --threads 2 --max-tokens 128
```

Pour comparer au pipeline Python Transformers exact, utiliser
`scripts/notification_onnx_reference.py` avec la même révision et
la même troncature, et compléter la matrice de parité.

## POC Android isolé

Dépendance : `com.microsoft.onnxruntime:onnxruntime-android:1.23.2`.
Le fichier ONNX n'est pas dans l'APK et aucun téléchargement n'est
déclenché par l'application.

Installer explicitement les poids dans le dossier externe privé de l'app
de test (sans données personnelles) :

```sh
adb shell mkdir -p /sdcard/Android/data/fr.loevan.jeancalcul.v2/files/notification-model
adb push /CHEMIN/PRIVE/model_quantized.onnx \
  /sdcard/Android/data/fr.loevan.jeancalcul.v2/files/notification-model/model_quantized.onnx
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.mangi.eta.agent.device.notifications.NotificationOnnxAndroidPocTest
```

Le test vérifie la somme SHA-256, l'ouverture de session, les entrées,
les 10 jeux de logits et l'écart maximal à la référence CPU Linux
(tolérance provisoire 0,05 absolu). Il affiche le temps de chargement
et les latences ainsi que le delta de PSS sans journaliser de notification.
Si les poids sont absents, le test est **ignoré**, jamais « réussi ».
Tester ARM64 sur le téléphone physique et x86_64 sur l'émulateur.

## Conditions d'acceptation avant toute intégration

- Test instrumenté Android réellement exécuté et logits comparés.
- Tokenizer mmBERT intégré, paires et IDs vérifiés octet à octet sur Android.
- Parité avec Transformers pour single-label et multi-label, y compris
  textes FR/accents/emojis/longs, score/labels inchangés.
- Benchmarks sur appareil réel : p50/p95, PSS, coût par notification,
  veille/Doze, économie batterie, baseline idle 24 h,
  charges simulées 50/200/500 notifications/jour.
- Tests e2e avec refus/révocation de permission, erreurs, débordement de
  file, mises à jour, groupes et protections critiques, et revue visuelle COUI.
- **Aucun contenu de notification transmis au cloud**. Ne pas activer
  de transport réseau ni de télémétrie pour le pipeline.
- Observation par défaut. Toute décision de suppression requiert une
  preuve explicite, une validation utilisateur et une vérification de
  la notification active, inchangée et supprimable. Sur erreur : conserver.

## Limites de cette livraison

Le banc Linux et le test Android instrumenté sont des outils de POC.
La classification Android des notifications réelles, la page de paramètres,
les tests E2E visuels et les métriques d'autonomie **ne sont pas livrés**.
L'application n'effectue aucune suppression de notification.

## Vérifications CI réalisées le 16 novembre 2026

- GitHub Actions V2 a construit l'APK et les tests JVM. Le POC ONNX a été exécuté sur émulateur Android 35 x86_64 : SHA contrôlé, session initialisée et 10 jeux de logits traités par le test instrumenté (`OK (1 test)`, durée du test entier 2,877 s). Cette durée n'est **pas** la latence par notification. Le job a ensuite échoué sur un `grep` de métriques absent du flux shell ; il ne s'agit pas d'un échec d'inférence.
- Deux défauts de tests précédents étaient des **tests ignorés**, malgré une CI partiellement verte. Le protocole exige désormais un fichier en stockage privé via `run-as` et refuse toute absence de poids.
- ONNX Runtime Extensions `HfJsonTokenizer` (0.15.0, hôte Linux) n'a **pas** produit les mêmes token IDs que le tokenizer HF officiel sur une paire de test. Cette voie ne peut être utilisée telle quelle.
- Une bibliothèque `tokenizers` Rust `0.22.2` via JNI a été compilée en cross-target Android x86_64 dans la CI. Le test exact de parité des 14 paires est en cours de qualification, ne pas déclarer son succès avant preuve.
- Aucune mesure de PSS fiable sur téléphone, aucun coût énergétique mJ, aucune autonomie 24h, aucun test de suppression ou de confidentialité réseau sur téléphone. L'APK de POC ne contient pas encore de moteur de classification dans le listener ni de page COUI dédiée.

La PR de ce POC doit demeurer **draft**, sans merge, jusqu'à la décision GO/NO-GO issue des mesures réelles.

## Résultat Android émulateur validé (run 43870495709)

Le job GitHub Actions [ONNX Android POC](https://github.com/Loe159/Jean-Calcul-Assistant/actions/runs/43870495709) s'est terminé en **succès** : les deux tests instrumentés ont été exécutés (pas ignorés).

- Modèle quantifié SHA-256 vérifié ; inférence ONNX CPU sur les 10 jeux de 7 hypothèses synthétiques.
- Logcat Android 35 x86_64 : `session_load_ms=694.710244`, `p50_ms=108.861329`, `p95_ms=139.700105`, `pss_delta_kib=290162` (~283.4 MiB), `max_logit_delta=1.0967254638671875E-5` versus l'ONNX Linux de référence.
- Tokenizer officiel Hugging Face `tokenizers=0.22.2` compilé pour Android via Rust/JNI : **14/14 encodages de paires** concordent avec les IDs HF du banc ; la métrique `elapsed_ms=1543.627677` inclut le chargement du tokenizer depuis le JSON et les 14 paires, ce n'est pas une latence par notification.
- Build Android Gradle passé en CI. Les `onnxruntime-extensions` n'ont pas donné la même parité ; choisir Rust JNI pour les essais suivants.

**Limites de preuve :** la comparaison couvre ONNX quantifié versus le même ONNX Linux ; la parité avec les logits FP32 du pipeline Python Transformers reste à mesurer. Deux textes validés sur 7 labels pour JNI ne suffisent pas à généraliser la parité à tous les messages longs/Unicode. Ni Samsung physique ni baseline 24h batterie. Pas de suppression. Pas de mode automatique autorisé à ce stade.
