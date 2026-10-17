# Jarvis Mobile — Android (Kotlin) & iOS (Swift)

> **Current source of truth:** Android `versionName`, `versionCode` and
> `applicationId` live in [android/app/build.gradle.kts](android/app/build.gradle.kts).
> Sections below describe **historical milestones**, not the current installed
> build or a promise that every feature is verified. Check the exact GitHub
> release tag, source commit and [manual QA matrix](docs/ANDROID_SIGNED_RELEASE_QA_MATRIX.md)
> before installing or publishing any update.

## Android 0.2.3-dev — guarded AI → native Android tool execution (test)

Real function-calling now connects both the direct OpenRouter chat mode and the self-hosted server mode to the installed Jarvis Android UI. The AI can *propose* opening one existing menu page. The Android app independently checks that the original user explicitly requested opening something, rejects unsupported functions, extra parameters and multiple actions, and then navigates through its existing foreground handlers. Voice requests wait for the Jarvis foreground activity. Ordinary generated text cannot execute commands.

This is an **allowlisted navigation bridge**, not unrestricted Android automation. Sending messages, account actions, system settings modification, background automation, deleting data, or autonomous self-repair are **not** enabled. The hosted server must be deployed/updated separately to use its mode; the OpenRouter direct mode still requires each tester's individual key. Security and manual test plan: [docs/AI_TOOL_BRIDGE_023.md](docs/AI_TOOL_BRIDGE_023.md).

## Android 0.2.2-dev — Jarvis identity and tool awareness

Jarvis' AI system messages identify the Jarvis Mobile app separately from its underlying AI provider. A local offline self-description reads the real Android menu catalog, so questions such as "Ki vagy?", "Mire vagy képes?" or "Listázd az összes eszközöd" no longer return generic AI-provider descriptions. Existing local commands remain deterministic; explicit page-opening requests now also reach the Munka, Ismerősök and Névjegyek pages in text and voice. Topic questions no longer inadvertently open an app. The LLM still cannot independently execute arbitrary Android actions or access private accounts. See the Android debug APK workflow and regression tests; physical-device testing is still required.

## Android 0.2.1-dev — direct OpenRouter AI for personal testing

Settings → AI-agy now offers **Közvetlen OpenRouter**, where each tester can enter a personal, low-spending-limit OpenRouter API key once. This removes the need for the undeployed Jarvis HTTPS server during small private tests. The key is stored locally with Android Keystore-backed encryption and is sent only directly to OpenRouter via HTTPS. There is **no API key embedded in the APK, GitHub source code or release**. The original **Saját szerver** connection mode remains available. This direct mode is not suitable for an uncontrolled public launch: phone owners can access/extract their own credentials, and each request can incur API charges. Previous keys shared in messages or otherwise exposed must be rotated. No voice transcript or private device data is automatically uploaded; explicit text questions are sent to the selected provider.



## Android 0.2.0-dev — AI test pairing (not yet deployed)

The Android app now supports separately paired owner/family test accounts through a self-hosted HTTPS AI gateway; typed and voice questions go to the backend. Provider credentials stay exclusively on the server. The server needs deployment, real secrets and tester code provisioning before the APK can give AI answers. No public login, image AI or agentic device control. See [tester gateway guide](docs/AI_GATEWAY_ROADMAP.md) and GitHub Actions for an installable debug build.


Separate **native mobile** Jarvis repository, independent from the Windows Electron app. This is a first **functional Basic prototype**, not a production release.

## Android 0.1.9-dev — revised menu, settings and file opening

The home Jarvis/Munka switch is removed. Open the side menu by swiping or tapping the central Jarvis name. System Status, the duplicate Map entry and the Files/Photos page are removed. Notes opens the phone's notes application. Settings uses compact grouped rows and Android-owned permission dialogs, with system pages for special access. EXE attachments, external file opening and format-only Play Store search are supported. Short greetings work offline in both typed and hands-free commands. [Test scenarios and limits](docs/JARVIS_ANDROID_0.1.9_TEST.md).

