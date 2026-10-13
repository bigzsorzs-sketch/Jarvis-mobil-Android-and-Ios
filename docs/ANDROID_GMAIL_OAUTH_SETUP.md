# Jarvis Android Gmail — OAuth setup and device QA

This branch contains a test integration, NOT a published/verified release.
It uses Google Identity AuthorizationClient on Android and Gmail REST API directly
from the phone. No Gmail password or API key should be entered into Jarvis source,
and OAuth tokens must not be saved in preferences or sent to the AI gateway.

## Google Cloud configuration (required BEFORE any on-device success)

1. In https://console.cloud.google.com/ create/select a Google Cloud project
   and enable Gmail API.
2. Under Google Auth Platform → Branding / Audience / Data Access, set up
   OAuth consent, declare Gmail scopes:
   - https://www.googleapis.com/auth/gmail.readonly (restricted)
   - https://www.googleapis.com/auth/gmail.send (sensitive)
   In Testing mode, add the exact Google accounts used to test the app.
3. Under Google Auth Platform → Clients, create an Android OAuth client:
   - Package name: ai.jarvis.mobile
   - Signing SHA-1: fingerprint of the exact APK signing certificate installed
     on the test phone. It must match the existing Jarvis signing keystore,
     not a freshly generated certificate or the repository commit SHA.
     Jarvis official signed release includes the public certificate fingerprint:
       https://github.com/bigzsorzs-sketch/Jarvis-mobil-Android-and-Ios/releases/download/v0.2.24-signed-test.1/SIGNING-CERTIFICATE.txt
     Copy the Signer #1 certificate SHA-1 digest shown there (not SHA-256).
     Alternatively inspect the installed APK using Android SDK:
       apksigner verify --print-certs path/to/Jarvis-Android.apk
4. Use an Android phone with Google Play services and launch Jarvis → Email →
   Connect Google account. Choose the intended account and authorize Gmail.
5. Never paste Google passwords, OAuth secrets, tokens or signing credentials
   into source files or public issues.

OAuth registration is done in Google Cloud, NOT with a Google Maps API key.
Creating the branch does not authorize anyone's account automatically.

## Supported direct Gmail operations

- Typed or foreground voice: Van új emailem?
- Typed or foreground voice: Olvasd fel az új emailjeimet
- Typed or foreground voice: Küldj emailt anna@example.com tárgy Találkozó üzenet Holnap tízkor
- Typed or foreground voice: Válaszolj az utolsó emailre, hogy Rendben, köszönöm
- Email screen: connect, check unread, read, compose/send, reply to a sender,
  and revoke Gmail authorization.
- Írj emailt ... with complete fields does NOT send without explicit küldj
  or an Email-page Send press. Incomplete messages do not send.
- Contact names must resolve to exactly one address in Android Contacts. Missing
  contacts permission or multiple addresses must be resolved by the user.

## Security and privacy

- Only Gmail API users.messages.list, users.messages.get, users.messages.send,
  and users.getProfile are used (plus Google authorization/revocation).
- All Gmail HTTP requests are made on Android with temporary OAuth token.
  No message contents are included in reasoning-AI prompts or persistent chat history.
- User-commanded read aloud uses Jarvis's configured cloud voice: email text may
  be transmitted to that speech provider under separate voice consent. If cloud
  voice is unavailable, the fetched text appears transiently on the Email screen.
  Audit the disclosure before public distribution.
- Sending success requires Gmail response message ID; threaded reply also
  checks returned thread ID. Timeout after sending is shown as uncertain.
- Gmail read is a restricted OAuth scope. Review Google's current verification
  requirements and whether third-party security assessment applies.

## Real-device acceptance tests (required before merge/release)

1. Wrong/no account or decline consent: no sending; accurate error.
2. Correct signed APK + Cloud client: consent succeeds, profile correct.
3. New mail: Check sees it; Read retrieves plain text and HTML-only messages,
   shows content locally, speaks via explicitly enabled AI voice.
4. Send explicit address: exactly one item in Sent; UTF-8 accents preserved.
5. Send to Android contact with one email; contact with two addresses must
   require an explicit address instead of guessing.
6. Reply to sender: verify In-Reply-To/References and original Gmail thread.
7. Simulate 401/403/429, airplane mode, slow timeout: no invented success,
   no silent retry / duplicate message.
8. Hungarian and English commands, language switch, background/foreground,
   Android UI, and zero Maps regressions.
9. Disconnect, re-open, verify access is revoked.
10. Pass Android unit tests, lint, debug/release builds, UI emulator smoke,
    and physical-device QA. A green workflow alone does not prove OAuth,
    mail delivery or spoken read-aloud.
