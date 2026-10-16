/**
 * Read the owner's/family's lifetime grant from the actual Supabase Auth user.
 * The RPC is SECURITY DEFINER and compares auth.uid() to auth.users.id AND
 * requires email_confirmed_at. No email from the mobile client is trusted.
 * Failure to reach Supabase never grants free access.
 */
export async function hasLifetimeLicense(
  issuer: string,
  publishableKey: string | undefined,
  verifiedAccessToken: string,
  fetcher: typeof fetch = fetch
): Promise<boolean> {
  if (!publishableKey?.startsWith("sb_publishable_") ||
      verifiedAccessToken.length > 10000 || verifiedAccessToken.length < 50)
    return false;
  let url: URL;
  try {
    const base = new URL(issuer);
    // Avoid SSRF: call only the same verified Supabase identity provider.
    if (base.protocol !== "https:" ||
        !base.hostname.endsWith(".supabase.co") ||
        base.username || base.password || base.port ||
        base.pathname.replace(/\/$/, "") !== "/auth/v1")
      return false;
    url = new URL("/rest/v1/rpc/jarvis_my_lifetime_license", base.origin);
  } catch {
    return false;
  }
  const controller = new AbortController();
  const deadline = setTimeout(() => controller.abort(), 4500);
  try {
    const response = await fetcher(url, {
      method: "POST",
      headers: {
        apikey: publishableKey,
        Authorization: "Bearer " + verifiedAccessToken,
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      body: "{}",
      signal: controller.signal,
      redirect: "error",
    });
    if (!response.ok) return false;
    if (Number(response.headers.get("content-length") || "0") > 256) return false;
    const value: unknown = await response.json();
    return value === "owner" || value === "family";
  } catch {
    return false;
  } finally {
    clearTimeout(deadline);
  }
}
