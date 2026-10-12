# Jarvis Android release and family installation

## Release status

Jarvis v0.2.14 is the planned **signed** release, not a debug APK renamed to sound stable. The release workflow in GitHub Actions is manual and requires:
- A real Android handset test, including voice, UI, offline history, phone permissions, update from previous signed version and network errors.
- A running private HTTPS Jarvis gateway with an accessible OpenRouter model and working independent smoke-test beta session.
- The public repository variable JARVIS_GATEWAY_ORIGIN, matching the backend domain and not pointing to a placeholder.
- A stable Android signing keystore provided via protected production environment secrets.
- Reviewed privacy/security policy; restricted, approved beta invitations are still required.
- A GitHub Actions production environment with required reviewers and explicit RELEASE_APPROVED confirmation.

GitHub production environment Secrets:
- ANDROID_UPLOAD_KEYSTORE_BASE64
- ANDROID_UPLOAD_STORE_PASSWORD
- ANDROID_UPLOAD_KEY_ALIAS
- ANDROID_UPLOAD_KEY_PASSWORD
- JARVIS_RELEASE_SMOKE_TOKEN (an isolated, revocable tester session; do not use the provider key)

The production build workflow requires a real successful authenticated /v1/chat response. The OpenRouter API provider key NEVER goes into the Android build.

Only then: GitHub Actions -> Publish signed Jarvis Android release -> Run workflow on main -> RELEASE_APPROVED. On success it publishes v0.2.14 with signed APK, AAB and SHA256SUMS. The SHA-256 file verifies bytes downloaded; Android signature determines whether an installed update can replace the prior app.

## Send an installation link to a family member

Before the signed release is available, the currently verifiable installation link is:
https://github.com/bigzsorzs-sketch/Jarvis-mobil-Android-and-Ios/releases/download/v0.2.13-dev-test.1/Jarvis-Mobile-Android-0.2.13-test.apk

This is the 0.2.13 test version. Do NOT call it the new shared-AI release.

Once the signed v0.2.14 release is actually published and verified, the shareable official release page is:
https://github.com/bigzsorzs-sketch/Jarvis-mobil-Android-and-Ios/releases/tag/v0.2.14

Do not send that second link as a working download before the release exists.

On the child's Android phone:
1. Open the verified GitHub Release URL in Chrome and tap the APK under Assets.
2. Check the downloaded filename and repository owner. Avoid files forwarded by unknown senders.
3. Android may ask permission for Chrome to install a downloaded app. Enable it temporarily for Chrome only, then turn it off again.
4. Tap Install; open Jarvis, grant only necessary permissions.
5. Once the private HTTPS server is running, obtain a unique single-use activation code from the repository owner, agree to cloud processing, and activate in AI-agy. NEVER share the OpenRouter API key.

**Existing installations:** a GitHub Actions debug APK may be signed by a different identity than the new production keystore. If Android reports "App not installed"/signature mismatch, do NOT uninstall the existing Jarvis until conversations and invoices have a confirmed backup/export. The encrypted conversation database uses an Android Keystore key and cannot simply be copied after uninstall or phone replacement. The new signed release cannot force Android to migrate data across signing identities.

**Limits:** Jarvis private beta is not anonymous unlimited use. The tester must be separately invited, and a live backend is required. A GitHub Secret does not run a server by itself.
