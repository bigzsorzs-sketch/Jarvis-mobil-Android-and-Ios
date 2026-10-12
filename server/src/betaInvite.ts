import { createHash, randomBytes } from "node:crypto";
import { createClient } from "redis";
type InviteRedis = ReturnType<typeof createClient>;

export const INVITE_BYTES = 24;
export const SESSION_BYTES = 32;
export const INVITE_TTL_SECONDS = 2 * 86400;
export const SESSION_TTL_SECONDS = 90 * 86400;
const allowedId = /^[A-Za-z0-9_-]{1,40}$/;
const hex = (s: string): string => createHash("sha256").update(s, "utf8").digest("hex");

export function isInviteCode(input: unknown): input is string {
  return typeof input === "string" && /^[0-9a-f]{48}$/.test(input);
}

export function createInviteCode(): string { return randomBytes(INVITE_BYTES).toString("hex"); }

/** Store only a hash of the high-entropy, one-time invitation. */
export async function saveInvite(redis: InviteRedis, invite: string, id: string): Promise<void> {
  if (!isInviteCode(invite) || !allowedId.test(id)) throw new Error("Invalid invitation");
  const ok = await redis.set("beta:invite:" + hex(invite), id, {
    NX: true, EX: INVITE_TTL_SECONDS
  });
  if (ok !== "OK") throw new Error("Invite collision");
}

/** Atomic one-use redemption. A compromised invite cannot be used twice. */
export async function redeemInvite(redis: InviteRedis, input: unknown):
    Promise<{ token: string; expiresIn: number } | null> {
  if (!isInviteCode(input)) return null;
  const id = await redis.getDel("beta:invite:" + hex(input));
  if (!id || !allowedId.test(id)) return null;
  const token = randomBytes(SESSION_BYTES).toString("hex");
  await redis.set("beta:session:" + hex(token), id, { EX: SESSION_TTL_SECONDS });
  return { token, expiresIn: SESSION_TTL_SECONDS };
}

/** Revocation: delete beta:session:<SHA256(token)> in Redis. */
export async function betaSessionSubject(redis: InviteRedis, token: string):
    Promise<string | null> {
  if (!/^[0-9a-f]{64}$/.test(token)) return null;
  const id = await redis.get("beta:session:" + hex(token));
  return id && allowedId.test(id) ? "tester:beta_" + id : null;
}
