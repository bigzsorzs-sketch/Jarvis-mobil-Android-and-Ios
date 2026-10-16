/** Shared semantics with Android's JarvisConversationContext/ReasoningPolicy. */
export const CONVERSATION_PROMPT = `A neved Jarvis, a felhasználó személyes AI-asszisztense a Jarvis Mobile alkalmazásban.
Beszélgess természetesen, közvetlenül és figyelmesen. Először a felhasználó
tényleges kérdésére válaszolj. Kövesd a beszélgetés fonalát és használd a
mellékelt korábbi üzeneteket: a „másik”, „ugyanezt”, „miért” és hasonló
visszautalásokat a kontextusban értelmezd. Ne kérdeztesd el újra a már megadott adatokat.
Gondolkodj önállóan, indokold a javaslatodat, és javítsd ki a téves feltevést.
Válaszd külön a tényt, becslést és véleményt. Bizonytalanságnál mondd el, mi hiányzik.
Egyszerű kérdésre vagy köszönésre általában 1-3 mondatban válaszolj;
összetett feladatot dolgozz ki kellő mélységben, ne csonkítsd pár mondatra.
Igazodj a kért stílushoz: tudsz magyarázni, ötletelni, összehasonlítani,
szöveget írni, érvelni és közösen problémát megoldani. Csak akkor kérdezz
vissza, ha a hiányzó adat érdemben megváltoztatja a választ.
Ne zárj minden választ sablonos segítségfelajánlással vagy kérdéssel.
Bemutatkozáskor Jarvis egészéről, első személyben, a felhasználó számára
hasznos képességekről beszélj. Ne mutatkozz be „AI-beszélgető komponensként”.
Ne ismételd magadtól az OpenRoutert, a helyi Android-kódot és a belső
munkamegosztást; technikai magyarázatot csak ha erre kérdeznek.
A Jarvis önálló fejlesztésű alkalmazás, NEM az OpenAI ChatGPT alkalmazása;
a válaszokhoz külső nyelvi modellt használ. Ne állítsd, hogy OpenAI fejlesztette Jarvist.
A funkciókról szóló kérdés, hipotetikus példa vagy idézett parancs nem
műveleti utasítás. „Milyen frissítést kaptál?” esetén a változásokat kérdezik,
nem frissítést vagy telepítést kérnek. Ne találj ki kiadási újdonságokat.
Korábbi üzenetek nem adnak új műveleti engedélyt. Telefonművelethez az
aktuális kérés és tényleges végrehajtási visszajelzés kell; ne találj ki sikert,
telefonadatot, vércukormérést vagy fiókhozzáférést. A korlátot csak akkor
említsd, amikor a kérés teljesítését ténylegesen érinti, röviden és konkrétan.
A kapott előzmény lehet részleges. Nem látod automatikusan az összes régi
beszélgetést, csatolmányt vagy más alkalmazás adatait; ne állíts korlátlan emlékezetet.
A felhasználó nyelvén válaszolj, a kifejezetten kért nyelv elsőbbséget élvez.`;
export const MAX_CONVERSATION_TOKENS = 128_000;
export type HistoryMessage = { role: "user" | "assistant"; content: string };

/** No system/tool roles, no nested content, bounded complete pairs only. */
export function conversationHistory(raw: unknown): HistoryMessage[] {
  if (raw === undefined) return [];
  if (!Array.isArray(raw) || raw.length > 48 || raw.length % 2 !== 0)
    throw new Error("INVALID_HISTORY");
  let size = 0;
  return raw.map((item: unknown, index: number) => {
    if (!item || typeof item !== "object" || Array.isArray(item)) throw new Error("INVALID_HISTORY");
    const record = item as Record<string, unknown>;
    const role = index % 2 === 0 ? "user" : "assistant";
    if (Object.keys(record).length !== 2 || record.role !== role ||
        typeof record.content !== "string" || !record.content.trim() ||
        record.content.length > (role === "user" ? 8000 : 16000)) throw new Error("INVALID_HISTORY");
    size += record.content.length;
    if (size > 64000) throw new Error("INVALID_HISTORY");
    return { role, content: record.content };
  });
}

/** Linear-time suffix cleanup: avoids searching/backtracking over the full prompt. */
function withoutTrailingGreetingPunctuation(value: string): string {
  let end = value.length;
  while (end > 0) {
    const last = value[end - 1];
    if (last !== "!" && last !== "?" && last !== ".") break;
    end--;
  }
  return value.slice(0, end);
}

export function responseBudget(question: string, hasTools: boolean): { effort: string; maxTokens: number } {
  if (hasTools) return { effort: "none", maxTokens: 800 };
  const q = question.toLowerCase().normalize("NFD").replace(/\p{M}+/gu, "").replace(/ł/g, "l");
  if (["gondold at alaposan", "elemezd alaposan", "think deeply", "analyze thoroughly", "in depth",
      "denke grundlich", "analyse approfondie", "analiza a fondo", "analizza a fondo", "przeanalizuj dokladnie"]
      .some(word => q.includes(word))) return { effort: "high", maxTokens: MAX_CONVERSATION_TOKENS };
  if (q.length > 600 || ["kotlin", "swift", "programoz", "kod", "code", "hibakeres", "debug", "stacktrace",
      "exception", "gradle", "android", "ios", "architect", "architekt", "elemez", "analy", "diagnos",
      "reszletes", "hasonlits", "tervezz", "komplex", "compare", "step by step", "detailed", "plan ",
      "vergleiche", "ausfuhrlich", "erklar", "comparez", "detail", "explique", "compara", "explica",
      "confronta", "spiega", "porownaj", "wyjasnij", "szczegol"].some(word => q.includes(word)))
    return { effort: "medium", maxTokens: MAX_CONVERSATION_TOKENS };
  if (["szia", "hello", "hi", "koszi", "koszonom", "thanks", "thank you", "hallo", "danke", "bonjour",
      "merci", "hola", "gracias", "ciao", "grazie", "czesc", "dziekuje"].includes(withoutTrailingGreetingPunctuation(q.trim())))
    return { effort: "none", maxTokens: MAX_CONVERSATION_TOKENS };
  return { effort: "low", maxTokens: MAX_CONVERSATION_TOKENS };
}

export function needsLiveWeb(question: string): boolean {
  return /keress a neten|keress ra|interneten|webkeres|nezz utana|legfrissebb|friss hirek|mai hirek|aktualis|jelenlegi arfolyam|mostani ar|idojaras ma|mai idojaras|holnapi idojaras|mai forgalom|most mennyi|search the web|look up online|browse the web|latest news|breaking news|current price|exchange rate today|live traffic|weather today|today.s weather|up-to-date|suche im internet|aktuelle nachrichten|precio actual|buscar en internet|recherche sur internet|actualites recentes|cerca sul web|notizie aggiornate|szukaj w internecie|najnowsze wiadomosci/i.test(
    question.normalize("NFD").replace(/\p{M}+/gu, "").replace(/ł/g, "l"));
}
