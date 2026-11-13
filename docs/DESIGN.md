# Jean-Calcul — interface conversation

Surface Android d'usage : lire, rédiger et naviguer entre chat et préférences.
Références imposées : Eta chat_home.jpg et settings.jpg, épinglées dans
`e2e/references.json`. La composition neutre, la saisie flottante et les groupes
COUI sont les repères communs. Les fonctions IA et outils Eta non raccordées ne
sont pas exposées comme fonctionnelles.

Les tokens exécutables sont dans `JeanTheme.kt` : fond clair #F7F7F7,
fond sombre #101112, surface sombre #222426, texte secondaire #5C6168 / #B5B9BE,
accent COUI, bulle sortante #E4EFFB / #183858. Marges 16 dp, capsule 28 dp,
élévation 6 dp, bulles 22 dp avec un angle 6 dp orienté vers leur auteur.
Les tailles de texte viennent des rôles COUI et les cibles font au moins 48 dp.

Accueil sobre, état déconnecté explicite ; capsule en deux rangées pour laisser
la saisie grandir sans comprimer l'action d'envoi. La navigation native révèle
l'action nouvelle conversation après saisie ; le clavier masque la navigation
pour préserver l'espace. Les réglages et la galerie défilent sur petit écran.
La transition de destination est un fondu 180 ms utilisant l'échelle système.
L'effacement demande confirmation, le retour système remonte galerie → réglages
→ chat, et le brouillon survit à ces changements de page.
