# Jarvis Android — Google Play submission gate

Prepared 16 October 2026. **This is an engineering gate, not a guarantee of Google Play acceptance.**

## Package and build
- [x] Target Android API 36 in Android configuration (as of this revision).
- [x] Use the `play` build type, `:app:bundlePlay`, and keep separate GitHub `release` build for signed in-app APK test updates.
- [x] In Play artifact, remove `REQUEST_INSTALL_PACKAGES`, `ACCESS_BACKGROUND_LOCATION` and `SCHEDULE_EXACT_ALARM`.
- [x] Remove `JarvisAccessibilityService` from Play manifest. Hide Play UI entries for unavailable features. Play in-app updates lead to the Play Store instead of APK installer.
- [ ] Verify final merged **Play AAB manifest**, `aapt` APK permission checks, packaging and Google Play App Signing compatibility. CI guards must pass.
- [ ] Owner must sign with Google Play registered upload key; do not assume the existing GitHub sideload signing certificate is registered in Play.
- [ ] Test real device Play install, launch, restore, chat/voice, Health Connect, notification permissions and navigation.

## Play Console declarations and materials — owner action required
- [ ] **Create a Google Play Console developer account** and accept current developer agreements, provide verified contact information, pay one-time registration if applicable.
- [ ] Fill truthful **Data safety** (device data, cloud AI requests, audio transcription, location, health, backups, processors and retention) based on actual production back end.
- [ ] Fill **Health apps declaration**; identify optional blood glucose data access and data safety; provide health-feature details, medical disclaimer.
- [ ] Publish a privacy policy at a stable, publicly accessible URL and add a private support/privacy contact; check that the policy reflects actual server-side and third-party retention.
- [ ] Declare foreground service microphone use; verify user initiation and visible persistent notification.
- [ ] Complete app access credentials/reviewer instructions for any login/paywall; no invented test account.
- [ ] Complete Content rating, ads declaration, target audience, store listing, graphics, screenshots and any applicable advertising ID declarations.
- [ ] Ensure real subscriptions, if offered, use compliant **Google Play Billing** and offer required terms/cancellation; do not advertise unimplemented payments.
- [ ] Verify app identity/ownership, public support and app content in Play Console.
- [ ] If the account is a recently created personal account, check applicable closed-testing/production-access requirements.
- [ ] Submit through Play Console and await Google's manual/automated policy review.

## Important product differences
**GitHub test APK:** opt-in home-departure background location alerts, optional cross-app accessibility UI control and the user-approved signed APK self-updater remain available.
**Play AAB:** removes those restricted capabilities and uses the Play Store for app updates, while preserving AI chat, contacts/calls with permissions, navigation, manual Smart Home, glucose records, encrypted backups and Jarvis AI logout. No background alerts, exact alarm access or accessibility automation in the Play variant.

## Source requirements / reference links
- Package install: https://support.google.com/googleplay/android-developer/answer/12085295
- Background location: https://support.google.com/googleplay/android-developer/answer/9799150
- Accessibility API: https://support.google.com/googleplay/android-developer/answer/10964491
- Health declaration: https://support.google.com/googleplay/android-developer/answer/14738291
- Exact alarm: https://support.google.com/googleplay/android-developer/answer/16558241

**Do not upload a sideload APK as a Google Play production build. Use the signed Play AAB with the corresponding signing/ownership verification. Do not claim public release or Play approval before actual confirmation from Play Console.**
