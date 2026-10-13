package ai.jarvis.mobile

/** Navigation prompts always follow the selected seven-language Jarvis setting. */
internal object JarvisNavigationText {
    fun result(language: JarvisLanguage, result: VoiceNavigation.LaunchResult): String =
        when (result) {
            VoiceNavigation.LaunchResult.GUIDANCE_REQUESTED -> choose(language,
                "Elküldtem a navigációindítási kérést a Google Mapsnek.",
                "I sent the turn-by-turn navigation request to Google Maps.",
                "Ich habe die Navigation in Google Maps gestartet.",
                "J'ai demandé le démarrage de la navigation dans Google Maps.",
                "He solicitado iniciar la navegación en Google Maps.",
                "Ho richiesto l'avvio della navigazione in Google Maps.",
                "Wysłano polecenie rozpoczęcia nawigacji w Mapach Google.")
            VoiceNavigation.LaunchResult.DIRECTIONS_OPENED -> choose(language,
                "Megnyitottam az útvonalat. Google Maps nélkül az Indítás gombot még meg kell nyomni.",
                "I opened the directions. Without Google Maps, you may still need to tap Start.",
                "Die Route ist geöffnet. Ohne Google Maps musst du möglicherweise auf Start tippen.",
                "L'itinéraire est ouvert. Sans Google Maps, tu devras peut-être appuyer sur Démarrer.",
                "He abierto la ruta. Sin Google Maps, puede que debas pulsar Iniciar.",
                "Ho aperto il percorso. Senza Google Maps potrebbe essere necessario toccare Avvia.",
                "Trasa została otwarta. Bez Map Google może być konieczne naciśnięcie Start.")
            VoiceNavigation.LaunchResult.UNAVAILABLE -> choose(language,
                "Nem sikerült megnyitni a navigációt. Ellenőrizd a térképalkalmazást.",
                "I couldn't open navigation. Check your maps app.",
                "Ich konnte die Navigation nicht öffnen. Prüfe deine Karten-App.",
                "Je n'ai pas pu ouvrir la navigation. Vérifie l'application de cartes.",
                "No pude abrir la navegación. Comprueba tu aplicación de mapas.",
                "Non sono riuscito ad aprire la navigazione. Controlla l'app delle mappe.",
                "Nie udało się otworzyć nawigacji. Sprawdź aplikację map.")
        }

    fun missingDestination(language: JarvisLanguage): String = choose(language,
        "Ehhez a célhoz nincs mentett cím. Ha haza navigálnál, add meg az otthoni címedet.",
        "No address is saved for this destination. Set your home address to navigate home.",
        "Für dieses Ziel ist keine Adresse gespeichert. Hinterlege zuerst deine Heimadresse.",
        "Aucune adresse enregistrée pour cette destination. Enregistre ton adresse personnelle.",
        "No hay una dirección guardada para este destino. Configura la dirección de casa.",
        "Non c'è un indirizzo salvato per questa destinazione. Imposta l'indirizzo di casa.",
        "Nie zapisano adresu tego celu. Ustaw adres domowy.")

    fun awaitingForeground(language: JarvisLanguage): String = choose(language,
        "Az úti célt megértettem. A navigáció indításához hozd előtérbe Jarvist; az Android nem engedi a háttérből történő automatikus alkalmazásnyitást.",
        "Destination received. Bring Jarvis to the foreground to launch Maps; Android restricts automatic app launches from the background.",
        "Ziel erkannt. Öffne Jarvis im Vordergrund, um Maps zu starten; Android beschränkt App-Starts im Hintergrund.",
        "Destination reconnue. Affiche Jarvis au premier plan pour lancer Maps : Android limite les ouvertures d'apps en arrière-plan.",
        "Destino reconocido. Pon Jarvis en primer plano para abrir Maps; Android limita el inicio de aplicaciones en segundo plano.",
        "Destinazione riconosciuta. Porta Jarvis in primo piano per aprire Maps; Android limita l'avvio di app in background.",
        "Cel rozpoznany. Otwórz Jarvis na pierwszym planie, aby uruchomić Mapy; Android ogranicza uruchamianie aplikacji w tle.")

    fun starting(language: JarvisLanguage): String = choose(language,
        "Indítom a navigációt.",
        "Starting navigation.",
        "Navigation wird gestartet.",
        "Démarrage de la navigation.",
        "Iniciando navegación.",
        "Avvio della navigazione.",
        "Uruchamiam nawigację.")

    private fun choose(language: JarvisLanguage, hu: String, en: String, de: String,
        fr: String, es: String, it: String, pl: String): String = when (language) {
        JarvisLanguage.HUNGARIAN -> hu
        JarvisLanguage.ENGLISH -> en
        JarvisLanguage.GERMAN -> de
        JarvisLanguage.FRENCH -> fr
        JarvisLanguage.SPANISH -> es
        JarvisLanguage.ITALIAN -> it
        JarvisLanguage.POLISH -> pl
    }
}
