# ADR 0005 - Observabilite de performance de la phase 0

## Decision

La phase 0 mesure le parcours vocal avec un module Android partage,
`core-observability`. Le module enregistre uniquement des jalons structures dans
Logcat, relies par un UUID aleatoire par invocation et horodates avec
`SystemClock.elapsedRealtime()`.

Les jalons sont : reception de l'invocation, premiere frame, microphone pret,
debut de parole, premiere transcription, resultat final, demande de volume et
volume observe. Le service et la session relevent aussi leur PSS avec un PID et
un nom de processus. Aucun texte transcrit, audio, parametre d'outil ou identite
utilisateur n'est ecrit dans les traces.

Les mesures restent dans Logcat pendant le PoC. Une base, un export persistant
ou une telemetrie distante ne sont pas introduits : ils ne sont pas necessaires
pour l'issue #15 et contrediraient la minimisation des donnees locale-first.

## Consequences

- Les delais sont comparables entre les processus et ne sont pas affectes par
  les changements d'heure murale.
- Les tests unitaires verifient le calcul des deltas et les tests instrumentes
  couvrent trente parcours determines sans crash.
- Les mesures Samsung restent des preuves manuelles reproductibles : le rapport
  de phase 0 definit la procedure et les budgets, mais ne les invente pas sans
  appareil connecte.
