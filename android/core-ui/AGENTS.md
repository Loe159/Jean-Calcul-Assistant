# Primitives Compose

Lire spec UX et design/compose-design-system.md. core-ui contient tokens et composants de présentation,
pas provider, registre, permissions ou appels réseau. Le host passe texte/état/callbacks.

Utiliser MaterialTheme/JeanCalculDesign, tailles de texte en sp, cibles 48 dp et sémantiques utiles.
Les effets réduits, thème clair et grande police sont des états normaux. GlassSurface est un traitement
tonal ; ne pas promettre un backdrop blur Android réel. Orbe/onde ne capturent jamais le micro.

Les previews et tests de tokens ne prouvent pas les écrans intégrés. Captures depuis Android avec
progression/amplitude déterministes ; conserver la compatibilité des API de composants si possible.
