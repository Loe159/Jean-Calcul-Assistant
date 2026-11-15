# Jean-Calcul — instructions de développement

Assistant Android dérivé d’ETA GPL-3.0, source épinglée dans `docs/eta/UPSTREAM.md`.
La reprise et la sélection ont été expressément demandées le 14/11/2026.

- Lire `docs/PRODUCT.md`, `docs/ARCHITECTURE.md`, `docs/QUALITY.md` et `docs/eta/SELECTION.md`.
- Le périmètre est appliqué par `product/JeanCalculProfile.kt` : COUI, vLLM et ChatGPT uniquement pour la conversation. Les fournisseurs vocaux sont distincts.
- Réutiliser runtime, composants, services et tests amont ; pas de réimplémentation parallèle.
- Les types internes amont liés à Room/navigation peuvent rester pour compatibilité ; ne pas réexposer les blocs exclus.
- L’identité distribuée est `fr.loevan.jeancalcul.v2`. Le namespace Kotlin/JNI amont est conservé.
- Aucune dépendance obligatoire à root/LSPosed ; vérifier la disponibilité et les permissions avant toute action.
- Aucun secret, compte personnel ou donnée réelle dans les tests/artefacts.
- Conserver LICENSE et attributions. Ne pas fusionner ni installer sur un téléphone personnel sans demande.

## Vérification

JDK 25 et SDK Android 37. `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`.
Pour l’émulateur x86_64 : ajouter `-Pjeancalcul.emulator=true` ; cette variante ne valide pas le PTY ARM64/root.
`python3 e2e/run.py --run-id eta-selection --attempt 1` crée un AVD synthétique ;
ouvrir chaque PNG avec la vision native et renseigner les rapports. Maximum trois tentatives, conserver les échecs.
PR vers `feature/jean-calcul-v2-foundation`, jamais main.
