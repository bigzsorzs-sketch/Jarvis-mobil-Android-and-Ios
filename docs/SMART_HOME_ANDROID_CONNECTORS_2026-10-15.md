# Jarvis Android Smart Home – kapcsolatprotokollok és kiadási szerződés (2026-10-15)

## Mit jelent a kapcsolat?

**Felderítve**: az eszköz vagy szolgáltatás látható a helyi hálózaton, illetve Bluetooth-hirdetést ad. Ez nem jelent hitelesítést, párosítást vagy vezérelhetőséget.

**Csatlakoztatva**: a felhasználó az adott gyártó/ökoszisztéma hitelesítési módjával engedélyezte a kapcsolatot, és a Jarvis valódi eszközállapotot tud lekérni.

**Vezérlés igazolva**: az API a művelet után a valódi eszköz módosult állapotát visszaadja. A puszta HTTP 200 / BLE írás / UI kattintás nem elegendő.

## Kapcsolati térkép

| Kapcsolat | Felderítés | Párosítás | Valódi vezérlés | Megjegyzés |
|---|---|---|---|---|
| Wi-Fi, Android NSD / DNS-SD / mDNS | PR #60, kézi 12s | Nincs | Nincs | Google Cast, AirPlay, HAP, ESPHome, Matterc/Matter szolgáltatások; nem hálózati portscan |
| Bluetooth LE hirdetések | PR #60, kézi 10s | Nincs | Nincs | `BLUETOOTH_SCAN` és `CONNECT` Android 12+, régebbieken helyengedély; nincs titkos GATT írás |
| Home Assistant HTTPS REST | PR #59 | felhasználó által megadott szerver/token | lámpa/TV/ventilátor korlátozott be/ki és visszaolvasás | mosógép, mikró, zár, kapcsolós konnektor csak lekérdezhető |
| Matter | DNS-SD szignatúra PR #60 | **Nincs még** | **Nincs még** | a felhasználói Matter commissioning és fabric kapcsolat külön, jóváhagyott integrációt igényel |
| Samsung SmartThings | Nincs még | OAuth user consent és device scope | Nincs még | hivatalos SmartThings API, soha nincs embedded app secret |
| Bluetooth Classic | Nincs még | Android párosítás és márkaspecifikus profil | Nincs még | BT Classic nem azonos BLE-vel |
| Gyártói Wi-Fi / BLE | Nincs még | gyártó és pontos modell szükséges | Nincs még | nem állítjuk, hogy minden „Wi-Fi-s” vagy „Bluetooth-os” készülék vezérelhető |

## Hivatalos források

- Android mDNS / NSD: https://developer.android.com/develop/connectivity/wifi/use-nsd
- Android BLE felderítés: https://developer.android.com/develop/connectivity/bluetooth/ble/find-ble-devices
- Android Bluetooth runtime engedélyek: https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
- Matter commissionable DNS-SD: `_matterc._udp.`; operational: `_matter._tcp.` (https://developers.home.google.com/matter/primer/commissionable-and-operational-discovery)
- Home Assistant REST `GET /api/states`, `POST /api/services/domain/service`: https://developers.home-assistant.io/docs/api/rest/
- SmartThings OAuth scopes: https://developer.smartthings.com/docs/service-integrations/app-setup
- SmartThings eszközparancs: https://developer.smartthings.com/docs/service-integrations/control-devices

## Biztonsági szabályok

1. Semmilyen LAN-/BLE-felderítés nem indul automatikusan; csak a Smart Home felületen, kézi gombra. Meghatározott idő után és képernyő bezárásakor megáll.
2. Nincs portscan, alapértelmezett jelszó próbálgatás vagy rejtett eszközvezérlés.
3. A felfedezett eszközök azonosítója és címe nem kerül át az AI modellhez.
4. Nem írunk tetszőleges BLE karakterisztikát, nem küldünk ismeretlen HTTP parancsot. Gyártó-specifikus parancs csak protokollteszt és hitelesített párosítás után.
5. Állapotkérésből nem lesz távoli mosógép-/mikró-/sütőindítás, zárnyitás vagy fűtésprogram-állítás.
6. Fizikai hálózati és Bluetooth-eszközteszt szükséges ahhoz, hogy egy konkrét márkáról kompatibilitást állítsunk.

## Távozási lámpaellenőrzés – következő, elkülönített lépés

A felhasználó kifejezett engedélyével és helyben rögzített otthoni helyével geofence EXIT esemény figyelhető. Android hálózati helybecslése miatt 100–150 m javasolt, 50 m nem garantálható. Esemény után Home Assistant HTTPS-en kérjük le a `light.*` állapotot, és csak ténylegesen „on” állapotú lámpákhoz küldünk értesítést. `unknown` vagy elérhetetlen szerver esetén nincs hamis „minden lekapcsolva” válasz. Nem kapcsolunk automatikusan semmit.

A háttér-helyhozzáférés és az értesítési engedély külön Android-rendszerjóváhagyást, a háttér-működés Google Play szabályzati ellenőrzést és valódi utcai tesztet igényel. Dokumentáció: https://developer.android.com/develop/sensors-and-location/location/geofencing

## Kiadási állapot

PR #59/60 forráskód fejlesztés, még nincs általánosan igazolt fizikai eszközkompatibilitás, nincs új aláírt publikus Android kiadás. A platformos CI zöld állapota nem pótolja a valódi eszközön történő párosítás-/vezérlés- és háttér-értesítési QA-t.
