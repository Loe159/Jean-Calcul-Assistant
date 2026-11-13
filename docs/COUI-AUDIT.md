# Audit COUI 0.0.1-alpha7 — issue #55

Audit des sources Maven de la version **épinglée** :
https://repo.maven.apache.org/maven2/io/github/itoseo/coui/0.0.1-alpha7/
Documentation amont : https://github.com/ItosEO/COUI-Kit
Les sources de versions ultérieures ne définissent pas les API utilisées ici.

| Besoin | API alpha7 et décision |
| --- | --- |
| Thème, couleurs, texte | `CouiTheme`, `CouiPresets`, `Text`, `CouiTextKind` natifs. Palette neutre, accent bleu ; contraste secondaire renforcé. |
| Conteneur et insets | Colonne Foundation sans enregistrement du fond ; slots avec insets système et `imePadding` dans la zone basse. Exception de rendu explicitée ci-dessous. |
| Navigation progressive | Adaptateur Foundation API 33+, palette/texte/feedback COUI ; bouton nouvelle conversation après le premier message, navigation masquée avec le clavier. Le rendu natif alpha7 échoue visuellement sur les tentatives archivées. |
| Réglages | `Group`, `Item`, `Divider` natifs, sans interrupteurs factices pour les fonctions non raccordées. |
| Menu / dialogue | `Menu`, `CouiMenuItem`, `Dialog`, `CouiDialogAction` natifs. Confirmation avant effacement ; annulation et retour système. |
| Saisie libre | Aucun TextField public généraliste dans alpha7 : `BasicTextField` Foundation avec style `rememberCouiTextStyle(Body)`, curseur accentué, capsule et insets de l'application. `InputDialog` ne convient pas à une saisie de chat. |
| Boutons hors toolbar | Aucun bouton public généraliste ; `TopAppBarButton` exige API 34 et une portée toolbar. Action Foundation avec `couiButtonFeedback`, sémantique Button, cible 48 dp et nom accessible. |
| Typographie | Corps et résumés suivent la police système. COUI fixe volontairement Toolbar et PageTitle en dp : limitation alpha7 documentée, sans modifier la bibliothèque. |
| Mouvement | Fondu de destination Compose 180 ms ; navigation Foundation avec feedback COUI, menus et dialogues natifs COUI. Durée Compose soumise à l'échelle d'animation Android. Les captures statiques ne prouvent pas la fluidité. |
| Haptique | `LocalHapticFeedback` à l'envoi et à la navigation ; vérification physique sur appareil NOT_RUN. |

La galerie est accessible depuis les réglages ou le menu. Les deux directions de
bulles y sont explicitement des exemples visuels. Le chat n'invente jamais de
réponse IA et utilise désormais le runtime persistant documenté dans `RUNTIME.md`.

Aucun import Material3 dans le code applicatif et dépendance directe supprimée.
COUI reste en alpha7, minSdk 33 conservé ; le build/lint doit vérifier les gardes API.
Le package Maven contient ses notices MPL-2.0 / AndroidX / Skia. Aucune source
COUI n'est recopiée ou modifiée ; aucune source Eta GPL n'est utilisée.
Seules les références JPG Eta épinglées servent à la comparaison visuelle.

SHA-256 des sources Maven auditées :
`3786ca7079d23b5cb21b7e3cf26108a59eeeb1f0249d24ead9332a45336ec4ff`.

La fenêtre edge-to-edge utilise `adjustResize`, conformément à la configuration
Compose documentée : https://developer.android.com/develop/ui/compose/system/setup-e2e
L'espace IME est réservé dans le slot bas. Le diagnostic de la tentative 13
montrait que l'activité restait RESUMED dans les préférences après Retour :
`adjustNothing` ne permettait pas une gestion fiable de l'IME. Les assertions
et la revue des pixels sont conservées ; une sémantique présente ne garantit
pas que l'en-tête soit effectivement peint.

## Décision finale pour la navigation

La recréation des couches natives ne résout pas tous les rendus incomplets
constatés sur les captures 5–7 : certains états n'affichent que les libellés.
La navigation utilise donc l'adaptateur Foundation commun (API 33+, texte
agrandi), avec `CouiNavigationItem`, palette COUI, texte COUI et feedback COUI.
Le bouton nouvelle conversation apparaît après un message. Cette exception
est explicite : la barre n'est pas annoncée comme un `NavigationBar` natif.
Aucune source de la bibliothèque n'est modifiée et aucune dépendance Material3
n'est réintroduite. Les groupes, menus et dialogues restent natifs. Les captures précédentes restent la preuve de la limite rencontrée.

