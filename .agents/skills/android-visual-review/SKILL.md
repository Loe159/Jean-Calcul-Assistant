---
name: android-visual-review
description: Examiner avec la vision native les captures Android de Jean-Calcul V2 et motiver les résultats UX, y compris le défaut volontaire.
---
Lire `e2e/README.md`. Avant la revue, écrire le périmètre visuel de l'issue :
écrans/états modifiés, états voisins susceptibles de régresser et critères
d'acceptation concernés. L'agent qui implémente ouvre lui-même chaque PNG de ce
périmètre avec son outil d'image natif (par exemple `view_image`). Inscrire les
noms des états dans `_scope.states` et le motif dans `_scope.reason` du fichier
`visual-review.json`. Les autres captures restent NOT_RUN pour la revue visuelle ; ne pas prétendre avoir validé
toute l'interface.
Une galerie HTML, une liste de fichiers, un hash ou une CI verte ne constitue
pas une inspection visuelle. Si l'outil vision n'est pas disponible, NOT_RUN.
N'envoyer aucune capture à un service vision tiers ; aucune clé API nécessaire.

Examiner les critères pertinents pour la fonctionnalité : lisibilité, hiérarchie,
navigation, contraste clair/sombre, texte coupé, chevauchements, cibles
accessibles, insets système, saisie et bouton avec clavier, petit écran, police
200 % et animations réduites. Étendre la revue aux variantes concernées par le
changement et aux régressions plausibles, sans parcours exhaustif systématique.
Une modification de thème, navigation globale ou composant partagé élargit le
périmètre aux écrans utilisateurs affectés. Les captures ne prouvent ni
haptique physique ni fluidité des animations : garder ces limites dans le rapport.
Comparer aux références UI approuvées pour les états du périmètre qui ont une
référence pertinente ; pour les autres, indiquer NOT_RUN ou non applicable avec
motif, jamais une approbation implicite des captures générées.

Remplir `visual-review.json` pour chaque image effectivement ouverte : statut
PASS/FAIL, observation concrète (défaut ou critères visibles), nom de l'outil
utilisé dans `opened_with`, hash déjà fourni. Ne pas renseigner une image non vue.
Le `regression_fixture` est revu lors des changements du dispositif de capture,
du rapport ou de la revue visuelle. S'il est examiné, il doit être FAIL avec une
description du défaut visible ; `e2e/report.py <dossier>` traduira ce résultat
en détection PASS. Ne pas modifier le rendu de l'app pour cacher ce défaut,
qui existe seulement dans le test.

Corriger les défauts réels, puis nouvelle tentative du même run-id (maximum 3).
Rouvrir les nouvelles images du périmètre ; ne pas recopier une revue sur de
nouveaux hashes. Le rapport présente le statut du périmètre ciblé et laisse
NOT_RUN sur les autres captures.
Générer le rapport et livrer APK, observations, logs expurgés et limites dans
une PR vers `feature/jean-calcul-v2-foundation`. Ne déclarer validés que les
critères réellement exécutés. L'utilisateur décide de la fusion.
