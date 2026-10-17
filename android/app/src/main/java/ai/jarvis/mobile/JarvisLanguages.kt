package ai.jarvis.mobile

import android.content.Context
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit

/** Stable language codes, unrelated to the internal Hungarian route IDs. */
internal enum class JarvisLanguage(val tag: String, val flag: String, val label: String) {
    HUNGARIAN("hu-HU", "🇭🇺", "Magyar"),
    ENGLISH("en-GB", "🇬🇧", "English"),
    GERMAN("de-DE", "🇩🇪", "Deutsch"),
    FRENCH("fr-FR", "🇫🇷", "Français"),
    SPANISH("es-ES", "🇪🇸", "Español"),
    ITALIAN("it-IT", "🇮🇹", "Italiano"),
    POLISH("pl-PL", "🇵🇱", "Polski");
    companion object {
        fun fromTag(raw: String?): JarvisLanguage =
            entries.firstOrNull { it.tag == raw } ?: fromSpeechTag(raw) ?: HUNGARIAN

        /** Accept recognized regional accents (e.g. en-US, es-MX, fr-CA). */
        fun fromSpeechTag(raw: String?): JarvisLanguage? {
            if (raw.isNullOrBlank()) return null
            val language = raw.replace('_', '-').substringBefore('-').lowercase(java.util.Locale.ROOT)
            return entries.firstOrNull { it.tag.substringBefore('-') == language }
        }
    }
}

internal object JarvisLanguageSettings {
    private const val PREFS = "jarvis_ui_language_v1"
    private const val KEY = "selected_locale"
    fun load(context: Context): JarvisLanguage = JarvisLanguage.fromTag(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
    )
    fun save(context: Context, language: JarvisLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, language.tag).apply()
    }
}

internal val LocalJarvisLanguage = compositionLocalOf { JarvisLanguage.HUNGARIAN }

