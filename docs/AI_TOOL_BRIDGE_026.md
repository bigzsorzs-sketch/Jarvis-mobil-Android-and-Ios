# Jarvis Android 0.2.6 – AI ellenőrzött üzenetpiszkozat

## Új AI eszköz
A `prepare_message_draft(platform)` két engedélyezett értéket fogad: `whatsapp`, `messenger`. A modell csak az alkalmazást választhatja; nem adhat meg címzettet, szöveget, telefonszámot, küldési parancsot vagy tetszőleges Android Intentet.

Az Android kizárólag az eredeti felhasználói kérésből, rögzített nyelvtani minták szerint állítja elő a címzettet és a szöveget. Kétértelmű címzettnél, eltérő alkalmazásnál, hiányzó szövegnél, váratlan paraméternél vagy több eszközhívásnál nincs végrehajtás.

## Írásos és hangos használat
- Példa: **Jarvis, kérlek küldj WhatsApp üzenetet Eszternek, hogy Szia, este hívlak.**
- Példa: **Írj Messengeren Ferinek hogy Mindjárt indulok.**
- Példa: **Send WhatsApp message to Eszter saying On my way.**
- Szöveges használatnál a Jarvis párbeszédablak címzettet, platformot és teljes szöveget mutat. Az engedély csak 40 másodpercig él.
- Hangos kérésnél Jarvis felolvassa a címzettet és a szöveg első 350 karakterét, majd igent vagy nemet kér. A jóváhagyás 40 másodpercig él.
- A később előtérbe kerülő alkalmazás legfeljebb 30 másodperces hangos handoff-kérést teljesít; az elavultat elutasítja.
- WhatsAppnál az Ismerősök és becenevek részben megbízható telefonszám szükséges. Messengernél a címzettet a külső appon belül kell kiválasztani.
- Az üzenet elküldése és kézbesítése nem igazolt és nem automatikus. A külső alkalmazásban a felhasználó küldi el.

## Stabilitási kapuk
Android: `testDebugUnitTest`, `lintDebug`, `assembleDebug`. Backend: strict TypeScript check, AI tool tests és Docker konfigurációellenőrzés. A főági APK tesztkiadás csak sikeres Android workflow után engedélyezett. Fizikai telefonos próba szükséges.

**Nem támogatott:** több címzett, szabadon modellezett üzenetszöveg, SMS automatikus küldés, harmadik féltől érkező szöveg parancsként való végrehajtása, kézbesítési státusz bizonyítása, több AI-eszközhívás egy válaszban.
