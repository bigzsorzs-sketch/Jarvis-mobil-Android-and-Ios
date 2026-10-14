# Jarvis Basic — Android release gate (target: 2026-11-14)

**Target:** UK Play Store Basic at £24.99/month. Target date is conditional on
functional, privacy, payment, and device acceptance; not a commitment to ship
broken features. No new home-screen controls are required.

## First priority: ChatGPT-like hands-free conversation
- [ ] Install signed, upgrade-compatible APK on a real Android phone without
      deleting existing app data.
- [ ] Read microphone privacy/consent requirements before starting a session.
- [ ] Hold 30 Hungarian and 30 English natural conversations, both Wi-Fi and
      mobile data. Test speaker, wired/Bluetooth headset, quiet room and traffic
      noise **while safely parked**.
- [ ] Measure `end_of_speech_to_first_audio_ms` (debug Logcat; no transcript)
      and compare median and 95th percentile to the ChatGPT voice experience
      on the *same* handset, network and test phrases. Define 95% speed as at
      least 0.95x baseline speed, equivalent to no more than 1/0.95x matched
      latency; do not claim success without measurements.
- [ ] Measure time to response **separately** for basic local actions, ordinary
      AI answers, external web answers, Gmail and glucose; they have different
      server and permission latencies.
- [ ] Interrupt 30 cloud spoken answers naturally; verify old answer stops,
      user input is actually recognised, old responses never resume, and the
      microphone always becomes available again.
- [ ] Test successive turns and corrections, language selection, bad network,
      Bluetooth routing and AEC-disabled devices. Do not execute partial or
      echo-transcribed words as commands.
- [ ] Treat uncaught errors and unrecognised requests as clarification prompts,
      not verified action success.
- [ ] **Architectural gap:** current `askStructured` uses OpenRouter
      `stream:false` and currently waits for complete text, then full MP3
      generation. PRs #41–#43 reduce avoidable delays, but cannot by themselves
      guarantee low-latency full-duplex voice. Pilot WebRTC realtime speech
      against a private backend with ephemeral session credentials, VAD,
      echo cancellation and reliable tool-result attribution. Do not replace
      existing stable device actions until a real-device test meets acceptance.

## Command execution (end-to-end; speaking and typing)
- [ ] **Navigation:** "take me home", cities, full address, saved places,
      named contact "Eszter" (only with an actually saved address). Confirm
      Google Maps starts requested route, not only an empty map. The user's
      Google Maps account's private saved-home address is **not** directly
      readable from another app; Jarvis must store/import an authorised copy.
- [ ] **Call:** matching Android contact including duplicate first names and
      nickname handling; spoken confirmation for ambiguous/irreversible calls.
      Test telecom handoff and Bluetooth audio. Never claim a call connected
      merely because Android accepted a dial request.
- [ ] **Gmail:** actual Google OAuth grants, recipient lookup, subject/body,
      typed/voice flows, attachment selection (explicit user access), send
      acknowledgement and uncertain-network-outcome handling. Prevent duplicate
      sends. Do not claim unrestricted access to other apps.
- [ ] **Glucose:** the LibreLink launch button does not grant access to Abbott
      measurement data. Only use approved source integrations or explicitly
      imported glucose records. Show timestamp, units, provenance and staleness.
      If no current measurement exists, say so; never invent a reading or make
      insulin/medication decisions.
- [ ] **Billing:** create, review, PDF and send; verify existing SMTP setup,
      customer selection, invoice state/idempotency and language localisation.
- [ ] **Help/web:** route genuine up-to-date queries to supported, permissioned
      online search; report outages rather than silently fabricate answers.

## Release and data integrity
- [ ] Keep provider secrets out of APK/source; use encrypted on-device user
      credentials or short-lived backend sessions.
- [ ] Validate consent, privacy policy, Google Play Data Safety and any health
      data permissions; do not silently monitor bystanders.
- [ ] Check all seven language UI/voice paths, permissions, accessibility,
      background restrictions and Android OS versions supported.
- [ ] GitHub CI unit/lint/build + emulator all green **on the exact release SHA**.
- [ ] Signed upgrade uses identical signing identity, increasing versionCode;
      test install-over without data loss, backup/restore and rollback.
- [ ] Publish only after actual device verification; keep the last stable
      signed build available.

## Priority order
1. Fix hands-free stability, objective speech latency and clarification routing.
2. Build actual low-latency streaming audio if required by the 95% target.
3. Validate navigation and calls, then Gmail/email and billing.
4. Verify glucose source access with a paired sensor/official Libre app, without
   assuming cross-app permission.
5. Play Store/commercial readiness. A silent self-improvement system may store
   user-approved corrections and developer diagnostics, but must not
   autonomously modify executable code, leak personal data, or self-publish.
