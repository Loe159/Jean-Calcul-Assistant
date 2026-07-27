# Phase 1 voice pipeline validation

Issue: #20

## Automated coverage

The unit suites cover:

- partial and final recognition results;
- listening and transcription timeouts;
- RMS normalization and stable silence detection;
- Bluetooth, wired and built-in route classification;
- propagation of a changed BCP 47 locale to recognition;
- transient audio-focus loss and automatic listening resume;
- release of active audio resources after cancellation or recognition error;
- text input when Android recognition is unavailable.

Run:

```bash
./gradlew :core-domain:test :feature-voice:testDebugUnitTest :assistant-session:testDebugUnitTest
```

## Device procedure

Use the Samsung reference device from `device-matrix.md`, the `coreDebug` variant and an expurged
`JeanCalculPerf` log. Do not record audio or full transcriptions.

| ID | Initial state | Action | Expected result |
| --- | --- | --- | --- |
| VP-01 | Bluetooth headset connected and selected by Android | Invoke, speak, disconnect, reconnect | Bluetooth wave state is visible; recognition either continues or returns a recoverable error; the microphone is released after disconnect. |
| VP-02 | Assistant listening | Receive and answer an incoming call, then hang up | Audio focus interrupts listening; no recognizer remains active during the call; listening resumes after focus returns. |
| VP-03 | Locale `fr-FR` | Dictate a French phrase with partial results | Partial text appears, silence completes the phrase and the final result uses the French recognizer. |
| VP-04 | Change system locale to `en-GB` while the session is visible | Start a new listening request and speak English | The next request uses `en-GB`; an unavailable language pack produces a recoverable message and text remains usable. |
| VP-05 | Disable or remove the installed recognition service | Invoke the assistant | Voice is reported unavailable; text submission still produces a structured result and response. |
| VP-06 | Start listening, then press Back or Interrupt | Immediately invoke again | The first recognizer is destroyed, focus is abandoned and the second invocation can acquire the microphone. |

For each physical run, record date, build SHA, Android/One UI version, headset model, installed STT
and TTS engine, locale, result and a link to the expurged trace in issue #20. Hardware results are
not inferred from unit tests.
