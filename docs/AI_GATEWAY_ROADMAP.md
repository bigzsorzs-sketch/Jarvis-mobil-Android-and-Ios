# Jarvis Mobile AI gateway — development only

This directory is a non-deployed backend skeleton. The Android APK still uses
local command routing, NOT cloud AI. No provider keys are embedded in the APK.

The future /v1/chat endpoint accepts only a signed OIDC bearer token that must
pass JWKS signature, issuer and audience checks. A separate trusted billing
system must provision an active subscription in Redis. The server enforces
per-minute and monthly request counts, chooses a server-approved AI model,
and sends provider requests using a backend-only OPENROUTER_API_KEY.

Do not commercially deploy without: authenticated billing webhooks, atomic
distributed spending / token budgets, pricing and cost metering, secure logging
that excludes sensitive contents, streaming responses, privacy/legal review,
credential rotation, abuse detection, HTTPS deployment and security testing.
The starter limit is a request-count guard, not a guaranteed monetary budget.

Configure OPENROUTER_API_KEY and OIDC credentials using the server-side
deployment secret manager. Never share an API key in chat, commit it into GitHub,
or put it into the Android or iOS applications.

Status: not deployed, no user login, no Android AI connector, no Play Store
release. Repository should be Private before commercial use.
