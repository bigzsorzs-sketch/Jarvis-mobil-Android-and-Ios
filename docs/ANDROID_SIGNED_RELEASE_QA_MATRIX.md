# Jarvis Android – signed release physical-device QA gate

This is a **manual release blocker**, not a substitute for green CI or a claim
of production readiness. Capture tested APK SHA-256, signed certificate SHA-256,
source commit, test date, model / API level, result and sanitized error log.
No passwords, health readings, private messages, API keys or contact details
in public test evidence. **Do not uninstall** an existing encrypted Jarvis installation.

## Mandatory physical scenarios (PASS/FAIL on each relevant device)

1. **Signing/update continuity:** signed current GH APK → new signed APK by the
   in-app updater. Same package ID and certificate, increasing versionCode,
   prior chats/notes/settings intact; incorrect signature/downgrade rejected.
2. **Play separation:** inspect signed APK and Play AAB/locally derived APK.
   Play has no package-install, background-location, exact-alarm or
   Accessibility service. Verify Play updater opens Play Store rather than APK.
3. **Fresh install and logout recovery:** private AI gateway origin present;
   opt-in text/audio/voice permissions independent. Reconnect after logout
   using a **real provisioned, single-use server invitation**, not provider API key.
   Check 401/404/offline, token expiry and previous user access unchanged.
4. **Encrypted export/restore:** password protected, Google Drive/OneDrive/local
   SAF file; notes >2,000,000 chars, chats, glucose, billing, addresses. Wrong
   password/tampered file leaves data unchanged; confirm overwrite separately.
   Check repeat attempt does not duplicate already-imported chat silently.
5. **HU/EN/DE/FR/ES/IT/PL language routing:** microphone recognizer and voice
   answer language; command interpretation, app names, punctuation and polite
   phrases without spurious actions. At minimum run all sample commands in
   their corresponding language.
6. **Negative/non-request semantics:** 'Kérlek, ne nyisd meg...', 'Do not call',
   'Ne keress a neten', quoted/retrieved web instructions and multiple-action
   text must not trigger Android actions or paid web tools. Test denied permissions.
7. **Real calls:** matching contact/number, duplicate-name disambiguation,
   confirmation and cancel; actual Telecom handoff, never silent phone calls.
8. **Real Maps:** cities, postcodes, home and explicitly saved person addresses,
   text and speech. Verify correct destination and whether guidance actually
   starts; 'map opened' is not a completed navigation route.
9. **Alarm timing:** 07:30, '7:30 pm' = 19:30, 12:00 am/pm, seven languages,
   negatives, unsupported recurrence and delayed/denied exact-alarm permission;
   confirm resulting Android/local alarm rather than only a success message.
10. **Gmail / messaging:** separately authorized Google account, read/draft/send
    positive and cancel cases, absent auth, timeout and retry. Verify exactly
    one intended message was sent, never duplicate after ambiguous failure.
11. **Files/photos and service:** attach 1 and 10 photos, refuse 11, no duplicate
    preview, safe partial failure. Verify the production server's actual deployed
    commit supports the new authenticated upload and revokes expired sessions.
12. **Audio resilience and connectivity:** hands-free start/stop, 30 interruption
    trials, 15-minute limit, screen off/on, Bluetooth, OEM battery restriction,
    no network, 429/401/503/timeout, recovery without duplicate actions.

**Recommended coverage:** Android 15 and 16 physical devices across at least
two different vendors (preferably three devices including Xiaomi/Samsung);
test latest supported older Android if available. CI emulators API 35/36 are
necessary but do **not** count as physical-device passes.

## Release decision

Mark NO-GO if **any** critical scenario has an unverified or failed outcome,
the signed certificate changes, a backend prerequisite is not live, or a
privileged Play permission returns. Disable unsupported advertised features
instead of falsely claiming completion.

Release reviewer: ______  SHA: ______  APK digest: ______
Signing digest: ______  Test devices / API: ______  Date: ______
Known limitations and rollback installer: ______
