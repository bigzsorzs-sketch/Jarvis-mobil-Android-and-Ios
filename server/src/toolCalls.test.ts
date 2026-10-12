import test from "node:test";
import assert from "node:assert/strict";
import { JARVIS_ROUTES, JARVIS_TOOLS, PHONE_ACTIONS, verifiedToolCall } from "./toolCalls.js";

const call = (name: string, args: unknown) => ({
  type: "function", function: { name, arguments: JSON.stringify(args) }
});

test("exports only real Jarvis menu routes", () => {
  assert.equal(JARVIS_ROUTES.length, 13);
  assert.equal(JARVIS_TOOLS[0]?.function.name, "open_jarvis_page");
  assert.equal(JARVIS_TOOLS[0]?.function.parameters.additionalProperties, false);
});

test("valid one-action menu tool call is proposed, not executed", () => {
  assert.deepEqual(verifiedToolCall([call("open_jarvis_page", { route: "Vércukor" })]), {
    name: "open_jarvis_page", route: "Vércukor"
  });
});

test("unknown routes, extra arguments and executable command names are blocked", () => {
  assert.equal(verifiedToolCall([call("open_jarvis_page", { route: "DELETE_ACCOUNT" })]), null);
  assert.equal(verifiedToolCall([call("shell", { command: "shutdown" })]), null);
  assert.equal(verifiedToolCall([call("open_jarvis_page", { route: "Munka", command: "delete" })]), null);
});

test("multiple calls, malformed JSON and wrong types fail closed", () => {
  const good = call("open_jarvis_page", { route: "Munka" });
  assert.equal(verifiedToolCall([good, good]), null);
  assert.equal(verifiedToolCall([{ type: "function", function: { name: "open_jarvis_page", arguments: "{" } }]), null);
  assert.equal(verifiedToolCall([{ type: "function", function: { name: "open_jarvis_page", arguments: "[]" } }]), null);
  assert.equal(verifiedToolCall([{ type: "function", function: { name: "open_jarvis_page", arguments: JSON.stringify({ route: 42 }) } }]), null);
  assert.equal(verifiedToolCall(null), null);
});

test("can propose exactly one whitelisted external Android app only", () => {
  assert.deepEqual(PHONE_ACTIONS, ["calendar", "alarms", "email", "maps", "dialer"]);
  assert.equal(JARVIS_TOOLS.length, 2);
  assert.deepEqual(verifiedToolCall([call("open_phone_app", { route: "maps" })]), {
    name: "open_phone_app", route: "maps"
  });
  assert.equal(verifiedToolCall([call("open_phone_app", { route: "DELETE" })]), null);
  assert.equal(verifiedToolCall([call("open_phone_app", { route: "maps", text: "secret" })]), null);
  assert.equal(verifiedToolCall([call("make_call", { route: "dialer" })]), null);
});
