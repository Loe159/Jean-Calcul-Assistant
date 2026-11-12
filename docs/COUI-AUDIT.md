# Audit COUI 0.0.1-alpha7 — issue #55

Audit des sources Maven de la version **épinglée** :
https://repo.maven.apache.org/maven2/io/github/itoseo/coui/0.0.1-alpha7/
Documentation amont : https://github.com/ItosEO/COUI-Kit
Les sources de versions ultérieures ne définissent pas les API utilisées ici.

| Besoin | API alpha7 et décision |
| --- | --- |
| Thème, couleurs, texte | `CouiTheme`, `CouiPresets`, `Text`, `CouiTextKind` natifs. Palette neutre, accent bleu ; contraste secondaire renforcé. |
| Conteneur et insets | `com.itos.coui.layout.Scaffold` ; les slots gèrent leurs insets verticaux, le contenu consomme le padding. L'hôte applique `imePadding`. |
| Navigation progressive | `NavigationBar` + `CouiFloatingRenderer.standalone()` dès API 34 ; bouton nouvelle conversation révélé après le premier message, masqué avec le clavier. Android 13 conserve deux actions Foundation avec feedback COUI. |
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