/** Only UI chrome is translated. Stored chat messages and user data are not rewritten. */
internal object JarvisTranslations {
    internal fun fixedTextKeys(): Set<String> = english.keys
    private val english = mapOf(
        "Új beszélgetés" to "New chat",
        "Legutóbbiak" to "Recents",
        "Még nincs mentett beszélgetés" to "No saved conversations yet",
        "Korábbi beszélgetések" to "Previous conversations",
        "További beállítások" to "More settings",
        "Saját OpenRouter-kapcsolat: nincs szükség Jarvis-szervercímre. Az API-kulcsodat a telefon titkosított tárhelyére mentjük." to "Personal OpenRouter connection: no Jarvis server address is needed. Your API key is saved in encrypted storage on this phone.",
        "Főoldal" to "Home",
        "Munka" to "Work",
        "Navigáció" to "Navigation",
        "Alkalmazások" to "Apps",
        "Előzmények" to "History",
        "Vércukor" to "Blood glucose",
        "Smart Home" to "Smart Home",
        "Számlázás" to "Invoicing",
        "Számlák" to "Invoices",
        "Email" to "Email",
        "Üzenetek" to "Messages",
        "Hívás" to "Call",
        "Névjegyek" to "Contacts",
        "Naptár" to "Calendar",
        "Átadtam a naptár megnyitását az Androidnak." to "I requested your phone calendar to open.",
        "Nem sikerült elindítani a naptárat." to "I could not open a calendar app.",
        "Jegyzetek" to "Notes",
        "Ébresztő" to "Alarm",
        "Beállítások" to "Settings",
        "Hangbeállítások" to "Voice settings",
        "AI-agy" to "AI brain",
        "Engedélyek" to "Permissions",
        "Továbbiak" to "More",
        "Eszközök" to "Tools",
        "Készen állok" to "Ready",
        "Figyelek rád..." to "Listening...",
        "Jarvis beszél..." to "Jarvis is speaking...",
        "Kérdezd meg Jarvist" to "Ask Jarvis",
        "Menü megnyitása" to "Open menu",
        "Világos" to "Light",
        "Sötét" to "Dark",
        "☀ Világos" to "☀ Light",
        "☾ Sötét" to "☾ Dark",
        "Kikapcsolva" to "Off",
        "Aktív" to "Active",
        "Engedélyezve" to "Allowed",
        "Nincs engedély" to "Not allowed",
        "Mentés" to "Save",
        "Törlés" to "Delete",
        "Mégse" to "Cancel",
        "Megnyitás" to "Open",
        "Bezárás" to "Close",
        "Folytatás" to "Continue",
        "Vissza" to "Back",
        "Betöltés…" to "Loading…",
        "Korábbi üzenetek" to "Earlier messages",
        "Két művelet jóváhagyása" to "Confirm two actions",
        "Mindkét lépés indítása" to "Start both steps",
        "Üzenetpiszkozat ellenőrzése" to "Review message draft",
        "Piszkozat megnyitása" to "Open draft",
        "Hívás jóváhagyása" to "Confirm call",
        "Hívás indítása" to "Start call",
        "Alkalmazás: " to "App: ",
        "Címzett: " to "Recipient: ",
        "Szöveg: " to "Message: ",
        "Fel szeretnéd hívni " to "Do you want to call ",
        "A küldés csak a külső alkalmazásban történhet meg." to "Sending can only happen in the external app.",
        "Hangos útvonaltervezés: a mentett címek alapján a telepített térképalkalmazás indul." to "Voice navigation opens your installed map app using a saved address.",
        "Navigálás haza" to "Navigate home",
        "Otthoni cím beállítása" to "Set home address",
        "Emailvázlatot készíthetsz. Gmail-fiók olvasása és keresése külön OAuth-kapcsolatot igényel." to "You can prepare an email draft. Reading and searching Gmail requires a separate OAuth connection.",
        "Email írása" to "Compose email",
        "Naptár megnyitása" to "Open calendar",
        "Esemény hozzáadása" to "Add event",
        "Telefon ébresztői" to "Phone alarms",
        "Jarvis-ébresztő kezelése" to "Manage Jarvis alarm",
        "Aktív saját Jarvis-ébresztő." to "A Jarvis alarm is active.",
        "Nincs aktív saját Jarvis-ébresztő." to "No active Jarvis alarm.",
        "Adatvédelmi szabályzat" to "Privacy policy",
        "Jarvis Mobile – Google Play és GitHub" to "Jarvis Mobile – Google Play and GitHub",
        "Általános" to "General",
        "Megjelenés" to "Appearance",
        "Hangvezérlés" to "Voice control",
        "AI-kapcsolat" to "AI connection",
        "AI-kapcsolat beállítva" to "AI connection configured",
        "Kulcs mentve · hozzájárulás szükséges" to "Key saved · consent required",
        "Csatlakoztatás szükséges" to "Connection required",
        "Telefon-hozzáférés" to "Phone access",
        "Adatvédelem és helyi adatok" to "Privacy and local data",
        "Saját adatok kezelése" to "Manage your data",
        "Android alkalmazásadatok: helyi jegyzetek, vércukornapló, mentett kapcsolat" to "Android app data: local notes, glucose readings, saved connection",
        "A Jarvis az AI-kérdéseket a korábban engedélyezett kapcsolaton keresztül dolgozza fel. A beállítóoldal eltávolítása nem módosítja a mentett hozzáférést vagy hozzájárulásokat. Az alkalmazás helyi adatainak végleges törlését az Android Alkalmazásadatok / Tárhely menüjében indíthatod." to "You've already authorised the AI connection. Removing the setup screen does not change saved access or consent. You can permanently clear the app's local data from Android App info / Storage.",
        "Hozzáférés a telefonhoz" to "Phone permissions",
        "Mikrofon" to "Microphone",
        "Helymeghatározás" to "Location",
        "Pontos hely" to "Precise location",
        "Hozzávetőleges hely" to "Approximate location",
        "Telefonhívások" to "Phone calls",
        "Jarvis-értesítések" to "Jarvis notifications",
        "Különleges hozzáférések" to "Special access",
        "Értesítési hozzáférés" to "Notification access",
        "Pontos ébresztés" to "Exact alarms",
        "Android-engedélyek" to "Android permissions",
        "Kezelés a telefon beállításaiban" to "Manage in Android settings",
        "· más alkalmazások" to "· other apps",
        "A kihagyott engedélyek később is megadhatók." to "Skipped permissions can be granted later.",
        "Engedélyek frissítve." to "Permissions updated.",
        "Az értesítési hozzáférés Android-szinten más alkalmazások értesítéseit is láthatóvá teszi. Jarvis csak az ismert üzenetalkalmazások friss értesítéseit dolgozza fel; legfeljebb 30 előnézetet tart ideiglenesen a memóriában. Az üzeneteket nem továbbítja az AI-nak. A hozzáférést az Android beállításaiban bármikor kikapcsolhatod." to "Android notification access may expose notifications from other apps. Jarvis only processes recent notifications from known messaging apps, keeps up to 30 previews temporarily in memory, and does not forward messages to AI. You can revoke access in Android settings.",
        "AI-agy – belső teszt" to "AI brain – internal test",
        "AI-agy – biztonságos szerverkapcsolat" to "AI brain – secure server connection",
        "Közvetlen OpenRouter" to "Direct OpenRouter",
        "Saját szerver" to "Own server",
        "Jelenleg mentett kapcsolat: " to "Currently saved connection: ",
        "Közvetlen OpenRouter (modell: openai/gpt-4o-mini)" to "Direct OpenRouter (model: openai/gpt-4o-mini)",
        "Saját Jarvis AI-szerver (/v1/chat)" to "Own Jarvis AI server (/v1/chat)",
        "A módváltás csak Mentés után lép életbe." to "The mode changes only after you press Save.",
        "Tesztmód: nincs szükség szervercímre. Az OpenRouter-kulcsodat egyszer kell megadni, a telefon titkosított tárhelyére mentjük." to "Test mode: no server address needed. Enter your OpenRouter key once; it will be saved to encrypted storage on this phone.",
        "Új OpenRouter-kulcs (csak cseréhez)" to "New OpenRouter key (only to replace)",
        "OpenRouter API-kulcs" to "OpenRouter API key",
        "A kulcs nem kerül az APK-ba vagy a GitHubra. Teszteléshez korlátozott keretű, visszavonható kulcsot használj. Mindkét telefonra külön meg kell adni a saját kulcsot." to "Your key is never included in the APK or GitHub. Use a revocable low-budget test key. Enter a separate key on each phone.",
        "A korábban mentett kulcsot megtartjuk. A hozzájárulás mentéséhez nem kell újra beírnod." to "Your existing key is kept. You do not need to enter it again to save consent.",
        "Privát Jarvis AI-szerver: a tesztelőnek nem kell OpenRouter API-kulcs. A szervert először üzembe kell helyezni; csak ezután működik a meghívós aktiválás." to "Private Jarvis AI server: testers do not need an OpenRouter API key. The server must be deployed before invitation activation works.",
        "AI-szerver HTTPS-címe" to "AI server HTTPS address",
        "Egyszer használatos Jarvis meghívó" to "One-time Jarvis invite",
        "Jarvis aktiválása" to "Activate Jarvis",
        "Ezen a telefonon már aktív a privát Jarvis-hozzáférés. A szerver a hozzáférést visszavonhatja." to "Private Jarvis access is active on this phone. The server may revoke access.",
        "Az AI-nak küldött kérdésed szövege a kiválasztott külső AI-szolgáltatóhoz vagy a privát Jarvis szerveren keresztül a modellszolgáltatóhoz kerül. Ne írj be jelszót, API-kulcsot, orvosi adatot vagy más személyek bizalmas adatait. A helyi névjegyek, üzenetértesítések, fájlok és vércukornapló nem részei automatikusan az AI-kérésnek." to "Your question text is sent to your chosen AI provider, either directly or through the private Jarvis server. Never enter passwords, API keys, medical details, or other people's confidential information. Local contacts, message notifications, files, and glucose logs are not automatically sent.",
        "AI-hozzáférés mentve ezen a telefonon." to "AI access saved on this phone.",
        "Ehhez a módhoz még nincs beállított AI-hozzáférés." to "AI access is not configured for this mode.",
        "Leválasztás" to "Disconnect",
        "Kapcsolódás..." to "Connecting...",
        "AI-próbakérdés" to "Test AI connection",
        "A szokásos telefonparancsok helyben futnak. Az általános kérdéseket a kiválasztott AI-kapcsolat válaszolja meg." to "Regular phone commands run locally. General questions are answered using your selected AI connection.",
        "Hozzájárulok ahhoz, hogy a saját AI-kérdéseim szövegét a kiválasztott külső AI-szolgáltató feldolgozza." to "I agree that the external AI provider I selected may process the text of my AI questions.",
        "Vércukornapló" to "Blood glucose log",
        "mmol/L · helyi mentés · nem orvosi döntéstámogató eszköz" to "mmol/L · stored locally · not a medical decision-support tool",
        "Mért érték (mmol/L)" to "Reading (mmol/L)",
        "Mérés mentése" to "Save reading",
        "Mérési áttekintés" to "Reading summary",
        "Átlag: " to "Average: ",
        "Tájékoztató grafikon. Orvosi döntést nem helyettesít." to "Illustrative chart. Not a substitute for medical advice.",
        "Még nincs mentett mérés." to "No readings saved yet.",
        "Egyszerű számlázás" to "Simple invoices",
        "Helyi prototípus: éles adózási/VAT megfelelőség még nincs ellenőrizve." to "Local prototype: tax/VAT compliance has not been verified.",
        "Ügyfél neve" to "Client name",
        "Összeg (£)" to "Amount (£)",
        "Számlavázlat mentése" to "Save invoice draft",
        "PDF megosztás" to "Share PDF",
        "🧠 Titkosítva tárolt jegyzetek" to "🧠 Encrypted local notes",
        "Új jegyzet" to "New note",
        "Basic eszközök" to "Basic tools",
        "🌐 Biztonságos fiókmegnyitás" to "🌐 Secure account access",
        "Az UC Journal csak a hivatalos GOV.UK bejelentkezés után olvasható. Jarvis nem ismeri a jelszavadat, és nem tudja ellenőrizni az új üzeneteket." to "The UC Journal can only be accessed after signing in on the official GOV.UK site. Jarvis does not know your password or check new messages.",
        "UC – hivatalos bejelentkezés" to "UC – official sign-in",
        "📧 Email" to "📧 Email",
        "Valódi emailírás a telefon levelezőalkalmazásával. Gmail-fiók olvasásához külön OAuth-kapcsolat szükséges." to "Compose email with your phone's email app. Reading Gmail requires a separate OAuth connection.",
        "🧭 Navigáció" to "🧭 Navigation",
        "Otthoni cím (csak ezen a telefonon)" to "Home address (on this phone only)",
        "Otthoni cím mentése" to "Save home address",
        "Térkép megnyitása" to "Open map",
        "📅 Naptár és emlékeztetők" to "📅 Calendar and reminders",
        "Esemény címe" to "Event title",
        "Esemény létrehozása" to "Create event",
        "Ébresztők" to "Alarms",
        "⏰ Jarvis ébresztő" to "⏰ Jarvis alarm",
        "Hangos értesítéssel jelez. Az ébresztés pontos időzítéséhez külön Android-engedély kell." to "Uses an audible notification. Exact alarm scheduling requires separate Android permission.",
        "Ébresztési engedélyek" to "Alarm permissions",
        "Saját ébresztő törlése" to "Delete Jarvis alarm",
        "📞 Telefon" to "📞 Phone",
        "A tárcsázót nyitja meg. Hívást nem indít önállóan." to "Opens the dialer; does not place a call automatically.",
        "Tárcsázó" to "Dialer",
        "🔐 Engedélyek és üzenetek" to "🔐 Permissions and messages",
        "Fotók, dokumentumok, pontos GPS, névjegyek és értesítések: mindegyik külön engedélyezhető." to "Photos, documents, precise GPS, contacts and notifications each require separate permission.",
        "Engedélyközpont megnyitása" to "Open permission centre",
        "WhatsApp / Messenger / üzenetek" to "WhatsApp / Messenger / messages",
        "Névjegyek megtekintése" to "View contacts",
        "🔒 Integrációk folyamatban" to "🔒 Integrations in progress",
        "Központi AI, Gmail-fiókszinkron, Libre 2 szinkron, OBD2, 42 nyelv és PC-s adatszinkron további fejlesztést igényel. A Self-Repair nem kerül mobilra." to "Central AI, Gmail sync, Libre 2 sync, OBD2, more languages and PC sync need further development. Self-Repair is not included on mobile.",
        "Hangos helyi parancsok" to "Local voice commands",
        "Telepített alkalmazások" to "Installed apps",
        "Jarvis csak megnyitja a kiválasztott alkalmazást. Nem olvas bele, és nem irányítja tetszőleges módon." to "Jarvis only opens the selected app. It does not read or control its private content.",
        "Keresés az alkalmazások között" to "Search installed apps",
        "Lista frissítése" to "Refresh list",
        "További találatokhoz pontosítsd a keresést." to "Refine the search to see more results.",
        "Helyi beszélgetések" to "Local conversations",
        "A Jarvis saját beszélgetéseit titkosítva ezen a telefonon tárolja. A keresés offline történik. Más alkalmazások teljes chatjeit nem olvassa." to "Jarvis stores its own conversations encrypted on this phone. Search works offline. It cannot read complete chats from other apps.",
        "Keresés az előzményekben" to "Search history",
        "Helyi keresés" to "Search locally",
        "Keresés…" to "Searching…",
        " mentett beszélgetési forduló; legfeljebb 60 találat látható." to " saved conversation turns; up to 60 matches are shown.",
        "A névjegyeket csak a telefonon olvassuk. Nem továbbítjuk AI-nak vagy szerverre." to "Contacts are only read on your phone and are not sent to AI or a server.",
        "Engedélyek kezelése" to "Manage permissions",
        "Névjegy-hozzáférés engedélyezése" to "Allow contacts access",
        "A névjegyek megtekintéséhez előbb engedélyt kell adnod." to "Grant permission before viewing contacts.",
        "Névjegy keresése" to "Find a contact",
        "Tárcsázás előkészítése" to "Prepare dialling",
        "Legfeljebb 80 névjegyet jelenítünk meg a prototípusban." to "The prototype displays up to 80 contacts.",
        "Tárcsázó megnyitva." to "Dialer opened.",
        "A tárcsázót nem sikerült megnyitni." to "Could not open the dialer.",
        "🎙️ Hey Jarvis – kéz nélküli mód (BÉTA)" to "🎙️ Hey Jarvis – hands-free mode (BETA)",
        "Privát üzenetek felolvasása lezárt képernyőn" to "Read private messages aloud on the lock screen",
        "Alapértelmezés: kikapcsolva. Mások is hallhatják a felolvasást." to "Off by default. Others may hear your messages.",
        "Hangfigyelés indítása" to "Start listening",
        "Hangfigyelés leállítása" to "Stop listening",
        "Állapot: " to "Status: ",
        "Hallottam: " to "Heard: ",
        "Jarvis: " to "Jarvis: ",
        "A mikrofon aktív állapotát az Android értesítés mutatja." to "Android shows a notification when the microphone is active.",
        "WhatsApp, Messenger, SMS-alkalmazások és más támogatott appok friss értesítései." to "Recent notifications from WhatsApp, Messenger, SMS apps and other supported apps.",
        "Értesítési engedélyek" to "Notification permissions",
        "Üzenetlista ürítése" to "Clear message list",
        "Nincs új, elérhető üzenetértesítés. Engedélyezd az értesítések olvasását, majd várj egy új üzenetre." to "No new message notifications. Allow notification access and wait for a new message.",
        "🔊 Felolvasás" to "🔊 Read aloud",
        "Válasz" to "Reply",
        "🎤 Válasz diktálása" to "🎤 Dictate reply",
        "Válasz elküldése" to "Send reply",
        "Ehhez az értesítéshez az alkalmazás nem adott gyorsválasz-lehetőséget." to "This app did not provide a quick-reply action for this notification.",
        "Megnyitás az eredeti alkalmazásban" to "Open in original app",
        "Videó áttekintése: " to "Video preview: ",
        "Hossz: " to "Duration: ",
        "Helyben kinyert képkockák: " to "Locally extracted frames: ",
        "Ez még nem AI-alapú tartalomelemzés. Beszédátirat és" to "This is not AI content analysis yet. A speech transcript and",
        "Csatolás Jarvishoz" to "Attach to Jarvis",
        "📷  Kamera" to "📷  Camera",
        "▧  Fényképek és videók" to "▧  Photos and videos",
        "▤  Fájlok" to "▤  Files",
        "Képkockák" to "Frames",
        "Eltávolítás" to "Remove",
        "A telefon nem engedte elindítani a mikrofont." to "Android did not allow the microphone to start.",
        "A kéz nélküli módhoz mikrofonengedély szükséges." to "Microphone permission is required for hands-free mode.",
        "A diktáláshoz engedélyezd a mikrofont." to "Allow microphone access to dictate.",
        "Beszélj most…" to "Speak now…",
        "Hallgatlak…" to "Listening…",
        "Felismerés folyamatban…" to "Recognising…",
        "Mikrofon elindítva…" to "Microphone started…",
        "Nem kaptam felismert szöveget." to "No recognised text.",
        "A magyar beszédfelismerés nem érhető el. Állítsd be az Androidon." to "Speech recognition for this language is unavailable. Check Android language settings.",
        "Nem sikerült feloldani az előzményeket. Nem töröltem adatot." to "Could not open history. No data was deleted.",
        "A Jarvis saját alkalmazása nem tud minden külső appot vezérelni." to "Jarvis cannot control every external app.",
        "A helyben tárolt hozzáférést töröltem." to "Local access credentials removed.",
        "Jarvis AI sikeresen aktiválva ezen a telefonon." to "Jarvis AI successfully activated on this phone.",
        "Hozzáférés és hozzájárulás mentve. Teszteld az AI-kapcsolatot!" to "Access and consent saved. Test the AI connection!",
        "Előbb aktiváld a Jarvist a meghívóval." to "Activate Jarvis with an invitation first.",
        "A külső AI-feldolgozáshoz először jelöld be a hozzájárulást." to "Consent to external AI processing first.",
        "Az AI-kérdések továbbításához adott hozzájárulást visszavontam." to "Consent to transmit AI questions revoked.",
        "Az AI-szöveg továbbításához külön, önkéntes hozzájárulás szükséges." to "Separate voluntary consent is required to transmit AI text.",
        "Nem sikerült kapcsolódni az AI-hoz. Ellenőrizd az internetet és a kulcsot." to "Could not connect to AI. Check your internet connection and key.",
        "A kérdés üres vagy túl hosszú." to "The question is empty or too long.",
        "Az AI-hozzáférés nincs engedélyezve." to "AI access is not authorised.",
        "Elérted az ideiglenes használati limitet. Próbáld később." to "Temporary usage limit reached. Try again later.",
        "Nincs email alkalmazás." to "No email app found.",
        "Nincs térképalkalmazás." to "No map app found.",
        "Térkép megnyitva." to "Map opened.",
        "Email alkalmazás megnyitva." to "Email app opened.",
        "Indítsd el, majd mondd: Jarvis vagy Hey Jarvis. Az üzeneteket felolvasom, a válaszokat csak az igen/nem megerősítésed után adom át a gyorsválasznak." to "Start listening, then say Jarvis or Hey Jarvis. I can read messages aloud; replies are handed to quick reply only after your yes/no confirmation.",
        "Időkorlát: 15 perc. A telefon beszédfelismerőjét használja, amely nem egész napos ébresztőszó-motor; egyes készülékeken háttérben leállhat, és a beszédfelismerő szolgáltatója felhőt is használhat." to "Time limit: 15 minutes. This uses Android speech recognition, not an always-on wake-word engine; it may stop in the background, and the recognition provider may use cloud processing.",
        "A mikrofon aktív állapotát az Android értesítés mutatja. Ott is leállíthatod a figyelést. Az üzenetekhez külön értesítési hozzáférés kell." to "Android shows an active microphone notification. You can stop listening there. Message access requires separate notification permission.",
        "Jarvis csak megnyitja a kiválasztott alkalmazást. Nem olvas bele, nem nyom meg gombokat és nem küld üzeneteket helyetted. A telepített alkalmazások listája csak a telefonon marad; nem küldöm sem a Jarvis szerverre, sem az AI-szolgáltatónak." to "Jarvis opens the selected app but cannot read its private content, press its buttons or send messages for you. Your installed apps list stays on the phone and is not sent to a Jarvis server or AI provider.",
        "WhatsApp, Messenger, SMS-alkalmazások és más támogatott appok friss értesítései. A teljes beszélgetéselőzményt Jarvis nem látja. Az üzeneteket nem tölti fel és nem menti tartósan." to "Recent notifications from WhatsApp, Messenger, SMS and other supported apps. Jarvis cannot see complete conversation histories and does not upload or permanently save messages.",
        "Ez még nem AI-alapú tartalomelemzés. Beszédátirat és a videó értelmezése az AI-agy bekötése után lesz elérhető." to "This is not AI content analysis yet. Speech transcription and video understanding will require a connected AI service.",
        "A kiválasztott nyelven nem érhető el hangos válasz. Ellenőrizd az Android szövegfelolvasó beállításait." to "Voice output is not available in the selected language. Check Android text-to-speech settings.",
        "A kiválasztott nyelven nem érhető el beszédfelismerés. Állítsd be az Androidon." to "Speech recognition is not available in the selected language. Configure it in Android settings.",
    )
    // Separate short fragments are needed for labels that contain values.
    private val dynamicPrefixes = listOf(
        "Állapot: " to "Status: ",
        "Hallottam: " to "Heard: ",
        "Videó áttekintése: " to "Video preview: ",
        "Hossz: " to "Duration: ",
        "Jelenleg mentett kapcsolat: " to "Currently saved connection: ",
        "Átlag: " to "Average: ",
        "Fel szeretnéd hívni " to "Do you want to call ",
        "Helyben kinyert képkockák: " to "Locally extracted frames: ",
    )
    fun text(original: String, language: JarvisLanguage): String {
        if (language == JarvisLanguage.HUNGARIAN) return original
        val en = english[original] ?: dynamicPrefixes.firstOrNull {
            original.startsWith(it.first)
        }?.let { (from, to) -> to + original.removePrefix(from) } ?: original
        if (language == JarvisLanguage.ENGLISH) return en
        // Translation keys are the original fixed UI labels, never user input.
        return JarvisMoreTranslations.text(original, language) ?: en
    }
}

