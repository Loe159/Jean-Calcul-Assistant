# Notes d’implémentation Android

Ce document complète `DESIGN.md`. Il décrit comment transposer les références Stitch dans `core-ui` sans copier le prototype Web.

## Principes non négociables

1. Le fond reste sombre, calme et peu texturé.
2. La lumière bleu-violet indique une activité de l’assistant ; elle n’est pas un décor appliqué à chaque carte.
3. Les surfaces flottent au-dessus du fond et ne forment pas un empilement de panneaux edge-to-edge.
4. Le contenu reste lisible sans blur, shader ni animation.
5. Les états microphone actif, local, distant, hors connexion, proposition, confirmation et exécution restent explicites par texte et icône, pas uniquement par couleur.

## Verre

### Référence Web observée

- carte standard : blanc à environ 3 % ;
- panneau : surface sombre à environ 60 % ;
- blur courant : 12 à 24 px ;
- overlay compact : blur renforcé jusqu’à 48 px ;
- bordure haute : blanc à environ 15 % ;
- bordures secondaires : blanc entre 2 et 10 % ;
- reflet interne : trait blanc très léger ;
- ombre : noire diffuse, parfois complétée par un halo violet très faible.

### Traduction Compose

- encapsuler le matériau dans `GlassSurface` ;
- ne pas imbriquer plusieurs blurs ;
- utiliser une teinte tonale opaque lorsque le blur système est absent ;
- conserver une bordure supérieure/reflet et une élévation légère pour maintenir la profondeur ;
- éviter toute dépendance à une API constructeur spécifique.

## Lumière ambiante

Références :

- violet actif : `rgba(138, 91, 245, 0.15)` ;
- bleu ambiant : `rgba(1, 100, 180, 0.10)` ;
- diffusion très large, faible opacité, centre doux ;
- un halo principal par zone active, rarement davantage.

En Compose, privilégier `Canvas` et des dégradés radiaux. Prévoir une version statique et une version sans shader.

## Orbe et visualisation vocale

Les prototypes montrent deux expressions compatibles :

- une lueur centrale minimale pour les sessions compactes ;
- une forme plus large et organique pour le mode vocal focalisé.

Le composant `GradientOrb` doit accepter :

- un état visuel ;
- une amplitude normalisée indépendante du microphone Android ;
- une progression déterministe pour les previews et tests ;
- un niveau d’effets complet, réduit ou désactivé.

`VoiceWave` reste un composant distinct. Les barres observées utilisent une boucle d’environ 1,2 s avec déphasage entre éléments. Le mode réduit remplace cette boucle par une variation statique ou une transition brève.

## Mouvement observé

- apparition verticale douce : environ 1,2 s dans le prototype, à raccourcir pour l’usage Android courant ;
- respiration ambiante : environ 4 s ;
- flottement décoratif : environ 6 s, à réserver au mode focalisé ;
- rotation lente : 8 à 16 s ;
- shimmer : 2 à 3 s, uniquement pendant une génération active ;
- interaction standard : 200 à 300 ms.

Pour l’application :

- le feedback tactile doit être immédiat ;
- aucune boucle ne doit empêcher l’annulation ;
- les écrans statiques ne doivent pas animer en continu ;
- le mode effets réduits supprime blur, shader, shimmer, flottement et rotation continue.

## Typographie

Les références nomment :

- Hanken Grotesk pour les titres ;
- Inter pour le corps ;
- Geist pour les labels et métadonnées.

L’implémentation doit respecter les licences et la stratégie de distribution Android. Les composants ne doivent pas dépendre du chargement réseau d’une police. Prévoir un fallback de plateforme documenté.

## Iconographie

- Material Symbols Outlined dans les prototypes ;
- poids visuel fin et uniforme ;
- conteneurs circulaires ou en pilule pour les actions importantes ;
- état sélectionné par surface, icône et sémantique, pas uniquement par couleur.

## Performance

- limiter les dégradés animés de grande taille ;
- éviter les calques translucides imbriqués ;
- découpler animation et fréquence audio réelle ;
- fournir des previews/test doubles déterministes ;
- vérifier le rendu sur le Samsung cible ;
- ne jamais rendre un shader indispensable à la compréhension ou à l’action.

## Références à consulter

- `examples/voice-ambient.html` pour le mode vocal focalisé ;
- `examples/overlay-compact.html` pour l’overlay compact ;
- `examples/conversation.html` pour le streaming et les cartes d’action ;
- `examples/approval-biometric.html` pour la hiérarchie d’une confirmation ;
- `examples/settings.html`, `examples/audit.html` et `examples/diagnostics.html` pour les composants denses.
