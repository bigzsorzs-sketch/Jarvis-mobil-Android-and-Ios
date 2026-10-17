import assert from "node:assert/strict";
import test from "node:test";
import { relayTextOnlySse } from "./chatStream.js";

function response(chunks: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  return new ReadableStream({
    start(controller) {
      for (const chunk of chunks) controller.enqueue(encoder.encode(chunk));
      controller.close();
    }
  });
}
const textEvent = (text: string) =>
  "data: " + JSON.stringify({ choices: [{ delta: { content: text } }] }) + "\n\n";

test("forwards split text safely, keeps usage for server billing, then completes", async () => {
  const result: string[] = [];
  const events = textEvent("Helló ") + textEvent("világ") +
    'data: {"choices":[],"usage":{"total_tokens":41}}\n\n' +
    "data: [DONE]\n\n";
  const tokens = await relayTextOnlySse(response([
    events.slice(0, 11), events.slice(11, 29), events.slice(29)
  ]), frame => result.push(frame));
  assert.equal(tokens, 41);
  assert.deepEqual(result, [textEvent("Helló "), textEvent("világ"), "data: [DONE]\n\n"]);
});

test("does not expose provider reasoning, metadata or account details", async () => {
  const result: string[] = [];
  const events = 'data: {"choices":[{"delta":{"reasoning":"private reasoning","content":"Answer"}}],"provider":"secret"}\n\n' +
    "data: [DONE]\n\n";
  await relayTextOnlySse(response([events]), frame => result.push(frame));
  assert.equal(result.join(""), textEvent("Answer") + "data: [DONE]\n\n");
});

test("accepts CRLF framing split between network reads", async () => {
  const frames = textEvent("Hello").replace(/\n/g, "\r\n") +
    "data: [DONE]\r\n\r\n";
  const boundary = frames.indexOf("\r\n") + 1;
  const collected: string[] = [];
  await relayTextOnlySse(response([frames.slice(0, boundary), frames.slice(boundary)]),
    item => collected.push(item));
  assert.deepEqual(collected, [textEvent("Hello"), "data: [DONE]\n\n"]);
});

test("rejects unexpected tool calls and incomplete output", async () => {
  await assert.rejects(() => relayTextOnlySse(response([
    'data: {"choices":[{"delta":{"tool_calls":[{"id":"x"}]}}]}\n\n'
  ]), () => {}), /UNEXPECTED_STREAM_ACTION/);
  await assert.rejects(() => relayTextOnlySse(response([textEvent("not done")]),
    () => {}), /INCOMPLETE_PROVIDER_STREAM/);
});

test("rejects malformed provider errors without echoing secret details", async () => {
  await assert.rejects(() => relayTextOnlySse(response([
    'data: {"error":{"message":"hidden-token"}}\n\n'
  ]), () => {}), /PROVIDER_STREAM_ERROR/);
});
