import test from "node:test";
import assert from "node:assert/strict";
import { MAX_PROVIDER_RESPONSE_BYTES, readLimitedProviderJson } from "./responseLimits.js";

test("reads a small valid JSON result", async () => {
  const body = { choices: [{ message: { content: "Szia" } }] };
  assert.deepEqual(await readLimitedProviderJson(new Response(JSON.stringify(body))), body);
});

test("refuses an oversized response even when the server omits content-length", async () => {
  const body = new Response("123456");
  await assert.rejects(() => readLimitedProviderJson(body, 5), /PROVIDER_RESPONSE_TOO_LARGE/);
});

test("refuses untrusted advertised size before consuming body", async () => {
  const response = new Response("{}", { headers: { "content-length": "99999999" } });
  await assert.rejects(() => readLimitedProviderJson(response), /PROVIDER_RESPONSE_TOO_LARGE/);
});

test("rejects malformed JSON and invalid limits", async () => {
  await assert.rejects(() => readLimitedProviderJson(new Response("{")), SyntaxError);
  await assert.rejects(() => readLimitedProviderJson(new Response("{}"), 0), /INVALID_RESPONSE_LIMIT/);
  await assert.rejects(() => readLimitedProviderJson(new Response("{}"), MAX_PROVIDER_RESPONSE_BYTES + 1),
    /INVALID_RESPONSE_LIMIT/);
});
