# UX et design du produit existant

## Direction de la remise en état

**CIBLE** : une application d'assistance calme, lisible et rapide à utiliser sur Android.
Conserver la base Lumina et ses tokens ; améliorer la structure des écrans réels. L'autorisation
de refonte ne justifie pas une bibliothèque graphique supplémentaire ou un prototype HTML livré
à la place des écrans Compose. Les références Stitch montrent une intention, pas un parcours fonctionnel.

Sources : [design Compose](../design/compose-design-system.md),
[tokens réels](../../android/core-ui/src/main/kotlin/fr/loevan/jeancalcul/ui/DesignTokens.kt),
[références disponibles](../design/stitch-reference/MANIFEST.md).
Certaines références nommées par d'anciens documents ne sont pas présentes ; vérifier les fichiers
avant de demander à l'agent de les suivre. Ne pas inventer une capture d'un écran jamais exécuté.

## Fondations

**UI-01 — Tokens et rôles.** Material 3 pour composants/interactions, rôles de thème pour couleurs,
typographie en sp, tokens de spacing et formes centralisés. Palette actuelle sombre : fond #101415,
surface #181C1D, texte #E0E3E4 ; bleu secondaire, violet d'activité. Respecter les variantes claires.
Pas de hex par écran ni nouvelle palette ad hoc ; une évolution durable modifie tokens et tests.

**UI-02 — Accessibilité.** Contrôles d'au moins 48 dp, espacement suffisant, labels TalkBack
compréhensibles, ordre de lecture et focus cohérents. États annoncés par texte/sémantique en plus
de la couleur. Corps/placeholder avec contraste cible 4,5:1, grands textes 3:1. Police 1,3× et 2×
sans disparition d'action critique ; les longs noms de profil et erreurs s'enroulent ou sont tronqués
avec accès à leur texte complet. Ne pas forcer une largeur ou hauteur fixe au contenu textuel.

**UI-03 — Effets.** Verre/halo indiquent une zone active, pas chaque carte. Aucun blur réel
de l'arrière-plan Android ne doit être promis par un effet tonal. ReduceMotion supprime les boucles ;
highContrast/no-blur gardent profondeur et lisibilité. Une animation ne bloque ni affichage de texte
ni annulation. Les captures utilisent progression/amplitude déterministes.

## Navigation et fenêtres

**UI-04 — Navigation native.** Quatre destinations actuelles : Assistant, Conversations, Audit,
Réglages. Navigation Material compacte sur téléphone, rail/drawer si largeur le justifie.
Status/navigation bars, cutouts et IME doivent être pris en compte par les insets, sans padding
bottom=80 dp supposé suffisant sur tout appareil. Le bouton Retour ferme d'abord clavier/feuille
selon les conventions Android. Ne pas fermer silencieusement le tour réseau quand l'utilisateur
change seulement d'onglet. Pas de dépendance de navigation ajoutée sans besoin démontré.

## Assistant et première utilisation

**UI-05 — Mise en route claire.** Afficher séparément : rôle assistant, micro, moteur de réponse.
La première action utile est « Configurer un assistant » ou « Ouvrir une conversation » selon état.
Le rôle Power et le micro sont optionnels pour le texte. Après configuration, éviter de présenter
indéfiniment l'onboarding comme l'accueil principal ; donner accès à la conversation et au statut.
Une erreur de rôle mène aux réglages officiels avec explication concrète.

## Conversation

**UI-06 — Composition.** En haut : titre de conversation et profil/destination active ; actions
Nouvelle et historique accessibles. Au centre : messages scrollables occupant l'espace disponible.
En bas : composeur ancré au-dessus de l'IME avec Envoyer/Arrêter clairement distincts. Exporter et
Supprimer sont des actions secondaires du menu, pas deux gros boutons permanents sous chaque draft.

Schéma de hiérarchie indicatif, indépendant des pixels :

```text
Conversation                     Nouvelle / menu
Profil choisi · traitement distant/local
──────────────────────────────────────────────
Historique et message(s)
  Réponse en cours / action à confirmer
  Erreur locale au tour, si nécessaire
                         Derniers messages ↓
──────────────────────────────────────────────
Votre message…                         Envoyer
```

**UI-07 — État vide et streaming.** Sans profil : explication courte et CTA réglages ; avec profil :
invitation à écrire, pas de réponse fictive. Pendant streaming, texte progressif et action Arrêter.
Scroll automatique seulement si le lecteur était déjà en bas ; s'il consulte plus haut, conserver
sa position et proposer de rejoindre la réponse. Une première réponse longue ne doit pas repousser
le composeur hors écran. Les états completed/failed/streaming sont traduits, jamais enums brutes.

**UI-08 — Erreur et récupération.** Une erreur d'envoi est attachée au tour et propose Réessayer
ou Configurer/Reconnecter selon cause. Une erreur d'export dit « Export impossible » uniquement pour
l'export. Un arrêt volontaire dit « Réponse arrêtée ». Ne pas afficher deux fois la même erreur en
bannière et bulle sans valeur supplémentaire. Le draft nouveau reste éditable pendant une réponse,
mais un second envoi reste bloqué par la règle d'exclusion CONV-05.

## Outils et confirmation

**UI-09 — Décision protégée.** Une carte/feuille de confirmation affiche action précise, effet,
profil/origine, paramètres compréhensibles et expiration. Confirmer/Refuser ont des libellés explicites.
Le bouton confirme seulement après décision de l'utilisateur. Fermeture/retour/refus n'exécutent rien.
Biométrie utilise l'UI OS. Le statut « Exécution » apparaît après reçu valide, pas dès la proposition.

## Réglages

**UI-10 — Hiérarchie.** Première section : assistant actif et état de connexion réel. Séparer
les connexions (endpoint/secret), profils modèles, profils agents, voix, apparence et confidentialité.
Les fonctions avancées restent accessibles sans noyer le premier parcours. Le bouton Activer montre
le profil qui va recevoir les prochains échanges et résout l'exclusivité modèle/agent.

Un secret saisi n'est pas réaffiché ; montrer « enregistré », action remplacer/supprimer. Tests de
connexion : repos, en cours, succès daté, échec avec récupération. « Connexion vérifiée » ne signifie
pas « toutes les capacités testées ». Le chemin direct ChatGPT non validé ne porte pas une promesse
de gratuité, d'inclusion universelle ou de fonctionnement sans PC non démontrée.

## Audit et diagnostic

**UI-11 — Explicabilité.** Liste filtrable par période/outil/résultat, labels français, détail
action/décision/autorisation/exécution/rejeu. Afficher refus et échecs aussi clairement que succès.
État vide utile et erreur de chargement récupérable. Export et purge explicitent leur portée ; la
purge a une confirmation. Aucun secret ou texte privé de demande dans un aperçu de diagnostic.

## Preuves visuelles

**UI-12 — Matrice de rendu.** Capturer sur Android, avec commit et état : téléphone compact et
large, paysage ou taille étendue si supportée, sombre/clair, police 1,3× et contrôle à 2×, IME ouvert,
empty/streaming/error/confirmation, effets réduits. Les tests core-ui ne prouvent pas à eux seuls
l'intégration app. Vérifier aussi TalkBack et retour. Rapporter explicitement les tailles non exécutées.

Priorité d'arbitrage : action compréhensible, contenu lisible, états exacts, stabilité, puis effets.
La refonte des écrans ne doit pas modifier la politique pour réduire le nombre de confirmations.
