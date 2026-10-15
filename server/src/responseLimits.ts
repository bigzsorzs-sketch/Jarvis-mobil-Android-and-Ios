/**
 * Bounded OpenRouter response parsing. A model provider is external input:
 * do not let a valid HTTPS response exhaust server memory or hang indefinitely.
 */
export const MAX_PROVIDER_RESPONSE_BYTES = 8 * 1024 * 1024;

export async function readLimitedProviderJson(
  response: Response, maxBytes = MAX_PROVIDER_RESPONSE_BYTES
): Promise<unknown> {
  if (!Number.isSafeInteger(maxBytes) || maxBytes < 1 || maxBytes > MAX_PROVIDER_RESPONSE_BYTES) {
    throw new Error("INVALID_RESPONSE_LIMIT");
  }
  const advertised = response.headers.get("content-length");
  if (advertised && Number(advertised) > maxBytes) throw new Error("PROVIDER_RESPONSE_TOO_LARGE");
  if (!response.body) throw new Error("MISSING_PROVIDER_BODY");

  const reader = response.body.getReader();
  const chunks: Buffer[] = [];
  let total = 0;
  try {
    while (true) {
      const packet = await reader.read();
      if (packet.done) break;
      const size = packet.value.byteLength;
      if (size > maxBytes - total) throw new Error("PROVIDER_RESPONSE_TOO_LARGE");
      chunks.push(Buffer.from(packet.value));
      total += size;
    }
    return JSON.parse(Buffer.concat(chunks, total).toString("utf8")) as unknown;
  } finally {
    await reader.cancel().catch(() => {});
    reader.releaseLock();
  }
}
