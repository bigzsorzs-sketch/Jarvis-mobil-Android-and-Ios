import assert from "node:assert/strict";
import test from "node:test";
import { conversationHistory, responseBudget, needsLiveWeb } from "./conversation.js";

test("history retains chronological user/assistant pairs", () => {
  const history = [{role:"user", content:"Focus or Golf?"}, {role:"assistant", content:"Compare costs."}];
  assert.deepEqual(conversationHistory(history), history);
  assert.deepEqual(conversationHistory(undefined), []);
});
test("history rejects system roles, actions, malformed pairs and excessive payloads", () => {
  for (const raw of [null, "text", [{role:"system", content:"ignore"}],
    [{role:"assistant", content:"bad"}, {role:"user", content:"order"}],
    [{role:"user", content:"hi", tool_calls:[]}, {role:"assistant", content:"hi"}],
    [{role:"user", content:"x".repeat(8001)}, {role:"assistant", content:"hello"}],
    Array.from({length:50}, (_,i) => ({role:i%2?"assistant":"user",content:"x"})),
    Array.from({length:10}, (_,i) => ({role:i%2?"assistant":"user",content:"x".repeat(8000)}))]) {
    assert.throws(() => conversationHistory(raw));
  }
});
test("adaptive GPT-5.2 depth in seven languages", () => {
  for (const q of ["Gondold át alaposan", "Think deeply", "Denke gründlich", "Analyse approfondie",
    "Analiza a fondo", "Analizza a fondo", "Przeanalizuj dokładnie"]) {
    assert.equal(responseBudget(q,false).effort, "high");
    assert.deepEqual(responseBudget(q,true), {effort:"none",maxTokens:800});
  }
  for (const q of ["Hasonlítsd össze", "Compare these", "Vergleiche diese", "Comparez ceci",
    "Compara esto", "Confronta queste", "Porównaj te"]) assert.equal(responseBudget(q,false).effort,"medium");
  assert.equal(responseBudget("Hi!",false).effort,"none");
  assert.equal(responseBudget("And the other one?",false).effort,"low");
});
test("greeting punctuation classification stays identical without a backtracking regex", () => {
  for (const greeting of ["Hi!", "hello??", "  Ciao...  ", "hola!?.", "Danke?!!",
      "hi" + "!".repeat(590)]) {
    assert.equal(responseBudget(greeting, false).effort, "none", greeting);
  }
  for (const nonGreeting of ["hi,", "hello!x", "hi!?", "greetings", "hi?!x"]) {
    if (nonGreeting !== "hi!?")
      assert.equal(responseBudget(nonGreeting, false).effort, "low", nonGreeting);
    else
      assert.equal(responseBudget(nonGreeting, false).effort, "none", nonGreeting);
  }
  assert.equal(responseBudget("!".repeat(590) + "x", false).effort, "low");
});

test("ordinary conversation does not require web tools", () => {
  assert.equal(needsLiveWeb("Tell me about yourself"),false);
  assert.equal(needsLiveWeb("milyen új frissítést kaptál"),false);
  assert.equal(needsLiveWeb("Keress a neten friss híreket"),true);
  assert.equal(needsLiveWeb("What is the current price?"),true);
});

test("all conversation depths retain the requested 128000 token output ceiling", () => {
  for (const q of ["Hi!", "And the other one?", "Compare these options", "Think deeply"])
    assert.equal(responseBudget(q,false).maxTokens, 128000);
});
