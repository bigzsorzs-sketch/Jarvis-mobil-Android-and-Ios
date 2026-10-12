import { createClient } from "redis";
import { createInviteCode, saveInvite } from "./betaInvite.js";
import { randomBytes } from "node:crypto";

/**
 * Run ONLY inside the private gateway host, never in GitHub Actions logs:
 * docker compose exec gateway node dist/createInvite.js tester
 * The printed invitation is short-lived and one-use; send it privately.
 */
const rawName = process.argv[2] || "tester";
const label = rawName.replace(/[^A-Za-z0-9_-]/g, "").slice(0, 22);
if (!label) throw new Error("Invalid tester name");
if (process.env.BETA_INVITES_ENABLED !== "true" ||
    process.env.PUBLIC_LAUNCH_ENABLED === "true") {
  throw new Error("Invitation creation allowed only for private beta");
}
const url = process.env.REDIS_URL;
if (!url) throw new Error("Missing REDIS_URL");
const redis = createClient({ url });
try {
  await redis.connect();
  const id = label + "_" + randomBytes(5).toString("hex");
  const invitation = createInviteCode();
  await saveInvite(redis, invitation, id);
  process.stdout.write("PRIVATE one-time Jarvis activation (expires in 48 hours):\n" +
    invitation + "\n");
} finally {
  if (redis.isOpen) await redis.quit();
}
