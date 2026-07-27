# 0009 - Replaceable Android voice pipeline

Status: accepted
Issue: #20

## Decision

`core-domain` owns platform-neutral contracts for speech recognition, speech synthesis,
microphone amplitude, voice activity, audio focus and input routing. A voice provider exposes a
stable identifier and capabilities so a later settings screen can select another implementation.

`feature-voice` owns the Android implementations. `AndroidVoicePipelineFactory` builds the
default `SpeechRecognizer` and `TextToSpeech` providers together with their audio signals. The
assistant session receives the resulting pipeline and does not construct either Android engine
directly.

The recognizer sends its real RMS values to `AudioAmplitudeSource`. Values are normalized to
`0..1`; `VoiceActivityDetector` adds a stable speech/silence state. The session passes the real
microphone amplitude to `VoiceWave` and `GradientOrb` only while listening.

Android audio focus is acquired for recognition and synthesis. A transient loss interrupts the
active work and remembers an interrupted listening session. Listening restarts after focus is
regained. A permanent loss becomes a recoverable assistant error. Cancelling or failing an
interaction destroys the active recognizer, stops the amplitude/VAD sources, unregisters route
callbacks and abandons audio focus.

The locale is a BCP 47 tag carried by each STT/TTS request. A configuration change updates the
next request without recreating a parallel session state. Bluetooth is detected from Android
input devices; Android remains responsible for selecting and routing the actual microphone.

## Consequences

- The text fallback remains available when recognition is missing, busy or denied.
- `assistant-service` remains minimal and owns no audio resource.
- No audio or transcription is persisted by the voice pipeline.
- A future provider only needs to implement the domain contracts and be returned by a different
  `VoicePipelineFactory`.
- Physical Bluetooth, telephony and language-pack behavior still depends on the installed Android
  services and must be revalidated with the device procedure for every target build.
