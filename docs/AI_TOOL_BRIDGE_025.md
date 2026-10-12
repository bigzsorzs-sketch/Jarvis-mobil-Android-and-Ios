# Jarvis Android 0.2.5 – AI-javasolt hívás, külön megerősítéssel

## Támogatott útvonal
- Közvetlen OpenRouter (a telefonon beállított, külön tesztkulccsal): `prepare_phone_call(target)` strukturált tool javaslat.
- Privát Jarvis szerver: ugyanaz a javaslat szűrt szerver-válaszként; a szerver soha nem telefonál.
- A telefon újra elemzi az EREDETI felhasználói mondatot. Csak egyértelmű híváskérés és az AI célpontjával egyező név/szám engedélyezett.
- Android névjegykeresés vagy korábban megbízhatóként rögzített ismerős. Két azonos nevű címzett esetén nem indul hívás.
- Szöveges kérésnél az alkalmazás címzett/szám ellenőrző párbeszédet mutat; hangos kérésnél "igen" vagy "nem" megerősítést vár. Lezárt telefonon a privát hangparancs ellenőrzött; a szükséges jogosultságokat nem kerüli meg.

## Korlátok és ellenőrzés
- Ez a telefonhívás ELŐKÉSZÍTÉSE, nem automatikus, korlátlan AI-eszközvezérlés.
- Kizárólag explicit, pontos címzettet tartalmazó kérések támogatottak. A normál, helyi hívásparancsok továbbra is helyben működnek, internet nélkül.
- Hangos jóváhagyás 30 másodpercig él, azonosítható megerősítés nélkül nincs hívás.
- Híváskezdeményezést Android Telecom kapja meg; a hívás létrejötte és kapcsolása nincs visszaigazolva az alkalmazás számára.
- A telefonos képességekről szóló AI-rendszerleírás a jelenlegi, ellenőrzött eszközkészletet sorolja fel.
- Unit teszt, lint és debug APK build ellenőrzése GitHub Actions alatt; külön fizikai Android-telefonos teszt is kell.
- Biztonsági és költség okból ne tedd az OpenRouter API-kulcsot a repóba vagy az APK-ba.

## Próbamondatok
- "Jarvis, kérlek hívd fel Esztert"
- "Fel tudnád hívni Esztert?"
- "Call +441234567890"

## Tiltandó próbák
- "Ne hívd fel Esztert"
- AI más személyt ad meg, mint amit a felhasználó kért
- Több AI tool hívás vagy ismeretlen paraméter
- Küldésnek szánt "küldd el"/"send" válasz egy hívási megerősítéskor
