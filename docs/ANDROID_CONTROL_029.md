# Jarvis Android 0.2.9 – kezelőfelület és telepített alkalmazások

## Cél és ténylegesen megvalósított változások

Az előzetes 75% UI és 45% telefonvezérlés **szubjektív becslés** volt, nem mért termékminőség. A 100% / 95% csak előzetes célérték: a teljes Android és más alkalmazások tetszőleges vezérlése nem áll rendelkezésre, és kizárólag CI-zöld ellenőrzés alapján sem adható hiteles teljesítési százalék.

### Helyi felhasználói felület

- A telepített, Android által indíthatónak jelzett alkalmazások kereshető listáját a **Jarvis → Alkalmazások** oldal mutatja. Megnyitás kézi érintéssel vagy szóban/írásban, az alkalmazás egyértelműen felismert nevével lehetséges.
- **Jarvis → Előzmények**: Jarvisban ténylegesen befejezett kérés/válasz párok SQLite adattáblába kerülnek. A tartalom AES-256-GCM-mel, Android Keystore eszközkulccsal titkosított. A főoldal az utolsó 50 fordulót tölti vissza; a kereső az adott készüléken offline keres a naplóban, legfeljebb 60 találatot mutat. A napló nem törli automatikusan a régi fordulókat.
- A korábban csak memóriában tartott (nem mentett) beszélgetéselőzmények **utólag nem állíthatók vissza**. A helyi tárolás Android-rendszerbackupból ki van zárva. A következő fázishoz felhasználói helyreállítási kóddal titkosított ZIP-/Drive-/OneDrive-mentés külön szükséges.
- Írásos és hangos parancsok közös háttér- és megjelenítési útvonalon jutnak el az alkalmazásnyitóhoz. Régi hangparancs 30 másodperc után lejár; nem nyit külső appot lezárt képernyőről.
- A hozzáférési hiba nem tünteti el és nem törli a helyi beszélgetésnaplót; a főoldalon figyelmeztetés jelenik meg.

### Telefonvezérlés határai

| Funkció | Megvalósítva a kódban | Feltétel |
|---|---|---|
| Támogatott Jarvis-menük és eszközök | Igen | Helyi/strukturált navigáció |
| Naptár, ébresztők, térkép, email és tárcsázó megnyitása | Igen | Android alkalmazás-átadás |
| Egyértelmű nevű telepített Android-alkalmazások megnyitása | Új | Felhasználó kifejezett parancsa; feloldás; egyetlen találat |
| Telepített Android-alkalmazások keresése és kiválasztása | Új | Csak LAUNCHER aktivitások; nem az összes csomag |
| Jarvis saját ébresztőbeállítása | Igen, korlátozott | Android engedélyek és készülék teszt |
| Hívás, WhatsApp/Messenger piszkozat | Jóváhagyásos átadás | Külső app küldés külön szükséges |
| Tetszőleges app képernyőn belüli gombnyomás, adatmódosítás | **Nem** | Alkalmazásonkénti hivatalos API vagy külön jóváhagyott eszköz-integráció kell |
| Messenger/Gmail/UC fiók teljes olvasása | **Nem** | Explicit OAuth/API és jogosultság kell |
| Fizetés és pénzátutalás, adót bevallás beküldése | **Nem** | Biztonságos pénzügyi, adóhatósági integráció kell |

Nincs általános AccessibilityService-vezérlés, képernyőleolvasás, teljes appengedély vagy titkos AI-kulcs. A telepített alkalmazások listáját és nevét a privát telefonoldal állítja elő, az AI nem adhat meg tetszőleges csomagnevet. A megnyitás sikere **nem egyenlő** azzal, hogy a külső alkalmazáson belüli feladat megtörtént.

## Android QA és kockázatok

GitHub CI: Kotlin unit tesztek, lintDebug/lintRelease, assembleDebug/assembleRelease/bundleRelease és szerver TypeScript-tesztek. Kiemelt automata regresszió: magyar toldalékok, másik alkalmazás nevére tett téves AI-javaslat, többértelmű nevek, tiltás/tagadás, összetett művelet, üres/nem telepített app és helyi menüútvonalak.

Továbbra is szükséges **valós készülékes** teszt:
1. Android 13, 15 és 16, különböző gyártók és méretek; billentyűzet, szövegbevitel, kezdőoldal, képernyőforgatás, sötét/világos mód.
2. 30 telepített app többnyelvű névvel, duplikált appcímkékkel; megnyitás hanggal és érintéssel; nem telepített app, appeltávolítás közvetlenül a megnyitás előtt; háttérben futó Jarvis.
3. Hangos mód zárolt/feloldott állapotban, 30 másodperces lejárat, mikrofon és más Android-engedélyek visszavonása, akkumulátoroptimalizálás.
4. 1000 és 10 000 hosszú beszélgetési forduló mentése, újraindítás, lokális keresés válaszidő, Android Keystore állapotromlás, tárhelybetelés. A bejegyzések nem mennek felhőbe.
5. Stabil aláírt update: a GitHub debug APK aláírása változhat, emiatt adatvesztést okozó újratelepítés válhat szükségessé. Helyi titkosított adatokról **még nincs** külön felhasználói mentés. Éles felhasználói adatokkal teszt APK-t ne használj.

**Éles kiadási döntés:** a 100% UI és 95% harmadik féltől származó alkalmazásvezérlés szint nem igazolt; az utóbbi nem általános Android-képesség. Éles kiadáshoz még helyreállítható titkosított mentés, Google Play/privát aláírás, felületi/accessibility QA, hitelesítés és adatvédelmi felülvizsgálat kell.
