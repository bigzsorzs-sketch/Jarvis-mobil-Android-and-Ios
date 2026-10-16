package ai.jarvis.mobile

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.jarvis.mobile.LocalizedText as Text

/**
 * Privacy/permissions only. The old manual OpenRouter key and gateway-mode
 * settings are intentionally absent. Existing encrypted credentials remain
 * untouched; this screen never provisions new credentials or bundles keys.
 */
@Composable
internal fun JarvisAiSettings(context: Context) {
    val language = LocalJarvisLanguage.current
    fun label(hu: String, en: String, de: String, fr: String, es: String, it: String, pl: String) =
        listOf(hu, en, de, fr, es, it, pl)[language.ordinal]

    var cloudConsent by remember { mutableStateOf(JarvisAiGateway.cloudConsent(context)) }
    var voiceConsent by remember { mutableStateOf(JarvisAiGateway.voiceConsent(context)) }
    var transcriptionConsent by remember {
        mutableStateOf(JarvisAiGateway.transcriptionConsent(context))
    }
    val paired = JarvisAiGateway.hasSavedConnection(context)

    Text(label("AI-adatvédelem és hozzáférés", "AI privacy and access",
        "KI-Datenschutz und Zugriff", "Confidentialité et accès IA",
        "Privacidad y acceso a IA", "Privacy e accesso all'IA",
        "Prywatność i dostęp do AI"),
        color = JarvisPalette.mainText, style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(12.dp))
    Text(if (paired)
        label("Az ezen a telefonon mentett AI-kapcsolat megmaradt.",
            "The existing AI connection on this phone has been preserved.",
            "Die bestehende KI-Verbindung auf diesem Gerät bleibt erhalten.",
            "La connexion IA existante sur ce téléphone est conservée.",
            "Se ha conservado la conexión de IA de este teléfono.",
            "La connessione IA esistente su questo telefono è stata mantenuta.",
            "Istniejące połączenie AI na tym telefonie zostało zachowane.")
    else
        label("Nincs aktív AI-hozzáférés ezen a telefonon. Új hozzáféréshez biztonságos aktiválás szükséges.",
            "No AI access is configured on this phone. Secure activation is required for new access.",
            "Auf diesem Gerät ist kein KI-Zugang eingerichtet. Für einen neuen Zugang ist eine sichere Aktivierung erforderlich.",
            "Aucun accès IA n'est configuré sur ce téléphone. Une activation sécurisée est nécessaire.",
            "No hay acceso a IA configurado en este teléfono. Hace falta una activación segura.",
            "Non è configurato alcun accesso IA su questo telefono. Serve un'attivazione sicura.",
            "Na tym telefonie nie skonfigurowano dostępu do AI. Wymagana jest bezpieczna aktywacja."),
        color = JarvisPalette.subtleText, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(12.dp))

    Text(label(
        "Az AI-kérdéseket és a kiválasztott beszélgetési előzményeket külső szolgáltató dolgozza fel. A helyi névjegyek, fájlok és vércukoradatok nem kerülnek automatikusan a kéréshez.",
        "An external provider processes AI questions and selected conversation history. Local contacts, files and glucose data are not attached automatically.",
        "Ein externer Anbieter verarbeitet KI-Fragen und ausgewählte Chatverläufe. Kontakte, Dateien und Blutzuckerdaten werden nicht automatisch angehängt.",
        "Un fournisseur externe traite les questions IA et l'historique choisi. Contacts, fichiers et données glycémiques ne sont pas joints automatiquement.",
        "Un proveedor externo procesa preguntas IA y el historial elegido. Los contactos, archivos y datos de glucosa no se adjuntan automáticamente.",
        "Un fornitore esterno elabora domande IA e cronologia selezionata. Contatti, file e glicemia non vengono allegati automaticamente.",
        "Zewnętrzny dostawca przetwarza pytania AI i wybraną historię rozmowy. Kontakty, pliki i glukoza nie są dodawane automatycznie."),
        color = JarvisPalette.subtleText, style = MaterialTheme.typography.bodySmall)

    Spacer(Modifier.height(12.dp))
    RegisterJarvisControl("AI-kérdések felhőbe küldése", click = {
        cloudConsent = !cloudConsent
        JarvisAiGateway.setCloudConsent(context, cloudConsent)
    })
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = cloudConsent, onCheckedChange = { enabled ->
            cloudConsent = enabled
            JarvisAiGateway.setCloudConsent(context, enabled)
        })
        Text(label("AI-kérdések felhőbe küldése", "Send AI questions to the cloud",
            "KI-Fragen an die Cloud senden", "Envoyer les questions IA au cloud",
            "Enviar preguntas de IA a la nube", "Inviare domande IA al cloud",
            "Wysyłanie pytań AI do chmury"),
            color = JarvisPalette.mainText, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    RegisterJarvisControl("AI-hang használata", click = {
        voiceConsent = !voiceConsent
        JarvisAiGateway.setVoiceConsent(context, voiceConsent)
    })
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = voiceConsent, onCheckedChange = { enabled ->
            voiceConsent = enabled
            JarvisAiGateway.setVoiceConsent(context, enabled)
        })
        Text(label("Felhős AI-hang engedélyezése", "Allow cloud AI voice",
            "Cloud-KI-Stimme erlauben", "Autoriser la voix IA cloud",
            "Permitir voz IA en la nube", "Consenti voce IA cloud",
            "Zezwalaj na głos AI w chmurze"),
            color = JarvisPalette.mainText, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    RegisterJarvisControl("GPT beszédfelismerés engedélyezése", click = {
        transcriptionConsent = !transcriptionConsent
        JarvisAiGateway.setTranscriptionConsent(context, transcriptionConsent)
    })
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = transcriptionConsent, onCheckedChange = { enabled ->
            transcriptionConsent = enabled
            JarvisAiGateway.setTranscriptionConsent(context, enabled)
        })
        Text(label("Felhős beszédfelismerés engedélyezése",
            "Allow cloud speech recognition", "Cloud-Spracherkennung erlauben",
            "Autoriser la reconnaissance vocale cloud", "Permitir reconocimiento de voz en la nube",
            "Consenti riconoscimento vocale cloud", "Zezwalaj na rozpoznawanie mowy w chmurze"),
            color = JarvisPalette.mainText, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))
    Text(label(
        "A felhős AI-hanghoz a felolvasott szöveg, a felhős beszédfelismeréshez a mikrofonhang külön engedéllyel kerül a szolgáltatóhoz. A hozzáférést a Beállítások → Kijelentkezés menüben bontod; a helyi adatok megmaradnak.",
        "Cloud voice sends spoken text, while cloud transcription sends microphone audio, each with separate consent. Settings → Log out disconnects AI access while keeping local data.",
        "Cloud-Stimme sendet Text, Cloud-Transkription Mikrofonaufnahmen – jeweils nur mit gesonderter Zustimmung. Einstellungen → Abmelden trennt den KI-Zugang bei Erhalt lokaler Daten.",
        "La voix cloud transmet le texte et la transcription cloud l'audio, avec des accords distincts. Paramètres → Déconnexion conserve les données locales.",
        "La voz cloud envía texto y la transcripción cloud envía audio, con permisos separados. Ajustes → Cerrar sesión conserva los datos locales.",
        "La voce cloud invia testo e la trascrizione cloud invia audio, con consensi separati. Impostazioni → Esci conserva i dati locali.",
        "Głos AI wysyła tekst, a transkrypcja nagrania mikrofonowe, za osobną zgodą. Ustawienia → Wyloguj zachowuje dane lokalne."),
        color = JarvisPalette.subtleText, style = MaterialTheme.typography.bodySmall)
}
