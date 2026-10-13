# Jarvis Android in-app updates: signing continuity

## What ships with v0.2.17

Settings → Updates can check `/releases` from the exact official GitHub repository, select a newer version, download the GitHub APK over HTTPS, verify the release-asset SHA-256 digest, inspect APK package ID/versionCode/signing certificate, and hand it to Android's package installer. Users approve installation and any "Install unknown apps" grant through Android. It does not silently install, delete previous app data, bypass Android security or allow downgrades.

Voice/text examples: "Jarvis, ellenőrizd a frissítéseket", "Jarvis, frissítsd magad", "Jarvis, check for updates". The voice request opens Settings and triggers the same verified check; install requests still require Android's system confirmation. UI and feedback: HU/EN/DE/FR/ES/IT/PL.

## Prerequisite: **one permanent signing key**

Android permits an in-place upgrade only when the application ID (`ai.jarvis.mobile`) and signing lineage match. A higher versionCode alone does **not** fix a signing mismatch.

Old GitHub Actions `app-debug.apk` artifacts were signed with ephemeral auto-generated debug keys. **Do not assume** two GitHub runs share the same certificate. The previous `v0.2.16-dev-test.1` and future `v0.2.17-signed-test.1` may be incompatible as upgrades.

**Never tell users to uninstall Jarvis to update.** Its Android backup is disabled, and its encrypted on-device chat, health and billing data may be lost. If you still possess the keystore that signed the user's currently installed APK, preserve it and use that exact keystore. Otherwise a one-time, user-approved migration/backup method is needed before changing the signing identity.

The manually triggered `publish-android-signed-test.yml` workflow already expects these **GitHub Actions environment production secrets**, supplied through repository Settings (never chat, repository source, APK, build logs or PR):
- `ANDROID_UPLOAD_KEYSTORE_BASE64` — existing JKS converted to base64
- `ANDROID_UPLOAD_STORE_PASSWORD`
- `ANDROID_UPLOAD_KEY_ALIAS`
- `ANDROID_UPLOAD_KEY_PASSWORD`

It restores the exact same signing key every time; do not generate a new key per run. Save the original keystore and passwords in secure offline backups. GitHub's protected production environment may need manual approval. Verify the old APK fingerprint via Android build-tools `apksigner verify --print-certs old.apk`, compare with the signing certificate for the new APK, and only release as an update when they match. APK format and signature compatibility on the target physical phone must also be tested.

## Release process

1. Keep monotonically increasing `versionCode` and semantic `versionName`; for v0.2.17 set code 28, name 0.2.17.
2. Require all unit/lint/build and real Android emulator UI tests to pass for the exact commit. Do not merge a red build.
3. Configure the **same** protected signing keystore once. Run the signed-test workflow manually from the main branch and verify the resulting signature.
4. Publish an immutable GitHub Release tag with the APK and GitHub API asset SHA-256 digest. The app only accepts the official release URL and rejects a mismatched digest, package ID, version or certificate.
5. On a real test device that already has the previous APK signed by that **same key**, use Settings → Updates. Confirm in-place installation and persistence of the local encrypted notes, conversations, invoices and settings.
6. Only after testing an actual in-place upgrade can the product promise zero-uninstall updates.

Do not market the `0.2.16-dev` → `0.2.17-signed-test` step as a guaranteed upgrade: the earlier temporary signing key might not exist anymore.

## 0.2.18 cumulative update: blocked until the previous signing identity is restored

The consolidated Android 0.2.18 / versionCode 29 source includes system-contacts calling, seven-language calls, live web search, cloud AI speech and automatic foreground Google Maps navigation. These compile and emulator tests do not replace live-phone/provider tests.

The `publish-android-signed-test.yml` manual workflow must use the **original** signing keystore stored in protected GitHub Actions production secrets. It verifies APK v2/v3 signatures and `ai.jarvis.mobile` versionCode 29, publishes a SHA256SUMS file and signer certificate, and **refuses to publish** unless the new certificate fingerprint equals the published `v0.2.17-dev-test.1` certificate. This is intentionally stricter than simply producing a signed APK.

A signed-test attempt on 2026-10-12 stopped at `Restore EXISTING protected signing keystore` with `Missing existing upload keystore secret`. Do not create or rotate a substitute key and advertise it as an in-place update. GitHub secrets must be configured using the repository Settings UI with the *original* private signing material, never sent through chat, logs or checked into source. A matching certificate with the `0.2.17` release does not prove a particular tester installed that exact APK; their device also verifies its own installed signer before opening Android's system installer.

If the original signer is permanently unavailable, an in-place update for that signing lineage is cryptographically impossible. Preserve the existing installation and work out a user-approved data-export/import migration before attempting replacement. A green CI build alone does not establish that local encrypted data will survive an uninstall.
