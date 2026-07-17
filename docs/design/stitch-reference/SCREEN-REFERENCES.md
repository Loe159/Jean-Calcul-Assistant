# Références d’écrans Stitch

Ce document retranscrit les informations utiles des écrans retenus dans le dernier export Stitch. Les captures et prototypes servent uniquement de références artistiques : les textes, données, modèles, fournisseurs et fonctionnalités fictives ne constituent pas des exigences produit.

## Principes communs observés

- fond obsidienne `#101415`, avec zones plus profondes proches de `#0B0F10` ;
- lueurs bleu-violet diffuses, peu nombreuses et centrées sur la zone active ;
- surfaces flottantes semi-transparentes, bordure haute lumineuse et ombre noire diffuse ;
- marges latérales généreuses, densité faible et séparation importante entre les groupes ;
- formes `12–24 dp`, avec pilules pour la navigation, les actions focales et les statuts ;
- titres Hanken Grotesk, contenu Inter, labels techniques Geist ;
- icônes fines de type Material Symbols Outlined ;
- état actif exprimé par une combinaison de surface, lumière, icône et texte ;
- aucune information essentielle ne doit dépendre du blur ou d’une animation.

## 1. Assistant principal

Source Stitch : `jean_calcul_assistant_principal`.

### Composition à retenir

- barre supérieure transparente avec identité du produit et accès aux réglages ;
- grande zone centrale respirante orientée vers l’assistant ;
- statut du système compact dans une surface glass ;
- orbe ou lumière ambiante comme point focal, sans surcharge ;
- entrée texte ou vocale flottante ;
- navigation inférieure détachée des bords, en pilule.

### Composants réutilisables

- `AmbientGlow` ;
- `GradientOrb` ;
- `ProviderChip` ou statut compact ;
- `AssistantInputBar` ;
- `FloatingBottomNavigation` ;
- bouton d’icône circulaire.

### À ignorer

Les cartes météo, réunions, maison connectée, compte utilisateur et autres exemples de dashboard.

## 2. Conversation

Source Stitch : `conversation_jean_calcul`.

### Composition à retenir

- conversation sur fond sombre avec messages peu encadrés ;
- réponse assistant pouvant se fondre dans la surface principale ;
- messages utilisateur plus clairement délimités ;
- métadonnées et statuts en petit corps Geist ;
- streaming signalé par un shimmer discret ou un curseur, jamais sur tout l’écran ;
- proposition d’action isolée dans une carte structurée ;
- zone de saisie flottante conservant une forte lisibilité.

### Composants réutilisables

- `AssistantBubble` ;
- `ActionCard` ;
- `ProviderChip` ;
- `PrivacyIndicator` ;
- `StatusBadge` ;
- champ multiligne et actions arrêter/réessayer/copier.

### États à distinguer

- réponse en cours ;
- réponse interrompue ;
- erreur récupérable ;
- action proposée ;
- confirmation attendue ;
- action exécutée ;
- résultat ou refus.

## 3. Session transparente en écoute

Source Stitch : `session_assistant_overlay_coute`.

### Composition à retenir

- application sous-jacente toujours perceptible mais visuellement calmée ;
- scrim sombre et vignette, sans masquer totalement le contexte ;
- surface principale ancrée vers le bas ;
- orbe, onde et transcription regroupées dans une hiérarchie courte ;
- microphone actif visible immédiatement ;
- annulation accessible sans défilement.

### Contraintes Android

- contenu sensible masqué sur écran verrouillé ;
- lisibilité garantie sans blur système ;
- surface compatible avec une largeur mobile de 360 à 430 dp ;
- commandes tactiles d’au moins 48 dp.

## 4. Mode vocal ambiant

Source Stitch : `mode_vocal_activation_ambiante`.

Le prototype interactif retenu est versionné dans [`examples/voice-ambient.html`](examples/voice-ambient.html).

### Composition à retenir

- écran focalisé presque vide ;
- orbe bleu centrale entourée d’une lueur violette très diffuse ;
- grand statut « Je vous écoute… » ;
- libellé microphone secondaire et discret ;
- absence de navigation pendant l’état vocal transactionnel.

### Mouvement à retenir

