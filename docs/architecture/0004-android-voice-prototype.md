# ADR 0004 - Prototype vocal Android de la session

## Decision

La session d'assistant possede les adaptateurs Android de `SpeechRecognizer` et de
`TextToSpeech`. Le service d'assistant reste sans ressource audio. Les contrats
`SpeechToTextProvider` et `TextToSpeechProvider` vivent dans `core-domain`, sans type Android.

La reconnaissance produit des hypotheses partielles, puis un resultat final structure avec le
texte et, lorsqu'il est disponible, le score de confiance. La session arrete une ecoute qui dure
plus de 15 secondes et une attente de resultat final qui dure plus de 5 secondes.

`RECORD_AUDIO` est demande uniquement depuis `MainActivity` et verifie avant le demarrage de la
reconnaissance. Quand la permission ou la reconnaissance manque, la session conserve une saisie
texte qui produit le meme resultat structure. Aucun flux audio, enregistrement ou transcription
n'est persiste.

## Consequences

- La fermeture, Retour et l'annulation arretent le recognizer et le TTS. La destruction de la
  session appelle aussi `destroy` sur le recognizer et `shutdown` sur le TTS.
- Le prototype repose uniquement sur les services vocaux Android installes sur l'appareil. Il ne
  fait aucun appel reseau et ne fournit pas encore de fournisseur vocal externe.
- Les contrats permettent une implementation remplacable plus tard sans introduire de fournisseur
  ou de configuration hors perimetre de l'issue #13.
