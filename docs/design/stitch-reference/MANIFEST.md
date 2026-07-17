# Manifeste des ressources

## Export d’origine

- Fichier reçu : `stitch_jean_calcule_assistant_os (1).zip`
- SHA-256 : `f5b221487bdd8e66e3a2e90659d87367456d4d6f2fe915e9c44de6fd50e9c1e5`
- Source canonique sélectionnée dans l’export : `jean_calcule_lumina/DESIGN.md`

## Ressources versionnées

- `README.md` — index et règles d’utilisation ;
- `DESIGN.md` — tokens et principes canoniques ;
- `SCREEN-REFERENCES.md` — retranscription des neuf écrans retenus ;
- `IMPLEMENTATION-NOTES.md` — traduction Android/Compose ;
- `MANIFEST.md` — provenance et inventaire ;
- `examples/README.md` — portée des prototypes ;
- `examples/index.html` — navigation locale ;
- `examples/voice-ambient.html` — orbe et mouvement vocal ;
- `examples/overlay-compact.html` — overlay glass compact.

## Choix de conservation

Les autres HTML et captures de l’export ne sont pas requis pour implémenter l’issue #18 : leurs informations utiles ont été consolidées dans `SCREEN-REFERENCES.md`. Cette consolidation évite :

- les variantes historiques contradictoires ;
- la duplication de plusieurs écrans portant des contenus fictifs ;
- l’interprétation des exemples comme architecture métier ;
- une dépendance documentaire à des images et polices distantes.

Les deux prototypes interactifs conservés couvrent les effets qui ne peuvent pas être décrits fidèlement par une capture statique : mouvement de l’orbe, onde vocale, shimmer, blur, bordures lumineuses et halo contextuel.

Aucun fichier de police n’est versionné.
