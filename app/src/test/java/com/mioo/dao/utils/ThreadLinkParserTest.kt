package com.mioo.dao.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThreadLinkParserTest {

    @Test
    fun parsesDirectXIslandThreadUrls() {
        assertEquals(
            "61234567",
            ThreadLinkParser.parseXIslandThreadIdFromWebUrl("https://www.nmbxd1.com/t/61234567")
        )
        assertEquals(
            "61234567",
            ThreadLinkParser.parseXIslandThreadIdFromWebUrl("https://nmbxd.com/t/61234567?page=2#r9988")
        )
        assertEquals(
            "61234567",
            ThreadLinkParser.parseXIslandThreadIdFromWebUrl("https://www.nmbxd1.com/t/61234567.html")
        )
        assertEquals(
            "61234567",
            ThreadLinkParser.parseXIslandThreadIdFromWebUrl("https://www.nmbxd1.com/m/t/61234567")
        )
        assertEquals(
            "61234567",
            ThreadLinkParser.parseXIslandThreadIdFromWebUrl("https://www.nmbxd1.com/Forum/thread/id/61234567.html")
        )
        assertEquals(
            "123456",
            ThreadLinkParser.parseXIslandThreadIdFromWebUrl("https://nmbxd.com/t?id=123456")
        )
    }

    @Test
    fun unwrapsSearchEngineRedirectUrlsToXIslandThreads() {
        assertEquals(
            "61234567",
            ThreadLinkParser.parseXIslandThreadIdFromWebUrl(
                "https://www.google.com/url?q=https%3A%2F%2Fwww.nmbxd1.com%2Ft%2F61234567&sa=U"
            )
        )
        assertEquals(
            "61234567",
            ThreadLinkParser.parseXIslandThreadIdFromWebUrl(
                "https://duckduckgo.com/l/?uddg=https%3A%2F%2Fwww.nmbxd1.com%2Ft%2F61234567&rut=abc"
            )
        )
        val bingEncoded = "a1" + java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("https://www.nmbxd1.com/t/61234567".toByteArray())
        assertEquals(
            "61234567",
            ThreadLinkParser.parseXIslandThreadIdFromWebUrl(
                "https://cn.bing.com/ck/a?!&&p=123&u=$bingEncoded&ntb=1"
            )
        )
        assertEquals(
            "61234567",
            ThreadLinkParser.parseXIslandThreadIdFromWebUrl(
                "https://www.google.com/amp/s/www.nmbxd1.com/t/61234567"
            )
        )
    }

    @Test
    fun ignoresNonXIslandDomainsSearchPagesBoardLinksAndImages() {
        assertNull(
            ThreadLinkParser.parseXIslandThreadIdFromWebUrl("https://adnmb3.com/thread/123456")
        )
        assertNull(
            ThreadLinkParser.parseXIslandThreadIdFromWebUrl("https://nmb.fastmirror.org/t/123456")
        )
        assertNull(
            ThreadLinkParser.parseXIslandThreadIdFromWebUrl(
                "https://www.google.com/search?q=site%3Anmbxd1.com+%2Ft%2F61234567"
            )
        )
        assertNull(
            ThreadLinkParser.parseXIslandThreadIdFromWebUrl(
                "https://cn.bing.com/search?q=site%3Anmbxd1.com+61234567&first=11"
            )
        )
        assertNull(
            ThreadLinkParser.parseXIslandThreadIdFromWebUrl("https://www.nmbxd1.com/Home/Forum/showf/id/4.html")
        )
        assertNull(
            ThreadLinkParser.parseXIslandThreadIdFromWebUrl("https://www.nmbxd1.com/f/%E7%BB%BC%E5%90%88%E7%89%881")
        )
        assertNull(
            ThreadLinkParser.parseXIslandThreadIdFromWebUrl("https://www.nmbxd1.com/Forum/po/id/12345")
        )
        assertNull(
            ThreadLinkParser.parseXIslandThreadIdFromWebUrl("https://image.nmbxd1.com/t/61234567.jpg")
        )
        assertNull(
            ThreadLinkParser.parseXIslandThreadIdFromWebUrl("https://fake-nmbxd1.com/t/61234567")
        )
        assertNull(
            ThreadLinkParser.parseXIslandThreadIdFromWebUrl("intent://www.nmbxd1.com/t/61234567#Intent;end")
        )
    }
}
