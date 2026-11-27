# Assist

The tablet listens for a wake word on the device. Speech-to-text, the conversation, and the spoken reply
stay on the Assist pipeline the screen already uses. Room audio leaves the tablet only after the wake word,
and only for that command.

Assist is off until a screen turns it on (`ScreenSettings.assist`, default false).

## Turn it on

In the screen's settings, under **Standby**, tick **Assist**. Pick a wake word and a pipeline, then
**Save & send**.

| Wake word id | Phrase |
| --- | --- |
| `okay_nabu` | Okay Nabu (default) |
| `hey_jarvis` | Hey Jarvis |
| `hey_mycroft` | Hey Mycroft |
| `alexa` | Alexa |

Any other id is stored as `okay_nabu`. The models are English (`trained_languages: ["en"]` in each
`player/src/main/assets/wakewords/*.json`). A phrase that exists only as openWakeWord is not heard.

**Pipeline** blank means Home Assistant's preferred pipeline. A saved id longer than 80 characters is
dropped. The editor loads the list from `GET /api/assist/pipelines`, which calls
`assist_pipeline/pipeline/list`. If that call fails, the dropdown stays on **Preferred**.

The Home Assistant integration exposes one config switch, **Assist** (`switch` key `assist`). It sets the
boolean only. Wake word and pipeline stay whatever the screen file already has.

