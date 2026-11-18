# Modes 2 et 3 — notifications intelligentes (PR dédiée, non fusionnée)

Modes utilisateur : **Désactivé**, **Observer**, **Prudent**, **Filtrage automatique** (anciennement Personnalisé).
Les préférences sont privées au téléphone (SharedPreferences), pas de cloud.
Migration : l'ancien booléen d'observation reste en mode observation sans filtrage.
Les réglages v1 avec liste de packages passent au format v2 sans liste. Par précaution,
si une suppression définitive était autorisée sur certaines applications seulement,
elle est rétrogradée en report 1 h : le nouvel accord global doit être confirmé explicitement.

- **Prudent** : seuls les textes issus d'applications tierces non sensibles, étiquetés « a promotional advertisement » avec score >= 0,995 et marge >= 0,40, sont **reportés 1 heure** avec l'API officielle Android. Aucune suppression définitive. Un score zero-shot n'est PAS une probabilité calibrée, même à 99,5 %.
- **Filtrage automatique** : toutes les applications tierces **éligibles** sont évaluées, sans identifiant de package à fournir ni liste blanche. Le filtrage des publicités/spams, le report 1 h ou 24 h et les heures locales (22–7/0–7/9–18) sont configurables. Seuil >= 0,990 et marge >= 0,30 pour un report. Les autres labels et les notifications protégées sont conservés.
- La **suppression définitive** est une option avancée *du mode 3 uniquement*, choisie par confirmation explicite valable pour toutes les applications éligibles, seuil pub >= 0,40 et marge >= 0,08 ; spam >= 0,65 et marge >= 0,20. **Irréversible côté Android : le listener ne peut pas recréer la notification.** La suppression définitive n'est jamais activée par défaut ; sa copie et son statut restent dans l'historique local Now Brief (7 jours, jusqu'à 1 000 notifications enregistrées).

## Protections indispensables

Listener existant seulement, pas de second service. Un évènement est ignoré sans modèle installé, consentement, texte, catégorie sûre ou si une notification/application est protégée. Exclusion des appels, messages, alarmes, calendrier, banque, paiements, sécurité, OTP, messages avec réponse, notifications système, groupes et notifications non effaçables, applications système, priorité HIGH ou supérieure. Les changements de contenu, de postTime, de mode et de catégories autorisées annulent une action en attente. La vérification finale est faite sur le thread principal du listener, contre la notification **toujours active**.

Une notification reportée revient automatiquement via Android à l'expiration du délai, sauf si l'application source l'annule ; impossible de la restaurer manuellement depuis l'API publique du listener. Aucune promesse de récupération après suppression définitive. Les demandes de masquage n'ont pas valeur de confirmation; seul le callback de retrait du système confirme l'action.

## Batterie et vie privée

Pas de polling, threads d'inférence arrêtés après l'évènement. Un maximum d'une inférence/minute et 100/jour ; suspendu sous 20 % de batterie et en mode économie d'énergie. Modèle et tokenizer déchargés après chaque inférence. Le compteur IA conserve 50 métadonnées en RAM ; indépendamment, l'historique privé sur SQLite conserve au maximum 1 000 notifications reçues (titre, contenu et état) pendant 7 jours et en affiche jusqu'à 300 dans Now Brief. Rien n'est transmis au cloud. Les poids préalablement téléchargés restent sur stockage privé.

## Validation

Tests JVM des seuils, labels, applicabilité à plusieurs packages sans liste, plage horaire, migration v1→v2 sans élargissement implicite de l'autorisation de suppression définitive, et configurations incorrectes. Tests Android E2E techniques/captures de la page avec les deux modes. Il faudra encore mesurer les effets sur un Samsung physique, vérifier réellement la livraison des callbacks de retrait (y compris le report) et relire visuellement les captures. Lancer les tests et signaler tout critère non réalisé.


### Ajustement du filtrage des publicités

Le modèle NLI de notification renvoie une distribution softmax de sept étiquettes,
pas une probabilité calibrée qu'une notification soit indésirable.
L'ancien seuil de suppression de 99,9 % était pratiquement inaccessible.
En mode 3, une publicité (étiquette gagnante) est désormais candidate à
suppression si son score relatif atteint au moins 40 %, avec au moins 8 points
d'avance sur la deuxième étiquette. Le spam reste plus strict (65 %, écart 20 points).
La comparaison des labels, la somme et les valeurs finies restent vérifiées.
Les protections Android (conversation, contenu sensible, importance haute,
notification non effaçable, contrôle de l'identité et de la stabilité) sont
appliquées ensuite ; une étiquette seule n'autorise pas une suppression.
L'appareil doit être dans le mode personnalisé avec suppression explicitement
activée. Une notification déjà enregistrée reste dans l'historique ;
le nouveau seuil concerne les décisions futures, pas les alertes supprimées
ou conservées antérieurement.
