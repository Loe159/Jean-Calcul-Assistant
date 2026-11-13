# Audit COUI 0.0.1-alpha7 — issue #55

Audit des sources Maven de la version **épinglée** :
https://repo.maven.apache.org/maven2/io/github/itoseo/coui/0.0.1-alpha7/
Documentation amont : https://github.com/ItosEO/COUI-Kit
Les sources de versions ultérieures ne définissent pas les API utilisées ici.

| Besoin | API alpha7 et décision |
| --- | --- |
| Thème, couleurs, texte | `CouiTheme`, `CouiPresets`, `Text`, `CouiTextKind` natifs. Palette neutre, accent bleu ; contraste secondaire renforcé. |
| Conteneur et insets | `com.itos.coui.layout.Scaffold` ; les slots gèrent leurs insets verticaux, le contenu consomme le padding. L'hôte applique `imePadding`. |
| Navigation progressive | `NavigationBar` + `CouiFloatingRenderer()` dès API 34 ; bouton nouvelle conversation révélé après le premier message, masqué avec le clavier. Android 13 conserve deux actions Foundation avec feedback COUI. |
| Réglages | `Group`, `Item`, `Divider` natifs, sans interrupteurs factices pour les fonctions non raccordées. |
| Menu / dialogue | `Menu`, `CouiMenuItem`, `Dialog`, `CouiDialogAction` natifs. Confirmation avant effacement ; annulation et retour système. |
| Saisie libre | Aucun TextField public généraliste dans alpha7 : `BasicTextField` Foundation avec style `rememberCouiTextStyle(Body)`, curseur accentué, capsule et insets de l'application. `InputDialog` ne convient pas à une saisie de chat. |
| Boutons hors toolbar | Aucun bouton public généraliste ; `TopAppBarButton` exige API 34 et une portée toolbar. Action Foundation avec `couiButtonFeedback`, sémantique Button, cible 48 dp et nom accessible. |
| Typographie | Corps et résumés suivent la police système. COUI fixe volontairement Toolbar et PageTitle en dp : limitation alpha7 documentée, sans modifier la bibliothèque. |
| Mouvement | Fondu de destination Compose 180 ms ; navigation, menus et dialogues COUI. Durée Compose soumise à l'échelle d'animation Android. Les captures statiques ne prouvent pas la fluidité. |
| Haptique | `LocalHapticFeedback` à l'envoi et à la navigation ; vérification physique sur appareil NOT_RUN. |

La galerie est accessible depuis les réglages ou le menu. Les deux directions de
bulles y sont explicitement des exemples visuels. Le chat n'invente jamais de
réponse IA et conserve uniquement les messages saisis, en mémoire.

Aucun import Material3 dans le code applicatif et dépendance directe supprimée.
COUI reste en alpha7, minSdk 33 conservé ; le build/lint doit vérifier les gardes API.
Le package Maven contient ses notices MPL-2.0 / AndroidX / Skia. Aucune source
COUI n'est recopiée ou modifiée ; aucune source Eta GPL n'est utilisée.
Seules les références JPG Eta épinglées servent à la comparaison visuelle.

Premier test Android : `NavigationBar` exige `CouiFloatingRenderer()` (trois
surfaces), pas `standalone()` (une surface) ; l'assertion de la bibliothèque a
été détectée et l'intégration corrigée sans toucher à ses sources. Les labels
natifs sont placés dans 48 dp fixes : au-delà de fontScale 1.3, les destinations
utilisent une variante Foundation/feedback COUI à hauteur libre et sélection
accessible. Le POM alpha7 importe Compose BOM alpha 2026.08.01, Foundation et
Runtime 1.12.0, Kotlin 2.4.20 : le BOM historique de l'hôte n'impose donc pas
à lui seul les versions résolues. La validation porte sur cette résolution.
SHA-256 des sources Maven auditées :
`3786ca7079d23b5cb21b7e3cf26108a59eeeb1f0249d24ead9332a45336ec4ff`.

La revue réelle des captures #55 expose des couches de navigation vides après
reset/changement de destination malgré des sémantiques présentes. Leur durée de
vie est maintenant liée à la destination et à la session UI (`key`) : chaque
nouveau contexte alloue ses propres ressources COUI, sans réutiliser un rendu
précédent. Les transitions de page restent assurées par le fondu Compose.

L'espace IME est réservé dans le slot bas ; la fenêtre hôte conserve sa taille.
Sur minSdk 33, `adjustNothing` laisse la gestion des insets au Compose hôte et
évite un déplacement automatique de la fenêtre qui masquait l'en-tête. Le test
clavier vérifie aussi la présence visible de cet en-tête. Une vérification du
rendu réel reste nécessaire en plus des assertions sémantiques.

## Décision finale pour la navigation

La recréation des couches natives ne résout pas tous les rendus incomplets
constatés sur les captures 5–7 : certains états n'affichent que les libellés.
La navigation utilise donc l'adaptateur Foundation commun (API 33+, texte
agrandi), avec `CouiNavigationItem`, palette COUI, texte COUI et feedback COUI.
Le bouton nouvelle conversation apparaît après un message. Cette exception
est explicite : la barre n'est pas annoncée comme un `NavigationBar` natif.
Aucune source de la bibliothèque n'est modifiée et aucune dépendance Material3
n'est réintroduite. Les autres conteneurs, groupes, menus et dialogues restent
natifs. Les captures précédentes restent la preuve de la limite rencontrée.