## Android 0.1.8-dev — simplified home and tools navigation

The top-left menu button is removed from Home and every subpage, including System Status. All previous tools are available under **Munka**, including permissions/settings; Navigation and Map still open the installed map app. The centered Jarvis/Munka switch, theme toggle, system Back navigation and bottom prompt/attachment bar remain available. See [Android test scenarios](docs/JARVIS_ANDROID_0.1.8_TEST.md).

## Android 0.1.7-dev — voice-first Jarvis home (internal only)

The approved light white/cyan design is implemented as **native Jetpack Compose**, not a static screenshot. Home shows a reactive Jarvis orb (animated wave ribbons and center waveform), a two-line drawer button, Chat/Munka switch, and a bottom prompt bar with dictated command, tools access and opt-in 15-minute voice session button. The drawer includes every requested Basic tool; routes open real Android screens or truthful external-app handoff explanations. One-shot home prompts run through the **same existing local executor as Chat**, not a pretend AI bot.

Orb motion is driven by Android speech recognizer RMS and by synthesized speech PCM energy **when the chosen TTS engine supplies it**. Where TTS sample callbacks are unavailable, an ambient speaking animation remains. Tight audiovisual timing, hands-free audio capture and background operation require physical Android device verification.

**Not production-ready:** cloud AI is not connected; third-party messages and UC Journal do not gain unauthorized account access. This is a debug build, with no GitHub Release or Play Store publication.

## Android 0.1.6-dev — named contacts and cross-app voice handoffs (internal only)

This adds an encrypted, user-edited **nicknames + phone numbers + addresses** directory, including Hungarian name endings such as "drágámat" and "Eszterhez". With explicit contact mapping, a spoken phone-call request still requires confirmation. Navigation can use the contact's saved address.

Voice messages can be dictated, spoken back and confirmed before **opening** WhatsApp (phone-number deep link) or Messenger (share/recipient-selection UI). The **message is not automatically sent** inside those apps. Messenger cannot identify a personal friend by name through a public third-party API in this build. Gmail can only open a web search; email reply/read via Gmail API requires separate OAuth consent. Jarvis opens the **official** GOV.UK UC sign-in page but does **not** enter login details or check Journal updates.

All code remains **development/debug-only**; compilation/Lint are not proof of real-device behavior. See [full test scenarios](docs/ANDROID_VOICE_TASKS_0.1.6-dev.md) and [internal QA checklist](docs/ANDROID_INTERNAL_QA.md). No cloud AI, production Android signing, Play release or full-account integration has been created. The repo is public; do not commit provider secrets.

## Android 0.1.5-dev — internal development (no public release)

The development build includes the approved blue Jarvis orb launcher icon, a cleaned-up Chat screen (the "Próbáld..." example removed), and Hungarian alarm commands. The app can schedule **one Jarvis-owned** Android AlarmManager alarm at a time and cancel it by command. Alarm timing and notification permissions are requested separately. The alarm currently triggers an Android sound notification rather than a fully tested full-screen alarm; **do not rely on it as your only important wake-up alarm**.

The optional, user-started 15-minute "Hey Jarvis" foreground voice mode can handle supported commands, including a spoken reply to a supported messaging notification with explicit yes/no confirmation. Android voice recognition is **not a guaranteed always-on/offline hotword engine**; recognition can depend on device services, network and background restrictions.

- **Phone:** Contact lookup and voice-initiated phone calls require explicit contacts/phone permissions, exact matching and spoken yes/no confirmation; sending a call request is not proof the remote party answered.
- **Navigation:** Spoken destination parsing and an encrypted on-device home address; map activity opens when Android permits it.
- **Email:** Creates a draft in the user's mail app for review, **never sends in the background**, and does not yet read Gmail.
- **AI:** `server/` contains an authenticated, *non-deployed* backend proof of concept and a separate type-check workflow. **No live AI key, AI login or mobile AI connection has been configured.**
- **Internal only:** GitHub Actions produces debug APK artifacts, but no GitHub Release, Play Store submission or production signing has been created.

