# ADR 0006 - Design system Compose sans dependance metier

## Decision

Le design system de phase 1 vit dans `core-ui` et expose uniquement des tokens, composants et
etats visuels generiques. Les surfaces glass utilisent une teinte tonale, une bordure et une
elevation legere comme rendu de reference. Un blur de fond ou un shader n'est jamais requis.

`VisualEffects` permet a l'hote de selectionner les effets reduits, le fallback sans blur, les
gradients Canvas et le contraste renforce. L'orbe et l'onde vocale recoivent une amplitude et une
progression injectees. Elles ne lisent pas le microphone et ne representent un microphone actif
que dans l'etat `Listening` fourni par l'hote.

## Consequences

- `core-ui` reste reutilisable par l'application et la session transparente sans dependance vers
  les fournisseurs, agents, outils, permissions ou politiques metier.
- Les previews et golden specifications sont reproductibles sans pipeline audio, reseau ou temps
  reel.
- Le rendu reste lisible sur les appareils ou les effets graphiques sont indisponibles ou reduits.
