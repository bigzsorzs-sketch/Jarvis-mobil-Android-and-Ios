# Jarvis private beta AI gateway

The OpenRouter API secret is held only on the private gateway server, NEVER in the Android APK, GitHub source, or tester-visible UI.

Each authorized tester gets a random, **one-use invitation valid 48 hours**, not an OpenRouter key. Upon redemption the server issues a separate 256-bit, revocable session valid 90 days. Sessions and per-account quotas are kept in Redis. Users' questions still reach OpenRouter through the gateway with explicit privacy consent. This is a **restricted beta**, not unrestricted anonymous public signup.

## Deploy once

The GitHub Actions repository secret named OPENROUTER_API_KEY DOES NOT create a live server on its own. For deployment, prepare a VPS with Docker Compose v2, a dedicated SSH deploy user permitted to write to /opt/jarvis and run Docker, DNS pointing to the VPS and ports 80/443 open.

GitHub Actions repository **Secrets**:
- OPENROUTER_API_KEY (ROTATE the key visible in the user's screenshots first)
- JARVIS_DEPLOY_HOST (VPS host/IP)
- JARVIS_DEPLOY_USER (restricted SSH user)
- JARVIS_DEPLOY_SSH_PRIVATE_KEY (private SSH key, never in source)
- JARVIS_DEPLOY_KNOWN_HOSTS (pinned SSH host public-key record, independently verified)

Repository **Variables**:
- JARVIS_DOMAIN (e.g. ai.example.com, DNS configured)
- JARVIS_GATEWAY_ORIGIN (e.g. https://ai.example.com, public nonsecret HTTPS origin to be baked into test APK)
- OPENROUTER_MODEL (accessible OpenRouter model identifier)

Protect the GitHub Actions environment named private-beta with manual reviewer approval. After code review on the main branch, run the workflow **Deploy Jarvis private AI gateway**, enter DEPLOY. The workflow verifies backend tests and deploys the private server by SSH, putting the OpenRouter key only into the server .env file excluded from Docker build and Git. Finally it verifies the HTTPS health endpoint. It does not deploy from PRs or automatically publish anonymous access.

## Privately enroll each tester

After a healthy deployment, execute the following on the PRIVATE VPS terminal (never in a GitHub Actions log or public screenshot):

    cd /opt/jarvis/server
    docker compose exec -T gateway node dist/createInvite.js testerA

Send the generated one-use, 48-hour invitation privately. In the Android app select AI-agy → Saját szerver, accept cloud processing and enter the invitation. One-tap activation saves the session encrypted on the device. The OpenRouter provider API key is never sent to the tester.

Remove beta:session:<SHA256-of-the-session-token> in Redis to revoke an individual session. Revocation and quotas do not affect other testers.

## Limits / status

- This is private, invitation-only testing. Anyone downloading the APK is NOT automatically entitled to an unlimited paid AI connection.
- Public signup requires Google/Microsoft/OIDC or equivalent per-user authentication, abuse detection, cost limits, service policies and account recovery.
- A running domain, VPS and valid provider quota have not yet been configured or externally verified; do not claim the AI is live until HTTPS health and a real authenticated chat request both pass.
- The separate encrypted on-device chat database and its cross-phone recovery are not part of the beta account session.
- Installing a shared gateway does not give the Jarvis app privileged access to arbitrary third-party Android app interfaces.
