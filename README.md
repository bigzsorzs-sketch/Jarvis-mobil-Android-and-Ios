# Jarvis Mobile — Android (Kotlin) & iOS (Swift)

Separate **native mobile** Jarvis repository, independent from the Windows Electron app. This is a first **functional Basic prototype**, not a production release.

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