- respiration lente du halo ;
- pulsation liée à une amplitude normalisée ;
- rotations très lentes réservées au mode complet ;
- variante statique et variante sans shader obligatoires.

## 5. Overlay assistant compact

Source Stitch : `overlay_assistant_coute_active`.

Le prototype interactif retenu est versionné dans [`examples/overlay-compact.html`](examples/overlay-compact.html).

### Composition à retenir

- pilule glass flottante près du bas de l’écran ;
- action d’extension à gauche ;
- onde vocale compacte ;
- transcription tronquée avec masque progressif ;
- fermeture circulaire à droite ;
- halo bleu-violet localisé sous la barre.

### Composants réutilisables

- `GlassSurface` variante overlay ;
- `VoiceWave` compacte ;
- `AssistantInputBar` ;
- boutons d’icône circulaires ;
- texte streaming compact.

## 6. Confirmation et biométrie

Source Stitch : `confirmation_de_s_curit_biom_trie`.

### Composition à retenir

- sheet ou modal glass nettement séparée du contexte ;
- titre et conséquence de l’action avant les détails techniques ;
- niveau de risque visible mais non anxiogène ;
- justification déterministe dans un bloc secondaire ;
- action destructive ou sensible clairement différenciée ;
- bouton d’annulation immédiatement accessible ;
- passage à la biométrie présenté comme une étape Android distincte.

### Variantes nécessaires

- confirmation simple ;
- confirmation détaillée ;
- biométrie ;
- permission Android manquante ;
- ouverture d’un panneau système ;
- refus ;
- expiration ;
- écran verrouillé.

## 7. Réglages

Source Stitch : `r_glages_configuration_assistant`.

### Composition à retenir

- titre de page et description courte ;
- sections glass espacées ;
- lignes de réglage avec titre, sous-titre, état et chevron ou contrôle ;
- sélecteurs segmentés sobres ;
- switches compacts ;
- navigation inférieure flottante ;
- contenu dense mais jamais présenté comme une grille web.

### Primitives réutilisables

- `SettingsSection` ;
- `SettingsRow` ;
- `SegmentedControl` ;
- `ProviderChip` ;
- switch, bouton pilule, champ sécurisé et état de validation.

### À ignorer

Compte Premium/Admin, maison connectée, calendrier, Gmail et mode développeur tels qu’affichés dans l’ancien exemple.

## 8. Journal d’audit

Source Stitch : `journal_d_audit_s_curit`.

### Composition à retenir

- filtres dans des chips ou contrôles compacts ;
- événements présentés comme une liste structurée et non comme un tableau web ;
- date, outil, décision et résultat hiérarchisés ;
- codes techniques en Geist ;
- couleurs de statut très contenues ;
- détail d’un événement dans une surface élevée ;
- paramètres et erreurs expurgés explicitement indiqués.

### États nécessaires

- succès ;
- refus ;
- annulation ;
- expiration ;
- permission manquante ;
- erreur ;
- action bloquée sur écran verrouillé.

## 9. Diagnostic

Source Stitch : `diagnostic_syst_me_tat_local`.

### Composition à retenir

- résumé global en tête ;
- groupes de vérifications avec icône, libellé, résultat et détail facultatif ;
- statuts locaux/distants lisibles sans couleur seule ;
- données techniques en Geist et dans une surface secondaire ;
- actions de correction près du diagnostic concerné ;
- rapport expurgé clairement distingué des données sensibles.

### Vérifications prévues

- rôle assistant Android ;
- permission microphone ;
- STT et TTS ;
- fournisseur et profil actifs ;
- réseau et fonctionnement hors connexion ;
- stockage local ;
- Android Keystore ;
- registre d’outils ;
- audit ;
- dernière erreur expurgée.

## Variantes à dériver pendant l’implémentation

Les références approuvées sont principalement sombres. Les variantes suivantes sont des sorties attendues de l’issue #18 et non des ressources manquantes :

- thème clair cohérent, pas une inversion brute ;
- mode effets réduits ;
- fallback sans blur ;
- contraste renforcé ;
- grande taille de texte ;
- état hors connexion ;
- contenu masqué sur écran verrouillé.
