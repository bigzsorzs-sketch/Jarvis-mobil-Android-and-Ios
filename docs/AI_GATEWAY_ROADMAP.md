# Jarvis Mobile AI gateway — 0.2.0-dev tester integration

## What's implemented

- Android Home sends ordinary typed questions over HTTPS to `POST /v1/chat`.
- Android hands-free voice questions can use the same AI gateway, and the answer is spoken by Android TTS.
- Existing device actions, calls and app handoffs remain local and require the existing permissions/confirmations. Generated AI text NEVER executes tools.
- Android Settings → AI-agy pairs a device with a server HTTPS origin and an independently issued **64-character hexadecimal tester token**. The token is stored by `SecurePrefs` with Android Keystore encryption. No API provider key is in the app or repo.
- The Node backend authenticates up to 10 explicitly provisioned test subjects using constant-time SHA-256 hash verification. Each subject has separate Redis request quotas. Existing production JWT/OIDC + subscription checks remain supported when OIDC is configured.
- Server-side OpenRouter key is supplied at runtime only through `OPENROUTER_API_KEY`. Server must have Redis, HTTPS reverse proxy, and a supported Node runtime.

## How to prepare exactly two testers

1. Rotate any OpenRouter API key ever pasted into chat or committed to a repository. Put the **new** key in your server hosting provider's secret/environment settings, never in `.env.example`, GitHub source or APK.
2. On a trusted machine generate independent random 256-bit access codes and SHA-256 hashes:
   ```sh
   node -e 'const c=require("node:crypto"); for (const name of ["owner","family"]) {const t=c.randomBytes(32).toString("hex"); console.log(name+" CODE="+t+" HASH="+c.createHash("sha256").update(t).digest("hex"));}'
   ```
   Keep the CODE values private. In the server's environment secret manager configure `TESTER_TOKEN_SHA256_JSON` as a JSON object mapping `owner` and `family` to their respective HASH values, for example `{"owner":"<owner SHA-256 hash>","family":"<family SHA-256 hash>"}`. Do not put the CODE values into this object.
3. Configure `OPENROUTER_MODEL`, `OPENROUTER_API_KEY`, `TESTER_TOKEN_SHA256_JSON` and `JARVIS_DOMAIN` in a **server/.env** file on a trusted VPS (not on GitHub). Point your DNS A/AAAA record to the VPS, open inbound ports 80/443 and run `cd server && docker compose up -d --build`. The supplied Docker Compose stack runs Node 22, a non-public Redis service and Caddy with automatic HTTPS. Alternatively, deploy behind your own trusted HTTPS reverse proxy. Never publish Redis or port 8080 directly.
4. Install the Android 0.2.0-dev test APK on both devices. On each: **Beállítások → AI-agy** → same HTTPS server address, but their *own* private CODE. Save and press **AI-próbakérdés**. A successful answer confirms real upstream AI usage.
5. Invalidate any compromised individual access code by removing/replacing its hash in `TESTER_TOKEN_SHA256_JSON` and restarting the gateway.

## Limits and security constraints

- This is a *private testing scheme*, **not** public user registration or complete consumer authentication. Test tokens have no expiry by themselves; revoke or rotate them when a device is lost. Use one token per tester, rate limits and an upstream spending limit on your OpenRouter account.
- The built-in 12 requests/minute and 1000 requests/month quotas are **request caps**, not guaranteed spending caps; the provider account must also enforce spending limits.
- Backend `/health` is unauthenticated for service checks; `/v1/chat` needs valid credentials. Treat the entire server and Redis as a private service.
- Chat history, files, images, microphone recordings, contact data and local health records are **not** sent by this AI client. Only each explicitly submitted question is sent. Response body is currently text only and lacks tool calling, image understanding and conversation memory.
- Android enforces HTTPS, rejects userinfo/query/fragment/path in configured endpoint, and **does not follow HTTP redirects** with the token.
- The repository is public. Do not commit secrets. Commercial release still requires real registration/login, billing enforcement, server cost metering, hardened hosting, monitoring, abuse prevention, content policy and privacy/legal review.
- **Not yet deployed**: the code and build pipeline alone cannot activate AI. Backend host, provider credentials, approved model ID, Redis, HTTPS, and tester codes must actually be configured.

## QA

Run the Android `testDebugUnitTest lintDebug assembleDebug` workflow and the server strict TypeScript check. Then verify both testers on physical phones: success, incorrect token → 401, revoked token → 401, failed network, exhausted quota → 429, background/locked voice boundaries and correct local permission handoffs.
