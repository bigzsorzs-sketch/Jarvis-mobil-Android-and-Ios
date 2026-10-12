# Jarvis Mobile 0.2.3 — AI → Android tool bridge (private test)

## Implemented
- The direct OpenRouter AI (GPT-4o mini) and optional self-hosted Node gateway advertise ONE function: `open_jarvis_page`.
- Allowed `route` values exactly match `JarvisToolCatalog.all`. Neither the model nor gateway can define an Intent, script, executable, message or target URI.
- Android validates the function name, exactly one proposed tool call, exactly one `route` argument, allowlisted route and **original user message's explicit navigation request** before dispatch.
- Typed foreground requests open the actual app route; voice requests queue the route for when Jarvis is in foreground. The resulting status is reported from Android, not made up by the model.
- Plain model text is displayed/spoken only. Arbitrary JSON in text is **never** interpreted as a command.
- Existing deterministic local commands, permission checks and explicit call/message confirmations remain separate. The LLM has no permission to bypass them.

## Test examples
- "Nyisd meg az ismerőseimet" → Jarvis Ismerősök page, if the model selects the right function; ordinary deterministic commands may bypass AI.
- "Szeretném látni a naptárat" → allowed AI page-opening proposal.
- "Mi a naptár?" → answer as text, NEVER open a page.
- "Ne nyisd meg az üzeneteket" → no side effect.
- "Nyisd meg a naptárat és töröld az összes üzenetem" → only the allowlisted page open can be proposed; **never** delete messages.
- Invalid route, unknown function, extra argument or two tool calls → blocked, no action.
- Voice while locked → no private AI request; voice queue opens requested page only when Jarvis returns to foreground.

## QA / delivery status
GitHub Actions `android-debug.yml` runs Kotlin unit tests, Lint, and assembles a debug APK.
`gateway-typecheck.yml` runs strict TypeScript check, server tool-call tests and deployment config verification.
Before any public release: perform physical-device end-to-end testing of the chosen OpenRouter model, voice handoff, app lifecycle, Android 15/16 permissions and notifications, interruptions, no-network cases, accessibility and all locales. Check repeated requests and provider timeouts.

**Not production-ready.** Private test mode stores personal OpenRouter keys on the user's device, and the optional gateway needs real HTTPS deployment, OIDC accounts, spending controls, monitoring and rate limits. Public launch additionally needs signed, upgrade-stable production APKs/Play distribution, privacy/legal review, billing and secure account linking. Never embed a provider key in the app or repo.

## Security
All server `action` responses are untrusted. Only the Android validator makes the final route decision. This version intentionally does not support arbitrary app control, autonomous sending, contact/content access, camera capture, self-repair or background automatic operations. Separate feature-specific permissions, confirmations and real-device validation are prerequisites.
