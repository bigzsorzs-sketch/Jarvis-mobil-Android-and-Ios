/**
 * Models may support plain text while their allowed endpoints reject tools.
 * Do not include the tool schema in routine questions or informational prompts.
 * Android independently validates all actual device operations.
 */
export function shouldOfferToolCalls(raw: string): boolean {
  if (!raw || raw.length > 8000 || raw.includes("?")) return false;
  let text = raw.toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "")
    .replace(/^\s*(?:(?:hey|hej|szia|ok|okay)\s+)?jarvis[!,:]?\s*/, "")
    .replace(/^\s*(?:kerlek|please)\s+/, "").trim();
  if (!text || /^(?:ne|nem|dont|don't|never|not)\b/.test(text)) return false;
  if (/\b(?:majd|aztan|then|and then)\b/.test(text)) return false;
  // "írj" only requests a model tool when a messenger platform is named.
  if (/^(?:irj|kul(dj|d)|uzenj|send|write)\b/.test(text)) {
    return /\b(?:whatsapp|messenger)\b/.test(text);
  }
  // A named call or a single explicit Android launch is potentially executable,
  // but this function by itself never authorizes execution.
  return /^(?:nyisd meg|nyitnad meg|megnyitnad|mutasd|lepj|inditsd|open|launch|show|go to|hivd fel|tarcsazd|telefonalj|call|navigalj|allitsd be|ebressz|set alarm|torold)\b/.test(text);
}
