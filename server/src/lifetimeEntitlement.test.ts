import test from "node:test";
import assert from "node:assert/strict";
import { hasLifetimeLicense } from "./lifetimeEntitlement.js";

const issuer = "https://egokjtlaecnqbyxlehqu.supabase.co/auth/v1";
const key = "sb_publishable_testingonly";
const access = "x".repeat(128);

test("owner and family licenses are allowed after Supabase-side identity verification", async () => {
  for (const tier of ["owner", "family"]) {
    let called = 0;
    const mock = async (url: URL, options: RequestInit) => {
      ++called;
      assert.equal(url.toString(),
        "https://egokjtlaecnqbyxlehqu.supabase.co/rest/v1/rpc/jarvis_my_lifetime_license");
      assert.equal(options.method, "POST");
      assert.equal((options.headers as Record<string, string>).Authorization, "Bearer " + access);
      return new Response(JSON.stringify(tier), { status: 200 });
    };
    assert.equal(await hasLifetimeLicense(issuer, key, access, mock as typeof fetch), true);
    assert.equal(called, 1);
  }
});

test("non-family accounts or RPC errors never bypass paid subscription checks", async () => {
  for (const result of [null, "unknown", true, { role: "owner" }]) {
    assert.equal(await hasLifetimeLicense(issuer, key, access,
      (async () => new Response(JSON.stringify(result), { status: 200 })) as typeof fetch),
      false);
  }
  assert.equal(await hasLifetimeLicense(issuer, key, access,
    (async () => new Response("error", { status: 503 })) as typeof fetch), false);
  assert.equal(await hasLifetimeLicense(issuer, key, access,
    (async () => { throw new Error("network failure"); }) as typeof fetch), false);
});

test("invalid/missing Supabase settings never send tokens over network", async () => {
  let called = 0;
  const forbidden = (async () => { called++; throw new Error("Unexpected network call"); }) as typeof fetch;
  assert.equal(await hasLifetimeLicense("http://example.com/auth/v1", key, access, forbidden), false);
  assert.equal(await hasLifetimeLicense("https://evil.example.com/auth/v1", key, access, forbidden), false);
  assert.equal(await hasLifetimeLicense(issuer, "service_role_NOT_A_PUBLIC_KEY", access, forbidden), false);
  assert.equal(await hasLifetimeLicense(issuer, key, "short", forbidden), false);
  assert.equal(called, 0);
});
