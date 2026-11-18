# Modes 2 et 3 — notifications intelligentes (PR dédiée, non fusionnée)

Modes utilisateur : **Désactivé**, **Observer**, **Prudent**, **Personnalisé**.
Les préférences sont privées au téléphone (SharedPreferences), pas de cloud.
Migration : l'ancien booléen d'observation reste en mode observation sans filtrage.

- **Prudent** : seuls les textes issus d'applications tierces non sensibles, étiquetés « a promotional advertisement » avec score >= 0,995 et marge >= 0,40, sont **reportés 1 heure** avec l'API officielle Android. Aucune suppression définitive. Un score zero-shot n'est PAS une probabilité calibrée, même à 99,5 %.
- **Personnalisé** : aucune application n'est filtrée par défaut. Il faut ajouter explicitement son identifiant puis choisir FILTER; KEEP reste prioritaire. Les étiquettes publicité/spam, le report 1 h ou 24 h, et les heures locales (22–7/0–7/9–18) sont configurables. Seuil >= 0,990 et marge >= 0,30 pour un report. Les autres labels sont conservés.
- La **suppression définitive** est une option avancée *personnalisée uniquement*, choisie par confirmation explicite, seuil >= 0,999 et marge >= 0,50. **Irréversible : le listener ne peut pas recréer une notification d'une autre application.** La suppression définitive n'est jamais activée par défaut.

## Protections indispensables

Listener existant seulement, pas de second service. Un évènement est ignoré sans modèle installé, consentement, texte, catégorie sûre ou si une application est protégée. Exclusion des appels, messages, alarmes, calendrier, banque, paiements, sécurité, OTP, messages avec réponse, notifications système, groupes et notifications non effaçables, applications système, priorité HIGH ou supérieure. Les changements de contenu, de postTime, de mode et de règles annulent une action en attente. La vérification finale est faite sur le thread principal du listener, contre la notification **toujours active**.

Une notification reportée revient automatiquement via Android à l'expiration du délai, sauf si l'application source l'annule ; impossible de la restaurer manuellement depuis l'API publique du listener. Aucune promesse de récupération après suppression définitive. Les demandes de masquage n'ont pas valeur de confirmation; seul le callback de retrait du système confirme l'action.

## Batterie et vie privée

Pas de polling, threads d'inférence arrêtés après l'évènement. Un maximum d'une inférence/minute et 100/jour ; suspendu sous 20 % de batterie et en mode économie d'énergie. Modèle et tokenizer déchargés après chaque inférence. Historique des seules catégories, scores, actions et noms de packages : maximum 50 lignes **en RAM**, pas de texte ou de clé de notification persistés ni exportés. Les poids préalablement téléchargés restent sur stockage privé.

## Validation

Tests JVM des seuils, labels, règles applicatives, plage horaire, migrations et configurations incorrectes. Tests Android E2E techniques/captures de la page avec les deux modes. Il faudra encore mesurer les effets sur un Samsung physique, vérifier réellement la livraison des callbacks de retrait (y compris le report) et relire visuellement les captures. Lancer les tests et signaler tout critère non réalisé.
