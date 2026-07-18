# Références Stitch — Jean Calcul Lumina

Ce dossier versionne la direction artistique retenue pour l’issue [#18](https://github.com/Loe159/Jean-Calcul-Assistant/issues/18).

## Sources canoniques

- [`DESIGN.md`](DESIGN.md) est la source de vérité pour les tokens et principes artistiques.
- [`SCREEN-REFERENCES.md`](SCREEN-REFERENCES.md) retranscrit la composition et les composants utiles de tous les écrans retenus.
- [`IMPLEMENTATION-NOTES.md`](IMPLEMENTATION-NOTES.md) traduit les effets du prototype Web en contraintes Android/Jetpack Compose.
- [`examples/`](examples/) contient deux prototypes interactifs particulièrement importants : le mode vocal ambiant et l’overlay compact.

La direction retenue est **Jean Calcul Lumina / Ambient Intelligence / Refined Dark Glass**.

## Règle d’utilisation

Les références sont **artistiques uniquement**. Elles ne définissent pas l’architecture métier, les textes, les fournisseurs, les modèles, les données ni la navigation fonctionnelle du produit.

À conserver :

- fond obsidienne/charbon ;
- halos bleu-violet rares et fonctionnels ;
- surfaces glass sombres et flottantes ;
- forte respiration ;
- grands arrondis et pilules ;
- hiérarchie Hanken Grotesk / Inter / Geist ;
- animation vocale organique ;
- sobriété premium.

À ne pas reprendre comme exigences produit :

- météo, réunions, maison connectée ou autres données fictives ;
- compte Premium/Admin ;
- modèles et fournisseurs affichés dans les anciens exemples ;
- structure de navigation ou sections de réglages historiques ;
- HTML, Tailwind, WebGL ou assets distants comme dépendances de production.

## Écrans couverts

Le document [`SCREEN-REFERENCES.md`](SCREEN-REFERENCES.md) couvre :

1. assistant principal ;
2. conversation et streaming ;
3. session transparente en écoute ;
4. mode vocal ambiant ;
5. overlay assistant compact ;
6. confirmation et biométrie ;
7. réglages ;
8. journal d’audit ;
9. diagnostic.

Les deux prototypes dont le mouvement et le matériau sont difficiles à retranscrire uniquement par du texte restent consultables directement :

- [`examples/voice-ambient.html`](examples/voice-ambient.html) ;
- [`examples/overlay-compact.html`](examples/overlay-compact.html).

## Provenance

- Export source : `stitch_jean_calcul_assistant_os (1).zip`
- SHA-256 : `f5b221487bdd8e66e3a2e90659d87367456d4d6f2fe915e9c44de6fd50e9c1e5`
- Les variantes historiques du ZIP ne sont pas retenues comme sources canoniques afin d’éviter les contradictions.
- Les informations utiles des autres écrans ont été retranscrites dans `SCREEN-REFERENCES.md` afin que l’implémentation ne dépende pas de prototypes Web contenant des ressources distantes.
- Aucun fichier de police n’est versionné dans ce dossier.

Consulter [`MANIFEST.md`](MANIFEST.md) pour l’inventaire versionné.
