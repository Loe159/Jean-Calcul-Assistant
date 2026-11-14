# Validation de la reprise ETA — 14 novembre 2026

Code applicatif : `8b598ae1e3d66697319c0824e9c57921b8ba4a08` (arbre `e2177d9832b963441c6342aadea2df297415fa25`). Les ajouts de ce rapport ne modifient pas l’APK.

| Vérification locale | Résultat |
| --- | --- |
| Compilation APK ARM64 | Réussie |
| Compilation APK d’instrumentation | Réussie |
| Tests JVM | 2 024 réussis, 0 échec, 1 ignoré |
| Tests du rapport E2E Python | 9 réussis |
| Lint Android | PASS |
| Tests instrumentés exécutés | NOT_RUN |
| Captures et revue visuelle native | NOT_RUN |
| Comparaison visuelle aux références ETA | NOT_RUN |
| Connexion réelle au compte ChatGPT | NOT_RUN |
| Samsung / root / LSPosed | NOT_RUN |

Le test ignoré `EtaWaveShaderTest.effectCompilesWithAndroidShaderCompiler` nécessite le compilateur de shaders Android. Les tests des blocs exclus sont archivés dans `docs/upstream/tests`, et les contrôles de persistance vLLM/ChatGPT les remplacent pour le périmètre sélectionné. Les tests des fonctions conservées restent actifs.

Les trois tentatives locales ont échoué avant l’exécution instrumentée : arrêt de l’émulateur sans KVM, délai ADB, puis démarrage logiciel incomplet après dix minutes avec l’image ATD. Aucun PNG applicatif n’a été examiné. Ces échecs d’environnement ne sont pas transformés en validation UX. Aucune quatrième tentative locale n’a été lancée. Les scripts sont prêts pour un environnement avec KVM ; les résultats GitHub Actions doivent être consultés séparément.

L’APK distribué `JeanCalcul-ETA-selection-debug.apk` porte l’identité `fr.loevan.jeancalcul.v2`, contient ARM64 et vise Android 13+. Empreinte SHA-256 :

```
237a534a8ddd4222e8636c3eb691297b31ca37b02577cf4ba3f4412a4b6b7d8d
```

Les résultats structurés et les motifs des tentatives figurent dans [validation/results.json](validation/results.json). Les composants COUI sont repris des sources ETA ; une identité visuelle pixel à pixel, la fluidité et le fonctionnement de chaque outil sur Samsung ne sont pas revendiqués sans ces essais.
