# Jean-Calcul Assistant

Assistant Android dérivé d’[ETA](https://github.com/Mangi-11/Eta), avec son interface COUI et les fonctions sélectionnées pour Jean-Calcul.

## Modèles

- **ChatGPT/Codex** : connexion avec l’abonnement dans Réglages → Fournisseurs de modèles → ChatGPT / Codex.
- **vLLM local** : renseigner l’URL accessible du serveur, par exemple `http://adresse-du-serveur:8000/v1`, puis récupérer ou ajouter son modèle. La clé API est facultative si le serveur ne l’exige pas. `127.0.0.1` désigne le téléphone. Android 17 exige l’autorisation de réseau local.

Les services vocaux se configurent séparément. Root et LSPosed sont optionnels ; les outils non disponibles sont signalés.

## Périmètre et provenance

[Sélection validée](docs/eta/SELECTION.md) · [Catalogue](docs/eta/FEATURES.md) · [Provenance ETA](docs/eta/UPSTREAM.md) · [Architecture](docs/ARCHITECTURE.md).

Pas de style Miuix sélectionnable, environnement Linux/Kimi, personnages, sauvegardes/exports ni intégrations ColorOS/constructeur/Google. Les dépendances internes et types de compatibilité amont ne sont pas des entrées produit.

## Développement

JDK25, SDK Android37 : `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`.
APK distribué ARM64, Android13+. Identité : `fr.loevan.jeancalcul.v2`. Lire [AGENTS.md](AGENTS.md) et [la validation](docs/QUALITY.md).

Licence GPL-3.0. Copyright amont © 2026 蛮吉 (Mangi-11) ; adaptations Jean-Calcul documentées dans la provenance.
