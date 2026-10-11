# Jarvis Android 0.1.5-dev — belső tesztlista

Állapot: kizárólag fejlesztői/debug build. Nincs kiadott release vagy Play Store publikálás.

## Automatikus ellenőrzés

- Kotlin egységtesztek: alap parancsok, magyar ébresztőszó, ébresztési időpontok, navigáció, emailvázlat, telefonhívás és képernyőzáras adatvédelem.
- Az Android CI futtatja az egységteszteket, a Lint ellenőrzést és az APK-fordítást.
- A külön AI-szerver előkészítése szigorú TypeScript típusellenőrzést kap.
- A zöld CI NEM bizonyítja az éles készülékes működést.

## Kötelező készülékes próbák

1. Ikon, gesztusos/háromgombos navigáció, billentyűzet és a Vissza gomb ellenőrzése.
2. Hey Jarvis: kéz nélküli mód elindítása, leállítása, háttérben, képernyő lezárva, 15 perces időkorláttal.
3. WhatsApp/Messenger: friss értesítés felolvasása, válasz diktálása, igen/nem jóváhagyás, sikertelen kézbesítés elkülönítése.
4. Képernyőzár: privát üzenetek és hívások alapból letiltva; csak külön kapcsolóval engedélyezhetők.
5. Hívás: egyértelmű névjegy, duplikált név, hiányzó engedély, megtagadott jóváhagyás.
6. Ébresztés: pár perccel későbbi ébresztő, lezárt képernyő, alkalmazás bezárva, újraindítás, különengedély és értesítési hang ellenőrzése. Fontos időpontra addig nem alkalmas egyetlen ébresztőnek.
7. Otthoni cím, hangvezérelt navigáció és valódi térképmegnyitás tesztje.
8. Hangos emailvázlat: végső küldés továbbra is a levelező alkalmazásban történik.
9. APK újratelepítése: változó debug aláírás miatt adatvesztés előfordulhat; valódi személyes adatot ne használj.
10. Engedélyek megvonása, beszédfelismerő hiánya, offline állapot, akku-korlátozások.

## Változatlan hiányok

- Éles AI még nincs bekötve. Az előkészített szerver nincs telepítve, az Androidnak nincs kész fiókbejelentkezése vagy éles AI kliense.
- Gmail OAuth nincs bekötve. Csak az emailírás előkészítése működhet a telefon levelezőjével.
- Libre 2 közvetlen adatkapcsolat nem igazolt. A vércukornapló nem orvostechnikai döntéshozó eszköz.
- A számlázás jelenleg számlavázlat, nem teljes jogszabályi megfelelőségű platform.
- Éles előfizetés-ellenőrzés, tokenalapú AI-költségkeret, adatvédelmi szabályzat, fizikai telefonos QA és Play szabályellenőrzés még szükséges.
- A repó nyilvános: szolgáltatói kulcsot, ügyféladatot és titkot ide feltölteni tilos. Kereskedelmi fejlesztés előtt célszerű privátra állítani.

Nem hozunk létre kiadást; a GitHub Actions kizárólag fejlesztői APK-artifactot állít elő.
