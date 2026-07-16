# Matrice des appareils — phase 0

Cette matrice est le référentiel des essais dépendants d'Android et de One UI
pour la phase 0. Les valeurs sont relevées sur l'appareil, jamais déduites d'un
nom commercial. Chaque exécution doit ajouter sa date, l'identifiant de build
et un lien vers sa trace dans l'issue qui l'utilise.

## Environnements de référence

| Environnement | Statut | Matériel et système | Écran / mémoire | Utilisation |
| --- | --- | --- | --- | --- |
| Samsung principal | Relevé le 2026-07-16 | Samsung Galaxy S26 5G Dual SIM (`SM-S942B/DS`), Android 16 / One UI 8.5, build `BP4A.251205.006.S942BXXS3AZF1` | 2340 × 1080 px, 120 Hz, 12 Go RAM | Référence obligatoire pour le rôle assistant et l'invocation Power |
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
l'interface Samsung. Les valeurs publiées ci-dessous identifient le matériel et
le logiciel livré ; le build, le correctif de sécurité et la version installée
doivent toujours être confirmés sur l'appareil avant un essai reproductible.
Consigner le résultat ci-dessous :

| Champ | Valeur relevée | Date | Preuve / lien issue |
| --- | --- | --- | --- |
| Modèle Samsung | Galaxy S26 5G Dual SIM (`SM-S942B/DS`) | 2026-07-16 | [Support Samsung](https://www.samsung.com/nz/support/model/SM-S942BZVCXNZ/) |
| Android / niveau API | Android 16 / API 36 | 2026-07-16 | Relevé sur l'appareil |
| Correctif de sécurité | 5 juin 2026 | 2026-07-16 | Relevé sur l'appareil |
| One UI | One UI 8.5 | 2026-07-16 | Relevé sur l'appareil |
| Build | `BP4A.251205.006.S942BXXS3AZF1` | 2026-07-16 | Relevé sur l'appareil |
| Mise à jour système Google Play | 1 juin 2026 | 2026-07-16 | Relevé sur l'appareil |
| Résolution / densité | 2340 × 1080 px ; densité à relever | 2026-07-16 | [Fiche Samsung Galaxy S26](https://www.samsung.com/africa_en/business/smartphones/galaxy-s/galaxy-s26-sm-s942bzkoafb/) |
| RAM totale | 12 Go | 2026-07-16 | [Fiche Samsung Galaxy S26](https://www.samsung.com/uk/smartphones/galaxy-s26/) |

## Configuration Samsung à relever

| Paramètre | Valeur à documenter | Effet à vérifier |
| --- | --- | --- |
| Bouton latéral — appui long | Assistant numérique (Google) | L'assistant Android par défaut est invoqué sans ouvrir un écran Samsung concurrent |
| Bouton latéral — double appui | Appareil photo ; option activée | Absence de conflit avec les essais d'invocation |
| Assistant numérique par défaut | Google | À remplacer par Jean-Calcul Assistant lors des essais #10 et #12 |
| Batterie de l'application | À relever après installation de l'application | Impact sur le service, la session et la reprise après veille |
| Économie d'énergie | Désactivée | État de référence pour les premiers essais |
| Protection de la batterie | Activée, mode Basique | Consigner si ce réglage influence les essais prolongés |
| Batterie adaptative | Non relevée | À contrôler dans les limites d'utilisation en arrière-plan avant les essais de veille |
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
- [x] Samsung principal identifié : Galaxy S26 5G Dual SIM (`SM-S942B/DS`).
- [x] Build, correctif de sécurité et version One UI actuellement installée relevés sur l'appareil.
- [ ] Appareil Android non-Samsung physique ajouté si disponible.

La configuration individuelle de batterie ne peut être relevée qu'après
l'installation de Jean-Calcul Assistant ; elle est documentée dans les essais
des issues qui introduisent le service et son invocation.
