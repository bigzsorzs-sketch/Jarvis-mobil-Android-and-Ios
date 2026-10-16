/**
 * Owner and Family Lifetime are free entitlements, NOT paid subscriptions.
 * Supabase Auth writes app_metadata and signs it into the JWT. Unlike
 * user_metadata this field cannot be changed from a user's client session.
 * The caller MUST run jwtVerify with trusted issuer/audience/JWKS first.
 */
export type LifetimeLicense = "owner_lifetime" | "family_lifetime";

export function lifetimeLicenseFromVerifiedClaims(claims: unknown): LifetimeLicense | null {
  if (!claims || typeof claims !== "object" || Array.isArray(claims)) return null;
  const token = claims as Record<string, unknown>;
  if (token.is_anonymous === true) return null;
  if (!token.app_metadata || typeof token.app_metadata !== "object" ||
      Array.isArray(token.app_metadata)) return null;
  const value = (token.app_metadata as Record<string, unknown>).jarvis_license;
  return value === "owner_lifetime" || value === "family_lifetime" ? value : null;
}
