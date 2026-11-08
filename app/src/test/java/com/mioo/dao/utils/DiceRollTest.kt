package com.mioo.dao.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiceRollTest {
    @Test fun signedResultsAndBoundsAreRecognized() {
        val text = "骰点 -3[-10,5] 和 2[-5,10] 以及 4[0,9]"
        val matches = DiceRoll.findTokens(text)
        assertEquals(listOf(DiceRollToken(-3, -10, 5), DiceRollToken(2, -5, 10),
            DiceRollToken(4, 0, 9)), matches.map { it.token })
        assertEquals("-3[-10,5]", text.substring(matches.first().start, matches.first().end))
        assertTrue(DiceRoll.containsDice("<b>-3<small>[-10,5]</small></b>"))
        assertFalse(DiceRoll.containsDice("-12[-10,5]"))
    }
}
