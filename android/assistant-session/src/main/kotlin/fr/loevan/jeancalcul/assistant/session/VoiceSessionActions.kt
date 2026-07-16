package fr.loevan.jeancalcul.assistant.session

internal interface VoiceSessionActions {
    fun startListening()

    fun requestMicrophonePermission()

    fun cancelVoice()

    fun confirmVoiceCommand()

    fun speakTestResponse()

    fun textChanged(text: String)

    fun submitText()
}
