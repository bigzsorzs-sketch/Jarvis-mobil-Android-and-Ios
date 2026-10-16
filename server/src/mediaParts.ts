/**
 * Untrusted Android attachment envelopes. Only user-clicked Send supplies them;
 * they are never used as model instructions or phone actions.
 */
export type MediaPart =
  | { type: "text"; text: string }
  | { type: "image_url"; image_url: { url: string } }
  | { type: "file"; file: { filename: string; file_data: string } };

const MAX_ITEMS = 20;
const MAX_IMAGES = 10;
const MAX_FILE_BYTES = 5 * 1024 * 1024;
const MAX_TOTAL_BYTES = 20 * 1024 * 1024;
const IMAGE_MIME = new Set(["image/jpeg", "image/png", "image/webp", "image/gif"]);
const DATA_PREFIX = "data:";

function obj(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown> : null;
}
function fixedKeys(item: Record<string, unknown>, names: string[]): boolean {
  return Object.keys(item).length === names.length &&
    names.every(key => Object.hasOwn(item, key));
}
/** Reject external URLs, noncanonical base64, oversized or mislabeled binary. */
function bytesFromDataUrl(url: unknown, accepted: Set<string>): { bytes: Buffer; mime: string } {
  if (typeof url !== "string" || url.length > 7_200_000 ||
      !url.startsWith(DATA_PREFIX)) throw new Error("INVALID_MEDIA");
  const comma = url.indexOf(",");
  if (comma < 0 || comma > 60) throw new Error("INVALID_MEDIA");
  const header = url.slice(5, comma);
  if (!header.endsWith(";base64")) throw new Error("INVALID_MEDIA");
  const mime = header.slice(0, -7);
  if (!accepted.has(mime)) throw new Error("INVALID_MEDIA");
  const encoded = url.slice(comma + 1);
  if (!encoded || encoded.length > Math.ceil(MAX_FILE_BYTES / 3) * 4 + 4 ||
      !/^[A-Za-z0-9+/]*={0,2}$/.test(encoded) || encoded.length % 4 !== 0)
    throw new Error("INVALID_MEDIA");
  const bytes = Buffer.from(encoded, "base64");
  if (!bytes.length || bytes.length > MAX_FILE_BYTES ||
      bytes.toString("base64") !== encoded) throw new Error("INVALID_MEDIA");
  return { bytes, mime };
}
function correctImageMagic(buffer: Buffer, mime: string): boolean {
  if (mime === "image/jpeg") return buffer.length > 4 && buffer[0] === 0xff && buffer[1] === 0xd8;
  if (mime === "image/png") return buffer.length > 8 &&
    buffer.subarray(0, 8).equals(Buffer.from([137,80,78,71,13,10,26,10]));
  if (mime === "image/gif") return buffer.length > 6 &&
    (buffer.toString("ascii", 0, 6) === "GIF89a" || buffer.toString("ascii", 0, 6) === "GIF87a");
  if (mime === "image/webp") return buffer.length > 12 &&
    buffer.toString("ascii", 0, 4) === "RIFF" && buffer.toString("ascii", 8, 12) === "WEBP";
  return false;
}

/** A null return indicates ordinary text-only chat; errors are intentional 400s. */
export function validatedMediaParts(raw: unknown, message: string): MediaPart[] | null {
  if (raw === undefined) return null;
  if (!Array.isArray(raw) || raw.length < 2 || raw.length > MAX_ITEMS * 2 + 1)
    throw new Error("INVALID_MEDIA");
  const result: MediaPart[] = [];
  let images = 0;
  let files = 0;
  let total = 0;
  let textBytes = 0;
  for (let index = 0; index < raw.length; index++) {
    const part = obj(raw[index]);
    if (!part) throw new Error("INVALID_MEDIA");
    if (part.type === "text") {
      if (!fixedKeys(part, ["type", "text"]) || typeof part.text !== "string")
        throw new Error("INVALID_MEDIA");
      if (index === 0 && part.text !== message.trim()) throw new Error("INVALID_MEDIA");
      const length = Buffer.byteLength(part.text);
      if (!length || length > 256 * 1024) throw new Error("INVALID_MEDIA");
      textBytes += length;
      if (textBytes > 512 * 1024) throw new Error("INVALID_MEDIA");
      result.push({ type: "text", text: part.text });
    } else if (part.type === "image_url") {
      const inner = obj(part.image_url);
      if (!fixedKeys(part, ["type","image_url"]) || !inner || !fixedKeys(inner, ["url"]))
        throw new Error("INVALID_MEDIA");
      const { bytes, mime } = bytesFromDataUrl(inner.url, IMAGE_MIME);
      if (!correctImageMagic(bytes, mime)) throw new Error("INVALID_MEDIA");
      total += bytes.length;
      if (++images > MAX_IMAGES || total > MAX_TOTAL_BYTES) throw new Error("INVALID_MEDIA");
      result.push({ type: "image_url", image_url: { url: inner.url as string } });
    } else if (part.type === "file") {
      const inner = obj(part.file);
      if (!fixedKeys(part, ["type", "file"]) || !inner ||
          !fixedKeys(inner, ["filename", "file_data"]) ||
          typeof inner.filename !== "string" ||
          inner.filename.length < 1 || inner.filename.length > 120 ||
          /[\\/\x00-\x1f]/.test(inner.filename)) throw new Error("INVALID_MEDIA");
      const { bytes, mime } = bytesFromDataUrl(inner.file_data, new Set(["application/pdf"]));
      if (mime !== "application/pdf" || bytes.subarray(0,5).toString("ascii") !== "%PDF-")
        throw new Error("INVALID_MEDIA");
      total += bytes.length;
      if (total > MAX_TOTAL_BYTES) throw new Error("INVALID_MEDIA");
      files++;
      result.push({ type: "file", file: {
        filename: inner.filename, file_data: inner.file_data as string
      } });
    } else throw new Error("INVALID_MEDIA");
  }
  if (images + files === 0 || images + files > MAX_ITEMS) throw new Error("INVALID_MEDIA");
  return result;
}
