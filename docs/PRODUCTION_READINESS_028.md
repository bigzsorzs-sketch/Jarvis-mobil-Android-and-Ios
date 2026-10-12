# Jarvis Mobile – élesítési audit, 2026-10-12

## Állapot és döntés
**Belső teszt / release candidate előkészítés. Nem éles, fizetős, nyilvános termék.**
A zöld GitHub Actions (Kotlin teszt, Android Lint, debug-fordítás, TypeScript ellenőrzés és backend tesztek) nem bizonyítja a fizikai telefonos működést, az üzleti/hordozhatósági biztonságot vagy a jogi megfelelést.

## Már elvégzett forráskód-javítások (0.2.8-dev)
- Android 16 / API 36 célzás a 2026-os új Play-feltöltési elváráshoz; kompatibilis AGP és Gradle. A képernyőfelépítés, edge-to-edge és OEM funkciók Android 16-os próbakészüléken külön ellenőrizendők.
- A végleges Android buildből kimarad a saját, közvetlen OpenRouter API-kulcsot fogadó *tesztüzemmód*. A debug verzióban külön, korlátozott keretű tesztkulccsal használható. Publikus kiadásnál szerver + hitelesítés kell.
- Felhőbe küldött felhasználói kérdésekhez kifejezett, visszavonható hozzájárulás és adatkezelési ismertető került a beállításokba. Korábban mentett tesztkulcsokat a hozzájárulás újbóli mentéséig nem használja.
- Az AI válaszát a telefon és a privát szerver is méretkorláttal fogadja; a szerver végig megtartja a kérést megszakító határidőt, hogy ne lehessen végtelenül nagy vagy beragadt választ feldolgozni.
- Android hozzáférések külön engedélyezhetők; az értesítési hozzáférés kiterjedéséről előzetes információ jelenik meg.
- A szerver PUBLIC_LAUNCH_ENABLED=true módban csak teljes OIDC-beállítással és béta tesztelői tokenek nélkül indulhat.
- Külön kézi indítású, védett `Build signed Android release candidate` workflow készíthet tartós kulccsal aláírt APK-t és Google Play-feltöltéshez AAB-t. Kiadás nincs automatikusan publikálva; a teszt APK továbbra is csak debug.

## Nem oldható meg hiteles adatok, emberi teszt vagy külső szolgáltatás nélkül

1. **Valódi aláírókulcs és Play Console.** Egyszer kell létrehozni és biztonságosan elmenteni a hosszú távú, privát upload key-t; *soha ne kerüljön GitHub fájlba, ChatGPT-üzenetbe vagy az APK forrásába*. Google Play App Signing konfigurálása szükséges. A `production` nevű GitHub Environment alatt a következő secrets kellenek: `ANDROID_UPLOAD_KEYSTORE_BASE64`, `ANDROID_UPLOAD_STORE_PASSWORD`, `ANDROID_UPLOAD_KEY_ALIAS`, `ANDROID_UPLOAD_KEY_PASSWORD`. Javasolt kötelező reviewer a GitHub környezethez. A kézi workflow csak a `main` ágról, `QA_PASSED_AND_POLICY_REVIEWED` megerősítéssel indulhat, eredménye 7 napig megőrzött, ellenőrzött APK és AAB.
2. **Üzemelő backend, OIDC/fiókkezelés és előfizetés.** A Node/Caddy/Redis konfiguráció forráskód, nem igazoltan futó szolgáltatás. Egyenleg és API-kulcs a szerver titokkezelőjében, TLS, DNS, monitorozás, riasztások, visszaállítás/mentés és költségkeret kell. Jelenleg a számlázási jogosultság egy Redis subscription státuszból származik, nem bizonyított valódi fizetési integráció. Ne állítsuk fizetős rendszernek addig.
3. **Adatkezelés és szerződési feltételek.** Nyilvánosan elérhető, hiteles adatkezelési szabályzat valós adatkezelővel és elérhetőséggel; jogosultságok, adatfeldolgozók, tárolási idők, törlés, export, incidens és nemzetközi adattovábbítás tisztázása. Google Play Data safety, értesítési és mikrofon hozzáférési disclosure. Nem helyettesíthető kitalált cégnévvel vagy egy üres URL-lel.
4. **Egészségügyi funkciók átvilágítása.** Van vércukornapló. A Google Play Health apps deklaráció és pontos adatkezelési információk szükségesek, valamint annak meghatározása, hogy az alkalmazás csak naplóz vagy orvosi eszköznek minősülő tanácsokat is adna. Nem szabad terápiás döntésekre alkalmasnak hirdetni igazolás nélkül.
5. **Fizikai Android készülékes teszt.** Minimum Android 13, 15, 16; kisméretű kijelző, Samsung/Xiaomi akkukorlátozás, beszédfelismerő elérhetősége, hangos mód 15 perces időzítője, TTS, hívás és üzenet-jóváhagyás, zárképernyő, régi/új AI-válasz, szekvenciális parancs, kérés megszakítása, sérült vagy hiányzó Android engedélyek, újraindítás, API hiba, offline állapot, telepítés/frissítés, hosszú névjegyek és importált fájlok.
6. **Biztonsági és publikációs ellenőrzés.** A nyilvános repó kódvédelmi döntése, aláírás megőrzése és rotálási terv, titok- és dependency scan, threat model a notification listenerhez, release crash reporting adatminimalizálással és beleegyezéssel, készlet a harmadik féltől származó szoftverekhez, app leírása és felhasználói támogatás.
7. **iOS külön termék.** Az iOS prototípus nem tartalmaz teljes Android funkcióparitást; az Apple App Store kiadáshoz saját fejlesztői fiók, aláírás és tesztelés szükséges.

## Telepítési és kiadási rend

- A 0.2.8 belső debug tesztkiadás csak zöld Android build után tehető közzé a GitHub Releases oldalon. Nem használható Play-beküldéshez és változó debug aláírása miatt nem garantáltan frissíthető adatvesztés nélkül.
- A tartós kulcsot a tulajdonos saját környezetében hozza létre, titkosított jelszókezelőben tárolja, a base64 tartalmat kizárólag védett GitHub secrets-ként tölti fel.
- Tesztelés és jogi ellenőrzés után: GitHub Actions → Build signed Android release candidate → Run workflow a `main` ágon. Csak a valós QA teljesülése után írható be a megerősítés. A munkafolyamat ellenőrzi az aláírást és a 36-os target SDK-t; **nem nyomja ki automatikusan a Google Playre**.
- Az aláírt AAB-t a tulajdonos Play Console belső tesztcsatornán érdemes elsőként próbálni, majd kontrollált teszt és megfelelőségi jóváhagyás után lehet dönteni a nyilvános elérhetőségről.

## Hivatkozások
- Google Play 2026 API célzás: https://support.google.com/googleplay/android-developer/answer/11926878
- Android 16 SDK: https://developer.android.com/about/versions/16/setup-sdk
- Android App Signing: https://developer.android.com/studio/publish/app-signing
- Play User Data policy: https://support.google.com/googleplay/android-developer/answer/10144311
- Health apps: https://support.google.com/googleplay/android-developer/answer/16679511
