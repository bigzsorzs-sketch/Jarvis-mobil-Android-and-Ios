/**
 * Request quotas are NOT spending caps: tokens and TTS length still determine cost.
 * Raising these defaults requires an operator-supplied environment override.
 * Reject invalid values at startup instead of silently creating unlimited usage.
 */
export function boundedMonthlyQuota(raw: string | undefined, fallback: number, max: number): number {
  if (raw === undefined || raw.trim() === "") return fallback;
  if (!/^[1-9][0-9]*$/.test(raw)) throw new Error("INVALID_MONTHLY_QUOTA");
  const count = Number(raw);
  if (!Number.isSafeInteger(count) || count > max) throw new Error("MONTHLY_QUOTA_EXCEEDS_CAP");
  return count;
}

export function monthlyQuotas(env: NodeJS.ProcessEnv) {
  return {
    chat: boundedMonthlyQuota(env.JARVIS_CHAT_MONTHLY_REQUESTS, 1000, 10_000),
    voice: boundedMonthlyQuota(env.JARVIS_VOICE_MONTHLY_SEGMENTS, 250, 20_000),
    transcribe: boundedMonthlyQuota(env.JARVIS_TRANSCRIPTION_MONTHLY_REQUESTS, 400, 10_000)
  };
}
