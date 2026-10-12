# Jarvis Android: beszéd és eszközvezérlés javítása

## Az utolsó öt kiadás összevetése

| Kiadás | Pontos forrás-commit | Lényeges változás |
| --- | --- | --- |
| 0.2.6 | 35bfd96169ddc965d1a643ff1dc432bc35a1918b | Üzenetpiszkozat és jóváhagyás. A felismerő indításának kódja már itt is ugyanaz, mint a 0.2.10-ben. |
| 0.2.7 | 0e93b641de1e0031514ddba11dbac6bfde39163e | Közös natív műveletek, ébresztő + alkalmazás műveletsor; a beszédfelismerő indítása nem változott. |
| 0.2.8 | 9c1564b34f807742191441395216a0e3d270ec28 | Új felhős AI-hozzájárulás, külön engedélykérések, target/compile SDK 36. Régi mentett kulcs mellé nincs új hozzájárulási rekord, ezért az AI-kérés megáll. |
| 0.2.9 | 2ffbfaf7ada2c721d2cccf80f5329b4832891284 | Pontos Android-alkalmazásnév alapján indító ág, rendszerbeállítási panelek és helyi chatnapló. A nyelvi felismerő indítása továbbra is változatlan. |
| 0.2.10 | 7dce850e1c7d934fa8e4dec08f4bfa1bbdf0becf | A főoldali kézi Felolvasás gomb/TTS-példány eltávolítása, bemutatkozás és 404 diagnosztika. A HandsFreeService teljes fájlja és az alkalmazásindító kódja azonos a 0.2.9-ével. |

A kiadási tagekhez tartozó pontos commitokat hasonlítottuk össze, nem pusztán az utolsó öt egyedi commitot. A HandsFreeService onStartCommand–listen–speak szakasza mind az öt kiadásban byte-ra azonos. A legújabb kiadás tehát nem módosította magát a beszédfelismerőt.

## Bizonyított kódhibák és javításuk

- A főoldali engedélykérés csak a még hiányzó engedélyeket kéri. Ha a mikrofon már engedélyezett, csak a POST_NOTIFICATIONS szerepel a visszatérő eredményben; a régi callback a hiányzó RECORD_AUDIO bejegyzést elutasításnak vette. Most a ténylegesen megadott mikrofonengedélyt is figyelembe vesszük, és az opcionális értesítés elutasítása nem tiltja a hangindítást.
- Egy offline felismerő elérhetősége nem bizonyítja a hu-HU modell elérhetőségét. Hiányzó vagy nem támogatott nyelv esetén egyetlen tartalék váltás történik az alapértelmezett Android-felismerőre; ennek hibája látható állapotüzenetet ad. A szolgáltató hálózatot is használhat, ahogy a Hangbeállítások eddig is ismertették.
- A név előtt az ASR által beszúrt vessző, illetve a puszta „Jarvis” megszólítás eddig nem jutott el a parancsokhoz. Ezeket most felismerjük; a szöveg közepén említett puszta Jarvis név nem parancs.
- A helyi hangkezelő BasicAction ága több ismert rendszeralkalmazás-parancsot elutasított. A naptár, térkép, email, óra és tárcsázó kifejezett megnyitási parancsa most a közös helyi Android-műveletekhez kerül. Nincs AI-szükséglet.
- A 0.2.9 általános alkalmazásindítója a hiányzó pontos címkéjű app esetén nem engedte tovább az olyan érvényes rendszeraliasokat, mint az „óra alkalmazás”. A meglévő rendszeraliasokat továbbengedjük; egy valóban megtalált, név szerint kért telepített app továbbra is elsőbbséget kap.
- A régi oldalparancs-lista csak néhány menüpontot ismert. A teljes menü és a négy tényleges aloldal közös, helyi navigációt kapott. Kifejezett „oldal” kérés a Jarvis oldalt nyitja, a szokásos telefonalkalmazás-parancs megőrzi a natív átadást. Negált vagy több célt nevező kérést nem választunk szét találgatással.
- A szöveges parancsmező korábban csak a főoldalon volt. Most az összes aloldalon is rendelkezésre áll, ugyanazzal a végrehajtóval és mikrofonos diktálással.
- A régi AI-kulcs megmaradt, de a hozzájárulás mentéséhez ismét teljes kulcsot kért a beállítás. Most az üres cserekulcsmező megtartja a mentett kapcsolatot. A hozzájárulás külön marad: nem adjuk meg automatikusan, és visszavonáskor az új hálózati kérések továbbra is leállnak.
- Az Android 11+ TTS-csomagláthatósági deklaráció hiányzott. A TTS_SERVICE query hozzáadásával az Android beszédmotorja felderíthető.

## Ellenőrzés

- Kotlin regressziók: értesítés-only engedélyeredmény, mikrofonelutasítás, nyelvi fallback, megszólítás és írásjelek, öt helyi telefonművelet, összes menüpont/aloldal, negálás és többcélú kérés.
- Android készülékes/emulátoros regresszió: meglévő kulcs új hozzájárulással, visszavonás, szervercím-váltáskor nincs régi token újrafelhasználása.
- Compose emulátor: az összes 15 menüpont és 4 aloldal végigjárása írott paranccsal, parancsmező használata oldalváltás után, azonos célok fogadása a hangkezelő előtéri átadásából.
- A build/lint és az emulátor eredményét a javítás PR-jének GitHub Actions ellenőrzései tartalmazzák.

A fizikai telefon hiányzó hu-HU modelljét, gyártói háttérkorlátozását vagy egy konkrét mikrofonhibát forrás-összevetésből nem lehet bizonyítani. Valódi mikrofonos próba szükséges: hangfigyelés indítása, „Jarvis, nyisd meg a Munka oldalt”, „Jarvis, nyisd meg az AI-agy oldalt”, majd „Jarvis, nyisd meg a naptárat”.

A meglévő debug APK pipeline nem rögzít közös debug aláírókulcsot. Egy új build aláírása eltérhet a telepített APK-étól; ne töröld a régi alkalmazást a helyi adatok megőrzése nélkül.