The native Android and gateway builds are automated, but real-device checks remain necessary. The source repository is currently **public**; switch it to **private** before commercial code, keys, customer records or sensitive integration work.

## Android 0.1.4 — Hey Jarvis voice mode (beta)

- Start from the app Home screen; grant microphone and optional notification permissions.
- Once explicitly started, a microphone **foreground service** shows a persistent Android notification with **Stop** action. The session lasts at most **15 minutes**, never starts on boot and can be stopped from the app or by voice ("állj le").
- Speak **"Hey Jarvis"**, then a supported command (or give both together). Currently supported: opening Jarvis pages when the app is visible, reading fresh WhatsApp/Messenger and other supported notification previews, and dictating a reply.
- To send a notification quick reply, Jarvis reads the draft and asks **"Elküldhetem?"**. Only an explicit **"igen"** within 40 seconds invokes Android RemoteInput. "Nem" cancels. Delivery confirmation is **not** available.
- SpeechRecognizer prefers a device recognizer when available and restarts between utterances. **Android explicitly warns SpeechRecognizer is not intended for continuous wake-word monitoring**. It can use a remote voice provider, consume substantial battery, stop working on a locked/backgrounded phone, or terminate on device restrictions. This is a *limited experimental voice session*, NOT a reliable all-day always-on assistant. True on-device low-power keyword detection needs a separate wake-word engine and physical-device tests.
- Background opening of arbitrary Android apps and full message history remain unsupported by Android's current APIs. No remote AI integration or API key is present.

## Android 0.1.3 — permissions and messaging (2026-10-11)

- A first-run, **opt-in** permission center with per-feature controls and an explicit Continue button. Permissions remain optional.
- **Notification listener**: user enables it via Android Settings. This permission technically allows access to all app notifications; Jarvis processes only WhatsApp, WhatsApp Business, Messenger, Google/Samsung Messages, Telegram and Signal notifications. Messages stay in volatile app memory, max. 30, not uploaded to AI.
- **Read aloud**: a user presses the Read button on a visible notification preview. **Reply**: user types, reviews and presses Send. This uses Android RemoteInput **only if that app exposes a supported inline quick-reply action**. No autonomous sending or access to complete message history.
- **GPS**: a separate permission request for precise/approximate coordinates; displays the *last known* location on an installed map, not guaranteed real time.
- **Photos and files**: scoped Android photo picker and Storage Access Framework document picker. No blanket gallery, storage or all-files access.
- **Contacts**: optional Android READ_CONTACTS grant, local lookup of up to 80 entries, opens dialer for explicit user action.
- **Email**: opens the system email composer; reading/managing Gmail requires a separate Google OAuth authorization flow (not available yet).
- **Speech**: existing voice command recognition is mediated by a compatible installed Android recognition provider, and is not a 24/7 listening system.
- Android 0.1.3 is a **debug preview**, not a production Basic app. Automatic AI agent replies, actual WhatsApp/Messenger chat API access, full account synchronization, and Google Play policy review remain unfinished. Some Android devices restrict notification-listener access for sideloaded apps.
- **Do not paste a cloud AI API key into chats or commit it to GitHub.** Provider secrets must be kept on a restricted backend and accessed through authenticated, per-user requests. This repository was public at the time of development; change it to private before proprietary or sensitive integration work.

## Android 0.1.2 test build — 2026-10-11

The **Android 0.1.2** native prototype now includes:

