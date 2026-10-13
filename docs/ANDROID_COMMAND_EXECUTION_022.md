# Jarvis Android 0.2.22: one foreground command session

## Behavior

The user-started voice service submits recognized foreground requests to the same
Activity executor as typed messages. This includes its pending call, page,
settings, billing, glucose, navigation, attachment and AI-result state. The
completed result is spoken; the Activity records one history entry. A newer
request invalidates earlier asynchronous results. Background speech retains the
existing Android handoff restrictions.

Current, composed Jarvis controls register their label, enabled state and actual
UI callback. Button/tab selection and non-secret field editing are local. There
is no reflection, accessibility grant, third-party screen read, credential
extraction or model-selected Android callback in this mechanism. Disposed pages
unregister their controls. Duplicate labels require an explicit ordinal; disabled
controls cannot execute. The underlying form still owns input validation,
persistence and the outcome of network/Android requests.

Every subpage has a Parancs button opening one command dialog without leaving the
form. Existing voice listening continues across subpages. Examples:

- Nyisd meg a Jegyzetek oldalt
- Írd az Új jegyzet mezőbe: tej és kenyér
- Nyomd meg a Mentés gombot
- Sorold fel az oldal vezérlőit
- Nyomd meg a második Mentés gombot (duplicate labels)
- Görgess le / vissza

Password fields, OS permissions and the Android installer keep their real user
interfaces. A control invocation is reported as a control invocation, not as
proof that an email was delivered or an installation completed.

## Android assistant entry

The settings page links to Android's assistant selection screen. An OS-protected
VoiceInteractionService and session launch Jarvis when the user invokes their
selected assistant. The launch carries a one-use, short-lived in-process token;
external apps cannot start the microphone by spoofing an exported Activity action.
Screen contents are not inspected. Microphone permission and an unlocked phone
are still required. This is not a claim of an always-on wake-word engine.

## Reference inspection

The user-supplied ChatGPT 1.2026.272 XAPK was inspected as an archive: base APK,
ARM64, English and density splits; manifest permissions, voice-interaction
services, voice/audio module names and a peer-connection native library were
observed. These observations do not reveal ChatGPT's backend or prove an API
contract. No ChatGPT implementation, binary library, assets, private endpoint or
credentials were copied into Jarvis.

The useful design principle is shared text/voice execution and separate audio
transport. Public sources:

- https://developers.openai.com/api/docs/guides/voice-agents
- https://developer.android.com/reference/android/service/voice/VoiceInteractionSession
- https://developer.android.com/develop/ui/compose/testing/synchronization

## CI failures examined

UI runs 170, 171, 172, 175, 179, 182, 183, 189 and 197 failed on a missing
subpage header at varying pages; 170–172 also missed the update button. Run 174
missed the chat answer. These tests clicked the send/microphone button while the
IME/layout could change. They now use the real IME Send action and await the
actual destination/answer. The new persistence tests exercise the shared voice
bridge rather than assigning a navigation destination directly.

Run 180 lacked the accessibility description resource (subsequently supplied).
Run 190 lacked the attachment list argument (subsequently supplied).
Attachment-only Send was still selecting dictation; this change fixes that
behavior and its send icon. Test reports are retained on every emulator run.

## Verification boundary

Emulator results do not prove microphone recognition, AI audio quality, outgoing
calls, Google Maps, Messenger, Libre integration, assistant gestures or update
installation on a physical Redmi. Those require device and account access.
