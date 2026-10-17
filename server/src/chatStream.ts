/** Relay only ordinary text deltas from OpenRouter's SSE response.
 * Tool calls, reasoning text, provider diagnostics and credentials are never
 * forwarded as Android commands. This is NOT a tool execution channel.
 */
const MAX_PROVIDER_BYTES = 8 * 1024 * 1024;
const MAX_VISIBLE_CHARACTERS = 2 * 1024 * 1024;
const MAX_SSE_FRAME = 65_536;

export async function relayTextOnlySse(
  source: ReadableStream<Uint8Array>,
  emit: (frame: string) => void
): Promise<number | null> {
  const reader = source.getReader();
  const decoder = new TextDecoder();
  let pending = "";
  let receivedBytes = 0;
  let visibleChars = 0;
  let usageTokens: number | null = null;
  let finished = false;

  const accept = (frame: string): void => {
    const data = frame.split(/\r?\n/).filter(line => line.startsWith("data:"))
      .map(line => line.slice(5).trimStart()).join("\n");
    if (!data) return;
    if (data === "[DONE]") {
      emit("data: [DONE]\n\n");
      finished = true;
      return;
    }
    if (data.length > MAX_SSE_FRAME) throw new Error("PROVIDER_FRAME_TOO_LARGE");
    let event: Record<string, unknown>;
    try { event = JSON.parse(data) as Record<string, unknown>; }
    catch { throw new Error("MALFORMED_PROVIDER_STREAM"); }
    if (!event || typeof event !== "object" || Array.isArray(event) || event.error)
      throw new Error("PROVIDER_STREAM_ERROR");
    const usage = event.usage as { total_tokens?: unknown } | undefined;
    if (typeof usage?.total_tokens === "number" && Number.isSafeInteger(usage.total_tokens) &&
        usage.total_tokens >= 0) usageTokens = usage.total_tokens;
    const choices = event.choices;
    if (!Array.isArray(choices) || choices.length === 0) return;
    const first = choices[0] as { delta?: { content?: unknown; tool_calls?: unknown; function_call?: unknown };
      finish_reason?: unknown } | undefined;
    if (!first || typeof first !== "object" ||
        first.delta?.tool_calls != null || first.delta?.function_call != null ||
        (first.finish_reason !== null && first.finish_reason !== undefined &&
         first.finish_reason !== "stop")) throw new Error("UNEXPECTED_STREAM_ACTION");
    const value = first.delta?.content;
    if (value === undefined || value === null || value === "") return;
    if (typeof value !== "string") throw new Error("INVALID_STREAM_CONTENT");
    visibleChars += value.length;
    if (visibleChars > MAX_VISIBLE_CHARACTERS) throw new Error("STREAM_TEXT_TOO_LARGE");
    emit("data: " + JSON.stringify({ choices: [{ delta: { content: value } }] }) + "\n\n");
  };

  try {
    while (!finished) {
      const next = await reader.read();
      if (next.done) break;
      receivedBytes += next.value.byteLength;
      if (receivedBytes > MAX_PROVIDER_BYTES) throw new Error("PROVIDER_STREAM_TOO_LARGE");
      pending += decoder.decode(next.value, { stream: true }).replace(/\r\n/g, "\n");
      if (pending.length > MAX_SSE_FRAME * 2) throw new Error("PROVIDER_FRAME_TOO_LARGE");
      let end: number;
      while (!finished && (end = pending.indexOf("\n\n")) !== -1) {
        const frame = pending.slice(0, end);
        pending = pending.slice(end + 2);
        accept(frame);
      }
    }
    if (!finished) throw new Error("INCOMPLETE_PROVIDER_STREAM");
    return usageTokens;
  } finally {
    await reader.cancel().catch(() => {});
    reader.releaseLock();
  }
}
