# Jarvis Android 0.2.7 – natív parancsok és korlátozott műveletsorok

## Mi működik a kódban?

A közvetlen OpenRouter-kapcsolat és a privát Node.js AI-kiszolgáló ugyanazt a `select_native_tool(action)` strukturált eszközt ajánlja fel a nyelvi modellnek. A támogatott műveletek: `navigate`, `set_alarm`, `cancel_alarm`, `compose_email`, `gmail_search`, `uc_portal`. A korábbi `open_jarvis_page`, `open_phone_app`, `prepare_phone_call` és `prepare_message_draft` megmaradnak.

Az Android nem hajtja végre automatikusan a modell által megadott paramétereket. A kért funkciót a felhasználó eredeti szövegéből külön ellenőrzi, és a tényleges adatokat is az eredeti kérésből nyeri vissza. Váratlan mező, több AI-tool hívás, ismeretlen eszköz vagy eltérő művelettípus esetén nincs végrehajtás.

A chat felület és a hangos szolgáltatás közös natív Android műveletkezelőt használ. Hang esetén a háttérből nem nyitható appok kérése 30 másodpercig várhat a Jarvis előtérbe helyezésére. Az ébresztő a saját Android AlarmManager rendszerével működik; a Gmail és UC funkció kizárólag a megfelelő oldalt nyitja meg, nem olvassa a fiókadatokat.

## Jóváhagyott kétlépéses példa

`Állítsd be az ébresztőt 7:30-ra majd nyisd meg a naptárat`

Ennél a pontos, támogatott mintánál Jarvis kijelzi vagy felolvassa a két lépést, és 40 másodpercen belüli jóváhagyást kér. Csak az ébresztő sikeres ütemezése után kezdeményezi a kért, megengedett Android app megnyitását. A külső alkalmazás indítása nem igazolja annak további működését. A jóváhagyott háttérhang-parancs előtérbe kerülésig legfeljebb 30 másodpercig érvényes.

Nincsenek tetszőleges hosszú AI-tervek, automatikus telefonhívás-/üzenetküldés-sorozatok, fiókbeállítás-módosítás, képernyővezérlési vagy Accessibility-alapú univerzális app-kezelés. Nem kerülnek be API-kulcsok az APK-ba. Az Android engedélyeit a felhasználónak kell megadnia.

## Automatikus tesztek

CI: Kotlin egységtesztek, Android Lint, debug APK fordítás, TypeScript ellenőrzés és szerveres AI-eszköz tesztek. Negatív esetek: eltérő AI-művelet, váratlan paraméter, több AI-eszközhívás, rossz időformátum, tagadó parancs, többértelmű/összetett utasítás és túl hosszú vagy lejárt jóváhagyás.

## Valós készülékes ellenőrzés – még szükséges

- Próba magyar és angol hangparancsokkal, különböző kiejtésekkel, háttérzajban.
- Telefon zárása/feloldása művelet előtt, jogosultság elutasítása és visszavonása.
- Hívás jóváhagyásának visszautasítása, helytelen névjegy és hiányzó engedély.
- WhatsApp/Messenger piszkozat visszautasítása, hibás címzett, külső app bezárása.
- Ébresztés sikeres időzítése, hiányzó értesítés-/pontos ébresztés-engedély és újraindítás utáni állapot.
- Kétlépéses művelet jóváhagyása és leállítása az első lépés hibáján.
- OEM háttérkorlátozások, hálózat elvesztése, lassú AI-válasz, újraindítás és kijelző-elforgatás.

**Automatikus CI-zöld nem egyenlő a fizikailag igazolt hibamentes működéssel.** A kiadott APK tesztkiadás. Android és iOS eltérő jogosultsági modellje miatt az iOS app teljeskörű irányítását ez az Android-kiadás nem biztosítja.
