import test from "node:test";
import assert from "node:assert/strict";
import { createClient } from "redis";
import { createInviteCode, saveInvite, redeemInvite, betaSessionSubject,
  isInviteCode, SESSION_TTL_SECONDS } from "./betaInvite.js";

function fakeRedis(): ReturnType<typeof createClient> {
  const data = new Map<string,string>();
  const client = {
    async set(key: string, value: string, opts: { NX?: boolean; EX?: number }) {
      assert.ok(opts.EX && opts.EX >= 1);
      if (opts.NX && data.has(key)) return null;
      data.set(key,value);
      return "OK";
    },
    async get(key: string) { return data.get(key) ?? null; },
    async getDel(key: string) {
      const value = data.get(key) ?? null;
      data.delete(key);
      return value;
    }
  };
  return client as unknown as ReturnType<typeof createClient>;
}

test("tester invitations are high entropy, one use and never provider API keys", async () => {
  const redis = fakeRedis();
  const invite = createInviteCode();
  assert.equal(invite.length, 48);
  assert.equal(isInviteCode(invite), true);
  assert.equal(isInviteCode("sk-or-abc"), false);
  await saveInvite(redis, invite, "tester_a");
  const [first, second] = await Promise.all([
    redeemInvite(redis, invite), redeemInvite(redis, invite)
  ]);
  assert.ok(first);
  assert.equal(first.token.length, 64);
  assert.equal(first.expiresIn, SESSION_TTL_SECONDS);
  assert.equal(second, null);
  assert.equal(await betaSessionSubject(redis, first.token), "tester:beta_tester_a");
  assert.equal(await betaSessionSubject(redis, "0".repeat(64)), null);
});

test("malformed codes and invalid IDs are refused", async () => {
  const redis = fakeRedis();
  assert.equal(await redeemInvite(redis, "not-a-code"), null);
  await assert.rejects(saveInvite(redis, createInviteCode(), "wrong id\n"));
});
