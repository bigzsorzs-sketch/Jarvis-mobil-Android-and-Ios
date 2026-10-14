package ai.jarvis.mobile

import java.text.Normalizer
import java.util.Locale

/**
 * Small, offline speech-to-time interpreter shared by both typed and
 * hands-free commands. Seven Jarvis languages: HU, EN, DE, FR, ES, IT, PL.
 * Never ask the LLM to infer a clock time for a device side effect.
 */
internal object SpokenAlarmTime {
    fun normalize(raw: String): String =
        Normalizer.normalize(raw.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace("ł", "l").replace("ß", "ss")
            .replace(Regex("[^a-z0-9:. ]+"), " ")
            .trim().replace(Regex("\\s+"), " ")

    private val hourWords: Map<String, Int> = buildMap {
        fun insert(number: Int, vararg forms: String) {
            forms.forEach { put(it, number) }
        }
        insert(0, "nulla", "zero", "nul", "null", "cero", "zero")
        insert(1, "egy", "egyre", "one", "ein", "eins", "pierwsza", "pierwszej")
        insert(2, "ketto", "kettore", "ket", "ketre", "two", "zwei", "deux", "dos",
            "due", "dwa", "druga", "drugiej", "druga")
        insert(3, "harom", "haromra", "three", "drei", "trois", "tres", "tre",
            "trzy", "trzecia", "trzeciej")
        insert(4, "negy", "negyre", "four", "vier", "quatre", "cuatro", "quattro",
            "cztery", "czwarta", "czwartej")
        insert(5, "ot", "otre", "five", "funf", "cinq", "cinco", "cinque",
            "piec", "piata", "piatej")
        insert(6, "hat", "hatra", "six", "sechs", "seis", "sei",
            "szesc", "szosta", "szostej")
        insert(7, "het", "hetre", "seven", "sieben", "sept", "siete", "sette",
            "siedem", "siodma", "siodmej", "siodma")
        insert(8, "nyolc", "nyolcra", "eight", "acht", "huit", "ocho", "otto",
            "osiem", "osma", "osmej")
        insert(9, "kilenc", "kilencre", "nine", "neun", "neuf", "nueve", "nove",
            "dziewiec", "dziewiata", "dziewiatej")
        insert(10, "tiz", "tizre", "ten", "zehn", "dix", "diez", "dieci",
            "dziesiec", "dziesiata", "dziesiatej")
        insert(11, "tizenegy", "tizenegyre", "eleven", "elf", "onze", "once",
            "undici", "jedenascie", "jedenasta", "jedenastej")
        insert(12, "tizenketto", "tizenkettore", "twelve", "zwolf", "douze",
            "doce", "dodici", "dwanascie", "dwunasta", "dwunastej")
    }

    /** Support spoken integer hours, HH:mm and explicit local-language time units. */
    fun parse(raw: String): AlarmTime? {
        val clean = normalize(raw)
        val clock = Regex("""(?<!\d)(\d{1,2})\s*[:.]\s*(\d{1,2})(?!\d)""").find(clean)
        val numericHour = Regex(
            """(?<!\d)(\d{1,2})\s*(?:ora(?:ra|kor|t)?|uhr|heures?|horas?|ore|godzin[aeąęy]?)(?:\s+(\d{1,2})\s*(?:perc(?:re|et)?|min(?:uten|utes|utos)?))?"""
        ).find(clean)
        // Articles un/une/una precede alarm nouns in Romance languages.
        // Treat them as 01:00 only in explicit clock expressions.
        val romanceOne = Regex(
            """\b(?:a|alle|a las|a la|pour|para las)\s+(?:un|une|una|uno)\s*(?:heure|heures|hora|horas|ore)?\b"""
        ).find(clean)
        val prepositionHour = Regex(
            """\b(?:at|um|alle|a las|a la|pour|na|o)\s+(\d{1,2})(?!\d)"""
        ).find(clean)
        val magyarSuffix = Regex("""\b(\d{1,2})\s*(?:ra|re|kor)\b""").find(clean)
        var hour: Int
        var minute = 0
        when {
            clock != null -> {
                hour = clock.groupValues[1].toInt()
                minute = clock.groupValues[2].toInt()
            }
            numericHour != null -> {
                hour = numericHour.groupValues[1].toInt()
                minute = numericHour.groupValues[2].toIntOrNull() ?: 0
            }
            prepositionHour != null -> hour = prepositionHour.groupValues[1].toInt()
            romanceOne != null -> hour = 1
            magyarSuffix != null -> hour = magyarSuffix.groupValues[1].toInt()
            else -> {
                // Colloquial Hungarian "fél nyolc" and German "halb acht" = 07:30.
                val half = Regex("""\b(?:fel|halb)\s+([a-z]+)\b""").find(clean)
                if (half != null) {
                    val next = hourWords[half.groupValues[1]] ?: return null
                    if (next !in 1..12) return null
                    hour = next - 1
                    minute = 30
                } else {
                    // Avoid "one time" being mistaken for a clock hour.
                    val scrubbed = clean.replace(Regex("""\bone time\b"""), "")
                        .let { if (Regex("""\b(?:a las|las|a la) once\b""").containsMatchIn(it))
                            it else it.replace(Regex("""\bonce\b"""), "") }
                    val matches = scrubbed.split(' ').mapNotNull(hourWords::get).distinct()
                    if (matches.size != 1) return null
                    hour = matches.single()
                }
            }
        }
        if (hour !in 0..23 || minute !in 0..59) return null
        val pm = Regex(
            """\b(este|delutan|evening|afternoon|pm|abends|nachmittags|soir|apres midi|tarde|noche|sera|pomeriggio|wieczorem|po poludniu)\b"""
        ).containsMatchIn(clean)
        val am = Regex(
            """\b(reggel|hajnal|morning|am|morgens|fruh|matin|manana|mattina|rano)\b"""
        ).containsMatchIn(clean)
        if (pm && am) return null
        if (pm && hour in 1..11) hour += 12
        if (pm && hour == 0) hour = 12
        if (am && hour == 12) hour = 0
        return AlarmTime(hour, minute)
    }

    private val alarmWords = Regex(
        """\b(?:ebreszt[a-z]*|ebressz[a-z]*|kelts[a-z]*|alarm[a-z]*|wecker[a-z]*|reveil[a-z]*|sveglia[a-z]*|budzik[a-z]*|budzika[a-z]*|alarma[a-z]*|budz[a-z]*)\b"""
    )
    private val actionWords = Regex(
        """\b(?:allits[a-z]*|set|wake|weck[a-z]*|stell[a-z]*|richt[a-z]*|mets|met|regle[a-z]*|reveille[a-z]*|pon|poner|programa[a-z]*|despierta[a-z]*|imposta[a-z]*|svegliami|sveglia[a-z]*|ustaw[a-z]*|nastaw[a-z]*|obudz[a-z]*|zbudz[a-z]*)\b"""
    )

    fun isSetIntent(raw: String): Boolean {
        val n = normalize(raw).removePrefix("jarvis ").removePrefix("hey jarvis ")
        if (n.length !in 8..250 || '?' in raw) return false
        val spokenTimeCue = Regex(
            """\b(?:reggel|este|delutan|hajnal|fel|halb|morning|evening|afternoon|morgens|abends|matin|soir|manana|tarde|noche|mattina|sera|rano|uhr|heures?|horas?|ore|godzin[a-z]*|\d{1,2}(?::\d\d)?)\b"""
        ).containsMatchIn(n)
        return (alarmWords.containsMatchIn(n) &&
            (actionWords.containsMatchIn(n) || spokenTimeCue)) ||
            Regex("""\b(?:ebressz|kelts|wake me|weck mich|reveille moi|despiertame|svegliami|obudz mnie|zbudz mnie)\b""")
                .containsMatchIn(n)
    }

    /** Safety: avoid silently scheduling every day or interpreting date requests as next occurrence. */
    fun unsupported(raw: String): Boolean {
        val s = normalize(raw)
        if ('?' in raw) return true
        val negations = Regex(
            """\b(?:ne|nem|dont|do not|never|without|nicht|kein|keine|pas|jamais|no|nunca|non|senza|nie|bez)\b"""
        )
        val repeats = Regex(
            """\b(?:minden\s+nap|naponta|hetente|minden\s+het|every\s+day|daily|every\s+week|weekly|jeden\s+tag|taglich|tous\s+les\s+jours|chaque\s+jour|cada\s+dia|todos\s+los\s+dias|ogni\s+giorno|tutti\s+i\s+giorni|codziennie|kazdego\s+dnia)\b"""
        )
        val dates = Regex(
            """\b(?:holnap|holnaputan|jovo\s+heten|tomorrow|next\s+week|morgen|nachste\s+woche|demain|apres\s+demain|manana|proxima\s+semana|domani|settimana\s+prossima|jutro|pojutrze|nastepny\s+tydzien)\b"""
        )
        val multi = Regex(
            """\b(?:majd|aztan|and\s+then|then|ensuite|puis|despues|luego|danach|dopo|poi|potem)\b"""
        )
        return negations.containsMatchIn(s) || repeats.containsMatchIn(s) ||
            dates.containsMatchIn(s) || multi.containsMatchIn(s)
    }
}
