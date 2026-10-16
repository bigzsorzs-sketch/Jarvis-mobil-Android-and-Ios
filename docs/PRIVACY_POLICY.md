# Jarvis Mobile — Privacy Policy

**Last updated: 16 October 2026.**

Jarvis Mobile is an Android AI-assistant application developed under the **Jarvis Mobile** project (GitHub repository: [bigzsorzs-sketch/Jarvis-mobil-Android-and-Ios](https://github.com/bigzsorzs-sketch/Jarvis-mobil-Android-and-Ios)).

**Contact:** For public product questions, use the project's GitHub issue tracker. **Before Google Play production submission, the developer must provide and publish a private, actively monitored support/privacy contact** so that users never have to post sensitive information in a public issue.

## Data and permissions

- **Chat and AI:** When you explicitly enable cloud AI services, messages, transcripts and requests that you send for AI processing are transmitted over HTTPS to the configured AI gateway/provider (which may use OpenRouter). Your selected provider's retention and privacy terms also apply. The app does not promise those services immediately erase submitted data on local logout.
- **Microphone:** Used for user-initiated speech recognition and optional hands-free conversation. Audio can be sent to a cloud transcription provider only when the separate audio-upload consent is enabled. Speech permissions can be revoked in Android Settings.
- **Contacts and calling:** With permission, Jarvis can read device contacts to resolve names and initiate user-requested calls. This is not a continuous contact sync.
- **Location:** Foreground location helps with user-requested location features. The GitHub test build additionally offers opt-in background home-departure geofencing; **the Google Play build does not request background location and disables that feature**.
- **Smart Home:** When configured, Jarvis exchanges requested control/status commands with your chosen HTTPS Home Assistant service; these commands may disclose your configured device names and status to that service.
- **Health:** Jarvis can optionally read blood glucose data via Android Health Connect with explicit system permission, and stores manually entered glucose readings locally. It is not a medical device or a substitute for professional medical advice.
- **Local data:** Saved chat, glucose history, notes, saved places, locally stored business/billing information and preferences are stored on-device; sensitive local content uses Android Keystore-backed encryption. The app does not turn these records into an automatic account-wide cloud backup.
- **Optional user-triggered backup:** You can encrypt an export with a password, then choose a document destination through Android's file picker, including Google Drive or Microsoft OneDrive if available on your phone. Your selected storage provider's own terms apply. Passwords, API keys, AI session tokens and other credentials are excluded from exports. Keep the password safe: it cannot be recovered.
- **Notification access:** If separately granted, Jarvis can inspect recent supported message notifications to carry out user-invoked tasks. Notification contents are not automatically uploaded as AI context.
- **Other permissions:** Bluetooth/NFC can discover nearby accessories on your request; telephone, microphone and notifications require Android's permissions. The Google Play build excludes app-package installation permission and the optional accessibility-based cross-app screen-control service.

## Security and retention

Communication with configured remote AI/Home Assistant endpoints requires HTTPS. Local history and credentials are encrypted on-device. The app can retain local data until you delete it or clear app storage. Android uninstall normally erases that local data; user-created encrypted exports remain wherever you stored them.

Using **Log out** disconnects locally stored Jarvis AI credentials and revokes local AI-upload consents, but **does not delete local chat/history and is not a remote account deletion**. You can manage Android permissions in device Settings. For deletion of information stored by an external AI provider or cloud storage service, contact that provider under its own policies.

## Your choices

You can avoid using cloud AI by not consenting to it, refuse optional permissions, export your local data using an encrypted backup, or delete the app's local storage through Android Settings. You may also remove cloud backup files from your chosen storage provider.

## Children

Jarvis is not designed specifically for children, and the Google Play audience/content declarations must reflect the intended actual audience.

## Changes

Material policy changes will be published at this URL with an updated date. **This policy must be reviewed against real production hosting, analytics, payment processing, third-party SDKs and customer support before a Google Play public release.**
