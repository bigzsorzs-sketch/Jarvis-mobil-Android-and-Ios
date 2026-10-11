package ai.jarvis.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class BasicCommandRouterTest {
    @Test fun hungarianNavigation() {
        assertEquals(BasicAction.OPEN_GLUCOSE, BasicCommandRouter.classify("Jarvis, nyisd meg a vércukornaplót"))
        assertEquals(BasicAction.OPEN_INVOICES, BasicCommandRouter.classify("Kérlek számlázás"))
        assertEquals(BasicAction.OPEN_EMAIL, BasicCommandRouter.classify("E-mail küldés"))
        assertEquals(BasicAction.OPEN_MAP, BasicCommandRouter.classify("Térkép"))
        assertEquals(BasicAction.OPEN_CALENDAR, BasicCommandRouter.classify("Naptár"))
        assertEquals(BasicAction.OPEN_ALARMS, BasicCommandRouter.classify("Ébresztő"))
        assertEquals(BasicAction.OPEN_DIALER, BasicCommandRouter.classify("Telefon"))
    }
    @Test fun unknownCommandsDoNotPretendToSucceed() {
        assertEquals(BasicAction.UNKNOWN, BasicCommandRouter.classify(" "))
        assertEquals(BasicAction.UNKNOWN, BasicCommandRouter.classify("Mennyi a legfrissebb tőzsdei árfolyam?"))
    }
}
