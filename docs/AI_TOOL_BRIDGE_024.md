# Jarvis Mobile 0.2.4: explicit command execution

## What executes today

- **Local deterministic commands:** the existing Android handlers can open Jarvis screens; prepare email drafts; hand off navigation to maps; show notifications with granted Android permission; schedule/cancel supported Jarvis alarms; prepare calls with contact confirmation; open external message composers without claiming delivery. Exact behavior depends on Android permissions, installed applications and app foreground state.
- **Model-driven on-device commands:** OpenRouter direct mode and the optional server may propose `open_jarvis_page` or `open_phone_app`. The latter has exactly five actions: `calendar`, `alarms`, `email`, `maps`, `dialer`.
- **Every AI proposal is separately validated by the installed Android app** against its own allowlist and the original text/voice command. Unknown tools, extra arguments, multiple calls, negated requests and wrong targets fail closed. Generated text cannot be parsed as a command.
- **Voice handoffs** go through the Android foreground UI. The queued external-app action expires after 30 seconds; the app does not silently start outside Android's foreground restrictions.
- Launching an external app is not proof a call was placed, email/message delivered, payment made or data edited.

## Intentionally not enabled

No arbitrary shell/EXE execution, uninstall, system configuration changes, payment, contact-history extraction, background Gmail or WhatsApp message sending, or other unapproved application automation. Any of these needs its own permission, confirmation, audited implementation and device test.

## Release readiness

This branch has CI tests for known routing/security scenarios, lint and APK assembly, plus strict server TypeScript checks. A green CI result is **not** evidence of comprehensive real-device compatibility.

A commercial/production release additionally requires: production-signed APK / Play signing with stable upgrade path; deployed HTTPS AI gateway; account and subscription handling; per-account spend controls and monitoring; security/privacy review; user permission experience; Android 14–16 real phone testing, including locked/background/denied permissions and app-not-installed cases. The current direct OpenRouter option is for private testing, with a separate private per-tester key; never hardcode a provider secret in a public APK.

Only a production test campaign can establish which device actions are reliable. No guarantee of zero bugs is possible.
