package ai.jarvis.mobile

internal object JarvisCommandTexts {
    private val all = mapOf(
        "panel" to listOf("Jarvis-parancs", "Jarvis command", "Jarvis-Befehl", "Commande Jarvis", "Comando Jarvis", "Comando Jarvis", "Polecenie Jarvis"),
        "command" to listOf("Parancs", "Command", "Befehl", "Commande", "Comando", "Comando", "Polecenie"),
        "send" to listOf("Küldés", "Send", "Senden", "Envoyer", "Enviar", "Invia", "Wyślij"),
        "close" to listOf("Bezárás", "Close", "Schließen", "Fermer", "Cerrar", "Chiudi", "Zamknij"),
        "voiceOn" to listOf("Hang indítása", "Start voice", "Sprache starten", "Démarrer la voix", "Iniciar voz", "Avvia voce", "Włącz głos"),
        "voiceOff" to listOf("Hang leállítása", "Stop voice", "Sprache stoppen", "Arrêter la voix", "Detener voz", "Ferma voce", "Wyłącz głos"),
        "example" to listOf(
            "Példa: nyomd meg a Mentés gombot. Írd az Új jegyzet mezőbe: bevásárlás.",
            "Example: click the Save button. Set New note field: shopping.",
            "Beispiel: klicke Speichern. Schreibe Neue Notiz Feld: Einkauf.",
            "Exemple : clique Enregistrer. Remplis Nouvelle note champ : courses.",
            "Ejemplo: pulsa Guardar. Rellena Nueva nota campo: compras.",
            "Esempio: premi Salva. Scrivi Nuova nota campo: spesa.",
            "Przykład: kliknij Zapisz. Wpisz Nowa notatka pole: zakupy."),
        "fields" to listOf("Mezők", "Fields", "Felder", "Champs", "Campos", "Campi", "Pola"),
        "buttons" to listOf("Gombok", "Buttons", "Schaltflächen", "Boutons", "Botones", "Pulsanti", "Przyciski"),
        "empty" to listOf("Ezen az oldalon nincs elérhető vezérlő.", "No controls on this page.", "Keine Steuerelemente auf dieser Seite.", "Aucun contrôle sur cette page.", "No hay controles en esta página.", "Nessun controllo in questa pagina.", "Brak kontrolek na tej stronie."),
        "missing" to listOf("Nem találom az aktuális Jarvis-oldalon: %s. Kérd: sorold fel az oldal vezérlőit.", "Not found on this Jarvis page: %s. Say: list page controls.", "Auf dieser Jarvis-Seite nicht gefunden: %s.", "Introuvable sur cette page Jarvis : %s.", "No se encuentra en esta página Jarvis: %s.", "Non trovato in questa pagina Jarvis: %s.", "Nie znaleziono na tej stronie Jarvis: %s."),
        "duplicate" to listOf("Több ilyen vezérlő van: %s. Add meg a sorszámát, például: nyomd meg a második Mentés gombot.", "Multiple controls match %s. Specify the number, for example: click second Save button.", "Mehrere Treffer für %s. Gib die Nummer vor dem Namen an.", "Plusieurs contrôles correspondent à %s. Précisez le numéro avant le nom.", "Varios controles coinciden con %s. Indica el número antes del nombre.", "Più controlli corrispondono a %s. Indica il numero prima del nome.", "Kilka kontrolek pasuje do %s. Podaj numer przed nazwą."),
        "disabled" to listOf("Ez a vezérlő most nem aktív: %s.", "This control is currently disabled: %s.", "Dieses Steuerelement ist deaktiviert: %s.", "Ce contrôle est désactivé : %s.", "Este control está desactivado: %s.", "Questo controllo è disattivato: %s.", "Ta kontrolka jest wyłączona: %s."),
        "filled" to listOf("Kitöltöttem ezt a mezőt: %s.", "Updated this field: %s.", "Feld ausgefüllt: %s.", "Champ rempli : %s.", "Campo rellenado: %s.", "Campo compilato: %s.", "Wypełniono pole: %s."),
        "clicked" to listOf("Működtettem ezt a vezérlőt: %s. Az oldal mutatja a művelet eredményét.", "Activated %s. The page shows the operation's result.", "%s betätigt. Die Seite zeigt das Ergebnis.", "%s activé. La page affiche le résultat.", "%s activado. La página muestra el resultado.", "%s attivato. La pagina mostra il risultato.", "Uruchomiono %s. Strona pokazuje wynik."),
        "failed" to listOf("Nem sikerült működtetni ezt a vezérlőt: %s.", "Could not activate %s.", "%s konnte nicht aktiviert werden.", "Impossible d'activer %s.", "No se pudo activar %s.", "Impossibile attivare %s.", "Nie można uruchomić %s."),
        "assistant" to listOf("Alapértelmezett asszisztens", "Default assistant", "Standardassistent", "Assistant par défaut", "Asistente predeterminado", "Assistente predefinito", "Domyślny asystent"),
        "assistantHelp" to listOf("Jarvis indítása az Android asszisztensgombjával", "Launch Jarvis with Android's assistant button", "Jarvis mit der Android-Assistententaste starten", "Ouvrir Jarvis avec le bouton assistant Android", "Abrir Jarvis con el botón de asistente Android", "Apri Jarvis con il pulsante assistente Android", "Uruchom Jarvis przyciskiem asystenta Android")
    )
    fun text(language: JarvisLanguage, key: String, label: String = ""): String =
        (all[key]?.get(language.ordinal) ?: key).replace("%s", label)
}
