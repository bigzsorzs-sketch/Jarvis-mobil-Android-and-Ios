# Jarvis Mobile Android 0.2.39 — teljes funkcióellenőrzés
Date: 2026-10-15 | Branch: `fix/voice-capture-recovery-20261015` | Draft PR #56
Android target: API 36, package `ai.jarvis.mobile`, versionCode 50.
Current public/signed main: 0.2.38 / versionCode 49, unchanged.

**Scope**: Source-code and CI evidence audit of all documented Android areas. An implementation, green build, screen that opens, or mocked unit test must NEVER be represented as a verified live external action.

## Status legend
- **A — SOURCE+CI:** Actual implementation and some source/unit/UI test evidence. External outcome and physical device are not validated.
- **B — PARTIAL:** An important user-requested behavior is not implemented or only supported through a restricted alternative.
- **C — BLOCKED LIVE:** Source path exists, but external credentials, permissions, account setup, physical device, signing, or third-party callback still need end-to-end proof.
- **D — PRODUCTION BLOCKER:** Cannot claim public feature/launch readiness before required compliance or production infrastructure verification.

## Area-by-area inspection

| # | Feature | Code evidence / existing tests | Honest status | Concrete exit criterion |
|---|---|---|---|---|
| 1 | GPT-5.2 typed Q&A | `JarvisAiGateway.kt`, `JarvisAiToolPolicy.kt`, backend `index.ts`; unit+server suite | A+C | Authorized, hosted HTTPS real conversation, multiple turns, usage and cancellation |
| 2 | GPT voice conversations | `HandsFreeService.kt`, `JarvisCloudSpeechCapture.kt`, `JarvisCloudVoicePlayer.kt`; voice regression + emulator | A+C | Real Android 16 microphone; HU/EN + remaining languages; 30 interrupts; log first-audio p95 |
| 3 | Background listening/wake phrase | Foreground `HandsFreeService`, opt-in user session; `VoiceIntentParser` explicitly not a DSP hotword engine | B+C | Prove supported states on real devices; never promise silent always-on or lockscreen mic |
| 4 | Phone contacts and outbound calls | `VoiceCalling.kt`, `JarvisPhoneActions.kt`; matching and confirmation tests | A+C | Actual phone contacts (names with duplicates), explicit correct target confirmation, real dialer |
| 5 | Maps destination and routing | `VoiceNavigation.kt`, `JarvisNavigationText.kt`; intent tests | A+C | On phone route opens with correct city, address, postcode and travel mode |
| 6 | Private Google Maps saved places | `JarvisSavedPlaces.kt` supports user-imported CSV/GeoJSON (Google Takeout), NOT private live Google Maps lists | B | Import own locations and test aliases. Live private list access not supported |
| 7 | Gmail read/check/send/reply | `JarvisGmail.kt` real Gmail HTTP endpoints + `JarvisGmailAuthorization.kt` OAuth; Gmail parser tests | A+C | Configure Google Cloud OAuth app/package/certificate and Gmail API; test with consenting account; verify real sent ID/thread |
| 8 | Gmail client-side long dictation | `GmailDraftConversation.kt`, screen, command parser | A+C | Create/review/send actual multilingual message, refuse duplicate, check mailbox |
| 9 | Billing: clients/profile/invoices/PDF | `JarvisBillingCore.kt`, `JarvisBillingMail.kt`, `JarvisBillingVoice.kt`; billing unit & UI tests | A+C | Real customer record, amount/VAT validation, generated PDF and upgrade retention |
| 10 | Billing: deliver invoice email | `BillingSmtp` directly submits SMTP via user-configured Gmail App Password; ambiguous send -> uncertain state | A+C | With test recipient confirm SMTP accept + destination mailbox. No duplicate resend or guarantee of inbox delivery |
| 11 | Billing UK taxes/production compliance | Billing screen explicitly labels VAT/tax features local prototype | D | Review invoicing/UK VAT/retention policy independently before paid product claims |
| 12 | Glucose manual history & charts | `JarvisGlucoseData.kt`, dashboard, multilingual strings and unit/UI tests | A+C | Compare units, timestamps, CSV import and chart with non-sensitive synthetic samples on phone |
| 13 | Glucose Health Connect latest | `JarvisHealthGlucose.kt`: only if Android API>=34 and explicitly authorized; source-dependent | A+C | Authorized Health Connect reading with actual publisher that exposes glucose |
| 14 | Abbott Libre2 Plus direct NFC/live readings | `JarvisLibreNfc.kt` only starts official LibreLink/NFC settings, deliberately no direct sensor read/API | B | No promise of Jarvis scanning sensor; prove Libre shared/synced data through supported authorized source |
| 15 | Installed-app launch | `JarvisInstalledApps.kt`; seven-language parser+matching tests | A+C | Device installed-app names, duplicate labels, intents, permissions |
| 16 | Third-party app manipulation | `JarvisScreenControl.kt` optional explicitly-granted accessibility bridge, ONE deterministic safe action; financial/sending/permissions forbidden | B+C | Prove supported single safe action only; do NOT promise arbitrary app automation |
| 17 | WhatsApp/Messenger notifications/replies | `MessagingAccess.kt`; notification-listener, allowed RemoteInput quick-reply; separate drafts | A+C | Permissions, real notifications, permitted quick reply & actual receive verification; limited by source app |
| 18 | Local read-aloud/text & TTS | `JarvisReadAloud.kt` uses cloud AI TTS; message-notification reader in `MessagingAccess.kt` separately uses Android TTS | B+C | Clarify and unify selected language/audio path; real speaker/Bluetooth test |
| 19 | Web/current info & shopping search | `JarvisAiGateway.kt` + upstream OpenRouter web tool, `JarvisAmazon.kt` native handoff | A+C | Real provider web permissions/current results and correct Amazon query; distinguish handoff from purchase |
| 20 | Files/photos/PDF for AI | `JarvisAttachments.kt` 1 GiB local staging, `JarvisAiAttachments.kt` only supported image/PDF/text, 5 MiB each and 20 MiB send | B+C | User-selected files, verified type/size, real provider analysis. EXE is neither run nor understood |
| 21 | Video analysis | `JarvisVideoInspector.kt` local frame extraction only; no complete cloud video reasoning/audio transcription | B | Implement specific authorized video pipeline and validate accuracy/cost before claiming full video AI |
| 22 | Alarm/reminders/notes/calendar | `JarvisAlarms.kt`, `JarvisNativeActions.kt`, `JarvisSystemPanels.kt`, `JarvisExternalApps.kt` | A+C | On-phone alarm actually rings with OEM restrictions; confirm each native intent/handoff separately |
| 23 | Chat history/new conversation/search | `JarvisChatStore.kt`, `JarvisChatSession.kt` encrypted Android Keystore + SQLite; store/UI tests | A+C | On-device upgrade/restart/data retention, corruption/migration, privacy deletion |
| 24 | All seven UI languages | `JarvisLanguages.kt`, `JarvisMoreTranslations.kt` and per-feature locale tests | A+B+C | All screens/tool responses/error strings/hints and STT/TTS localized; tests cover known keys, NOT every dynamic English/Hungarian literal |
| 25 | Light/dark, orbit animation, layout | `JarvisTheme.kt`, `JarvisVoiceOrb.kt`, Compose UI tests | A+C | Device visual + accessibility, keyboard, orientation, dark/light persistence |
| 26 | Settings, permissions and consent | `PermissionCenter.kt`, `RuntimePermissionPolicy.kt`, `JarvisAiSettings.kt` | A+C | Grant/deny/revoke each permission on phone and verify honest fallbacks |
| 27 | Updates/in-place APK installation | `JarvisAppUpdater.kt` SHA-256, same-signing-cert check, version & package check, user-confirmed install | A+C | Signed 0.2.39 with SAME historical certificate; actual 0.2.38 → 0.2.39 update, NO uninstall/data loss |
| 28 | Server quotas/real paying customer access | `server/src/index.ts`, monthly quotas; current simple beta-invite/Redis entitlement, no proven real subscription-billing integration | B+D | Production auth, actual payment verifier, spend limit, secrets and GDPR/privacy |
| 29 | Google Play public release | `docs/PRODUCTION_READINESS_028.md`, release workflows, signing config | D | Play Console access, store listing, policy/Data Safety/health, applicable testing requirements, live crashes & reviews |
| 30 | iOS platform parity | `ios/` SwiftUI prototype, separate product | B | Independent iOS full-stack development/testing; NOT covered by green Android CI |

## Fixes validated or submitted during this audit
- English/Hungarian/Polish spoken stop detection and false wake phrase; tests.
- Unbounded 44–45-minute GPT socket/server waits changed to finite adaptive deadlines; network calls cancellable.
- Cloud microphone error fallback and no sequential duplicate GPT request; tests.
- `JarvisSavedPlaces.normalized` now folds Polish ł and German ß, improving imported aliases such as Łódź/Lodz and Straße/Strasse, with regression unit tests.
- CI matrix added for Android 15 (API 35) and 16 (API 36).

## Release policy
- **No-Go for unrestricted all-features product**: user-requested direct Libre NFC, complete video AI analysis, live Google Maps private lists, all third-party app control, full feature-by-feature live test evidence and production billing/policy still missing.
- **Conditional Go for scoped Android beta**: latest exact SHA build, Kotlin unit/lint, gateway, Android 15+16 instrumentation all green; same-cert signed upgrade and real-phone HU/EN calls, navigation and voice trials PASS. Disabled/unsupported features must be explicitly labeled and never represented as working.
- Source success proves absence of failures in tests that exist, not 100% code/function coverage.
- No private contact data, actual glucose readings, credentials, Gmail messages or signing keys in public GitHub evidence.
