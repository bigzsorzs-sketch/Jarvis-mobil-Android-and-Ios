# Jarvis Mobile — limited GPT-5.2 plan / research / review agent

## Scope

The current single-response AI chat path is preserved for normal questions.
For an **explicit deep technical research request** (for example, "Elemezd alaposan
a Jarvis Android mikrofon hibáját"), Jarvis now runs a bounded workflow:

1. GPT creates a JSON plan containing 1–2 public technical research questions.
2. Android validates the plan and rejects extra or executable fields.
3. Jarvis requests official/public documentation and URLs for each question.
4. GPT receives the limited findings as **untrusted data** and critiques them,
   separating documented claims from hypotheses.

Only the original user request is forwarded; no additional chat history, private
device files, contacts, microphone samples, mail content, or health data are read
by the agent. The existing cloud AI consent remains mandatory.

The app's pre-existing, explicitly confirmed two-step alarm + app sequence is
also now checked against the locally persisted alarm state before continuing.
Opening another Android app is still a handoff, **not proof of completion** inside
the other app.

## Safety / functionality limits

- **No autonomous Android tool execution** by the research agent: it never
  reads device state or changes settings, accounts, source files or apps.
- No new buttons or settings. This runs on user-initiated technical questions,
  not as a 24/7 background job.
- Costs up to 4 OpenRouter model calls and up to 2 search calls for one complex
  question. It is **slower than ordinary chat** and must be separately benchmarked.
- A visible URL is not independently validated as a genuine source. The GPT
  review is a second model pass, not an independent scientific fact check.
- Unknown commands, API faults and invalid plans fail closed; no silent fallback
  to device execution or unknown provider tools.
- This is the **first constrained part** of a multi-step agent, not full general
  Android automation. Future steps require a dedicated typed action receipt
  API and real-world tool status checks for each supported command.

## Regression tests

Run Android unit tests + debug/release lint and APK builds; verify voice and
navigation on a physical device. Exercise a short ordinary chat, a supported
local navigation command, an approved alarm + app sequence, a deep technical
question with public references and one malformed/hostile model JSON plan.
Verify that provider quotas and failed web searches produce truthful failures.

The research agent deliberately does not use normal chat streaming so it can
review the completed research; the existing voice reply is delivered only when
the final response is ready.

## Foreground multi-step Android execution (this follow-up)

- A spoken or typed **explicit 2–3 step request** using connectors such as "majd", "aztán", "és", "and then" is split into user-authored fragments.
- Each fragment is independently validated by the existing native parser. Supported initial action kinds: Jarvis page navigation, local light/dark theme, supported language changes; an exact named phone-app opening or destination navigation may be the **last** step only.
- GPT-5.2 receives only the approved action types and ordinal indices, returns an exact JSON plan, and may answer CONTINUE or STOP after each *locally verified* action.
- The model cannot change targets, insert steps, impersonate a successful Android receipt or use free-form response text as a device command.
- The app checks UI routing state or persisted settings/language as local receipts. External Android intents return HANDOFF, explicitly **not verified completion** in the target app. Any failed, cancelled or handoff result stops the action loop.
- Calls, messaging, email sending, financial tasks, payment, deletion, authorization, installations, generic accessibility taps, automatic permission changes and arbitrary apps are **not** part of this chain. Existing single-action execution and dedicated call/message confirmations remain untouched.
- Requires existing cloud AI consent and configured OpenRouter/backend, an active foreground Jarvis Activity, and an explicit original user request. No scheduled/background autonomous phone actions.
- Performance: up to 3 model calls for a 3-step task (one plan + up to two decisions); do not assume it is faster than native commands.
- Example safe test: "Nyisd meg a Beállítások oldalát, majd kapcsold be a sötét módot". Example handoff: "Kapcsold be a sötét módot, majd navigálj Leedsbe". Test these on a real phone before issuing an APK update.
