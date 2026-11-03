# Compagnon Codex

Lire spec backends/auth, companion/README.md et tests/companion avant édition. Ce serveur Python stdlib
n'est pas le Gateway futur. Préserver HTTP loopback privé, Bearer d'appairage, version wire 2,
isolation de workspace et désactivation des outils hôte.

Auth ChatGPT gérée par CLI Codex ; aucun token ChatGPT vers Android. Le header de protocole est
obligatoire même pour status. Ne pas logger en-têtes/prompts ni ouvrir 0.0.0.0 pour simplifier un test.

Toute évolution wire touche DTO Kotlin et tests. Tester nettoyage threads/processus/sockets avec
ResourceWarning traité comme erreur. Déclarer la portée réelle du replay/résumé des runs en mémoire ;
ne pas annoncer une reprise durable qui n'existe pas. Tester avec fakes sans login personnel.
