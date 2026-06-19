# Guide d'implémentation Android

Ce document décrit comment implémenter progressivement le client Android Hermes.

## 1. Stack recommandée

- Kotlin ;
- Jetpack Compose ;
- Material 3 ;
- architecture MVVM ou MVI ;
- Ktor Client ou Retrofit pour HTTP ;
- Kotlinx Serialization pour JSON ;
- Room pour les logs locaux ;
- DataStore pour les préférences ;
- Android Keystore pour les secrets ;
- WorkManager pour le polling des jobs en arrière-plan.

## 2. Modèles de données

### Requête utilisateur

```kotlin
data class MobileRequest(
    val requestId: String,
    val input: UserInput,
    val client: ClientInfo,
    val capabilities: List<ToolCapability>
)
```

### Action retournée par Hermes

```kotlin
data class HermesAction(
    val actionId: String,
    val tool: String,
    val arguments: JsonObject
)
```

### Résultat d'outil

```kotlin
data class ToolResult(
    val requestId: String,
    val actionId: String,
    val tool: String,
    val status: ToolStatus,
    val result: JsonObject? = null,
    val error: String? = null
)
```

## 3. Client Hermes

Interface cible :

```kotlin
interface HermesClient {
    suspend fun health(): HermesHealth
    suspend fun sendRequest(request: MobileRequest): HermesResponse
    suspend fun sendToolResult(result: ToolResult): HermesFollowUp
    suspend fun getJob(jobId: String): HermesJobStatus
}
```

Implémentation :

- injecter `baseUrl` depuis DataStore ;
- ajouter `Authorization: Bearer <token>` si configuré ;
- appliquer des timeouts courts pour les commandes simples ;
- appliquer des timeouts plus longs ou jobs pour les traitements lourds ;
- convertir les erreurs réseau en états UI lisibles.

## 4. Registre d'outils

Interface cible :

```kotlin
interface AndroidTool {
    val name: String
    val risk: ToolRisk
    val requiresConfirmation: ConfirmationPolicy
    suspend fun validate(arguments: JsonObject): ValidationResult
    suspend fun execute(arguments: JsonObject): ToolExecutionResult
}
```

Le registre expose uniquement les outils activés par l'utilisateur :

```kotlin
class ToolRegistry(
    private val tools: Set<AndroidTool>,
    private val policyStore: ToolPolicyStore
) {
    fun capabilities(): List<ToolCapability>
    fun find(name: String): AndroidTool?
}
```

## 5. Exécution sécurisée d'une action

Pipeline recommandé :

```text
HermesAction
  -> retrouver outil
  -> vérifier activé
  -> valider arguments
  -> vérifier permissions Android
  -> appliquer politique de confirmation
  -> exécuter
  -> journaliser
  -> renvoyer ToolResult à Hermes
```

Pseudo-code :

```kotlin
suspend fun handleAction(action: HermesAction): ToolResult {
    val tool = registry.find(action.tool)
        ?: return ToolResult.notFound(action)

    val validation = tool.validate(action.arguments)
    if (!validation.ok) return ToolResult.invalid(action, validation.message)

    if (confirmationManager.requiresConfirmation(tool, action)) {
        val accepted = confirmationManager.requestConfirmation(tool, action)
        if (!accepted) return ToolResult.denied(action)
    }

    return try {
        val result = tool.execute(action.arguments)
        actionLog.recordSuccess(action, result)
        ToolResult.success(action, result.payload)
    } catch (error: Throwable) {
        actionLog.recordFailure(action, error)
        ToolResult.failure(action, error.message ?: "Erreur inconnue")
    }
}
```

## 6. Outils MVP

### `set_volume`

- API : `AudioManager` ;
- risque : faible ;
- confirmation : non ;
- validation : niveau 0 à 100, stream connu.

### `open_app`

- API : `PackageManager.getLaunchIntentForPackage` ;
- risque : faible ;
- confirmation : non par défaut ;
- validation : package installé.

### `toggle_flashlight`

- API : `CameraManager.setTorchMode` ;
- risque : faible ;
- confirmation : non ;
- permission / capacité caméra selon appareil.

### `send_notification`

- API : `NotificationManager` ;
- risque : faible ;
- confirmation : non ;
- permission `POST_NOTIFICATIONS` sur Android récent.

### `open_settings_page`

- API : intents `Settings.*` ;
- risque : faible à moyen ;
- confirmation : selon page.

## 7. Voix

MVP :

- utiliser `SpeechRecognizer` Android pour STT ;
- utiliser `TextToSpeech` Android pour TTS ;
- bouton push-to-talk ;
- affichage de l'état : idle, listening, thinking, speaking.

Plus tard :

- wake word local ;
- VAD ;
- Whisper local ;
- Piper local ;
- provider cloud optionnel.

## 8. Overlay

Étapes :

1. demander la permission `SYSTEM_ALERT_WINDOW` ;
2. créer un foreground service pour piloter l'overlay ;
3. afficher une vue Compose flottante ;
4. connecter l'overlay au state global ;
5. animer l'orb selon amplitude micro et état Hermes.

États visuels :

- `Idle` : orb discret ;
- `Listening` : orb réactif au micro ;
- `Thinking` : halo lent ;
- `Speaking` : pulsation synchronisée TTS ;
- `Acting` : icône d'outil ;
- `Error` : teinte rouge/orange.

## 9. Jobs longs

Quand Hermes retourne un job :

1. enregistrer le job localement ;
2. afficher une carte de tâche ;
3. lancer un polling court si l'app est ouverte ;
4. planifier WorkManager en arrière-plan ;
5. afficher une notification quand le job est terminé ;
6. ouvrir la conversation au clic sur la notification.

## 10. Mode dégradé

Si Hermes ne répond pas :

- afficher l'état de connexion ;
- proposer de relancer le health check ;
- exécuter seulement les commandes locales simples ;
- ne pas simuler la mémoire ou l'orchestration Hermes côté Android.

## 11. Tests recommandés

- tests unitaires sur la validation des arguments d'outils ;
- tests unitaires sur les politiques de confirmation ;
- tests du client HTTP avec MockWebServer ;
- tests instrumentés pour les outils Android simples ;
- tests UI Compose pour les écrans de configuration ;
- tests manuels sur appareil réel pour overlay, voix et notifications.
