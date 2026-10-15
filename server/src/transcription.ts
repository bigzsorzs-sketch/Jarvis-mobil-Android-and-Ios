/** Parse exactly one bounded mono 16k WAV supplied by an authenticated client.
 * Never store or log PCM. No URL fetching or model selection from the device.
 */
export type TranscriptionAudio = { data: string; format: "wav"; language: string };

const ALLOWED = new Set(["hu", "en", "de", "fr", "es", "it", "pl"]);
const MAX_WAV_BYTES = 550_000;
export const MAX_TRANSCRIPTION_JSON_BYTES = 900_000;

export function validatedTranscriptionAudio(request: unknown): TranscriptionAudio | null {
  if (!request || typeof request !== "object" || Array.isArray(request)) return null;
  const value = request as Record<string, unknown>;
  const audio = value.input_audio;
  if (!audio || typeof audio !== "object" || Array.isArray(audio)) return null;
  const a = audio as Record<string, unknown>;
  const language = value.language;
  const data = a.data;
  if (a.format !== "wav" || typeof language !== "string" || !ALLOWED.has(language) ||
      typeof data !== "string" || data.length < 1500 || data.length > 735_000 ||
      data.length % 4 !== 0 || !/^[A-Za-z0-9+/]+={0,2}$/.test(data)) return null;
  const wav = Buffer.from(data, "base64");
  if (wav.length < 1000 || wav.length > MAX_WAV_BYTES ||
      wav.toString("ascii", 0, 4) !== "RIFF" ||
      wav.toString("ascii", 8, 12) !== "WAVE" ||
      wav.toString("ascii", 36, 40) !== "data" ||
      wav.readUInt16LE(20) !== 1 || wav.readUInt16LE(22) !== 1 ||
      wav.readUInt32LE(24) !== 16000 || wav.readUInt16LE(34) !== 16 ||
      wav.readUInt32LE(40) !== wav.length - 44) return null;
  return { data, format: "wav", language };
}
