/** Explicit, untrusted client language choice. Never concatenate free-form
 * text into the privileged system prompt. Older clients default to Hungarian.
 */
export const SUPPORTED_RESPONSE_LANGUAGES = [
  "hu-HU", "en-GB", "de-DE", "fr-FR", "es-ES", "it-IT", "pl-PL"
] as const;

const instructions: Record<(typeof SUPPORTED_RESPONSE_LANGUAGES)[number], string> = {
  "hu-HU": "A felhasználó válasznyelve magyar. Alapértelmezés szerint magyarul válaszolj.",
  "en-GB": "The user's selected language is English. Respond in English by default.",
  "de-DE": "Die ausgewählte Sprache ist Deutsch. Antworte standardmäßig auf Deutsch.",
  "fr-FR": "La langue sélectionnée est le français. Réponds en français par défaut.",
  "es-ES": "El idioma seleccionado es español. Responde en español de forma predeterminada.",
  "it-IT": "La lingua selezionata è italiano. Rispondi in italiano per impostazione predefinita.",
  "pl-PL": "Wybrany język to polski. Odpowiadaj domyślnie po polsku."
};

export function selectedLanguageInstruction(input: unknown): string {
  if (typeof input !== "string" ||
      !SUPPORTED_RESPONSE_LANGUAGES.some(lang => lang === input)) {
    return instructions["hu-HU"];
  }
  return instructions[input as keyof typeof instructions];
}
