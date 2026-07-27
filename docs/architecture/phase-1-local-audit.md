# Journal d'audit local - phase 1

Issue de reference : #32.

## Decision

Chaque tentative d'outil utilise son `actionId` comme identifiant d'audit. Les evenements emis par le
Policy Engine et le registre d'outils sont fusionnes sequentiellement dans un seul `AuditEvent`. Il contient
l'origine, l'outil et sa version, le risque, la decision, l'approbation, le resultat, la duree et une erreur
eventuelle. La session vocale associe aussi l'identifiant de sa conversation locale a chaque tentative.

`PersistentAuditLogger` est la frontiere entre les callbacks synchrones du domaine et Room. Les callbacks
ne bloquent pas l'execution d'une action : ils placent une mise a jour dans une file locale ordonnee. Une
erreur d'audit n'autorise jamais un chemin alternatif autour du registre ou du Policy Engine.

## Persistance et migration

La table `audit_events` appartient a la base `jean_calcul.db`. `MIGRATION_1_2` la cree avec des index sur la
date, l'outil et le resultat. Les conversations du schema 1 sont conservees. Aucun fallback destructif n'est
active.

Le DAO applique les filtres de date, d'outil exact et de resultat avant `LIMIT`/`OFFSET`. L'ecran charge les
evenements par pages de 25. La retention est stockee dans DataStore, vaut 30 jours par defaut et peut etre
reglee a 7, 30 ou 90 jours dans l'interface. La purge est appliquee apres chaque ecriture, lors d'un changement
de retention et sur demande explicite.

## Redaction et export

`AuditRedactor` traite recursivement les parametres JSON avant Room. Les champs dont le nom indique une cle,
un token, un mot de passe, un secret ou un OTP sont remplaces par `[REDACTED]`. Les motifs d'autorisation,
cles API et JWT sont aussi retires des textes, resultats et erreurs.

L'export JSON repasse tous les champs persistants par la redaction. Il ne lit jamais `SecretStore` et ne
contient ni exception brute ni cause technique. Les donnees externes restent des donnees non fiables.

## Cycle de vie des donnees

- Finalite : expliquer les propositions, decisions de securite et executions locales.
- Conservation : 30 jours par defaut, configurable entre 1 et 3650 jours par le contrat de donnees.
- Suppression : purge automatique par date ou purge manuelle depuis l'ecran Audit.
- Export : JSON versionne et expurge, limite aux filtres actifs.
- Fournisseurs autorises : aucun fournisseur n'accede directement au journal.
- Sensibilite : historique local d'actions potentiellement personnel ; sauvegarde Android desactivee.

## Validation

Les tests JVM couvrent la fusion des decisions, refus, annulations, erreurs, la double redaction et l'export.
Les tests Room instrumentes couvrent les filtres, la pagination, la purge et la migration 1 vers 2.
