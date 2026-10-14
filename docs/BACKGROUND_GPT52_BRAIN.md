# Jarvis Mobile — bounded background GPT-5.2 brain

## What this PR actually does

- Existing Jarvis AI-consent + configured personal OpenRouter or private gateway connection are prerequisites.
- No additional button is added. The Android Activity schedules a unique WorkManager job at first launch.
- Work runs approximately daily **only while charging, battery not low and on unmetered network**. Android can delay work.
- A maximum of one provider request per UTC day is attempted for public developer learning (Kotlin/Android/Compose/Swift/iOS), using a rotating curriculum. This can consume paid model/web-search credits. A failed request is not retried immediately.
- Only public, fixed teaching topics are uploaded in this job. No chat transcripts, contacts, files, notifications, health data, API key in source, or live device state.
- Research notes are AES-GCM encrypted with existing SecurePrefs, marked **unverified**, and expire from relevance after 14 days. They are only included on a later *user-initiated*, relevant coding/debug question; they are never treated as executable instructions.
- GPT-5.2 direct-chat requests now use adaptive bounded reasoning: none for short chat and tool proposals, low for explanatory requests, medium for demanding coding/debug tasks. The server follows a separate bounded medium policy **only when** configured to run openai/gpt-5.2. Both modes preserve the Android tool allowlist and confirmation checks.

## What it deliberately does NOT do

- No AI model fine-tuning, no claim that neural weights improve.
- No 24/7 microphone access, passive file reading, device command execution or background message/email sending.
- No automatic changes to the Jarvis code or releases.
- No source verification guaranteed: the official-domain check reduces irrelevant results, but a model can still hallucinate citations. Research notes must be checked before applying technical recommendations.
- No guaranteed daily timing or zero cost.

## Owner controls and checks

To stop **AI network requests**, disable the existing AI text cloud consent in Settings; WorkManager will continue to wake occasionally but perform no AI work. To disable the scheduler entirely requires an app update or programmatic WorkManager cancellation, because this change intentionally adds no new UI control.

Before publication, verify the GitHub Actions Android unit-test/Lint/debug APK jobs plus real-device background scheduling and API-billing behavior. Confirm a complex Android coding question reports a longer, useful answer without changes to calling, navigation, or microphone paths.