The Supervisor panel repeats the user steps in [addon/DOCS.md](../addon/DOCS.md#assist).

## What the tablet does

`AssistCoordinator` keeps one microphone open. After the bundled model hits, it resets the detector and
ignores the wake word for two seconds (100 chunks of the 20 ms capture). The same capture then feeds the
pipeline. When Home Assistant is thinking or speaking, command audio stops and the capture keeps feeding
only the wake word. Saying the phrase again after that cooldown cancels playback and the socket and starts
another turn.

A new engine is warmed with four seconds of quiet noise (400 chunks) before the microphone opens. A cold
frontend misses the next phrase. The engine stays open across turns. `AcousticEchoCanceler` and
`NoiseSuppressor` attach on that capture when the device has them.

The wake chime is `wake_word_triggered_cue.pcm` (48 kHz, 16-bit mono) played on an `AudioTrack` with
`USAGE_MEDIA`. The FLAC next to it is the licensed source and is not what the tablet plays. A chime
failure is logged as `AssistChime` and the turn still starts. The spoken reply is the pipeline's
`audio/mpeg` bytes. Playback is treated as finished one second after the audio stops, including when the
player stays in `BUFFERING`.

Mute is the local verb `assist`. The player swaps `microphone` and `microphone-off` through
`sys.assist_mic` and `sys.assist_muted`, so mute does not rebuild the document. There is no second
microphone painted over the bar. An add-on built before that icon does not send it.

## What the add-on asks of Home Assistant

The tablet socket is `/assist` on the player port (8100). It opens for one command, including a follow-up,
then closes. The first text frame is `hello` with the tablet token, optional `pipelineId`, and `wakeWord`.
An untrusted token closes the socket. Spoken audio is binary, not a `Protocol` text message.

The add-on opens a separate Home Assistant websocket for the run. The state client reads text only and
cannot carry the PCM. The run is `assist_pipeline/run` with `start_stage: stt` and `end_stage: tts`. The
pipeline's own wake-word stage is not used. The first turn sends `wake_word_phrase`; audio from before the
hit is not sent. Binary PCM to Home Assistant is the `stt_binary_handler_id` byte from `run-start`, then
the samples. An empty frame ends the utterance.

Home Assistant sends JSON null for an absent object. Parsing those events with `jsonObject` throws, and
the socket then closes before `ended`, so the tablet never returns to the wake loop. `parsePipelineMessage`
uses a null-safe object read.

When the pipeline sets `continue_conversation`, the tablet sends `ready` after the reply finishes and the
add-on runs speech-to-text again with the same `conversation_id`. That stops when the pipeline does not ask
to continue, the turn fails, or the tablet does not report ready within 20 seconds. `run-end`, a timeout,
or an error closes `/assist`. It does not start a Home Assistant wake-word run.

Text frames from the add-on are `phase`, `transcript`, `reply`, `speech` (the next binary frame is the
reply audio), `ended`, and `failed`.

## Assist page

Add **Assist** from **Other tiles**. It is a full page and does not follow an entity. The Remote Compose
document is a hole (`LiveAssist`, first component id `0x62_0000`). The conversation is native, so a partial
transcript does not rebuild the document. Players that advertise `assist` receive `ServerMessage.AssistPage`
(phrase, pipeline label, theme colours). Older players see "Update the tablet app for Assist".

On any other page, the heard line and the reply sit in a caption above the navigation bar. It clears when
the turn ends, including when the socket drops. The Assist page keeps the last twelve lines. Below 700 dp
wide, the phase and Mute key stack above the conversation. The wake word does not change pages.

The phase line is Listening, Hearing you, Thinking, Speaking, Muted, or Unavailable.

## Microphone, intercom, and standby

The manifest permission is `RECORD_AUDIO`. A device-owner kiosk is granted it at process start, with no
prompt. Everyone else is asked on the rising edge of **Assist**, and only while Android has not already
granted it (an intercom grant counts). Declining shows the intercom denial toast
("Talking on the intercom needs the microphone. This tablet can still hear a call."). The microphone
is not opened. The page can still say **Listening** until the permission is granted; **Unavailable**
means the wake-word library did not load. Long-press the top bar and choose **Allow the microphone
for Assist**. That button is hidden while the kiosk is locked, because lock task blocks the system
screens.

An intercom call sets Assist paused: the detector stops and the microphone is released. Assist applies
again when the call ends, and that new engine is warmed before it listens.

Dimmed standby keeps the activity, so listening continues, and a hit calls the same wake path as a touch.
Real screen-off (`standbyScreenOff` and a device admin) starts `AssistListenService`
(`foregroundServiceType="microphone"`) with a low-importance notification, **Listening for Assist**, plus
the partial wake lock the player already holds. Some panels still drop the microphone when the display is
off. Mute and a paused intercom call do not start that service.

## Build constraints

The detector is the microWakeWord JNI engine in `player/src/main/cpp/microwakeword` (the same shape as the
Home Assistant companion). A TensorFlow interpreter on the raw `.tflite` skips the audio frontend and
misses the phrase. Each model's `probability_cutoff`, `sliding_window_size`, and `feature_step_size`
construct the detector.

Native libraries are `arm64-v8a` and `armeabi-v7a` only. x86 was left out because a tablet update that
carried every ABI was too large to install. An x86 emulator will not install this APK. If `libmicrowakeword`
fails to load, the Assist page stays on **Unavailable** and the coordinator retries.

The player build needs the Android NDK and CMake. The first native compile downloads the pinned kissfft,
tflite-micro, flatbuffers, gemmlowp, and ruy archives, so that build needs network access.

A device-owner tablet installs a higher `versionCode` with no prompt and Android kills the process. A
debug APK whose `versionCode` is below the installed release is replaced by that release. The chime is
silent again if the installed build is one that still played the FLAC.

## Demo

`DemoAssistRuns` does not open a Home Assistant socket. It reports the transcript "Turn off the kitchen
lights", the reply "The kitchen lights are off", and empty speech bytes. The editor's pipeline list is one
row named Preferred. The tablet still opens the microphone when Assist is on and the permission is granted.
`assistPage()` in the demo layout is a helper for tests and screenshots; it is not on the default demo
screen. The editor mockup draws the header microphone when `screen.settings.assist` is set.

## Where to look

| Piece | Path |
| --- | --- |
| Settings and validation | `shared/.../Layout.kt`, `shared/.../Assist.kt` |
| Wake-word engine | `player/.../microwakeword/MicroWakeWord.kt`, `player/src/main/cpp/microwakeword/` |
| Models | `player/src/main/assets/wakewords/` |
| Listening loop | `player/.../assist/AssistCoordinator.kt` |
| Tablet socket | `player/.../assist/KtorAssistTurnClient.kt` |
| Add-on bridge and pipeline | `server/.../assist/` |
| Page hole | `server/.../render/panels/assist/` |
| Native conversation | `player/.../ui/screens/player/assist/` |
| Editor controls | `editor/src/components/ScreenPanel.vue` |
| Home Assistant switch | `custom_components/remote_screens/switch.py` |

## Troubleshooting

- **Unavailable:** the native library did not load, or this device is not one of the two ARM ABIs. Confirm
  with `adb shell getprop ro.product.cpu.abi`.
- **Says Listening, but never hears you:** the microphone was denied, or the tablet is not paired yet
  (no token). Grant **Allow the microphone for Assist** in the tablet's settings. **Muted** is the
  header microphone or the page's Mute key. **Unavailable** means the wake-word library did not load.
- **Hears nothing:** the phrase is one of the four bundled models, in English. A custom wake word is
  ignored. The two-second cooldown also ignores the phrase that just hit.
- **Page says "Update the tablet app for Assist":** the player did not advertise the `assist` capability.
  Install the current APK. Update the add-on as well so the header microphone and the page shell match.
- **Chime missing, reply still spoken:** logcat tag `AssistChime`. The turn is supposed to continue.
- **Listening dies after a reply and never returns:** an add-on that still throws on JSON null in pipeline
  events closes `/assist` before `ended`. Update the add-on.
- **Silent while the screen is off:** the **Listening for Assist** notification should be posted. Some
  panels drop the microphone anyway. Dimmed standby (screen not locked off) does not use that service.
- **Intercom:** Assist stays paused for the whole call, then warms a new engine.

On-device checks are in [TESTING.md](TESTING.md#assist-checks).

```sh
./gradlew :shared:test --tests '*AssistSettingsTest' \
  :server:test --tests '*AssistPipelineTest' \
  :player:testDebugUnitTest --tests '*AssistCoordinatorTest'
cd editor && npm test -- --run src/components/assistMockup.test.ts
```
