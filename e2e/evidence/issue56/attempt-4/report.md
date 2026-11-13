# Issue #56 — tentative 4/4

Commit : `51c1b530638e8150183df4d99d924c1aba398523` ; modifications locales : False.
Dérogation au budget : Oui, autoriser la tentative 4.
Android API 35 ; émulateur `emulator-5580` ; données synthétiques.

| Critère | Statut |
| --- | --- |
| Tâches Gradle build/lint et instrumentation Compose | FAIL |
| Tests JVM réellement exécutés | PASS |
| Revue visuelle native des états normaux | PASS |
| Détection du défaut volontaire | PASS |
| Comparaison aux références Eta sélectionnées par l’utilisateur | PASS |
| Appareil Samsung / retour tactile physique | NOT_RUN |

Références : Eta, commit `4101bc700be7c13b7b72f85a59c1c320f2183c04`,
chat_home.jpg et settings.jpg (sources et empreintes dans references/manifest.json).
La comparaison porte sur les états communs du prototype ; elle ne prouve pas
la parité fonctionnelle avec Eta. La CI ne juge pas l'UX.

Observation comparative : Les deux références Eta et les dix-neuf captures de cette tentative ont été ouvertes. Fond neutre, capsule basse et groupes arrondis/titres-résumés restent cohérents. V2 met le vrai parcours ChatGPT en premier, avec un bouton noir nommé et un état connexion requise ; elle ne reprend pas les raccourcis Eta non raccordés. Navigation Chat/Réglages propre à V2. Aucun équivalent exact Eta des états OAuth, sombre, IME ou 200 % ; pas de parité pixel/fonctionnelle revendiquée. Le FAIL fonctionnel Retour/IME dans les tests reste distinct de cette inspection statique.

| État | Capture et assertions | Revue visuelle | Observation |
| --- | --- | --- | --- |
| empty | PASS | PASS | Accueil indique de connecter le compte ChatGPT ; capsule affiche connexion requise. Titre, trois actions et navigation visibles, aucune réponse inventée. |
| message | PASS | PASS | Message synthétique conservé, erreur réelle de connexion ChatGPT sur deux lignes et action Réessayer lisibles ; pas de bulle entrante fictive. |
| reset | PASS | PASS | Après effacement, accueil et saisie vide rétablis, ancien message absent et navigation Chat conservée. |
| settings | PASS | PASS | Groupe Abonnement ChatGPT en premier, bouton noir Continue with ChatGPT lisible et action Gérer l’utilisation visible. Le fournisseur API est une option distincte plus bas ; contenu défilable. |
| dark | PASS | PASS | Chat sombre : texte, consigne et statut connexion requise contrastés ; capsule et onglet actif identifiables, insets dégagés. |
| small_screen | PASS | PASS | 360×640 dp : titre et consigne reviennent à la ligne ; capsule, état de connexion et envoi restent lisibles, navigation visible. |
| large_text | PASS | PASS | 200 % : texte de connexion et état du composeur se réorganisent sur plusieurs lignes sans chevauchement. Titre COUI fixe mais présent ; placeholder abrégé. |
| keyboard | PASS | PASS | Clavier visible et envoi dégagé au-dessus de l’IME ; brouillon et curseur lisibles. Recadrage inspection/keyboard-header.png ouvert nativement : titre et trois actions présents. |
| reduced_motion | PASS | PASS | État stable sans animation : hiérarchie accueil/saisie/navigation conservée. Capture ne prouve ni fluidité ni haptique. |
| menu | PASS | PASS | Menu Galerie COUI/Préférences ancré sous les options et lisible, sans masquer le composeur. |
| gallery | PASS | PASS | Échantillons de palette, bulles explicitement illustratives et dialogue visibles, sans prétendre à une réponse IA connectée. |
| dialog | PASS | PASS | Titre, texte et Fermer dans le dialogue blanc au-dessus du fond assombri ; barre système dégagée. |
| chatgpt_dark | PASS | PASS | Entrée ChatGPT lisible en sombre : texte blanc sur bouton noir distinct de la carte anthracite ; accès usage et fournisseur API séparés, aucun compte annoncé connecté. |
| chatgpt_small_screen | PASS | PASS | Petit écran : Continue with ChatGPT tient sur une ligne et reste entièrement visible ; consigne et usage lisibles, fournisseur API sous le pli dans la liste défilable. |
| chatgpt_large_text | PASS | PASS | 200 % : bouton ChatGPT et accès usage lisibles, la carte grandit avec le texte sans superposer la navigation. Suite du texte et sections plus basses hors viewport. |
| provider_dark | PASS | PASS | Après défilement, formulaire API ouvert ; libellé URL et contour du champ visibles au bas. Suite sous le pli non validée, entrée ChatGPT conservée au-dessus. |
| provider_small_screen | PASS | PASS | Viewport défilé : champ URL bordé visible partiellement en bas ; son étiquette est lisible. Partie supérieure de la liste hors viewport ; bas du formulaire non inspecté. |
| provider_large_text | PASS | PASS | À 200 % après défilement, section API, action Configurer et étiquette URL lisibles, contour de champ identifiable en bas. Le contenu suivant nécessite de défiler ; il n’est pas validé par ce PNG. |
| regression_fixture | PASS | FAIL | FAIL volontaire : rectangle rouge opaque couvre la zone basse, masque connexion, envoi et navigation ainsi qu’une partie du composeur. |

Erreur : Instrumentation XML contains a failure: TEST-jc-e2e-issue56-4(AVD) - 15.xml
