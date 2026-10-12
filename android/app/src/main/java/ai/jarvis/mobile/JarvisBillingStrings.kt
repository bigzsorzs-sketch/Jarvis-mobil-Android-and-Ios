package ai.jarvis.mobile

/** Independently curated billing vocabulary, in the same seven saved Jarvis languages. */
internal object BillingStrings {
    private fun words(vararg values: String) = values.toList()
    private val translations = mapOf(
        "invoice" to words("Számla", "Invoice", "Rechnung", "Facture", "Factura", "Fattura", "Faktura"),
        "invoices" to words("Számlák", "Invoices", "Rechnungen", "Factures", "Facturas", "Fatture", "Faktury"),
        "quote" to words("Árajánlat", "Quote", "Angebot", "Devis", "Presupuesto", "Preventivo", "Wycena"),
        "quotes" to words("Árajánlatok", "Quotes", "Angebote", "Devis", "Presupuestos", "Preventivi", "Wyceny"),
        "clients" to words("Ügyfelek", "Clients", "Kunden", "Clients", "Clientes", "Clienti", "Klienci"),
        "settings" to words("Beállítások", "Settings", "Einstellungen", "Paramètres", "Ajustes", "Impostazioni", "Ustawienia"),
        "supplier" to words("Kiállító", "Supplier", "Aussteller", "Émetteur", "Emisor", "Emittente", "Wystawca"),
        "customer" to words("Ügyfél", "Customer", "Kunde", "Client", "Cliente", "Cliente", "Klient"),
        "issued" to words("Kiállítva", "Issued", "Ausgestellt", "Émise", "Emitida", "Emessa", "Wystawiono"),
        "due" to words("Határidő", "Due", "Fällig", "Échéance", "Vence", "Scadenza", "Termin"),
        "description" to words("Szolgáltatás", "Description", "Beschreibung", "Description", "Descripción", "Descrizione", "Opis"),
        "subtotal" to words("Nettó", "Subtotal", "Zwischensumme", "Sous-total", "Subtotal", "Imponibile", "Netto"),
        "total" to words("Végösszeg", "Total", "Gesamt", "Total", "Total", "Totale", "Razem"),
        "paymentDetails" to words("Fizetési adatok", "Payment details", "Zahlungsdaten", "Coordonnées de paiement", "Datos de pago", "Dati pagamento", "Dane płatności"),
        "emailBody" to words("Csatoltan küldöm a számlát.", "Please find your invoice attached.", "Ihre Rechnung finden Sie im Anhang.", "Veuillez trouver votre facture en pièce jointe.", "Adjunto encontrará su factura.", "In allegato trova la fattura.", "Faktura znajduje się w załączniku."),
        "smtpAccepted" to words("Az SMTP-szerver átvette a levelet:", "Mail accepted by the SMTP server:", "E-Mail vom SMTP-Server angenommen:", "E-mail accepté par le serveur SMTP :", "Correo aceptado por el servidor SMTP:", "Email accettata dal server SMTP:", "Wiadomość przyjęta przez serwer SMTP:"),
        "sendUncertain" to words("A küldés eredménye nem biztos. Ellenőrizd a Gmail Elküldött mappáját, mielőtt újra próbálod.", "Send outcome uncertain. Check Gmail Sent before retrying.", "Versand unklar. Vor erneutem Versuch Gmail Gesendet prüfen.", "Envoi incertain. Vérifiez Envoyés dans Gmail avant de réessayer.", "Envío incierto. Revisa Enviados de Gmail antes de reintentar.", "Invio incerto. Verifica Inviati in Gmail prima di riprovare.", "Wynik wysyłki niepewny. Sprawdź Wysłane w Gmail przed ponowieniem."),
        "alreadyProcessed" to words("Ez a számla már elküldött vagy feldolgozás alatt áll. Nem küldöm újra.", "Invoice already sent or processed; not sending twice.", "Rechnung bereits gesendet oder in Bearbeitung; kein Doppelversand.", "Facture déjà envoyée ou traitée ; aucun renvoi.", "Factura enviada o procesada; no se envía dos veces.", "Fattura già inviata o elaborata; nessun duplicato.", "Faktura już wysłana lub przetworzona; nie wysyłam ponownie."),
        "missingInvoice" to words("Nem találom a számlát.", "Invoice not found.", "Rechnung nicht gefunden.", "Facture introuvable.", "Factura no encontrada.", "Fattura non trovata.", "Nie znaleziono faktury."),
        "missingClient" to words("Nem találom az ügyfelet.", "Client not found.", "Kunde nicht gefunden.", "Client introuvable.", "Cliente no encontrado.", "Cliente non trovato.", "Nie znaleziono klienta."),
        "missingDetails" to words("Hiányzik az ügyfél e-mail-címe vagy postacíme, illetve a cég neve vagy címe.", "Complete the client's email and postal address and your business name and address.", "E-Mail und Anschrift des Kunden sowie Firmennamen und Adresse ergänzen.", "Complétez l'e-mail, l'adresse du client et les coordonnées de votre entreprise.", "Completa email y dirección del cliente y datos de tu empresa.", "Completa email e indirizzo del cliente e i dati aziendali.", "Uzupełnij email i adres klienta oraz nazwę i adres firmy."),
        "missingVat" to words("Áfás számlához mentsd el a VAT-számot.", "Save your VAT registration number for VAT invoices.", "Für Rechnungen mit MwSt. USt-IdNr. speichern.", "Enregistrez votre numéro de TVA.", "Guarda tu número de IVA.", "Salva il numero IVA.", "Zapisz numer VAT."),
        "missingSmtp" to words("Állítsd be a Gmail címet és az alkalmazásjelszót a számlázás beállításaiban.", "Set your Gmail address and app password in billing settings.", "Gmail-Adresse und App-Passwort in den Einstellungen speichern.", "Configurez votre Gmail et le mot de passe d'application.", "Configura Gmail y la contraseña de aplicación.", "Configura Gmail e password per app.", "Ustaw Gmail i hasło aplikacji."),
        "quoteCannotSend" to words("Előbb alakítsd számlává az árajánlatot.", "Convert the quote to an invoice first.", "Angebot zuerst in Rechnung umwandeln.", "Convertissez d'abord le devis en facture.", "Convierte el presupuesto en factura.", "Converti prima il preventivo in fattura.", "Najpierw zamień wycenę w fakturę."),
        "companyName" to words("Cég neve", "Business name", "Firmenname", "Nom de l’entreprise", "Nombre de empresa", "Nome azienda", "Nazwa firmy"),
        "address" to words("Postacím", "Postal address", "Postanschrift", "Adresse postale", "Dirección postal", "Indirizzo postale", "Adres pocztowy"),
        "email" to words("E-mail-cím", "Email address", "E-Mail-Adresse", "Adresse e-mail", "Correo electrónico", "Indirizzo email", "Adres e-mail"),
        "phone" to words("Telefonszám", "Phone number", "Telefonnummer", "Téléphone", "Teléfono", "Telefono", "Telefon"),
        "bank" to words("Bankszámla / utalási adatok", "Bank/payment details", "Bank-/Zahlungsdaten", "Coordonnées bancaires", "Datos bancarios", "Dati bancari", "Dane bankowe"),
        "vatNumber" to words("VAT-szám (ha van)", "VAT number (if registered)", "USt-IdNr. (falls vorhanden)", "N° TVA (si inscrit)", "N.º IVA (si registrado)", "Partita IVA (se presente)", "Numer VAT (jeśli dotyczy)"),
        "save" to words("Mentés", "Save", "Speichern", "Enregistrer", "Guardar", "Salva", "Zapisz"),
        "newClient" to words("Új ügyfél / ügyfél módosítása", "New / edit client", "Kunde anlegen / bearbeiten", "Créer / modifier un client", "Nuevo / editar cliente", "Nuovo / modifica cliente", "Nowy / edytuj klienta"),
        "newInvoice" to words("Új számla / ajánlat", "New invoice / quote", "Neue Rechnung / Angebot", "Nouvelle facture / devis", "Nueva factura / presupuesto", "Nuova fattura / preventivo", "Nowa faktura / wycena"),
        "amount" to words("Nettó összeg (£)", "Net amount (£)", "Nettobetrag (£)", "Montant HT (£)", "Importe neto (£)", "Importo netto (£)", "Kwota netto (£)"),
        "vatRate" to words("VAT mértéke", "VAT rate", "MwSt.-Satz", "Taux de TVA", "Tipo de IVA", "Aliquota IVA", "Stawka VAT"),
        "dueDate" to words("Határidő (ÉÉÉÉ-HH-NN)", "Due date (YYYY-MM-DD)", "Fällig (JJJJ-MM-TT)", "Échéance (AAAA-MM-JJ)", "Vence (AAAA-MM-DD)", "Scadenza (AAAA-MM-GG)", "Termin (RRRR-MM-DD)"),
        "selectClient" to words("Válassz ügyfelet", "Select a client", "Kunde wählen", "Choisir un client", "Elegir cliente", "Scegli cliente", "Wybierz klienta"),
        "saveDraft" to words("Számlavázlat mentése", "Save invoice draft", "Rechnungsentwurf speichern", "Enregistrer le brouillon", "Guardar borrador", "Salva bozza", "Zapisz szkic"),
        "saveQuote" to words("Ajánlat mentése", "Save quote", "Angebot speichern", "Enregistrer devis", "Guardar presupuesto", "Salva preventivo", "Zapisz wycenę"),
        "paid" to words("Fizetett", "Paid", "Bezahlt", "Payée", "Pagada", "Pagata", "Opłacona"),
        "unpaid" to words("Nem fizetett", "Unpaid", "Offen", "Impayée", "Pendiente", "Non pagata", "Nieopłacona"),
        "draft" to words("Vázlat", "Draft", "Entwurf", "Brouillon", "Borrador", "Bozza", "Szkic"),
        "sending" to words("Küldés folyamatban", "Sending", "Versand läuft", "Envoi en cours", "Enviando", "Invio in corso", "Wysyłanie"),
        "uncertain" to words("Küldés bizonytalan", "Uncertain send", "Versand unklar", "Envoi incertain", "Envío incierto", "Invio incerto", "Wysyłka niepewna"),
        "sendNow" to words("Küldés e-mailben", "Send by email", "Per E-Mail senden", "Envoyer par e-mail", "Enviar por correo", "Invia via email", "Wyślij emailem"),
        "markPaid" to words("Fizetettnek jelölés", "Mark paid", "Als bezahlt markieren", "Marquer payée", "Marcar pagada", "Segna pagata", "Oznacz opłaconą"),
        "pdf" to words("PDF megtekintés / megosztás", "View/share PDF", "PDF ansehen/teilen", "Voir/partager PDF", "Ver/compartir PDF", "Visualizza/condividi PDF", "Otwórz/udostępnij PDF"),
        "convert" to words("Ajánlatból számla", "Convert to invoice", "In Rechnung umwandeln", "Convertir en facture", "Convertir en factura", "Converti in fattura", "Zamień na fakturę"),
        "smtp" to words("Gmail küldés (alkalmazásjelszó)", "Gmail sending (app password)", "Gmail-Versand (App-Passwort)", "Envoi Gmail (mot de passe d'appli)", "Envío Gmail (contraseña de aplicación)", "Invio Gmail (password app)", "Wysyłanie Gmail (hasło aplikacji)"),
        "appPassword" to words("Gmail alkalmazásjelszó", "Gmail app password", "Gmail-App-Passwort", "Mot de passe d'application Gmail", "Contraseña de aplicación Gmail", "Password per app Gmail", "Hasło aplikacji Gmail"),
        "mailHelp" to words("Valódi küldés: Gmail SMTP TLS-en. Google-fiókban létrehozott alkalmazásjelszó kell; nem a normál jelszavad. A küldés nem igényel újabb jóváhagyást.", "Real sending over Gmail SMTP/TLS requires a Google App Password, not your regular password. No second send approval.", "Echter Versand per Gmail SMTP/TLS benötigt ein App-Passwort, nicht das normale Passwort. Keine zweite Freigabe.", "Envoi réel via Gmail SMTP/TLS avec un mot de passe d'application, pas le mot de passe habituel.", "Envío real con SMTP/TLS de Gmail y contraseña de aplicación, no la contraseña normal.", "Invio reale tramite Gmail SMTP/TLS con password per app, non la password abituale.", "Rzeczywista wysyłka Gmail SMTP/TLS wymaga hasła aplikacji, nie zwykłego hasła."),
        "saved" to words("Mentve.", "Saved.", "Gespeichert.", "Enregistré.", "Guardado.", "Salvato.", "Zapisano."),
        "invalid" to words("Ellenőrizd a kötelező mezőket, a pénzösszeget és a dátumot.", "Check required fields, amount and date.", "Pflichtfelder, Betrag und Datum prüfen.", "Vérifiez les champs, le montant et la date.", "Comprueba campos, importe y fecha.", "Controlla campi, importo e data.", "Sprawdź pola, kwotę i datę."),
        "firstManual" to words("Először kézzel ments ügyfelet és számlát. Utána hanggal kérheted a küldést.", "First save a client and invoice manually, then use voice to send.", "Zuerst Kunde und Rechnung manuell speichern; danach per Sprache senden.", "Enregistrez d'abord le client et la facture manuellement, puis envoyez par voix.", "Guarda primero el cliente y la factura manualmente; luego usa voz.", "Salva prima cliente e fattura manualmente, poi usa la voce.", "Najpierw zapisz klienta i fakturę ręcznie, potem użyj głosu."),
        "noClientMatch" to words("Nem találtam egyértelmű mentett ügyfelet. Pontosítsd a nevét.", "No unique saved client matched. Say the exact client name.", "Kein eindeutiger gespeicherter Kunde. Bitte Namen präzisieren.", "Aucun client enregistré unique. Précisez le nom.", "No hay un cliente guardado único. Indica el nombre exacto.", "Nessun cliente salvato univoco. Specifica il nome.", "Brak jednoznacznego klienta. Podaj dokładną nazwę."),
        "needAmount" to words("Mondd meg az összeget fontban és a mentett ügyfél nevét.", "Say the amount in pounds and the saved client name.", "Betrag in Pfund und gespeicherten Kundennamen nennen.", "Indiquez le montant en livres et le nom du client.", "Di el importe en libras y el cliente guardado.", "Indica l'importo in sterline e il cliente salvato.", "Podaj kwotę w funtach i nazwę klienta."),
        "needService" to words("Először kézzel rögzíts szolgáltatást ennél az ügyfélnél.", "First save a service description manually for this client.", "Zuerst die Leistung für diesen Kunden manuell speichern.", "Enregistrez d'abord une prestation manuellement pour ce client.", "Primero guarda manualmente el servicio de este cliente.", "Prima salva manualmente il servizio per questo cliente.", "Najpierw zapisz ręcznie usługę dla tego klienta."),
        "created" to words("Számlavázlat létrehozva:", "Invoice draft created:", "Rechnungsentwurf erstellt:", "Brouillon de facture créé :", "Borrador de factura creado:", "Bozza fattura creata:", "Utworzono szkic faktury:"),
        "paidDone" to words("Fizetettnek jelölve:", "Marked paid:", "Als bezahlt markiert:", "Marquée payée :", "Marcada pagada:", "Segnata pagata:", "Oznaczono opłaconą:"),
        "noUnpaid" to words("Nincs nyitott számla.", "No open invoices.", "Keine offenen Rechnungen.", "Aucune facture impayée.", "No hay facturas pendientes.", "Nessuna fattura aperta.", "Brak otwartych faktur."),
        "openPage" to words("Megnyitom a számlázást.", "Opening billing.", "Öffne Rechnungen.", "Ouverture de la facturation.", "Abriendo facturación.", "Apro fatturazione.", "Otwieram fakturowanie.")
    )
    fun get(language: JarvisLanguage, key: String): String {
        val values = translations[key] ?: error("Missing billing translation: " + key)
        return values[language.ordinal]
    }
    fun keys(): Set<String> = translations.keys
    fun complete(): Boolean = translations.values.all { it.size == JarvisLanguage.entries.size && it.all(String::isNotBlank) }
}
