import assert from "node:assert/strict";
import test from "node:test";
import { responseBudget } from "./conversation.js";
import { responseDeadlineMs } from "./responseDeadline.js";

test("hung provider is bounded without lowering 128k response ceiling", () => {
  const cases: Array<[string, string, number]> = [
    ["Hello!", "none", 45_000],
    ["And how does it work?", "low", 100_000],
    ["Compare these two Android APIs", "medium", 240_000],
    ["Think deeply about this algorithm", "high", 600_000]
  ];
  for (const [question, effort, maxWait] of cases) {
    const budget = responseBudget(question, false);
    assert.equal(budget.effort, effort);
    assert.equal(responseDeadlineMs(budget.effort, false), maxWait);
    assert.equal(budget.maxTokens, 128_000);
  }
  assert.equal(responseDeadlineMs("high", true), 45_000);
  assert.throws(() => responseDeadlineMs("unknown", false));
});
