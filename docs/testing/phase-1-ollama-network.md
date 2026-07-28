# Validation reseau Android pour Ollama

Issue: #25

## Politique de transport

- HTTPS utilise exclusivement la chaine de confiance Android. Un certificat auto-signe doit etre
  installe comme autorite approuvee sur l'appareil ou remplace par un certificat valide.
- Aucun mode `trust all`, aucun contournement de verification du nom d'hote et aucun certificat
  embarque ne sont autorises.
- HTTP peut etre active pour une connexion Ollama choisie par l'utilisateur sur un reseau local de
  confiance. L'ecran de test affiche alors `Connexion HTTP non chiffree`.
- Ne jamais exposer le port Ollama directement sur Internet. Utiliser HTTPS ou le futur Gateway pour
  un acces hors du LAN.

## Configuration du serveur local

1. Demarrer Ollama sur la machine du reseau local et verifier `GET /api/tags`.
2. Autoriser le port configure dans le pare-feu uniquement pour le sous-reseau local.
3. Si Ollama n'ecoute que sur la boucle locale, le lier explicitement a l'adresse LAN voulue.
4. Dans Jean-Calcul Assistant, enregistrer l'URL racine, par exemple
   `http://192.168.1.20:11434`.
5. Lancer le test de connexion et confirmer que l'avertissement HTTP est visible.

Pour l'emulateur Android standard, `10.0.2.2` designe la machine hote. Un telephone Samsung physique
doit utiliser l'adresse LAN de la machine et etre connecte au meme reseau.

## Scenarios appareil a consigner

- serveur disponible : la liste des modeles apparait sans bloquer l'interface ;
- serveur arrete : l'erreur reseau est recuperable et l'interface reste interactive ;
- modele outils : `tools` est annonce apres detection par `/api/show` ;
- modele vision : `vision` est annonce, les autres modeles restent texte uniquement ;
- URL HTTP : l'avertissement non chiffre est visible ;
- certificat auto-signe non approuve : la connexion echoue sans contournement TLS ;
- annulation : le flux s'arrete et la requete reseau est fermee.

Les tests JVM simulent ces contrats reseau. La validation sur Samsung reel reste a consigner lors de
la campagne d'integration de phase 1.