/** Tiny flag button beside the appearance toggle, shared by Home and every subpage. */
@Composable
internal fun JarvisLanguagePicker(
    language: JarvisLanguage, onSelect: (JarvisLanguage) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(shape = CircleShape, color = JarvisPalette.panel, shadowElevation = 2.dp) {
            IconButton(
                onClick = { expanded = true },
                modifier = Modifier.testTag("jarvis-language-switcher")
            ) {
                androidx.compose.material3.Text(language.flag, fontSize = 21.sp)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            JarvisLanguage.entries.forEach { option ->
                ControlledDropdownMenuItem(
                    text = { androidx.compose.material3.Text(option.flag + "  " + option.label) },
                    onClick = { expanded = false; onSelect(option) },
                    modifier = Modifier.testTag("jarvis-language-" + option.tag)
                , commandLabel = option.flag + "  " + option.label)
            }
        }
    }
}

/* Explicit alias as Text in UI source files ensures static labels share the
 * locale without changing route keys, backend commands, or user data storage. */
@Composable
internal fun LocalizedText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
) {
    androidx.compose.material3.Text(
        text = JarvisTranslations.text(text, LocalJarvisLanguage.current),
        modifier = modifier, color = color, fontSize = fontSize,
        fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily,
        letterSpacing = letterSpacing, textDecoration = textDecoration,
        textAlign = textAlign, lineHeight = lineHeight, overflow = overflow,
        softWrap = softWrap, maxLines = maxLines, minLines = minLines,
        onTextLayout = onTextLayout, style = style
    )
}

@Composable
internal fun LocalizedText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
) {
    androidx.compose.material3.Text(
        text = text, modifier = modifier, color = color, fontSize = fontSize,
        fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily,
        letterSpacing = letterSpacing, textDecoration = textDecoration,
        textAlign = textAlign, lineHeight = lineHeight, overflow = overflow,
        softWrap = softWrap, maxLines = maxLines, minLines = minLines,
        inlineContent = inlineContent, onTextLayout = onTextLayout, style = style
    )
}
