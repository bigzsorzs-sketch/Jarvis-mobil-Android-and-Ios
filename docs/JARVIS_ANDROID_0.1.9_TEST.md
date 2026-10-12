# Jarvis Mobile Android 0.1.9-dev – tesztkiadás

## Változások

- A főoldali Jarvis/Munka kapcsoló eltávolítva. Az oldalsó menü húzással vagy a főoldali **Jarvis** névre koppintva nyílik. Az aloldalak fejlécére koppintás ugyanezt teszi. A bal felső külön menügomb továbbra sincs.
- A Rendszerállapot, Térkép és Fájlok és képek menüpont és a kapcsolódó belső oldalak eltávolítva. A Navigáció marad, a csatolás a főoldali + gombon érhető el. A külön Chat oldal és a korábban eltávolított szlogen nem tér vissza.
- A Jegyzetek közvetlenül a telefon jegyzetalkalmazását nyitja. A modern Android-jegyzetkezelő és az elérhető Xiaomi/Redmi, Samsung vagy Google jegyzetappok támogatottak. Ha nincs megfelelő app, jegyzetalkalmazás-keresés nyílik.
- A Beállítások csoportosított listává alakítva: megjelenés, hangvezérlés, telefonengedélyek és különleges hozzáférések. A hosszú engedélymagyarázó kártyák kikerültek.
- Az egyes engedélyek vagy a közös „Engedélyek megadása” az Android saját engedélykérő ablakait indítja. Az értesítési hozzáférés és a pontos ébresztés rendszerbeállítási oldalán kell jóváhagyni a különleges engedélyt. A mikrofon engedélyezése önmagában nem indít hangfigyelést. A kihagyás továbbra is megengedett.
- A „jó reggelt”, „szia”, „köszönöm” és más rövid köszönések írásban és a helyi hangvezérlésben megfelelő, helyi választ kapnak. A köszönéssel kezdődő valódi parancs nem vész el.
- A csatolási ablak felső és alsó magyarázó bekezdése eltávolítva. A darab-/méretjelzés, a 20 képes és összesen 1 GiB-os korlát marad.
- EXE fájl is csatolható, felismerhető Windows-programként. A kiterjesztést a helyi fájlmásolat megőrzi.
- Minden csatolmány kapott Megnyitás gombot. Android FileProvider tartalom-URI és átmeneti olvasási jogosultság adja át a kiválasztott fájlt a megnyitó appnak. Ha nincs kezelő, fájlformátumhoz tartozó Play Áruház-keresés nyílik; a dokumentum neve és tartalma nem kerül a keresésbe. A keresés nem telepít automatikusan alkalmazást.
- A csatolmánylista görgethető; a műveletek külön sorban vannak, a lista beolvasás/elemzés közben nem módosítható.

## Automatizált ellenőrzés

Kotlin-egységtesztek, Android Lint, debug APK-fordítás. Új regressziós esetek: ékezetes/nagybetűs köszönések, parancsot tartalmazó mondatok, EXE MIME és kép-limit, biztonságos fájlkiterjesztés, dokumentumnevet kizáró alkalmazáskeresés, menüpontok eltávolítása és az Android hozzávetőleges/pontos helyengedélyének páros kérése.

## Fizikai telefonon ellenőrizendő

1. Oldalsó menü húzással/Jarvis névre koppintva; három eltávolított menüpont; visszalépés.
2. Jegyzetek a telefon saját appjában, menüből és beírt/hangos parancsból.
3. Beállítások mindkét témában; témaválasztás újraindítás után.
4. Mikrofon, névjegyek, telefon, hely és értesítési engedély: megadás, elutasítás, visszatérés és státuszfrissülés. Hozzávetőleges hely után pontosra váltás.
5. Az értesítési hozzáférés és ébresztési különengedély valódi Android-oldala.
6. „Jó reggelt”, „Szia”, „Köszönöm”, valamint „Jó reggelt, nyisd meg a jegyzeteket”.
7. PDF/DOCX/EXE/video csatolása; formátumfelismerés; fájl tényleges megnyitása és a hiányzó app keresése. A régi 20 kép/1 GiB limit.
8. Hosszú csatolmánylista, kis kijelző, billentyűzet, gyártói fájlválasztó és kamera.

## Határok

Az EXE csatolása és fájlkezelőnek átadása nem Windows-program futtatása Androidon. A külső app tényleges tartalomértelmezése nem igazolható pusztán az indításából. A Play-keresés nem garantál kompatibilis alkalmazást.

Az éles AI-agy továbbra sincs bekötve; a köszönések helyi válaszok. A csatolmányok helyi gyorsítótárban maradnak, a videóelőnézet helyi metaadatokat és képkockákat ad. Ez Android debug tesztkiadás, nem Play Store-kiadás. Eltérő debug-aláírás megakadályozhatja a korábbi tesztapp frissítését; eltávolítás törli a helyi adatokat.

Android-megvalósítási hivatkozások: [fájlmegosztás](https://developer.android.com/training/secure-file-sharing), [jegyzetalkalmazások](https://developer.android.com/develop/ui/compose/touch-input/stylus-input/create-a-note-taking-app), [helyengedélyek](https://developer.android.com/develop/sensors-and-location/location/permissions/runtime).
