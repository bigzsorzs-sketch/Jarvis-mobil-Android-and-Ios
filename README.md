# Jarvis Mobile — Android (Kotlin) & iOS (Swift)

Separate **native mobile** Jarvis repository, independent from the Windows Electron app. This is a first **functional Basic prototype**, not a production release.

## What runs now

- Native dark Jarvis dashboard with navigation.
- Local blood glucose logging (mmol/L), history and deletion.
- Local invoice **drafts** and native PDF export/share (not UK tax/VAT-compliant invoicing yet).
- Open the phone's email composer and native maps.
- Local notes.
- Chat UI clearly marks cloud AI as **not yet connected**; no fake AI replies.
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
