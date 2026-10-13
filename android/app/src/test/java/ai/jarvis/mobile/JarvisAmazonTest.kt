package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisAmazonTest {
    @Test fun hungarianProductSearchDefaultsToUkMarketplace() {
        val cmd = JarvisAmazon.parse("az amazonon keress egy Costa kávé babot")
            as AmazonAction.Search
        assertEquals(AmazonMarketplace.UK, cmd.marketplace)
        assertEquals("Costa kávé babot", cmd.query)
        assertFalse(cmd.business)
        assertTrue(JarvisAmazon.searchUrl(cmd).startsWith("https://www.amazon.co.uk/s?k="))
    }

    @Test fun hungarianCoffeeSearchUsesEnglishProductTermsNotCaseSuffixes() {
        val request = JarvisAmazon.parse("keress az amazonon Costa kávé babot")
            as AmazonAction.Search
        assertEquals("Costa kávé babot", request.query)
        assertEquals("Costa coffee beans",
            JarvisAmazon.productKeywords(request.query, request.marketplace))
        assertTrue(JarvisAmazon.searchUrl(request).contains("Costa+coffee+beans"))
        assertEquals("Costa 1 kg coffee beans",
            JarvisAmazon.productKeywords("Costa 1 kg kávébabot", AmazonMarketplace.UK))
        assertEquals("Costa kávé babot",
            JarvisAmazon.productKeywords("Costa kávé babot", AmazonMarketplace.DE))
    }

    @Test fun amazonBusinessUsesSameExactProductQuery() {
        val cmd = JarvisAmazon.parse("keress Amazon Business Costa Coffee Signature Blend")
            as AmazonAction.Search
        assertTrue(cmd.business)
        assertEquals("Costa Coffee Signature Blend", cmd.query)
        assertEquals(AmazonMarketplace.UK, cmd.marketplace)
        assertEquals(AmazonAction.ChangeToBusiness(true),
            JarvisAmazon.parse("az amazon business alkalmazáson keresztül"))
    }

    @Test fun officialProductLinksAreDomainAndAsinRestricted() {
        val found = JarvisAmazon.productFromUrl("https://www.amazon.co.uk/dp/B012345678?tag=demo")
        assertEquals(AmazonMarketplace.UK, found?.marketplace)
        assertEquals("B012345678", found?.asin)
        assertNull(JarvisAmazon.productFromUrl("https://amazon.co.uk.bad.example/dp/B012345678"))
        assertNull(JarvisAmazon.productFromUrl("http://www.amazon.co.uk/dp/B012345678"))
        assertNull(JarvisAmazon.productFromUrl("https://www.amazon.co.uk.evil.com/dp/B012345678"))
    }

    @Test fun explicitJarvisAmazonPageCommandIsLocalNavigation() {
        assertEquals("Amazon", JarvisCapabilities.localPageToOpen("Nyisd meg az Amazon oldalt"))
        assertEquals("Amazon", JarvisCapabilities.localPageToOpen("Jarvis, nyisd meg az Amazon oldalt"))
        assertEquals("Amazon", JarvisCapabilities.localPageToOpen("Nyisd meg az Amazon menüt"))
        // A product search must not masquerade as a local menu request.
        assertNull(JarvisCapabilities.localPageToOpen("keress Amazonon Costa kávébabot"))
    }

    @Test fun noUnrequestedAmazonHandoff() {
        assertNull(JarvisAmazon.parse("navigálj Manchesterbe"))
        assertNull(JarvisAmazon.parse("milyen új emailek vannak?"))
        assertNull(JarvisAmazon.parse("Costa kávé babot"))
    }

    @Test fun explicitGermanStoreDoesNotChangeDefaultForOthers() {
        val german = JarvisAmazon.parse("keress amazon.de Costa kávébab")
            as AmazonAction.Search
        assertEquals(AmazonMarketplace.DE, german.marketplace)
        assertEquals(AmazonMarketplace.UK,
            (JarvisAmazon.parse("keress amazonon mosogatószert")
                as AmazonAction.Search).marketplace)
    }
}
