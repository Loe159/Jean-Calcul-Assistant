# POC notification NLI — phase A (bloquante)

**État : préparation du banc de référence uniquement ; GO/NO-GO = NON DÉTERMINÉ.**

Le modèle imposé est `Horizon-Labs/multilingual-zeroshot-small` (141M).
Les labels et le template de la démo sont figés dans
`scripts/notification_onnx_reference.py`. Aucune notification réelle ne doit
figurer dans les fixtures, les traces, les rapports ou une CI distante.

## Reproduction

Dans un environnement de développement isolé, installer les dépendances
`torch transformers numpy onnxruntime` à des versions consignées dans le
rapport. Épingler **le SHA complet du commit du modèle Hugging Face**
(à relever avant de télécharger, non deviné dans ce POC).

Télécharger les fichiers de cette révision, vérifier leurs SHA-256, notamment
`onnx/model_quantized.onnx`, `tokenizer.json` et les fichiers de configuration.
Ne pas confondre `model_quantized.onnx` (~268 Mo, quantification partielle des
embeddings) avec un modèle entièrement INT8.

```bash
python3 scripts/notification_onnx_reference.py \
  --revision "$HF_REVISION" \
  --onnx /CHEMIN/VERIFIE/model_quantized.onnx \
  --output /CHEMIN/PRIVE/poc-results.json
```

Le script vérifie le sens des logits NLI, l'accord avec le pipeline
`zero-shot-classification` sur les exemples synthétiques et exporte les
IDs et masques du tokenizer. Attention : la référence compare un passage
`max_length=256` aux paramètres par défaut du pipeline ; les exemples
longs nécessitent de fixer une politique de troncature identique pour
une comparaison valide (voir la limitation ci-dessous). **Ce script ne prouve
ni la parité Android ni la compatibilité d'ONNX Runtime Mobile.**

## Étapes bloquantes avant l'intégration

1. Vérifier `tokenizer.json` (prétraitement et encodage des paires). Tester
   ONNX Runtime Extensions sur l'appareil ; utiliser `tokenizers` Rust via
   JNI uniquement si les IDs/paires ne sont pas fidèles.
2. Construire un **POC Android isolé**, minSdk 26, CPU ONNX Runtime,
   sans téléchargement automatique, sans service de notifications et sans
   accès réseau aux notifications. Vérifier I/O, opérateurs, ABI et charge.
3. Comparer token IDs, masques, logits et scores Python/Android avec les
   mêmes versions, 2/4/6/8 labels et 96/128/256 tokens. Vérifier
   `multi_label=false` et `multi_label=true`.
4. Mesurer cold/warm p50/p95, PSS/RSS, mJ par inférence, 1/2/4 threads et
   spinning désactivé, 3 politiques de session, mode avion, veille/Doze.
5. Tester sur **émulateur et Samsung physique**, avec baseline sans filtre.
   Mesurer 24 h idle et scénarios 50/200/500 notifications/jour.
6. Seulement après résultat positif : listener déclenché par événement,
   préfiltre déterministe, queue bornée, policy fail-closed, UI observation.
   La suppression automatique reste désactivée jusqu'à calibration et
   validation explicite.

## Matrice de preuves

| Critère | Résultat |
| --- | --- |
| Révision et checksum model/tokenizer | Non vérifié |
| tokenizer mmBERT, séquences paires | Non testé sur Android |
| Exécution ONNX Runtime Android | Non testée |
| Parité avec démo Small | Non mesurée |
| Latence froide/chaude | Non mesurée |
| RAM Android physique | Non mesurée |
| Consommation et autonomie 24 h | Non mesurées |
| E2E fonctionnels/visuels | Non exécutés |
| APK de validation | Non construit |
| Suppression automatique | Non implémentée (sécurité) |

Les valeurs numériques ne doivent jamais être inventées ; documenter modèle
du téléphone, version Android, SHA, température, batterie, modes d'économie
d'énergie, logs sans contenu personnel, commandes et conditions du protocole.

## Décision de confidentialité

Le futur `NotificationListenerService` ne transmettra aucun titre/corps
à un fournisseur LLM, Analytics, crash-reporting ou journal. Aucune
suppression automatique en mode observation. Sur timeout, OOM, erreur modèle,
perte de permission, classement ambigu ou événement obsolète : **conserver**.
