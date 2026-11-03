# Pont d'outils

Le registre exécute seulement les outils/version/schémas connus avec reçu vérifié. Pas de Compose
ici. Lire spec outils et skill jc-tools-policy avant un changement de politique/confirmation.

LocalAgentToolRuntime doit recevoir la configuration effective ; ne pas forcer ALLOW pour compenser
l'absence d'UI. Catalogues et exécution appliquent le même mapping de permissions, revérifié au moment
de l'effet. Conserver erreurs structurées, audit, idempotence et vérification des sorties.

Tester refus/expiration/révocation avec adaptateurs à compteurs, puis effet réel seulement sur
appareil autorisé. Restaurer volume/lampe et ne pas traiter un rejeu comme une deuxième exécution.
