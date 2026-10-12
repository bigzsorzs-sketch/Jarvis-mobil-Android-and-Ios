import http, { type IncomingMessage, type ServerResponse } from "node:http";
import { createHash, timingSafeEqual } from "node:crypto";
import { createRemoteJWKSet, jwtVerify } from "jose";
import { createClient } from "redis";

function required(name: string): string {
  const v = process.env[name]?.trim();
  if (!v || v.startsWith("REPLACE_") || v.includes("YOUR_OIDC_")) {
    throw new Error("Missing backend environment setting: " + name);
  }
  return v;
}

const secret = required("OPENROUTER_API_KEY");
const model = required("OPENROUTER_MODEL");
// Production OIDC is optional in the small private tester deployment. Either
// a complete OIDC setup or explicitly provisioned random tester tokens is required.
const oidcNames = ["OIDC_ISSUER", "OIDC_AUDIENCE", "OIDC_JWKS_URL"];
const oidcParts = oidcNames.map((name) => process.env[name]?.trim() || "");
if (oidcParts.some(Boolean) && !oidcParts.every(Boolean)) {
  throw new Error("Incomplete OIDC configuration");
}
const issuer = oidcParts[0];
const audience = oidcParts[1];
const jwks = oidcParts.every(Boolean) ? createRemoteJWKSet(new URL(oidcParts[2]!)) : null;

// SHA-256 hashes of independent 256-bit random bearer tokens; NEVER raw tokens.
// JSON format: {"george":"<64 hex chars>","tester2":"<64 hex chars>"}.
// These are for a small, controlled test only, not a public account system.
const rawTesters = JSON.parse(process.env.TESTER_TOKEN_SHA256_JSON || "{}") as unknown;
if (!rawTesters || typeof rawTesters !== "object" || Array.isArray(rawTesters)) {
  throw new Error("Invalid tester token configuration");
}
const testerRecords = Object.entries(rawTesters).map(([id, hash]) => {
  if (!/^[A-Za-z0-9_-]{1,40}$/.test(id) || typeof hash !== "string" ||
      !/^[a-fA-F0-9]{64}$/.test(hash)) throw new Error("Invalid tester token hash");
  return { id, digest: Buffer.from(hash, "hex") };
});
if (testerRecords.length > 10 || (!jwks && testerRecords.length === 0)) {
  throw new Error("Configure OIDC or 1–10 tester tokens");
}
function findTester(token: string): string | null {
  if (token.length !== 64 || !/^[a-fA-F0-9]{64}$/.test(token)) return null;
  const digest = createHash("sha256").update(token, "utf8").digest();
  for (const tester of testerRecords) {
    if (timingSafeEqual(digest, tester.digest)) return "tester:" + tester.id;
  }
  return null;
}
const redis = createClient({ url: required("REDIS_URL") });
redis.on("error", () => { /* Fail requests closed; never log private content */ });

const PORT = Number(process.env.PORT ?? 8080);
const HOST = process.env.HOST ?? "127.0.0.1";
const BODY_LIMIT = 16 * 1024;
const MAX_INPUT = 8000;
const MAX_OUTPUT_TOKENS = 800;

// Atomic, fail-closed rate and per-account request quota. No admin client input.
const QUOTA_SCRIPT = `
  local r = tonumber(redis.call('GET', KEYS[1]) or '0')
  local m = tonumber(redis.call('GET', KEYS[2]) or '0')
  if r >= tonumber(ARGV[1]) or m >= tonumber(ARGV[2]) then
    return 1
  end
  redis.call('INCR', KEYS[1])
  redis.call('INCR', KEYS[2])
  if r == 0 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3])) end
  if m == 0 then redis.call('EXPIRE', KEYS[2], tonumber(ARGV[4])) end
  return 0
`;

function reply(res: ServerResponse, status: number, data: Record<string, unknown>): void {
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
    "x-content-type-options": "nosniff"
  });
  res.end(JSON.stringify(data));
}
async function readInput(req: IncomingMessage): Promise<unknown> {
  const parts: Buffer[] = [];
  let size = 0;
  for await (const p of req) {
    const bytes = Buffer.isBuffer(p) ? p : Buffer.from(p);
    size += bytes.length;
    if (size > BODY_LIMIT) throw new Error("TOO_LARGE");
    parts.push(bytes);
  }
  return JSON.parse(Buffer.concat(parts).toString("utf8"));
}

