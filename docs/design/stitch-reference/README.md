# Références Stitch — Jean Calcule Lumina

Ce dossier versionne la direction artistique retenue pour l’issue [#18](https://github.com/Loe159/Jean-Calcul-Assistant/issues/18).

## Source canonique

- [`DESIGN.md`](DESIGN.md) est la source de vérité artistique extraite du dernier export Stitch reçu.
- La direction retenue est **Jean Calcule Lumina / Ambient Intelligence / Refined Dark Glass**.
- Les exports de [`examples/`](examples/) servent de références visuelles pour les composants, la composition, la densité et les états.
- [`IMPLEMENTATION-NOTES.md`](IMPLEMENTATION-NOTES.md) traduit les effets du prototype Web en contraintes Android/Jetpack Compose.

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
- modèles et fournisseurs affichés dans les exemples ;
- structure de navigation ou sections de réglages historiques ;
- HTML, Tailwind, WebGL ou assets distants comme dépendances de production.

## Exemples retenus

| Fichier | Référence Stitch d’origine | Usage |
| --- | --- | --- |
| `examples/assistant-principal.html` | `jean_calcul_assistant_principal` | accueil et navigation flottante |
| `examples/conversation.html` | `conversation_jean_calcul` | conversation, streaming et action |
| `examples/session-listening.html` | `session_assistant_overlay_coute` | session transparente en écoute |
| `examples/voice-ambient.html` | `mode_vocal_activation_ambiante` | état vocal focalisé et orbe |
| `examples/overlay-compact.html` | `overlay_assistant_coute_active` | barre compacte au-dessus d’une application |
| `examples/approval-biometric.html` | `confirmation_de_s_curit_biom_trie` | confirmation et biométrie |
| `examples/settings.html` | `r_glages_configuration_assistant` | composants de réglages |
| `examples/audit.html` | `journal_d_audit_s_curit` | journal, filtres et statuts |
| `examples/diagnostics.html` | `diagnostic_syst_me_tat_local` | diagnostic et données techniques |

## Provenance

- Export source : `stitch_jean_calcule_assistant_os (1).zip`
- SHA-256 : `f5b221487bdd8e66e3a2e90659d87367456d4d6f2fe915e9c44de6fd50e9c1e5`
- Les variantes historiques du ZIP ne sont pas retenues comme sources canoniques afin d’éviter les contradictions.
- Aucun fichier de police n’est versionné dans ce dossier.

Consulter [`MANIFEST.md`](MANIFEST.md) pour les empreintes des fichiers versionnés.
