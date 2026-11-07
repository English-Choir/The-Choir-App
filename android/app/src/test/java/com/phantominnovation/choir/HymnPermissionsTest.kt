package com.phantominnovation.choir

import org.junit.Assert.*
import org.junit.Test

class HymnPermissionsTest {
    @Test fun guestsCanAppendOrChooseButCannotRewriteClearOrRename() {
        val old = HymnPlan("1", text = "Come (Lord) [1].*\nJesus 🙏")
        assertTrue(isGuestHymnChange(old.title, old.text, old.copy(choices = mapOf(1 to "song"))))
        assertTrue(isGuestHymnChange(old.title, old.text, old.copy(text = old.text + "\nTrust in me")))
        for (text in listOf("", " ", "Replacement", "Come (Lord) [1].*", old.text + "suffix"))
            assertFalse(isGuestHymnChange(old.title, old.text, old.copy(text = text)))
        assertFalse(isGuestHymnChange(old.title, old.text, old.copy(title = "Renamed")))
        assertTrue(isGuestHymnChange(old.title, "", old))
    }
}
