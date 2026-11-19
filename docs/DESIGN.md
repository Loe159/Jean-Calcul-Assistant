# Design

Mode : interface opérationnelle Android. Autorité visuelle : COUI 2.0 (ColorOS 17), version épinglée dans `gradle/libs.versions.toml`.
Utiliser le composant COUI natif correspondant au widget ColorOS (préférences, `CardListGroup`, `SearchBar`, `OverlayContextMenu`, `OverlayDropdownPopup`, `OverlayBottomSheet`, `OverlayDialog` + `DialogButtonBar`, `EmptyState`, `Chip`, `IconButton`…) plutôt qu’une imitation en `Box`/`Text`.
Couleurs et textes uniquement via `COUITheme.colorScheme` / `COUITheme.textStyles` ; pas de second thème ni de style alternatif.
Exceptions explicites : l’assistant superposé (fenêtre système sans fond à filtrer) garde un remplissage opaque du thème et la pression `SinkFeedback` ; le bouton d’effacement natif de la recherche porte le libellé anglais figé de COUI (« Clear text »).
États à vérifier : accueil, chat/streaming, réglages, modèles, outils, menu, clair/sombre, clavier, petit écran, texte 200 %.
