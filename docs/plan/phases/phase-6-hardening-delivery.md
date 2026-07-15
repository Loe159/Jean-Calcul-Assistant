# Phase 6 — Durcissement, performance et livraison

## Objectif

Rendre l’application et le Gateway fiables, auditables, performants, distribuables et récupérables après erreur.

Cette phase est une finalisation. Les exigences de sécurité, tests et observabilité doivent toutefois être appliquées dès la phase 0.

## Axes

- threat model ;
- durcissement des actions ;
- sécurité réseau ;
- sécurité locale ;
- tests d’injection ;
- performance ;
- batterie ;
- compatibilité appareils ;
- CI/CD ;
- distribution ;
- support et récupération.

## Travaux

### Threat model

Menaces à documenter :

- agent compromis ;
- skill malveillant ;
- prompt injection ;
- notification ou mail hostile ;
- Gateway exposé ;
- vol du téléphone ;
- clé extraite ;
- appareil appairé compromis ;
- rejeu ;
- action écran verrouillé ;
- fuite dans les logs ;
- dépendance compromise.

Pour chaque menace : actif, attaquant, scénario, impact, prévention, détection, récupération et test.

### Actions

- validation stricte ;
- propriétés inconnues refusées ;
- plages et listes autorisées ;
- expiration ;
- idempotence ;
- état avant/après ;
- biométrie ;
- verrouillage de session ;
- politique écran verrouillé ;
- audit complet.

### Communications

- TLS ;
- identité d’appareil ;
- rotation ;
- révocation ;
- tokens courts ;
- nonce et anti-rejeu ;
- limite de débit ;
- limite de payload ;
- validation de protocole ;
- fermeture des connexions suspectes.

### Données locales

- Keystore ;
- chiffrement ciblé ;
- sauvegardes chiffrées ;
- exclusions des sauvegardes Android ;
- purge et rétention ;
- suppression de compte ;
- suppression sélective ;
- protection des captures sur écrans sensibles.

### Tests d’injection

- notification demandant une action ;
- mail demandant de révéler un secret ;
- page web imitant un message système ;
- skill modifiant ses permissions ;
- contenu demandant d’ignorer une confirmation ;
- pièce jointe malveillante ;
- résultat d’outil contenant une instruction.

### Performance

Budgets initiaux :

- première représentation visuelle après création de session : cible < 300 ms ;
- invocation complète visible : cible < 1 s sur l’appareil de référence ;
- action locale simple : cible < 500 ms ;
- interface à 60 FPS sur appareil cible ;
- aucun réseau ou base lourde sur thread principal.

Outils : Baseline Profiles, Macrobenchmark, traces, StrictMode en développement, profils CPU/mémoire et tests de fuite.

### Batterie

- pas d’écoute continue ;
- WebSocket seulement quand nécessaire ;
- WorkManager pour les traitements persistants ;
- batch ;
- classification locale légère ;
- respect économie d’énergie ;
- diagnostic Samsung ;
- statistiques de consommation.

### Matrice de tests

Versions : minimum supporté, intermédiaire, actuelle et preview séparée.

Appareils : Samsung cible, Samsung secondaire, Pixel/AOSP, émulateur, tablette/pliable si supporté.

Scénarios : verrouillage, économie d’énergie, réseau lent, changement réseau, faible mémoire, redémarrage, permission révoquée, Gateway arrêté, LLM indisponible et migration de base.

### CI/CD

Android : build, tests, lint, Detekt, ktlint, tests instrumentés, dépendances, SBOM et signature.

Gateway : Ruff, mypy, Pytest, intégration, scan conteneur, SBOM, signature et migrations.

### Distribution

- licence open source ;
- politique de confidentialité ;
- documentation des permissions ;
- builds signés ;
- changelog ;
- migrations ;
- canaux bêta/stable ;
- variante Core ;
- variante Power User ;
- guide d’auto-hébergement.

### Support

- Doctor ;
- export de logs expurgés ;
- rapport de bug ;
- versions des composants ;
- diagnostic réseau ;
- statut permissions ;
- procédures de sauvegarde, restauration et révocation.

## Difficultés

- comportements constructeurs ;
- compromis batterie/temps réel ;
- tests d’agents non déterministes ;
- migrations de données personnelles ;
- publication avec permissions sensibles ;
- compatibilité de dépendances ;
- incidents sans fuite de données dans les diagnostics.

## Contraintes

- aucun diagnostic ne contient de secret ;
- aucune mise à jour ne détruit les données sans migration ou avertissement ;
- la variante Core reste indépendante de l’accessibilité ;
- les performances sont mesurées sur appareils réels ;
- chaque incident critique possède une procédure de récupération.

## Critères de sortie

- threat model complet ;
- tests d’injection ;
- audit de permissions ;
- révocation fonctionnelle ;
- budgets mesurés ;
- consommation acceptable ;
- migrations testées ;
- builds Core et Power User ;
- documentation d’installation ;
- sauvegarde/restauration ;
- Doctor et rapports expurgés.
