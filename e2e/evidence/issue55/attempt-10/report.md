# Issue #55 — tentative 10/10

Commit : `f6810e8319feaa94c644cbca77332ad2ee83ea39` ; modifications locales : False.
Dérogation au budget : User explicitly requested continuing fixes until all tests pass; see e2e/evidence/issue55/README.md.
Android API 35 ; émulateur `emulator-5580` ; données synthétiques.

| Critère | Statut |
| --- | --- |
| Tâches Gradle build/lint et instrumentation Compose | FAIL |
| Tests JVM réellement exécutés | NOT_RUN |
| Revue visuelle native des états normaux | FAIL |
| Détection du défaut volontaire | PASS |
| Comparaison aux références Eta sélectionnées par l’utilisateur | NOT_RUN |
| Appareil Samsung / retour tactile physique | NOT_RUN |

Références : Eta, commit `4101bc700be7c13b7b72f85a59c1c320f2183c04`,
chat_home.jpg et settings.jpg (sources et empreintes dans references/manifest.json).
La comparaison porte sur les états communs du prototype ; elle ne prouve pas
la parité fonctionnelle avec Eta. La CI ne juge pas l'UX.

Observation comparative : 

| État | Capture et assertions | Revue visuelle | Observation |
| --- | --- | --- | --- |
| empty | PASS | PASS | Accueil et statut lisibles, capsule et navigation à deux destinations complètes, marges système respectées. |
| message | PASS | PASS | Message et avertissement mémoire lisibles ; navigation et action nouvelle conversation visibles après fermeture du clavier. |
| reset | PASS | PASS | Accueil restauré, message absent et navigation complète avec Chat sélectionné. |
| settings | PASS | PASS | Groupes natifs, résumés et séparation lisibles ; navigation complète avec Réglages sélectionné. |
| dark | PASS | PASS | Surfaces sombres distinctes, texte et icônes clairs, onglet sélectionné contrasté et barres système lisibles. |
| small_screen | PASS | PASS | 360×640 dp : titre sur deux lignes sans coupure, capsule et navigation accessibles au-dessus du geste. |
| large_text | PASS | PASS | Police 200 % : corps et destinations agrandis sans coupure, navigation à hauteur libre ; titres COUI fixes documentés. |
| keyboard | PASS | FAIL | Le clavier et Envoyer sont visibles ; l’en-tête reste hors écran malgré la frontière de relocation, chemin de saisie legacy encore actif. |
| reduced_motion | PASS | PASS | État final avec animations désactivées lisible, navigation et saisie complètes ; fluidité physique non prouvée. |
| menu | PASS | PASS | Menu natif ancré à droite, deux entrées lisibles ; navigation et saisie non masquées. |
| gallery | PASS | PASS | Typographie et bulles illustratives lisibles, navigation désormais complète ; swatch blanc sur surface blanche peu distinct. |
| dialog | PASS | PASS | Dialogue natif lisible avec action Fermer et marge au-dessus du geste ; arrière-plan assombri. |
| regression_fixture | PASS | FAIL | Surcouche rouge en bas masquant navigation, bouton Envoyer et une partie de la capsule, défaut volontaire détecté. |

Erreur : Instrumentation XML contains a failure: TEST-jc-e2e-issue55-10(AVD) - 15.xml
