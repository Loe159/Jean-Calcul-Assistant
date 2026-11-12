---
name: android-visual-review
description: Examiner avec la vision native les captures Android de Jean-Calcul V2 et motiver les résultats UX, y compris le défaut volontaire.
---
Lire `e2e/README.md`. L'agent qui implémente ouvre lui-même chaque PNG produit
par `e2e/run.py` avec son outil d'image natif (par exemple `view_image`).
Une galerie HTML, une liste de fichiers, un hash ou une CI verte ne constitue
pas une inspection visuelle. Si l'outil vision n'est pas disponible, NOT_RUN.
N'envoyer aucune capture à un service vision tiers ; aucune clé API nécessaire.

Examiner lisibilité, hiérarchie, navigation, contraste clair/sombre, texte coupé,
chevauchements, cibles accessibles, insets système, saisie et bouton avec clavier,
petit écran, police 200 % et animations réduites. Les captures ne prouvent ni
haptique physique ni fluidité des animations : garder ces limites dans le rapport.
Comparer aux références UI approuvées si elles sont fournies ; leur absence est
NOT_RUN, jamais une approbation implicite des captures générées.

Remplir `visual-review.json` pour chaque image effectivement ouverte : statut
PASS/FAIL, observation concrète (défaut ou critères visibles), nom de l'outil
utilisé dans `opened_with`, hash déjà fourni. Ne pas renseigner une image non vue.
Le `regression_fixture` doit être FAIL avec une description du défaut visible ;
`e2e/report.py <dossier>` traduira ce résultat en détection PASS. Ne pas modifier
le rendu de l'app pour cacher ce défaut, qui existe seulement dans le test.

Corriger les défauts réels, puis nouvelle tentative du même run-id (maximum 3).
Rouvrir les nouvelles images ; ne pas recopier une revue sur de nouveaux hashes.
Générer le rapport et livrer APK, observations, logs expurgés et limites dans
une PR vers `feature/jean-calcul-v2-foundation`. Ne pas clôturer #54 sans run
réel, revue native et comparaison approuvée. L'utilisateur décide de la fusion.
