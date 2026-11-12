# Preuves de la boucle agentique #54

Ces preuves proviennent d'émulateurs API 35 vierges sur Linux/NixOS avec KVM,
port 5580, aucune donnée personnelle. Les images ont été ouvertes par l'agent
implémenteur avec `functions.view_image`, sans service vision externe.
Elles documentent un run historique ; ce ne sont pas des goldens auto-approuvés.

- [Tentative 1](attempt-1/report.md) : captures inspectées, défauts de contraste,
  insets et texte à 200 % détectés. Exception d'ignorance présente dans le XML
  malgré le code de sortie Gradle vert : rapport technique corrigé en FAIL.
- [Tentative 2](attempt-2/report.md) : code `3b73a468c483475f194239aa9dfb331182edecbb`,
  trois tests connectés sans erreur et dix captures examinées ; corrections
  visuelles confirmées et défaut volontaire détecté.

- [Tentative 3](attempt-3/report.md) : trois tests connectés réussis, puis
  capture reset vide détectée. Rapport incomplet NOT_RUN ; aucun succès
  visuel global revendiqué. La CI du même code a passé, démontrant le caractère
  intermittent du problème de capture. Une quatrième validation a été
  explicitement autorisée par l’utilisateur le 2026-11-12.

- [Validation finale 4/4](attempt-4/report.md) : code
  `941334bb1a4133a8a1dc385e2fc251e917fc5b56`, budget supplémentaire autorisé
  explicitement par l’utilisateur. Build/lint, trois tests Android et dix
  captures complètes passent ; les dix images et les deux références Eta
  ont été réellement ouvertes. Neuf états normaux PASS, défaut volontaire
  FAIL et détection PASS. Les lectures du nouveau transfert sont toutes
  complètes dès le premier essai dans ce run.

Les références Eta sont celles fournies par l'utilisateur, épinglées dans
`e2e/references.json`. Les JPG restent chez leur auteur ; leurs manifestes et
empreintes sont conservés ici. La comparaison porte sur les états communs du
prototype et ne revendique pas une parité fonctionnelle avec Eta.

Chaque tentative conserve rapport, observations identifiant l'outil utilisé,
PNG originaux, résultats XML et logs expurgés. Les APK et disques AVD restent
hors de Git. Le rapport contient l'empreinte de l'APK local réellement testé.
Les neuf tests Python vérifient notamment qu'un PNG seul ne vaut pas revue,
qu'un changement d'image invalide la revue et qu'un XML en échec ne passe pas
sur la seule foi du code de sortie Gradle.

Tests JVM : NOT_RUN (aucune source JVM). Appareil Samsung, retour tactile et
fluidité physique : NOT_RUN. Le prototype ne comporte toujours ni moteur IA
connecté ni persistance des conversations.
