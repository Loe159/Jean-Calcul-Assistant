# Phases futures : spécifications de cadrage

**DIFFÉRÉ.** Ces domaines sont documentés pour préserver l'architecture et préparer de futures
missions. Leur implémentation est bloquée par les critères de sortie 1B. Aucun endpoint, scope
fournisseur, délai de rétention ou garantie de synchronisation non décidé n'est considéré acquis.
Avant codage, une issue doit fixer modèle de données, contrat, migration, permission et tests.

## Phase 2 — Notifications, tâches et calendrier

Référence : [plan phase 2](../plan/phases/phase-2-notifications-tasks-calendar.md).

### Notifications

Entrée : notification publiée, mise à jour ou retirée. Le listener observe après publication ; il
ne bloque pas la notification avant affichage par Android. Identité stable à définir depuis les
champs système, version de normalisation et source conservées. Une mise à jour remplace la bonne
entrée sans créer une nouvelle tâche à chaque modification de texte.

Chaîne : normalisation minimale → détection sensible → exclusion utilisateur → règles locales →
classification optionnelle autorisée → inbox → proposition. Les exclusions gagnent toujours sur
le classificateur. OTP, banque et sécurité ne partent pas vers le distant par défaut. Le contenu
est encapsulé comme donnée externe ; sa demande de « désactiver les confirmations » n'a aucun effet.

UX : vues à traiter/importantes/résumées/masquées, source et raison, restauration, exclusion d'app,
état permission. Archiver localement et supprimer la notification Android sont deux actions distinctes.
Pas de règle de suppression créée silencieusement par apprentissage.

Tests d'acceptation : notification doublonnée/mise à jour/groupée, contenu sensible, injection,
permission révoquée, restauration et provenance d'une tâche. La rétention et l'identité système
exacte sont des décisions de l'issue d'entrée, pas des suppositions de l'agent.

### Tâches

Modèle cible : ID, titre, description, statut, priorité, échéance/fuseau éventuel, source, projet,
tags et timestamps. Le LocalTaskStore MVP ne suffit pas à prouver ce modèle. Prévoir migration
des tâches existantes avant remplacement. Une source notification ne donne aucune permission
calendrier. Completion et suppression sont distinguées ; récurrence et conflits exigent une spec dédiée.

Tests : création idempotente depuis notification, report, completion, suppression, recherche,
reprise après mort processus et absence de doublon lors d'un retry d'outil.

### Calendrier et résumés

Choisir calendrier cible et permissions minimales. Construire un brouillon, détecter ambiguïté
date/fuseau/doublon/conflit, puis confirmer les champs exacts avant écriture Calendar Provider.
Une date ambiguë n'est pas remplacée silencieusement par le jour courant. Distinguer heures locales,
UTC et événements toute la journée ; tester changements d'heure Europe/Paris.

WorkManager planifie les résumés avec tolérance aux restrictions batterie. Aucune promesse de
livraison à la seconde exacte. Un retry de worker ne doit pas produire deux notifications de résumé.
Sortie de phase : inbox, règles prioritaires, tâches réelles, calendrier confirmé et restauration testés.

## Phase 3 — Gateway et Hermes

Référence : [plan phase 3](../plan/phases/phase-3-gateway-hermes.md).

Le Gateway est un nouveau service auto-hébergé, pas un renommage du compagnon Python actuel.
Technologies prévues : Python 3.12+, FastAPI/Pydantic, stockage migré, WebSocket versionné,
adaptateurs AgentBackend, conteneur et observabilité. Les choix SQLite/Postgres et queue doivent
être fixés par l'issue avant génération d'infrastructure.

Contrats à spécifier : health/readiness/version ; appairage temporaire ; identité appareil,
rotation/révocation ; création/reprise de session ; événement avec version/ID/session/séquence/
timestamp/correlation/payload strict ; heartbeat ; reprise avec curseur ; job asynchrone persistant.
La liste d'événements du plan est un vocabulaire cible, pas un protocole v2 compagnon compatible.

Confiance : TLS hors loopback, appareil révocable, moindre privilège de l'adaptateur, aucune clé
mobile transmise à Hermes et aucun outil Android exécuté par le Gateway. Android peut refuser même
une proposition prévalidée par le serveur. Les skills/shell côté serveur sont sandboxés par workspace.

Jobs : ID et état persistés, progression, délai/expiration, annulation, résultat et reprise serveur.
Une requête HTTP n'est pas maintenue ouverte pendant toute la durée d'un job. Livraison mobile
en arrière-plan et FOSS/Play font l'objet d'un choix documenté, pas d'une dépendance FCM imposée.

Tests : coupure mobile/reconnexion/doublon, token révoqué pendant run, redémarrage serveur, deux
appareils, saturation queue, outil refusé sur Android, timeout et isolation Hermes. Version réelle
Hermes vérifiée ; aucun endpoint supposé à partir de son nom. Sortie : installation reproductible,
appairage, reprise, outil confirmé et job persistant démontrés.

