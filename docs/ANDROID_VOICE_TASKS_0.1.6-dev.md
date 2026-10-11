# Jarvis Mobile Android 0.1.6-dev – kéz nélküli parancsok

**Csak fejlesztői/debug APK, nincs végleges publikálás.**

## Mi működhet natív Android-funkcióval?

| Hangutasítás | Mit csinál az alkalmazás? | Valódi korlát |
| --- | --- | --- |
| "Hívd fel a drágámat" | Feloldott telefonon vagy kifejezetten engedélyezett képernyőzáras használatnál: az általad mentett "Drágám" becenévhez tartozó számot kérdezi vissza; csak "igen" után adja át az Android telefonrendszernek. | A számot neked kell először hozzákapcsolnod, CALL_PHONE engedély is kell. A hívás felépülését nem igazolja. |
| "WhatsApp-on keresd meg Sulyt és írj neki egy üzit" | Ha a névhez nemzetközi telefonszámot tároltál: elkéri a szöveget, felolvassa, hangos megerősítés után megnyitja a WhatsApp beszélgetést és a vázlatot. | A személyes WhatsApp-ban a küldést neked kell véglegesítened. Nincs teljes API-integráció. |
| "Messengeren keresd meg Ferenc Várnait és írj neki" | Meghallgatja a szöveget, visszaolvassa, majd a Messengert / megosztási képernyőt nyitja. | A konkrét címzettet kézzel kell kiválasztani; a Messenger személyes beszélgetéseihez nincs általános külső hozzáférés. |
| "Keresd meg számla emailt" | Megnyitja a Gmail webes keresőoldalát. | Gmail OAuth nincs, Jarvis még nem olvashat, kereshet API-n vagy küldhet valódi válaszokat. |
| "Nyisd meg a Chrome-ot, lépj be az UC fiókomba és nézd meg a Journal üzeneteket" | A hivatalos GOV.UK Universal Credit belépőoldalt nyitja meg; lehetőség szerint Chrome-ban. | NEM jelentkezik be, nem használ és nem tárol jelszót, nem olvassa a Journal tartalmát, nem állítja, hogy vannak-e új üzenetek. |
| "Navigálj Eszterhez" | A mentett Eszter becenévhez tartozó címet kéri átadni a telepített térképes navigációnak. | Az első használat előtt a címet neked kell beírnod. Navigáció indítását készüléken kell ellenőrizni. |

## Kötelező előfeltételek

1. Az Androidon a Jarvis „Engedélyközpont” menüben külön engedélyezd a szükséges mikrofon-, névjegy-, telefon- és értesítés-hozzáférést.
2. Az „Eszközök → Ismerősök, becenevek és címek” menüben rögzítsd a becenevet, a +országkódos telefonszámot és opcionálisan a navigációs címet. Ezek helyi, titkosított adatok.
3. A Főoldalon indítsd el a Hey Jarvis munkamenetet. Jelenleg 15 percre korlátozott; az Android beszédfelismerőjétől és energiatakarékosságától függ.
4. Személyes adatokat a lezárt képernyőn alapértelmezés szerint nem olvas fel. Ezen csak tudatos felhasználói hozzájárulással lehet változtatni.
5. A „zöld CI” a Kotlin-tesztek, Android Lint és fordítás sikerét jelenti, nem teljes körű telefonnal igazolt működést.

## Ami az igazi végső célhoz még hiányzik

- Szerveroldali API-kulcs és hitelesített felhasználói fiók; a repó nyilvános, ezért oda titkok nem kerülhetnek.
- Valós Gmail OAuth, megfelelő Google API scopes és fiókengedélyezés.
- Teljesen hangvezérelt, ellenőrzött cross-app böngészés csak a szolgáltatói/Android szabályok által engedett módon; a UC Journal tartalmának automatikus lekérése különösen nem áll rendelkezésre.
- Hivatalos WhatsApp/Messenger támogatás vagy szabályos, a felhasználó által jóváhagyott alkalmazás-műveletek. Az üzenetküldés nem adható „sikeres” állapotba csupán attól, hogy egy ablak megnyílt.
- Device-level automatizált/instrumentációs tesztek, több telefonos kézi QA, adatvédelmi és Play Store szabályellenőrzés.

**Ne adj ki semmilyen képernyőn/repóban látható szolgáltatói API-kulcsot.** Az app még nem kereskedelmi kiadás.
