# Matrice des appareils — phase 0

Cette matrice est le référentiel des essais dépendants d'Android et de One UI
pour la phase 0. Les valeurs sont relevées sur l'appareil, jamais déduites d'un
nom commercial. Chaque exécution doit ajouter sa date, l'identifiant de build
et un lien vers sa trace dans l'issue qui l'utilise.

## Environnements de référence

| Environnement | Statut | Matériel et système | Écran / mémoire | Utilisation |
| --- | --- | --- | --- | --- |
| Samsung principal | Partiellement relevé | Samsung Galaxy S26 ; Android, niveau de correctif et One UI : à relever | Résolution, densité et RAM : à relever | Référence obligatoire pour le rôle assistant et l'invocation Power |
| Émulateur AOSP | Défini | Android Studio : image `Google APIs`, API 35, ABI `x86_64`, AVD `Pixel 8` | 1080 × 2400 px, 420 dpi, 8 Go RAM, stockage 8 Go | Régressions fonctionnelles ne dépendant pas de One UI |
| Appareil Android non-Samsung | Non disponible | Aucun appareil physique supplémentaire n'est actuellement disponible | S/O | À ajouter dès qu'un Pixel ou un autre appareil est disponible ; l'émulateur AOSP ne remplace pas cette validation matérielle |

Le projet cible Android 35 et exige Android 26 au minimum. L'émulateur AOSP est
donc défini sur API 35 afin de couvrir les changements de comportement les plus
récents, sans prétendre reproduire l'intégration constructeur Samsung.

## Relevé obligatoire du Samsung principal

Avant de considérer la matrice complète, connecter le téléphone en USB avec le
débogage activé puis renseigner la première ligne avec la sortie suivante :

```bash
adb shell getprop ro.product.manufacturer
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.version.security_patch
adb shell getprop ro.build.display.id
adb shell wm size
adb shell wm density
adb shell cat /proc/meminfo | head -n 1
```

Relever aussi dans **Paramètres > À propos du téléphone > Informations sur le
logiciel** la version One UI exacte. La version One UI n'est pas exposée de
façon stable par une propriété Android publique et doit donc être relevée dans
l'interface Samsung. Consigner le résultat ci-dessous :

| Champ | Valeur relevée | Date | Preuve / lien issue |
| --- | --- | --- | --- |
| Modèle Samsung | Samsung Galaxy S26 | 2026-07-16 | Déclaration du propriétaire |
| Android / niveau API | À renseigner | — | — |
| Correctif de sécurité | À renseigner | — | — |
| One UI | À renseigner | — | — |
| Build | À renseigner | — | — |
| Résolution / densité | À renseigner | — | — |
| RAM totale | À renseigner | — | — |

## Configuration Samsung à relever

| Paramètre | Valeur à documenter | Effet à vérifier |
| --- | --- | --- |
| Bouton latéral — appui long | Assistant sélectionné et chemin exact dans One UI | L'assistant Android par défaut est invoqué sans ouvrir un écran Samsung concurrent |
| Bouton latéral — double appui | Action configurée | Absence de conflit avec les essais d'invocation |
| Assistant numérique par défaut | Application sélectionnée | Persistance après arrêt forcé et redémarrage |
| Batterie de l'application | `Sans restriction`, `Optimisée` ou `Restreinte` | Impact sur le service, la session et la reprise après veille |
| Batterie adaptative / économie d'énergie | Activée ou désactivée | Comportement quand l'économie d'énergie est active |
| Autorisations microphone et notifications | État exact | L'état ne doit pas être confondu avec le rôle assistant |

Les essais ne doivent ni désactiver les protections Android ni contourner
l'écran verrouillé. Toute différence observée entre One UI et AOSP doit être
jointe aux issues #10 à #16 qui la rencontrent.

## Scénarios critiques

| ID | Contexte initial | Action | Résultat à consigner |
| --- | --- | --- | --- |
| DM-01 | Accueil déverrouillé | Invoquer l'assistant via le bouton latéral | Rôle utilisé, première surface affichée, logs et délai perçu |
| DM-02 | Application tierce au premier plan | Invoquer l'assistant via le bouton latéral | Application visible ou masquée, session reçue, conflit éventuel |
| DM-03 | Écran verrouillé | Invoquer l'assistant | Comportement système, informations affichées et actions autorisées/refusées |
| DM-04 | Téléphone redémarré puis déverrouillé | Vérifier le rôle puis invoquer | Persistance du rôle, disponibilité du service et éventuelle intervention utilisateur |
| DM-05 | Économie d'énergie active | Invoquer l'assistant | Invocation, disponibilité audio et restrictions signalées |
| DM-06 | Batterie de l'application en mode Optimisée puis Sans restriction | Répéter DM-01 après veille | Différence de fiabilité, latence et reprise du processus |

Pour chaque scénario, consigner l'environnement, la variante (`core` ou
`powerUser`), le résultat, la date et un extrait `logcat` expurgé. Les essais
sur écran verrouillé suivent les règles de confidentialité : aucune donnée
personnelle complète ne doit être visible et aucune action importante ne doit
être exécutée.

## État d'avancement

- [x] Environnement AOSP de référence défini.
- [x] Scénarios critiques définis.
- [ ] Samsung principal identifié avec ses valeurs exactes.
- [ ] Appareil Android non-Samsung physique ajouté si disponible.

L'issue #8 ne peut être fermée qu'après le relevé du Samsung principal ; cette
information dépend du téléphone physique et n'est pas disponible dans le dépôt.
