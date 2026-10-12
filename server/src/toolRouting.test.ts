import test from "node:test";
import assert from "node:assert/strict";
import { shouldOfferToolCalls } from "./toolRouting.js";

test("plain questions and chat never ask OpenRouter for function calling", () => {
  for (const input of [
    "Mennyit fogyaszt egy Ford Focus?",
    "Mit tudsz magadról?",
    "Mi az a naptár?",
    "Magyarázd el, hogyan működik a telefon",
    "Szia, mesélj valamit!",
    "Ez egy WhatsApp beszélgetésről szóló kérdés",
    "Ne nyisd meg az alkalmazást",
    "Jarvis, tudsz segíteni?"
  ]) assert.equal(shouldOfferToolCalls(input), false, input);
});

test("clearly imperative device requests may use one verified tool proposal", () => {
  for (const input of [
    "Nyisd meg a naptárat",
    "Hey Jarvis, kérlek hívd fel Esztert",
    "Jarvis navigálj haza",
    "Írj WhatsApp üzenetet Eszternek hogy szia",
    "Set alarm 7:30"
  ]) assert.equal(shouldOfferToolCalls(input), true, input);
});

test("compound, negative and question shaped device requests are refused", () => {
  for (const input of [
    "Nyisd meg a naptárat majd töröld az üzeneteket",
    "Nem nyisd meg a térképet",
    "Can you open the mail app?",
    "Írj egy verset a Messengerről",
    ""
  ]) assert.equal(shouldOfferToolCalls(input), false, input);
});
