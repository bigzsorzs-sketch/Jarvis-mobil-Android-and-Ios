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
