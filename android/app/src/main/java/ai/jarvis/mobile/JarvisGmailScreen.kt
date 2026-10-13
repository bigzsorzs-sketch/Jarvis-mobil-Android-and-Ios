package ai.jarvis.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** The same Gmail actions are available on the Email page and in Home voice/chat. */
@Composable
internal fun JarvisGmailScreen(
    language: JarvisLanguage,
    status: String,
    dispatch: (GmailRequest) -> Unit
) {
    fun tr(hu: String, en: String, de: String, fr: String, es: String, it: String, pl: String) =
        arrayOf(hu, en, de, fr, es, it, pl)[language.ordinal]
    val connect = tr("Google-fiók csatlakoztatása", "Connect Google account",
        "Google-Konto verbinden", "Connecter Google", "Conectar Google",
        "Connetti Google", "Połącz Google")
    val check = tr("Új emailek ellenőrzése", "Check unread emails",
        "Ungelesene E-Mails prüfen", "Vérifier les e-mails", "Comprobar correos",
        "Controlla email", "Sprawdź wiadomości")
    val read = tr("Új emailek felolvasása", "Read unread emails aloud",
        "E-Mails vorlesen", "Lire les e-mails", "Leer correos en voz alta",
        "Leggi le email", "Odczytaj wiadomości")
    val disconnect = tr("Hozzáférés visszavonása", "Disconnect Gmail",
        "Gmail trennen", "Déconnecter Gmail", "Desconectar Gmail",
        "Disconnetti Gmail", "Odłącz Gmail")
    val recipientLabel = tr("Címzett (email vagy pontos névjegynév)", "Recipient (email or exact contact name)",
        "Empfänger", "Destinataire", "Destinatario", "Destinatario", "Odbiorca")
    val subjectLabel = tr("Tárgy", "Subject", "Betreff", "Objet", "Asunto", "Oggetto", "Temat")
    val bodyLabel = tr("Levél szövege", "Message", "Nachricht", "Message", "Mensaje", "Messaggio", "Treść")
    val send = tr("Email küldése Gmaillel", "Send with Gmail", "Mit Gmail senden",
        "Envoyer avec Gmail", "Enviar con Gmail", "Invia con Gmail", "Wyślij przez Gmail")
    val senderLabel = tr("A megválaszolandó levél feladója", "Sender to reply to",
        "Absender für Antwort", "Expéditeur", "Remitente", "Mittente", "Nadawca")
    val reply = tr("Válasz elküldése", "Send reply", "Antwort senden",
        "Envoyer la réponse", "Enviar respuesta", "Invia risposta", "Wyślij odpowiedź")
    var recipient by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var sender by remember { mutableStateOf("") }
    var replyBody by remember { mutableStateOf("") }

    LaunchedEffect(status) {
        if (status.startsWith("A Gmail elfogadta az emailt.")) {
            recipient = ""; subject = ""; body = ""
        } else if (status.startsWith("A Gmail elfogadta a választ")) {
            sender = ""; replyBody = ""
        }
    }
    Text(connect, style = MaterialTheme.typography.titleMedium)
    Text(tr("A Google bejelentkezési ablakában kiválaszthatod a fiókodat. " +
            "Jarvis csak a jóváhagyott levelezési műveleteket végzi el.",
            "Choose your account in Google's authorization dialog. Jarvis only performs authorized mail actions.",
            "Wähle dein Konto in der Google-Autorisierung.",
            "Choisissez votre compte dans l'autorisation Google.",
            "Selecciona tu cuenta en la autorización de Google.",
            "Scegli il tuo account nell'autorizzazione Google.",
            "Wybierz konto w autoryzacji Google."),
        style = MaterialTheme.typography.bodySmall)
    Button(onClick = { dispatch(GmailRequest(connect, GmailOperation.Connect)) },
        modifier = Modifier.fillMaxWidth().testTag("gmail-connect")) { Text(connect) }
    OutlinedButton(onClick = { dispatch(GmailRequest(check, GmailOperation.Check)) },
        modifier = Modifier.fillMaxWidth().testTag("gmail-check")) { Text(check) }
    OutlinedButton(onClick = { dispatch(GmailRequest(read, GmailOperation.Read())) },
        modifier = Modifier.fillMaxWidth().testTag("gmail-read")) { Text(read) }
    if (status.isNotEmpty()) {
        Text(status, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("gmail-status"))
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
    Text(tr("Új email", "New email", "Neue E-Mail", "Nouvel e-mail",
        "Nuevo correo", "Nuova email", "Nowy e-mail"),
        style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(recipient, { recipient = it }, label = { Text(recipientLabel) },
        modifier = Modifier.fillMaxWidth().testTag("gmail-recipient"))
    OutlinedTextField(subject, { subject = it }, label = { Text(subjectLabel) },
        modifier = Modifier.fillMaxWidth().testTag("gmail-subject"))
    OutlinedTextField(body, { body = it }, label = { Text(bodyLabel) },
        minLines = 3, modifier = Modifier.fillMaxWidth().testTag("gmail-body"))
    Button(onClick = {
        dispatch(GmailRequest(send, GmailOperation.Send(recipient.trim(), subject.trim(), body.trim(), true)))
    }, enabled = recipient.isNotBlank() && subject.isNotBlank() && body.isNotBlank(),
        modifier = Modifier.fillMaxWidth().testTag("gmail-send")) { Text(send) }

    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
    Text(tr("Válasz egy beérkezett emailre", "Reply to a received email",
        "Auf E-Mail antworten", "Répondre à un e-mail", "Responder a un correo",
        "Rispondi a una email", "Odpowiedz na email"),
        style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(sender, { sender = it }, label = { Text(senderLabel) },
        modifier = Modifier.fillMaxWidth().testTag("gmail-reply-sender"))
    OutlinedTextField(replyBody, { replyBody = it }, label = { Text(bodyLabel) },
        minLines = 3, modifier = Modifier.fillMaxWidth().testTag("gmail-reply-body"))
    Button(onClick = {
        dispatch(GmailRequest(reply, GmailOperation.Reply(sender.trim(), replyBody.trim())))
    }, enabled = sender.isNotBlank() && replyBody.isNotBlank(),
        modifier = Modifier.fillMaxWidth().testTag("gmail-reply")) { Text(reply) }

    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
    Text(tr("A hozzáférést bármikor megszüntetheted.", "You can revoke access any time.",
        "Der Zugriff kann jederzeit widerrufen werden.",
        "Vous pouvez révoquer l'accès à tout moment.",
        "Puedes revocar el acceso en cualquier momento.",
        "Puoi revocare l'accesso in qualsiasi momento.",
        "Możesz cofnąć dostęp w każdej chwili."),
        style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = { dispatch(GmailRequest(disconnect, GmailOperation.Disconnect)) },
        modifier = Modifier.testTag("gmail-disconnect")) { Text(disconnect) }
}
