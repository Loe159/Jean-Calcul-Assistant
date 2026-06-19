# Roadmap

## Phase 0 — Documentation et contrat

- Définir la vision produit.
- Définir le protocole HTTP Hermes.
- Définir les responsabilités Android / Hermes.
- Définir la politique de sécurité locale.

## Phase 1 — Client Android minimal

Livrables :

- projet Android Kotlin / Compose ;
- écran de configuration Hermes ;
- stockage base URL et token ;
- bouton test de connexion ;
- `GET /api/mobile/health` ;
- écran conversation texte ;
- `POST /api/mobile/request`.

Critère de succès : envoyer un message texte à Hermes et afficher sa réponse.

## Phase 2 — Outils Android MVP

Livrables :

- registre d'outils ;
- `set_volume` ;
- `get_volume` ;
- `open_app` ;
- `toggle_flashlight` ;
- `send_notification` ;
- `POST /api/mobile/tool-result`.

Critère de succès : Hermes peut demander une action simple et recevoir son résultat.

## Phase 3 — Voix

Livrables :

- bouton parler ;
- STT Android ;
- TTS Android ;
- états UI listening / thinking / speaking ;
- réponse vocale.

Critère de succès : commander vocalement une action simple via Hermes.

## Phase 4 — Sécurité

Livrables :

- politiques par outil ;
- outils activables / désactivables ;
- confirmations locales ;
- journal d'actions ;
- validation stricte des arguments ;
- stockage sécurisé du token.

Critère de succès : Hermes ne peut pas exécuter une action sensible sans validation locale.

## Phase 5 — Jobs longs

Livrables :

- modèle local de job ;
- polling foreground ;
- polling WorkManager ;
- notifications de fin ;
- écran tâches récentes.

Critère de succès : Hermes peut lancer une tâche longue et notifier Android à la fin.

## Phase 6 — Overlay Gemini-like

Livrables :

- permission overlay ;
- service foreground ;
- orb animé ;
- transcription ;
- réponse courte ;
- carte de confirmation.

Critère de succès : utiliser l'assistant au-dessus d'une autre application.

## Phase 7 — Intégration Android

Livrables :

- tuile rapide ;
- notification persistante ;
- raccourcis ;
- assistant numérique par défaut si possible ;
- documentation limitations bouton Power.

Critère de succès : déclencher l'assistant rapidement sans ouvrir manuellement l'application.

## Phase 8 — Accessibilité optionnelle

Livrables :

- service d'accessibilité ;
- lecture d'écran ;
- actions UI limitées ;
- confirmations renforcées ;
- mode observer seulement.

Critère de succès : permettre à Hermes de demander des actions d'interface sous contrôle strict de l'utilisateur.

## Phase 9 — Stabilisation 1.0

Livrables :

- documentation utilisateur ;
- documentation développeur ;
- tests automatisés ;
- optimisation batterie ;
- builds signés ;
- préparation F-Droid si souhaitée.