## Phase 4 — Skills produit, mémoire et LifeOS

Référence : [plan phase 4](../plan/phases/phase-4-skills-lifeos.md).

Les skills de développement du présent dépôt ne sont pas les extensions installables du produit.
Format produit prévu : skill.yaml + SKILL.md, ID/version/auteur/licence, moteurs compatibles,
permissions/outils/entrées/sorties, besoins réseau/secrets, hash/signature et migrations.
Le manifeste est une demande de droits ; seul le sous-ensemble accordé est effectif.

Installation/mise à jour : inspection, comparaison des droits, consentement à toute élévation,
activation/désactivation, rollback et trace. Un SKILL.md hostile ne remplace aucune instruction
de l'application. Les scripts ne s'exécutent pas sur Android simplement parce qu'ils sont présents.

Import LifeOS : analyser dépendances CLI/scripts et produire compatible, Gateway seulement,
adaptation requise, incompatible ou dangereux. Rien n'est installé/exécuté par l'analyse. TELOS
reste éditable/exportable avec visibilité par agent ; LifeOS est optionnel.

Mémoire : source/date/confiance/portée/rétention/agents autorisés/preuve et statut confirmé ou inféré.
Une inférence n'est jamais promue automatiquement en fait. Modification/suppression et provenance
sont accessibles ; champs privés jamais injectés au profil non autorisé. Les hooks ne créent pas
une nouvelle voie d'action critique sans politique/confirmation.

Tests : élévation de permission, mise à jour malveillante, injection, exfiltration, rollback,
suppression de mémoire et visibilité par profil. Choix de sandbox, signature et indexation à décider
avant implémentation. Sortie : registre et permissions réels, import expliqué, mémoire traçable.

## Phase 5 — Dashboard, email et automatisations

Référence : [plan phase 5](../plan/phases/phase-5-dashboard-email.md).

Aujourd'hui agrège les décisions utiles avec source/raison/confiance et action explicite ; il ne
devient pas une grille de statistiques fictives. État vide fonctionne sans compte email/Gateway.
Les informations sensibles sur écran verrouillé sont réduites selon la politique.

EmailProvider : commencer par un fournisseur choisi dans l'issue, scopes minimaux vérifiés auprès
de sa documentation officielle, curseur de synchronisation et stratégie d'expiration. Contenu
complet/HTML/pièces jointes chargés à la demande et considérés non fiables. Un brouillon ne s'envoie
jamais seul ; destinataires, contenu et pièces jointes font partie de la confirmation, biométrie
selon plancher de risque. Gmail, Graph et IMAP ne partagent pas arbitrairement le même OAuth.

Recherche : plein texte local avec filtres/provenance avant embeddings. Suppression d'un élément
supprime ou invalide ses index. Multi-comptes : identité accountId + remoteId pour éviter collisions.

Automatisation : déclencheur, conditions, proposition/action, niveau de confirmation, canal de
résultat ; activable/désactivable et auditée. Un changement de règle ne réautorise pas tous les outils.
Sauvegarde chiffrée, synchronisation et résolution de conflit exigent des contrats distincts ; le
partage JSON actuel n'est pas cette sauvegarde.

Tests : mail hostile, cursor expiré, doublon multi-compte, brouillon modifié après approbation,
destinataire ambigu, pièce jointe absente, automatisation désactivée et reprise import/export.
Sortie : un provider complet, brief sourcé, brouillons sûrs, recherche et restauration démontrés.

## Phase 6 — Durcissement et distribution

Référence : [plan phase 6](../plan/phases/phase-6-hardening-delivery.md).

Sécurité/qualité commencent dès les missions actuelles. Finalisation : threat model par actif,
attaquant, scénario, prévention, détection, récupération et test ; dépendances/SBOM ; signature,
builds reproductibles autant que possible, licence, politique de confidentialité et canaux de release.

Prévoir restauration des anciennes données, rollback compatible ou explication irréversibilité,
révocation appareil/compte, diagnostic expurgé et incident sans fuite. Core reste indépendant de
l'accessibilité ; distribution Power User et permissions font l'objet d'une décision explicite.

Matrice : API minimale, intermédiaire, courante ; Samsung de référence, AOSP, tailles supportées ;
réseau lent, veille, batterie, mémoire, reboot, permissions et migration. Mesures réelles de latence,
FPS, PSS et batterie ; un seuil futur n'est pas une mesure. Pas d'annonce de validation Samsung
par simple passage d'un émulateur.

Sortie : contrôles automatisés verts, critères physiques consignés, sauvegarde/restauration,
révocation, diagnostic et procédure de mise à jour testés. Les comptes de publication, signature
et release exigent une autorisation de livraison distincte du travail de code.
