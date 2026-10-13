package ai.jarvis.mobile

/** Spoken call flow text follows the language picker; never changes the phonebook result. */
internal object JarvisCallText {
    fun get(language: JarvisLanguage, key: String, name: String = "", number: String = ""): String {
        val message = when (key) {
            "confirm" -> arrayOf(
                "Fel szeretnéd hívni $name névjegyet? A szám: $number. Mondj igent vagy nemet.",
                "Do you want to call $name at $number? Say yes or no.",
                "Möchtest du $name unter $number anrufen? Sag ja oder nein.",
                "Voulez-vous appeler $name au $number ? Dites oui ou non.",
                "¿Quieres llamar a $name al $number? Di sí o no.",
                "Vuoi chiamare $name al numero $number? Di sì o no.",
                "Czy zadzwonić do $name na numer $number? Powiedz tak lub nie."
            )
            "dialog" -> arrayOf(
                "Fel szeretnéd hívni $name számát: $number?",
                "Do you want to call $name at $number?",
                "Möchtest du $name unter $number anrufen?",
                "Voulez-vous appeler $name au $number ?",
                "¿Quieres llamar a $name al $number?",
                "Vuoi chiamare $name al $number?",
                "Czy zadzwonić do $name na numer $number?"
            )
            "timeout" -> arrayOf(
                "Lejárt a hívás megerősítési ideje. Nem indítottam hívást.",
                "Call confirmation expired. No call was placed.",
                "Die Anrufbestätigung ist abgelaufen. Kein Anruf wurde gestartet.",
                "La confirmation a expiré. Aucun appel n'a été lancé.",
                "La confirmación caducó. No se inició la llamada.",
                "Conferma scaduta. Nessuna chiamata avviata.",
                "Czas potwierdzenia minął. Nie wykonano połączenia."
            )
            "cancel" -> arrayOf(
                "Rendben, a hívást nem indítottam el.",
                "Okay, I did not place the call.",
                "Okay, ich habe den Anruf nicht gestartet.",
                "D'accord, je n'ai pas appelé.",
                "De acuerdo, no he llamado.",
                "Va bene, non ho chiamato.",
                "Dobrze, nie wykonałem połączenia."
            )
            "yesNo" -> arrayOf(
                "Csak az igen vagy nem választ várom a hívás előtt.",
                "Please say yes or no before calling.",
                "Bitte sag ja oder nein.",
                "Dites simplement oui ou non.",
                "Di sí o no.",
                "Di sì o no.",
                "Powiedz tak lub nie."
            )
            "submitted" -> arrayOf(
                "Átadtam a híváskérést az Android telefonrendszernek. A kapcsolat létrejöttét még nem ellenőriztem.",
                "I submitted the call to Android. I have not verified that it connected.",
                "Anruf an Android übergeben. Eine Verbindung ist noch nicht bestätigt.",
                "Appel transmis à Android. La connexion n'est pas encore confirmée.",
                "Llamada enviada a Android. La conexión aún no está confirmada.",
                "Chiamata inoltrata ad Android. Connessione non ancora confermata.",
                "Przekazano połączenie do Androida. Nie potwierdzono nawiązania rozmowy."
            )
            "failed" -> arrayOf(
                "Nem sikerült elindítani a hívást. Ellenőrizd a Telefon engedélyét.",
                "Could not start the call. Check phone permission.",
                "Anruf fehlgeschlagen. Prüfe die Telefonberechtigung.",
                "Impossible d'appeler. Vérifiez l'autorisation téléphone.",
                "No se pudo llamar. Comprueba el permiso del teléfono.",
                "Chiamata non riuscita. Controlla i permessi del telefono.",
                "Nie udało się zadzwonić. Sprawdź uprawnienia telefonu."
            )
            "noContacts" -> arrayOf(
                "Engedélyezd a telefon névjegyeinek olvasását a Jarvis Engedélyközpontban.",
                "Allow Jarvis to read your phone contacts in Permissions.",
                "Erlaube Jarvis den Zugriff auf Telefonkontakte.",
                "Autorisez Jarvis à lire les contacts du téléphone.",
                "Permite a Jarvis leer los contactos del teléfono.",
                "Consenti a Jarvis di leggere i contatti del telefono.",
                "Zezwól Jarvisowi na odczyt kontaktów telefonu."
            )
            "ambiguous" -> arrayOf(
                "Több ilyen névjegyet találtam a telefonon. Mondd a teljes nevet.",
                "I found several matching phone contacts. Say the full name.",
                "Mehrere Telefonkontakte passen. Sage den vollständigen Namen.",
                "Plusieurs contacts correspondent. Dites le nom complet.",
                "Hay varios contactos. Di el nombre completo.",
                "Ci sono più contatti. Di' il nome completo.",
                "Znalazłem kilka kontaktów. Podaj pełne imię i nazwisko."
            )
            "missing" -> arrayOf(
                "Nem találtam ilyen névjegyet a telefonon.",
                "I could not find that person in your phone contacts.",
                "Dieser Kontakt wurde im Telefon nicht gefunden.",
                "Contact introuvable dans le téléphone.",
                "No encuentro ese contacto en el teléfono.",
                "Non trovo quel contatto sul telefono.",
                "Nie znalazłem tego kontaktu w telefonie."
            )
            else -> error("Unknown call message key")
        }
        return message[language.ordinal]
    }
}