Le clavier peut encore déplacer la fenêtre via les demandes de relocation du
curseur, même avec les insets déjà réservés. La capsule intercepte maintenant
ces demandes à sa frontière fixe : le défilement interne de BasicTextField
reste actif, mais elles ne déclenchent plus le pan Android de toute la fenêtre.
Le test compare la position écran réelle de l'en-tête, avec saisie multiligne.
Cette décision s'appuie sur le chemin AndroidX documenté qui appelle
`View.requestRectangleOnScreen` pour chaque déplacement du curseur :
https://android.googlesource.com/platform/frameworks/support/+/fea5b9b3fefbe2a1a7ce6e0ae1b5362c64a725d6
Aucun code Eta ni code COUI interne n'est copié pour ce correctif.

La frontière ne suffit pas pour le champ value/onValueChange legacy : ce chemin
contient aussi sa propre gestion IME/rectangle Android. La saisie est passée au
champ Foundation basé sur `TextFieldState`, conservé au niveau de V2App et
sauvegardé par `rememberTextFieldState`. Il n'y a plus deux états à synchroniser.
Envoi et reset utilisent `clearText`, et le champ conserve son défilement interne.

## Hôte final

Les contrôles sont hébergés dans une colonne Foundation : en-tête, contenu
pondéré, zone basse et insets. La couche d'enregistrement du fond du Scaffold
COUI est retirée, aucun effet de prélèvement du fond n'étant nécessaire ici.
Les captures précédentes conservent les défauts visuels constatés malgré des
coordonnées sémantiques inchangées. Group/Item/Divider, Menu, Dialog et Text
restent natifs ; hôte, saisie, actions et navigation sont les exceptions
Foundation explicitement documentées. Aucune adaptation Material3 ne subsiste.

## Validation et limite de l'interprétation

La tentative 15 passe les six tests Android et la revue des treize PNG originaux,
avec aperçus réduits et recadrages d'inspection. Le FAIL visuel clavier de la
revue 13 était une erreur de lecture de l'image entière : l'en-tête est présent
sur son recadrage. Cet erratum est conservé avec les rapports. Les choix d'hôte
et de navigation Foundation sont des exceptions explicites de cette livraison ;
les revues historiques ne suffisent pas à attribuer un défaut à la bibliothèque.
Le défaut technique Retour/IME, reproduit dans la suite complète, disparaît avec
la configuration `adjustResize`, sans mise à jour de COUI ni d'Activity.

## Extension #56

Les champs de configuration réutilisent Foundation faute de TextField public
alpha7, avec typographie et couleurs COUI, contours de champ explicites et
cibles de 48 dp. La clé utilise un clavier Password sans autocorrection, masque
sa valeur et ne la sauvegarde pas dans l’état Compose. Le formulaire s’ouvre
uniquement après « Configurer le fournisseur » ; les réglages initiaux conservent
les groupes COUI. Le retour système ferme d’abord l’édition avant de quitter les
réglages. Les nouveaux écrans doivent être jugés sur les preuves #56 réelles.

## Extension de connexion ChatGPT

Le groupe ChatGPT précède le fournisseur API. Il réutilise Group, Text et Menu
COUI, les cibles Foundation de 48 dp et couiButtonFeedback. L’action de connexion
porte le libellé officiel Continue with ChatGPT, en blanc sur noir ; aucun faux
logo n’est dessiné. Le sélecteur de compte et les actions restent défilables.
Le consentement est dans le navigateur système, jamais une WebView embarquée.
Les dix-neuf captures de la tentative 4 ont été ouvertes : les états normaux
visibles sont lisibles, y compris l’entrée ChatGPT claire/sombre/petite/200 %.
Le FAIL technique Retour/IME reste présent (9/10 Android). La correction suivante
renouvelle la connexion via InputMethodManager.restartInput après la disparition
du champ, puis le test attend aussi isAcceptingText=false. Ce correctif n’est pas
validé par ces anciennes captures et requiert un nouveau run autorisé.
Référence API : https://developer.android.com/reference/android/view/inputmethod/InputMethodManager#restartInput(android.view.View)

## Refonte visuelle demandée dans #56

L'en-tête du chat reprend la structure Eta : options à gauche, modèle actif au
centre, menu à trois points à droite. Le menu contient la nouvelle conversation,
avec confirmation si un brouillon ou des messages existent. La navigation basse
est retirée. Les réglages et la sélection fournisseur/modèle utilisent des
groupes COUI et des lignes Foundation avec typographie et feedback COUI,
icône colorée, résumé et chevron. Le formulaire API est isolé dans sa propre
carte. Les commandes Eta non raccordées (outils, mémoire, permissions appareil)
ne sont pas représentées comme utilisables. Le code Eta n'est pas utilisé.