const server = http.createServer(async (req, res) => {
  if (req.method === "GET" && req.url === "/health") {
    reply(res, 200, { status: "ok" });
    return;
  }
  if (req.method !== "POST" || req.url !== "/v1/chat") {
    reply(res, 404, { error: "NOT_FOUND" });
    return;
  }
  try {
    const match = /^Bearer\s+(.+)$/i.exec(req.headers.authorization ?? "");
    if (!match?.[1]) return reply(res, 401, { error: "AUTH_REQUIRED" });
    let subject = findTester(match[1]);
    const tester = subject !== null;
    if (!subject && jwks) {
      try {
        const verified = await jwtVerify(match[1], jwks, { issuer, audience });
        subject = verified.payload.sub ?? null;
      } catch {
        return reply(res, 401, { error: "INVALID_TOKEN" });
      }
    }
    if (!subject || !/^[A-Za-z0-9._:@-]{1,160}$/.test(subject)) {
      return reply(res, 401, { error: "INVALID_USER" });
    }
    // Test accounts are explicitly enrolled by the server administrator.
    // Normal paid accounts still require server-verified billing state.
    if (!tester && await redis.get("subscription:" + subject) !== "active") {
      return reply(res, 403, { error: "SUBSCRIPTION_REQUIRED" });
    }
    let data: unknown;
    try { data = await readInput(req); }
    catch { return reply(res, 400, { error: "INVALID_BODY" }); }
    const message = (data as { message?: unknown } | null)?.message;
    if (typeof message !== "string" || !message.trim() || message.length > MAX_INPUT) {
      return reply(res, 400, { error: "INVALID_MESSAGE" });
    }
    // Redis Lua makes both quota checks and consumption atomic across devices.
    // Neither malformed payloads nor concurrent requests can consume/bypass a
    // partially updated quota. This is a request cap, NOT a monetary budget.
    const minute = Math.floor(Date.now() / 60000);
    const month = new Date().toISOString().slice(0, 7);
    const rateKey = "rate:" + subject + ":" + minute;
    const monthlyKey = "monthly:" + subject + ":" + month;
    const quota = await redis.eval(QUOTA_SCRIPT, {
      keys: [rateKey, monthlyKey],
      arguments: ["12", "1000", "180", String(40 * 86400)]
    });
    if (quota !== 0) return reply(res, 429, { error: "USAGE_LIMIT" });
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 25000);
    let upstream: Response;
    try {
      upstream = await fetch("https://openrouter.ai/api/v1/chat/completions", {
        method: "POST",
        headers: {
          "Authorization": "Bearer " + secret,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          model,
          messages: [{ role: "user", content: message }],
          stream: false,
          max_tokens: MAX_OUTPUT_TOKENS
        }),
        signal: controller.signal
      });
    } finally {
      clearTimeout(timer);
    }
    if (!upstream.ok) return reply(res, 502, { error: "PROVIDER_ERROR" });
    const modelReply = await upstream.json() as {
      choices?: Array<{ message?: { content?: unknown } }>;
      usage?: { total_tokens?: number };
    };
    const answer = modelReply.choices?.[0]?.message?.content;
    if (typeof answer !== "string") {
      return reply(res, 502, { error: "PROVIDER_BAD_REPLY" });
    }
    const used = modelReply.usage?.total_tokens;
    if (typeof used === "number" && Number.isFinite(used) && used >= 0) {
      await redis.incrBy("tokens:" + subject + ":" + month, Math.ceil(used));
    }
    reply(res, 200, { reply: answer, ai: true });
  } catch {
    reply(res, 503, { error: "SERVICE_UNAVAILABLE" });
  }
});

async function start(): Promise<void> {
  if (!Number.isInteger(PORT) || PORT < 1 || PORT > 65535) throw new Error("Invalid port");
  await redis.connect();
  server.listen(PORT, HOST);
}
start().catch(() => { process.exitCode = 1; });
