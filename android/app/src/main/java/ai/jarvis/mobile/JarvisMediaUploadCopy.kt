package ai.jarvis.mobile

/**
 * User consent to cloud video keyframes must be explicit and understandable in
 * the selected language. This copy describes the exact supported operation;
 * no untranscribed soundtrack or full video understanding is promised.
 */
internal object JarvisMediaUploadCopy {
    fun staged(language: JarvisLanguage): String = when (language) {
        JarvisLanguage.HUNGARIAN ->
            "A fájlok helyben megnyithatók. Az AI-nak kép, PDF és szöveg (5 MB/fájl, 20 MB összesen) vagy 1 videó (legfeljebb 128 MB, csak 4 képkocka, hang nélkül) küldhető; külön Küldés gombbal."
        JarvisLanguage.ENGLISH ->
            "Files can be opened locally. For AI analysis, explicitly send images, PDF or text (5 MB per file, 20 MB total), or one video (up to 128 MB, only 4 sampled frames, without audio)."
        JarvisLanguage.GERMAN ->
            "Dateien können lokal geöffnet werden. Für die KI-Analyse sende ausdrücklich Bilder, PDF oder Text (5 MB je Datei, insgesamt 20 MB) oder ein Video (bis 128 MB, nur 4 Einzelbilder, ohne Ton)."
        JarvisLanguage.FRENCH ->
            "Les fichiers s'ouvrent localement. Pour l'analyse IA, envoie explicitement images, PDF ou texte (5 Mo/fichier, 20 Mo au total), ou une vidéo (128 Mo maximum, 4 images extraites, sans audio)."
        JarvisLanguage.SPANISH ->
            "Los archivos se abren localmente. Para el análisis con IA, envía imágenes, PDF o texto (5 MB por archivo, 20 MB en total), o un vídeo (hasta 128 MB, solo 4 fotogramas, sin audio)."
        JarvisLanguage.ITALIAN ->
            "I file si aprono in locale. Per l'analisi IA, invia esplicitamente immagini, PDF o testo (5 MB per file, 20 MB totali), oppure un video (fino a 128 MB, solo 4 fotogrammi, senza audio)."
        JarvisLanguage.POLISH ->
            "Pliki można otwierać lokalnie. Do analizy AI wyślij obrazy, PDF lub tekst (5 MB na plik, 20 MB łącznie) albo jeden film (do 128 MB, tylko 4 klatki, bez dźwięku)."
    }

    fun preview(language: JarvisLanguage): String = when (language) {
        JarvisLanguage.HUNGARIAN ->
            "Ez helyi előnézet. A Küldés gombbal legfeljebb 4 videóképkockát elemeztethetsz az AI-val közvetlen OpenRouter-módban. A videót és hangját nem küldi el; nincs beszédátirat."
        JarvisLanguage.ENGLISH ->
            "This is a local preview. Press Send for AI analysis of up to 4 video frames in direct OpenRouter mode. The original video and audio are not uploaded; no speech transcript is available."
        JarvisLanguage.GERMAN ->
            "Dies ist eine lokale Vorschau. Über Senden kann die KI bis zu 4 Videobilder im direkten OpenRouter-Modus prüfen. Das Originalvideo und der Ton werden nicht hochgeladen; es gibt kein Transkript."
        JarvisLanguage.FRENCH ->
            "Aperçu local. En mode OpenRouter direct, Envoi permet d'analyser au maximum 4 images de la vidéo. Ni la vidéo ni son audio ne sont transférés; pas de transcription."
        JarvisLanguage.SPANISH ->
            "Vista previa local. En modo OpenRouter directo, Enviar permite analizar hasta 4 fotogramas. No se suben el vídeo original ni su audio y no hay transcripción."
        JarvisLanguage.ITALIAN ->
            "Anteprima locale. In modalità OpenRouter diretta, Invia permette di analizzare fino a 4 fotogrammi. Il video originale e l'audio non vengono caricati; nessuna trascrizione."
        JarvisLanguage.POLISH ->
            "Podgląd lokalny. Przycisk Wyślij pozwala AI przeanalizować do 4 klatek w trybie bezpośrednim OpenRouter. Film ani dźwięk nie są przesyłane; brak transkrypcji."
    }
}
