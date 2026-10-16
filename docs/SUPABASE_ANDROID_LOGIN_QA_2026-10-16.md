# Jarvis Android login – private QA rollout (2026-10-16)

## Implemented in this branch
- Supabase e-mail/password sign-up and sign-in (the Supabase server handles passwords).
- Google OAuth in a system browser with SHA-256 PKCE, one-time verifier and a 10-minute deadline.
- Login callback from `ai.jarvis.mobile://auth/callback` to Android `MainActivity`.
- Encrypted local access/refresh token storage using the existing Android Keystore-backed `SecurePrefs`.
- Serialized access token refresh, local sign-out, account-switch block to protect legacy local conversations.
- When configured, all Jarvis server-mode chat, speech and transcription calls use a user JWT; legacy tester API keys are not used.
- Builds without the public Supabase settings retain existing private QA behavior. **This is not yet a public release.**

## Required configuration – no private secrets in GitHub code or APK
Set these **public GitHub Actions repository variables** (Settings > Secrets and variables > Actions > Variables):
```
JARVIS_SUPABASE_URL=https://egokjtlaecnqbyxlehqu.supabase.co
JARVIS_SUPABASE_PUBLISHABLE_KEY=<sb_publishable_... from Supabase Project Settings > API Keys>
JARVIS_GATEWAY_ORIGIN=https://<your-Jarvis-Railway-domain>
```
The publishable key is intentionally suitable for clients; **do not use** a Supabase secret, `service_role` token, Google client secret, OpenRouter key or database password. Existing Android debug, candidate and private QA workflows pass the variables during build. For a QA build to show login, all three must be populated.

In Supabase **Authentication > URL Configuration > Redirect URLs** add exactly:
```
ai.jarvis.mobile://auth/callback
```
Keep the existing **Google Cloud Web OAuth authorized redirect** pointing to Supabase:
```
https://egokjtlaecnqbyxlehqu.supabase.co/auth/v1/callback
```
Those are two different hops. Google Auth Platform **Audience > Test users** must include the tester while Google OAuth is in Testing mode. **Site URL http://localhost:3000 is not a working customer confirmation landing page**; configure an accessible verified HTTPS website/confirmation landing before testing email sign-ups with confirmation and before public release.

In Railway keep `OPENROUTER_API_KEY` exclusively in its protected service variables, plus `REDIS_URL`, `OIDC_ISSUER`, `OIDC_AUDIENCE=authenticated` and `OIDC_JWKS_URL`. The server verifies Supabase JWT and **requires `subscription:<user-id> = active` in Redis** for non-tester accounts. A newly registered user should therefore be rejected with HTTP 403 until subscription billing is correctly provisioned. This must NOT be bypassed by trusting data from the client.

## QA acceptance requirements before enabling for public customers
1. Build compiles, Android unit tests pass, manifest callback routes exclusively to the app.
2. Sign up → verification email → sign in, incorrect password, logout, login again.
3. Google test account signs in, browser returns to Jarvis, malicious/expired callback rejected; interrupted OAuth doesn't leave a session.
4. Restart app and refresh JWT after 1 hour, revocation/expired token returns 401, missing subscription returns 403.
5. Confirm no Google Client Secret/OpenRouter key/server DB password is embedded in APK or logs.
6. Existing device features (hands-free audio, phone calls, Maps navigation, chat history) regression-tested on physical Android.
7. New account cannot read a different local account's historic conversations. Current safe policy **blocks switching account on the same installation** unless local app data are manually wiped; work on per-user storage before making multi-user device support.
8. Implement real subscription verification, per-user monetary usage budgets/alerts, GDPR exports/deletion, rate controls, email delivery, password reset, abuse protection, customer terms/privacy policy, and verified HTTPS Android App Links before **public** launch.
9. Rotate the Google OAuth Client Secret previously exposed in a screenshot/chat before public use.

No automated publication/update feed changes are included in this branch; merge and release only after review.
