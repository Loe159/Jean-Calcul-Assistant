---
name: jc-verify
description: "Qualifier une mission ou le parcours intégré Jean Calcul avec preuves exactes : tests, builds, revue de critères et rapport Samsung. Ne confond pas compilation, mocks et essais physiques."
---

# jc-verify

## Entrée

Lire la mission assignée, [la validation](../../../docs/agents/verification.md),
[la qualité](../../../docs/specs/07-quality-and-acceptance.md), le diff et les résultats existants.
Vérifier les commits réels des dépendances, pas uniquement les statuts déclarés.

## Niveau de preuve

Compilation, tests JVM, instrumentation, service réel et essai Samsung sont séparés.
Un faux token dans MockWebServer ne prouve pas l'accès abonnement ; un test APK assemblé
n'est pas un test exécuté ; KEYCODE_ASSIST ne remplace pas Power physique ou un appel entrant.

## Exécuter

Choisir les commandes de la matrice selon le diff ; lancer l'intégration complète au jalon
JC-11. Conserver exit codes et chemins des rapports. Un environnement absent produit NON EXÉCUTÉ,
pas PASS et pas nécessairement un échec du produit. Ne pas affaiblir les assertions pour passer.

Relire les frontières sensibles : annulation/cleanup, concurrence, profil, autorisation, secrets,
migration et export. Exiger un scénario reproductible et une gravité pour chaque défaut de revue.
Une correction limitée peut revenir à sa mission source ; éviter un chantier futur non demandé.

Pour JC-12, utiliser un appareil autorisé et [le modèle appareil](../../../docs/agents/templates/device-report.md).
Identifier commit/modèle/OS/réseau, jouer vingt tours/vingt outils, distinguer refus attendu et
succès exécuté, restaurer les réglages. Le consentement, biométrie et appel sont réels ou non exécutés.

## Sortie

Remplir [le résultat](../../../docs/agents/templates/result.md), exigence par exigence : PASS,
FAIL ou NON EXÉCUTÉ, preuve et limite. Ne pas fermer une issue physique sans essai et ne pas
publier de release. Aucun secret ni transcription personnelle dans les rapports.
