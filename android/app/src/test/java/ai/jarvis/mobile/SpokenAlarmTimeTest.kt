package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class SpokenAlarmTimeTest {
    @Test fun sameOneTimeMorningSevenAcrossAllSevenLanguages() {
        val commands = mapOf(
            "hu" to "Állíts ébresztőt reggel hétre egyszeri alkalommal",
            "en" to "Set an alarm for seven in the morning, just once",
            "de" to "Stell einen Wecker auf sieben Uhr morgens",
            "fr" to "Mets un réveil à sept heures du matin",
            "es" to "Pon una alarma a las siete de la mañana",
            "it" to "Imposta una sveglia alle sette del mattino",
            "pl" to "Ustaw budzik na siódmą rano"
        )
        for ((language, command) in commands) {
            assertTrue(language, SpokenAlarmTime.isSetIntent(command))
            assertEquals(language, AlarmTime(7, 0), AlarmCommands.parseAlarmTime(command))
            assertEquals(language, JarvisNativeActions.SET_ALARM,
                JarvisNativeActions.identify(command))
            assertFalse(language, JarvisAiRouting.shouldAskAi(command))
        }
    }

    @Test fun numericFormsAndAmPmWorkInSevenLanguages() {
        val commands = listOf(
            "Állítsd be az ébresztőt 7:30-ra",
            "Set an alarm at 7:30 am",
            "Stell einen Wecker um 7:30 Uhr morgens",
            "Mets une alarme à 7:30 du matin",
            "Pon una alarma a las 7:30 de la mañana",
            "Imposta una sveglia alle 7:30 di mattina",
            "Ustaw budzik na 7:30 rano"
        )
        for (command in commands) {
            assertEquals(command, AlarmTime(7, 30), AlarmCommands.parseAlarmTime(command))
        }
        assertEquals(AlarmTime(19, 0),
            AlarmCommands.parseAlarmTime("Set an alarm for seven in the evening"))
        assertEquals(AlarmTime(19, 0),
            AlarmCommands.parseAlarmTime("Stell einen Wecker um sieben Uhr abends"))
        assertEquals(AlarmTime(19, 0),
            AlarmCommands.parseAlarmTime("Pon una alarma a las siete de la noche"))
        assertEquals(AlarmTime(7, 0),
            AlarmCommands.parseAlarmTime("Pon una alarma a las siete de la mañana"))
        assertEquals(AlarmTime(7, 30),
            AlarmCommands.parseAlarmTime("Stell einen Wecker auf halb acht morgens"))
    }

    @Test fun rejectUnsupportedRepeatDatesNegationAndQuestions() {
        val blocked = listOf(
            "Állíts ébresztőt reggel hétre minden nap",
            "Set an alarm at seven every day",
            "Stell einen Wecker um sieben Uhr jeden Tag",
            "Mets un réveil à sept heures tous les jours",
            "Pon una alarma a las siete cada día",
            "Imposta una sveglia alle sette ogni giorno",
            "Ustaw budzik na siódmą codziennie",
            "Állíts ébresztőt holnap reggel hétre",
            "Set an alarm for seven tomorrow",
            "Pon una alarma mañana a las siete",
            "Ne állíts ébresztőt reggel hétre",
            "Don't set an alarm for seven",
            "Do not set an alarm for seven",
            "Can you set an alarm at seven?",
            "Állíts ébresztőt reggel hétre majd hívj fel valakit"
        )
        for (command in blocked) {
            assertNull(command, AlarmCommands.parseAlarmTime(command))
            assertNotEquals(command, JarvisNativeActions.SET_ALARM,
                JarvisNativeActions.identify(command))
        }
        assertNull(AlarmCommands.parseAlarmTime("Nyisd meg az ébresztőket"))
        assertNull(AlarmCommands.parseAlarmTime("Set a seven point rating"))
        assertNull(AlarmCommands.parseAlarmTime("Ébresztő 27:99"))
    }

    @Test fun politeQuestionRemainsExplicitAndNotAnInformationalAlarmQuestion() {
        assertEquals(AlarmTime(7, 30),
            AlarmCommands.parseAlarmTime("Be tudnád állítani az ébresztőt 7:30-ra?"))
        assertNull(AlarmCommands.parseAlarmTime("Can you set an alarm at seven?"))
        assertNull(AlarmCommands.parseAlarmTime("Ne állíts ébresztőt 7:30-ra?"))
    }

    @Test fun noBareAlarmKeywordOpensAFalseTime() {
        for (command in listOf("Ébresztő", "Open the alarms", "Alarm app",
            "What is an alarm?", "Set an alarm", "Imposta una sveglia")) {
            assertNull(command, AlarmCommands.parseAlarmTime(command))
        }
        assertEquals(AlarmTime(19, 0), AlarmCommands.parseAlarmTime("Ébresztő este 7 órára"))
        assertEquals(AlarmTime(6, 30), AlarmCommands.parseAlarmTime("Ébresztő fél 7-re"))
    }
}
