import test from "node:test";
import assert from "node:assert/strict";
import { boundedMonthlyQuota, monthlyQuotas } from "./monthlyQuotas.js";

test("uses conservative private beta defaults", () => {
  assert.deepEqual(monthlyQuotas({}), { chat: 1000, voice: 250, transcribe: 400 });
});

test("allows explicitly configured, capped high-usage plans", () => {
  assert.deepEqual(monthlyQuotas({
    JARVIS_CHAT_MONTHLY_REQUESTS: "3200",
    JARVIS_VOICE_MONTHLY_SEGMENTS: "10000",
    JARVIS_TRANSCRIPTION_MONTHLY_REQUESTS: "3200"
  }), { chat: 3200, voice: 10000, transcribe: 3200 });
});

test("rejects invalid, infinite, negative or excessive quotas at startup", () => {
  for (const raw of ["-1", "0", "3.5", "NaN", "Infinity", "abc", "20001", "1e6"]) {
    assert.throws(() => boundedMonthlyQuota(raw, 250, 20_000));
  }
  assert.throws(() => monthlyQuotas({ JARVIS_CHAT_MONTHLY_REQUESTS: "20000" }));
});
