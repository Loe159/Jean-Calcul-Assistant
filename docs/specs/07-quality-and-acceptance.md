# Qualité et critères de qualification

**QA-01 — Niveaux de preuve.** Séparer compilation, tests unitaires, tests instrumentés,
service réel et essai physique Samsung. Un APK androidTest compilé n'est pas un test exécuté.
Une capture preview n'est pas une invocation Power réelle. Chaque résultat donne commit et commande.

**QA-02 — Régression.** Toute correction d'un bug de cycle de vie, politique ou duplication
ajoute un test qui échoue avec l'ancien comportement. Tester les effets observables : appels,
messages persistés, statut, autorisation et cleanup, pas le nom d'une méthode ou une copie de l'algo.

**QA-03 — Robustesse.** Scénarios minimum : double envoi ; stop avant premier delta, en streaming
et en confirmation ; EOF ; erreur auth/réseau/quota ; profil supprimé ; permission révoquée ;
verrouillage entre proposition et exécution ; rotation ; mort processus ; export/suppression pendant tour.
Les codes d'erreurs réels sont normalisés, les scénarios concurrents utilisent un scheduler contrôlé.

**QA-04 — Confidentialité.** Injecter de faux secrets distinctifs dans erreurs, texte, résultats
d'outils et callback ; ils ne doivent apparaître ni dans audit/export expurgé ni dans traces. Test
malveillant : un résultat d'outil demande d'ignorer les confirmations ; aucune élévation de droit.

**QA-05 — Performance.** Réutiliser les [budgets phase 1 mesurables](../observability/phase-0-performance-stability.md)
et jalons de PerformanceTrace. Ne pas mélanger la cible future <300 ms avec un résultat observé.
Mesurer P50/P95 avec taille d'échantillon et conditions ; ne pas inventer des percentiles de production
à partir de six essais. Pas d'écoute continue ou polling non borné ajouté pour masquer la latence.

**QA-06 — Parcours réel.** Sur appareil de référence, authentifier le backend autorisé, envoyer
texte, recevoir deltas, lire batterie réelle, proposer lampe en STRICT, confirmer/refuser et consulter
l'audit. Rejouer par voix dans la même conversation. Vingt tours et vingt appels d'outils terminent
sans crash, doublon ni corruption. Répartir succès/refus/erreurs et noter les cas un par un.

**QA-07 — CI et release.** Builds Core/Power User, tests, ktlint, Detekt, lint, compilation baseline
et vérification docs doivent être verts avant merge. Les critères physiques non exécutés restent
ouverts ; une release ne peut se dire « phase 1B validée » sans eux. La signature de release et les
comptes/permissions de publication sont une opération séparée et autorisée.

**QA-08 — Rapport reproductible.** Résultat avec exigences PASS/FAIL/NON EXÉCUTÉ, environnement,
versions, commandes, preuve expurgée et limites. Un défaut hors mission est décrit avec scénario
et impact sans être caché ni transformer la mission en refonte illimitée.

## Table de qualification à renseigner au moment du test

| Scénario | Exigences | Preuve requise |
| --- | --- | --- |
| Modèle direct et agent | CONV-01/02/06/14 | Tests runtime + requêtes fake vérifiées |
| Stop et retry | CONV-05/07/08/09/10 | Test ordonnanceur + contrôle état Room |
| Rotation/processus | CONV-12, DATA-03 | Instrumentation et relance réelle |
| Batterie puis lampe | POL-01 à POL-11 | Test faux adaptateur puis appareil réel |
| Refus/expiration/révocation | POL-06/07/08, AUTH-06 | Aucun effet, audit et récupération |
| Texte et voix | VOICE-02/03/06/07 | Tests communs puis scénario Samsung |
| OAuth direct | AUTH-01/03/04/05 | Documentation officielle + consentement/inférence réels |
| Compagnon | NET-01 à NET-05 | Suite Python/Kotlin + coupure/redémarrage |
| Export et migration | DATA-04/06/07/08 | Fixture ancienne + snapshot expurgé |
| UX | UI-01 à UI-12 | Captures Android, IME, thèmes, police, TalkBack |

Voir [les commandes et procédures](../agents/verification.md). Cette table décrit des preuves
attendues ; aucune ligne n'est préremplie PASS par cette livraison documentaire.
