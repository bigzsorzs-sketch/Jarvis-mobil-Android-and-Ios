# Jarvis Android 0.2.39 — full functional acceptance matrix

Status: release **candidate only**, 2026-10-15. Source branch: `fix/voice-capture-recovery-20261015`; versionCode 50. This document is a test **plan**, not a claim of successful end-to-end execution.

## Evidence levels
- **A** — source-level/unit tests, Kotlin Lint, Android debug/release APK+AAB compilation, backend typecheck/tests.
- **B** — Android API 35 emulator Compose/instrumentation tests, Android intent capture; cannot prove OEM real-device behavior or external app outcomes.
- **C** — actual Android 13/15/16 handset with human tester, original signing identity, Wi-Fi/mobile data, audio routing, OEM power management and real contacts/apps.
- **D** — deployed, authenticated private HTTPS gateway or provider, authorizations, production payment, external email, Google Maps/Libre service and privacy review. Real end-to-end receipt required.

A/B green are necessary but not substitutes for C/D. Record for each scenario: device/build SHA, chosen language, input text or voice, preconditions, actual result, latency, PASS/FAIL/BLOCKED, screenshot/logcat without secrets or private data.

## Mandatory regression inventory

| Area | Typed path | Voice path | Proof required |
|---|---|---|---|
| Home, chat, drawer, history, new conversation, settings | A/B | B/C | Correct page, persisted history; no ghost duplicates |
| UI themes & permissions incl. deny/revoke | A/B | B/C | Persisted switch, truthful denied permission errors |
| AI GPT-5.2 Q&A, context continuity, factual/historical question | A/B/D | C/D | Correct answer, no fabricated tool execution |
| Cloud STT, seven languages HU/EN/DE/FR/ES/IT/PL | A/B | C/D | Actual microphone transcript, correct selected language |
| Cloud TTS & streaming, narrator stop and barge-in | A/B | C/D | Audible response, 30 interruption attempts, no stale answer |
| 15-minute foreground microphone, app switching, screen locking, Bluetooth | B | C | Visible foreground notification, no privacy bypass; background OS limits reported |
| Contact lookup, aliases, duplicates, call intent, exact approval | A/B | C | Correct selected phone number; no dial on incomplete/ambiguous transcript |
| Google Maps cities, full address, HOME, contacts with saved addresses | A/B | C/D | Correct external navigation intent and correct destination (not empty map) |
| Google Maps private saved places / import | A/B | C | Import recognized, no invented direct account access |
| Installed apps, home/subpage controls, Android settings panels | A/B | C | Opened requested app/page; no fake internal third-party action |
| Gmail OAuth, read, recipients, draft, actual send | A/B | C/D | Explicit user-authorized account, real send acknowledgement, no duplicate sends |
| WhatsApp/Messenger notifications and draft/quick reply | A/B | C/D | Allowed Android notifications, confirmation, real handoff vs verified send |
| Billing: customers, drafts, PDF, mail | A/B | C/D | Correct amounts/details, saved document, recipient, real send outcome |
| Glucose manual record, permitted health data/Libre integration, NFC | A/B | C/D | Value + units + timestamp + source; NEVER invent live reading |
| Web search, Amazon search, files/photos/exe/video attachments | A/B | C/D | Actual results/app handoff, bounded upload and explicit permissions |
| Alarms, calendar, notes, text read-aloud | A/B | C | Correct triggered alarm/event/hand-off, not just UI acknowledgement |
| Seven-language switching of ALL pages, tool names and voice responses | A/B | C/D | UI text, speech recognition and model output match selection |
| Android offline/airplane mode, slow connection, 401/402/429/5xx, timeouts | A/B | C/D | Recover gracefully; no infinite mic retry or model double-charge |
| Background app lifecycle, app kill/restart, rotation, small screen, keyboard | A/B | C | No ANR/crash, no stuck spinner, no stale completion |
| Update/installer, local encrypted data retention | A/B | C | v0.2.38/49 → v0.2.39/50 **same signer**, no uninstall or data loss |
| Private gateway auth, quotas, server health, spend alert & billing | A | D | Real authenticated end-to-end response, quota and entitlement enforced |
| Privacy, consent revocation, health declarations, Play readiness | A | C/D | Real policies, correct permission notices and account deletion path |

## Exact acceptance gate
1. Latest PR SHA: full `android-debug` + `android-ui-emulator` + `gateway-typecheck` all **success**; all release artifacts built from the identical approved source.
2. HU + EN: 30 real conversations in each, across Wi-Fi and mobile data. Log median and p95 end-of-speech-to-first-audio (without voice text or PII); compare with a baseline only if measured.
3. 30 natural interruptions; no ghost speech resumes; after STOP, old pending model proposals can NEVER dispatch native actions.
4. Three tested real-device scenarios for each critical user-facing feature above, plus negative cases (permission denied, duplicates, lock-screen, offline).
5. Play signing identity and verified upgrade on current phone; keep original signed build and personal data untouched if verification fails.
6. Backend authentication, payment entitlement, provider costs, privacy/health compliance actually reviewed **before public paid release**.
7. GO only for the specifically proven Android **beta** feature set. Label everything else BETA / BLOCKED / UNSUPPORTED; no empty claims. iOS is a separate workstream.

## Manual results
Fill one row per run:

`Date | device/API | installed version/SHA | feature | language | typed/voice | network | result PASS/FAIL/BLOCKED | actual behavior | evidence link (redacted)`

Do not upload private contacts, health readings, credentials, transcripts, or signing keys into public GitHub issues.
