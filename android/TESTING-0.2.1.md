# Jarvis Android 0.2.1 – OpenRouter tesztmód

A tesztmód nem igényel saját AI-szervert.

1. Hozz létre egy **új**, alacsony költési keretű OpenRouter-kulcsot. A korábban megosztott kulcsot vond vissza.
2. Telepítsd a 0.2.1-es teszt APK-t a saját telefonodra. Nyisd meg: Beállítások → AI-agy → **Közvetlen OpenRouter**.
3. A kulcsot **csak a telefonon** írd be, és válaszd a Mentés → AI-próbakérdés lehetőséget.
4. A második tesztelő ugyanígy beállíthatja a saját, külön kulcsát a saját telefonján. A kulcsokat ne oszd meg chaten, kódon vagy screenshoton.
5. Ha később lesz saját HTTPS-szerver, válthatsz a **Saját szerver** módra.

A közvetlen mód megkönnyíti a magánjellegű tesztelést, de nem biztonságos általános, nyilvános felhasználói terjesztésre: a kulcs a készüléken futó apphoz kötődik, és kompromittált/rootolt telefonon kinyerhető. A kérések az OpenRouter számláját terhelik. A modellt egyelőre `openai/gpt-4o-mini` értékre rögzítettük; az app nem írja át az Android-rendszerengedélyeket, nem hajt végre AI-ból tetszőleges telefonműveletet, és nem küld automatikusan fájlt vagy teljes beszélgetési előzményt. Írásos és hangos kérdések az AI-hoz kerülnek, ha az alkalmazás parancsértelmezője nem ismer fel helyi műveletet.

Tesztellenőrzések: helyes/hibás API-kulcs; elfogyott egyenleg; 429 használati limit; hálózat nélkül; két telefon külön kulccsal; magyar válasz felolvasása; meglévő telefonparancsok viselkedése; visszavont API-kulcs; kulcs eltávolítása.

Android test CI: Kotlin egységtesztek, Lint, debug APK build. Fizikai készülék és élő OpenRouter-próba külön szükséges, zöld CI önmagában nem bizonyítja.
