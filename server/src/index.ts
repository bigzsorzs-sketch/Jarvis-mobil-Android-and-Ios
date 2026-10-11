import http, { type IncomingMessage, type ServerResponse } from "node:http";
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
const issuer = required("OIDC_ISSUER");
const audience = required("OIDC_AUDIENCE");
const jwks = createRemoteJWKSet(new URL(required("OIDC_JWKS_URL")));
const redis = createClient({ url: required("REDIS_URL") });
redis.on("error", () => { /* Fail requests closed; never log private content */ });

const PORT = Number(process.env.PORT ?? 8080);
const HOST = process.env.HOST ?? "127.0.0.1";
const BODY_LIMIT = 16 * 1024;
const MAX_INPUT = 8000;
const MAX_OUTPUT_TOKENS = 800;

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
    const verified = await jwtVerify(match[1], jwks, { issuer, audience });
    const subject = verified.payload.sub;
    if (!subject || !/^[A-Za-z0-9._:@-]{1,160}$/.test(subject)) {
      return reply(res, 401, { error: "INVALID_USER" });
    }
    // Subscription is assigned only by a trusted billing service, never the app.
    if (await redis.get("subscription:" + subject) !== "active") {
      return reply(res, 403, { error: "SUBSCRIPTION_REQUIRED" });
    }
    const minute = Math.floor(Date.now() / 60000);
    const month = new Date().toISOString().slice(0, 7);
    const rateKey = "rate:" + subject + ":" + minute;
    const monthlyKey = "monthly:" + subject + ":" + month;
    const count = await redis.incr(rateKey);
    const monthly = await redis.incr(monthlyKey);
    await Promise.all([redis.expire(rateKey, 180), redis.expire(monthlyKey, 40 * 86400)]);
    if (count > 12 || monthly > 1000) {
      return reply(res, 429, { error: "USAGE_LIMIT" });
    }
    let data: unknown;
    try { data = await readInput(req); }
    catch { return reply(res, 400, { error: "INVALID_BODY" }); }
    const message = (data as { message?: unknown } | null)?.message;
    if (typeof message !== "string" || !message.trim() || message.length > MAX_INPUT) {
      return reply(res, 400, { error: "INVALID_MESSAGE" });
    }
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
