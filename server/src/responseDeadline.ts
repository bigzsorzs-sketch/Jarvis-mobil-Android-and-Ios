import type { responseBudget } from "./conversation.js";

/**
 * Bound every provider request, including slow/partial response bodies.
 * High-effort reasoning still receives a generous deadline; a dead upstream
 * must not hold a customer session or worker open for 44 minutes.
 * These are transport deadlines, NOT model output-token limits.
 */
export function responseDeadlineMs(
  effort: ReturnType<typeof responseBudget>["effort"],
  hasTools: boolean
): number {
  if (hasTools) return 45_000;
  switch (effort) {
    case "none": return 45_000;
    case "low": return 100_000;
    case "medium": return 240_000;
    case "high": return 600_000;
    default: throw new Error("INVALID_REASONING_EFFORT");
  }
}
