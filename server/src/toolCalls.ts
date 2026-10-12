// The model may only propose one navigation action; no device actions happen on this server.
// The Android client always validates the returned route and original user intent.
export const JARVIS_ROUTES = [
  "Főoldal", "Munka", "Navigáció", "Vércukor", "Számlák", "Email",
  "Üzenetek", "Névjegyek", "Naptár", "Jegyzetek", "Ébresztő",
  "Ismerősök", "Beállítások"
] as const;
const JARVIS_FUNCTION = "open_jarvis_page";
const PHONE_FUNCTION = "open_phone_app";
export const PHONE_ACTIONS = ["calendar", "alarms", "email", "maps", "dialer"] as const;
export const JARVIS_TOOLS = [{
  type: "function",
  function: {
    name: JARVIS_FUNCTION,
    description: "Propose opening exactly one Jarvis Mobile menu page when the user explicitly requests it. Never execute arbitrary device actions.",
    parameters: {
      type: "object",
      properties: { route: { type: "string", enum: JARVIS_ROUTES } },
      required: ["route"],
      additionalProperties: false
    }
  }
}, {
  type: "function",
  function: {
    name: PHONE_FUNCTION,
    description: "Only propose opening the exact Android app named by the user: Calendar, Alarm, Email composer, Maps or Dialer. Never send, call or modify data.",
    parameters: {
      type: "object",
      properties: { route: { type: "string", enum: PHONE_ACTIONS } },
      required: ["route"],
      additionalProperties: false
    }
  }
}];

type ProviderToolCall = { type?: unknown; function?: { name?: unknown; arguments?: unknown } };
export function verifiedToolCall(calls: unknown): { name: string; route: string } | null {
  if (!Array.isArray(calls) || calls.length !== 1) return null;
  const call = calls[0] as ProviderToolCall | null;
  if (!call || call.type !== "function" || (call.function?.name !== JARVIS_FUNCTION && call.function?.name !== PHONE_FUNCTION) ||
      typeof call.function.arguments !== "string" || call.function.arguments.length > 512) return null;
  let args: unknown;
  try { args = JSON.parse(call.function.arguments); } catch { return null; }
  if (!args || typeof args !== "object" || Array.isArray(args)) return null;
  const entry = args as Record<string, unknown>;
  if (Object.keys(entry).length !== 1 || typeof entry.route !== "string" ||
      !((call.function.name === JARVIS_FUNCTION && JARVIS_ROUTES.some(route => route === entry.route)) ||
        (call.function.name === PHONE_FUNCTION && PHONE_ACTIONS.some(route => route === entry.route)))) return null;
  return { name: call.function.name, route: entry.route };
}

