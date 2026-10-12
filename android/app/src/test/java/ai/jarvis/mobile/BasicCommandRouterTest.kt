package ai.jarvis.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class BasicCommandRouterTest {
    @Test fun hungarianNavigation() {
        assertEquals(BasicAction.OPEN_GLUCOSE, BasicCommandRouter.classify("Jarvis, nyisd meg a vércukornaplót"))
        assertEquals(BasicAction.OPEN_INVOICES, BasicCommandRouter.classify("Kérlek számlázás"))
        assertEquals(BasicAction.OPEN_EMAIL, BasicCommandRouter.classify("E-mail küldés"))
        assertEquals(BasicAction.OPEN_MESSAGES, BasicCommandRouter.classify("WhatsApp üzeneteim"))
        assertEquals(BasicAction.OPEN_MESSAGES, BasicCommandRouter.classify("Messenger"))
        assertEquals(BasicAction.OPEN_PERMISSIONS, BasicCommandRouter.classify("Engedélyek"))
        assertEquals(BasicAction.OPEN_PERMISSIONS, BasicCommandRouter.classify("Nyisd meg a beállításokat"))
        assertEquals(BasicAction.OPEN_NOTES, BasicCommandRouter.classify("Telefon jegyzetei"))
        assertEquals(BasicAction.OPEN_MAP, BasicCommandRouter.classify("Térkép"))
        assertEquals(BasicAction.OPEN_CALENDAR, BasicCommandRouter.classify("Naptár"))
        assertEquals(BasicAction.OPEN_ALARMS, BasicCommandRouter.classify("Ébresztő"))
        assertEquals(BasicAction.OPEN_DIALER, BasicCommandRouter.classify("Telefon"))
    }
    @Test fun unknownCommandsDoNotPretendToSucceed() {
        assertEquals(BasicAction.UNKNOWN, BasicCommandRouter.classify(" "))
        assertEquals(BasicAction.UNKNOWN, BasicCommandRouter.classify("Mennyi a legfrissebb tőzsdei árfolyam?"))
        assertEquals(BasicAction.UNKNOWN, BasicCommandRouter.classify("Mit tudsz a vércukornaplóról?"))
        assertEquals(BasicAction.UNKNOWN, BasicCommandRouter.classify("Hogyan működik a számlázás?"))
        assertEquals(BasicAction.UNKNOWN, BasicCommandRouter.classify("What are Gmail permissions?"))
        assertEquals(BasicAction.UNKNOWN, BasicCommandRouter.classify("Jarvis, mit tudsz a vércukorról?"))
        assertEquals(BasicAction.UNKNOWN, BasicCommandRouter.classify("Jarvis, ne nyisd meg a Gmailt"))
        assertEquals(BasicAction.UNKNOWN, BasicCommandRouter.classify("Tudsz emailt küldeni?"))
        assertEquals(BasicAction.UNKNOWN, BasicCommandRouter.classify("Meg tudod nyitni a naptárat?"))
        assertEquals(BasicAction.UNKNOWN, BasicCommandRouter.classify("Don't open the maps"))
    }
}