- Safe status-bar and navigation-bar layout on Android 15/16, system Back navigation to Home, and readable dark-page headings.
- Encrypted local glucose readings, invoice drafts, and notes using AES-GCM with an Android Keystore key, with one-time migration of earlier local test data.
- Glucose history, average, min/max, and on-device trend chart (not a medical decision tool).
- System email compose, maps, calendar event creation, alarms and dialer (each opens the system app for the user's action/confirmation).
- Local deterministic typed/spoken commands, speech recognition through an available installed Android speech service, and on-device text-to-speech. This is **not** remote LLM chat.
- Android CI that compiles the APK and runs Kotlin unit tests for offline command classification.

**Deployment truth:** The debug APK is installable, but this is **not a production Basic subscription**. Provider-authenticated live AI, Gmail account reading, compliant tax invoicing, cloud sync, Libre 2 integration, OBD2 and subscription enforcement require dedicated work, external service configuration and device testing. Do not use actual sensitive health/business data in an unverified debug build. The mobile app does not include Windows Self-Repair.

**Debug APK signing:** GitHub-hosted runners can generate different debug signing keys on different runs. An update may not install over an earlier test APK. Back up non-sensitive test data before uninstalling; uninstalling erases local app data. For reliable updates and production distribution, configure a protected persistent release signing key through repository secrets and a release workflow. Never commit the private signing key to this repo.

## What runs now

- Native dark Jarvis dashboard with navigation.
- Android: locally encrypted blood glucose logging (mmol/L), history, chart and deletion; iOS: prototype local logging only.
- Local invoice **drafts** and native PDF export/share (not UK tax/VAT-compliant invoicing yet).
- Android: open system email, maps, calendar, alarms and dialer; iOS: system email and maps.
- Local notes.
- Android: voice input, spoken playback and real offline system command routing; cloud AI chat **not yet connected**. iOS: chat placeholder only.
- Self-Repair **intentionally excluded** from both mobile apps.

Current app data is stored **on-device only**. There is **no account sync, no Libre 2 connection, no OBD2 connectivity, no Gmail OAuth, no live AI, no background speech assistant**, and no validated localisation of 42 languages yet. These require further implementation and tests.

## Install Android prototype

GitHub **Actions → Build Android APK → latest successful run → Artifacts → Jarvis-Android-Debug-APK**. Download the artifact ZIP, extract **app-debug.apk** and install on an Android device after explicitly allowing installation from that trusted file source. A debug APK is suitable only for testing, not distribution.

Or build locally with Android SDK 35, JDK 17, Gradle 8.9:

    gradle -p android :app:assembleDebug

Then find the APK at \`android/app/build/outputs/apk/debug/app-debug.apk\`.

## Open iOS prototype

Use **macOS + Xcode**. Install [XcodeGen](https://github.com/yonaskolb/XcodeGen), then:

    cd ios
    xcodegen generate --spec project.yml
    open JarvisMobile.xcodeproj

Choose the \`JarvisMobile\` scheme and an iPhone simulator, then Run. A simulator build is checked by **Actions → Build iOS Simulator**. To install on a physical iPhone, **Apple code signing and a development provisioning team** are needed; this repository does not supply signed IPA/TestFlight builds yet.

## Next milestones (not yet delivered)

1. Secure account system + centralized AI billing gateway; **never store a shared AI provider secret in mobile apps**.
2. Real chat with model routing, streaming, voice recognition and speech output.
3. Shared PC/mobile storage with encrypted health-related data.
4. Real Gmail OAuth and legally complete invoice functionality with audit trails.
5. Permissions-driven integrations for navigation, OBD2, optional glucose sensor links if supported, notifications and accessibility-safe phone actions.
6. QA on physical Android/iOS devices; localization, accessibility and app-store compliance.

No third-party proprietary source from the Windows repository is included. Everything starts as original Kotlin/Swift code.

## Security

This repository contains **no API credentials or customer data**. Keep it **Private** before commercial development. Sensitive glucose records need encryption and privacy review before production. The first prototype is not medical software or financial compliance software.
