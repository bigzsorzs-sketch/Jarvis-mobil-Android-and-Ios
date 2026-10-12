// The model may only propose one allowlisted action; no device actions happen on this server.
// Android validates proposals against the original user instruction and handles review.
export const JARVIS_ROUTES = [
  "Főoldal", "Munka", "Navigáció", "Vércukor", "Számlák", "Email",
  "Üzenetek", "Névjegyek", "Naptár", "Jegyzetek", "Ébresztő",
  "Ismerősök", "Beállítások"
] as const;
const JARVIS_FUNCTION = "open_jarvis_page";
const PHONE_FUNCTION = "open_phone_app";
const CALL_FUNCTION = "prepare_phone_call";
const MESSAGE_FUNCTION = "prepare_message_draft";
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
}, {
  type: "function",
  function: {
    name: CALL_FUNCTION,
    description: "Propose exactly one call when the user explicitly asked to call a named target. Android must independently match the original request, resolve the contact and require confirmation; the server must never place a call.",
    parameters: {
      type: "object",
      properties: { target: { type: "string", minLength: 1, maxLength: 70 } },
      required: ["target"],
      additionalProperties: false
    }
  }
}, {
  type: "function",
  function: {
    name: MESSAGE_FUNCTION,
    description: "Only propose a WhatsApp/Messenger draft if the user explicitly provided the app, recipient and exact text. Android reconstructs the recipient and content from the original command, requires review and never sends automatically.",
    parameters: {
      type: "object",
      properties: { platform: { type: "string", enum: ["whatsapp", "messenger"] } },
      required: ["platform"],
      additionalProperties: false
    }
  }
}];

type ProviderToolCall = { type?: unknown; function?: { name?: unknown; arguments?: unknown } };
export function verifiedToolCall(calls: unknown): { name: string; route?: string; target?: string; platform?: string } | null {
  if (!Array.isArray(calls) || calls.length !== 1) return null;
  const call = calls[0] as ProviderToolCall | null;
  if (!call || call.type !== "function" || (call.function?.name !== JARVIS_FUNCTION && call.function?.name !== PHONE_FUNCTION &&
       call.function?.name !== CALL_FUNCTION && call.function?.name !== MESSAGE_FUNCTION) ||
      typeof call.function.arguments !== "string" || call.function.arguments.length > 512) return null;
  let args: unknown;
  try { args = JSON.parse(call.function.arguments); } catch { return null; }
  if (!args || typeof args !== "object" || Array.isArray(args)) return null;
  const entry = args as Record<string, unknown>;
  if (Object.keys(entry).length !== 1) return null;
  if (call.function.name === MESSAGE_FUNCTION) {
    if (typeof entry.platform !== "string" ||
        !["whatsapp", "messenger"].includes(entry.platform)) return null;
    // Never take a message body or recipient from AI output.
    return { name: MESSAGE_FUNCTION, platform: entry.platform };
  }
  if (call.function.name === CALL_FUNCTION) {
    if (typeof entry.target !== "string" || entry.target.trim() !== entry.target ||
        entry.target.length < 1 || entry.target.length > 70 ||
        /[\r\n\u0000-\u001f]/.test(entry.target)) return null;
    // Proposal only. The client MUST validate against the original user's input.
    return { name: CALL_FUNCTION, target: entry.target };
  }
  if (typeof entry.route !== "string" ||
      !((call.function.name === JARVIS_FUNCTION && JARVIS_ROUTES.some(route => route === entry.route)) ||
        (call.function.name === PHONE_FUNCTION && PHONE_ACTIONS.some(route => route === entry.route)))) return null;
  return { name: call.function.name, route: entry.route };
}

