# Jarvis Mobile 0.1.8-dev – belső Android tesztkiadás

## Javítva

1. Menthető világos/sötét kapcsoló. Az új téma a főoldalra, menüre, eszközökre, névjegyekre, üzenetekre, engedélyekre és hangvezérlési panelre is érvényes.
2. A Jarvis-gömb és fényhullámok **nem animálódnak nyugalomban**. A mikrofon bekapcsolása önmagában nem indítja el a mozgást; csak Android által jelzett tényleges beszéd vagy Jarvis TTS-beszéd. Egyes beszédmotorok nem szolgáltatnak pontos hangerőadatokat, ezért a beszédszinkron készülékfüggő.
3. A külön Chat aloldal törölve. A beírt vagy diktált parancsra kapott helyi válasz a főoldal előzményeiben jelenik meg, felolvasható.
4. A Navigáció és Térkép menüpont közvetlenül az Android térképalkalmazását nyitja.
5. Az alsó `+` gomb **Kamera / Fényképek / Fájlok** választót ad. A kiválasztott fájl a telefon alkalmazásspecifikus cache tárhelyére ténylegesen, streameléssel kerül be; maximum 20 kép és **összesen 1 GiB**. A felhasználó eltávolíthatja a csatolmányokat a listából.
6. Az Engedélyközpont duplikált kép- és dokumentumválasztói eltávolítva. A fájlválasztók csak a kiválasztott fájlokhoz férnek hozzá, nem a teljes tárhelyhez.
7. A bal felső menügomb a főoldalról és az aloldalakról is eltávolítva. Nincs oldalról behúzható menü; minden korábbi eszköz a **Munka** fülről érhető el. A főoldal felső váltója középen marad, a témakapcsoló jobbra.

## Ezt teszteld a telefonon

- Világos/sötét váltás: azonnal változik-e, és újraindítás után megmarad-e?
- A gömb és az energiahullámok álló helyzetben mozdulatlanok-e? Beszédnél együtt mozognak-e?
- Főoldal: a beírt kérés nem navigál Chat aloldalra, hanem a választ helyben mutatja-e?
- A főoldalon és a Rendszerállapot oldalon sincs bal felső menügomb; oldalirányú húzás sem nyit menüt?
- Munka: mind a 14 eszköz elérhető-e; a Navigáció és Térkép érintésre rögtön a telepített térképapp indul-e?
- + gomb: Kamera, Fényképek, Fájlok; 20 kép után nem enged-e többet; az együttes méret nem lehet nagyobb 1 GiB-nál?
- Az Engedélyközpontban nincsenek már párhuzamos kép/fájlkiválasztó kártyák?
- Vissza gomb, állapot- és navigációs sáv színei, billentyűzet és gesztusvezérlés mindkét témában.

## Korlátok és adatvédelem

**NEM valódi felhőfeltöltés.** A kiválasztott fájlok appprivát helyi gyorsítótárban vannak; nem kerülnek AI-hoz vagy Gmailhez, mivel a biztonságos, fiókhoz kötött AI-feldolgozás még nincs bekötve. Appadatok törlése elveszítheti a csatolmányokat.

A zöld Kotlin-egységteszt + Android Lint + APK-fordítás nem garantálja a hibamentes működést a felhasználó telefonján. Külön figyelni kell a kamera- és fájlválasztó gyártói eltéréseire, a hangszinkronra és a háttérben futó mikrofon korlátaira.

**Csak teszt / GitHub prerelease**, nem Google Play kiadás.
