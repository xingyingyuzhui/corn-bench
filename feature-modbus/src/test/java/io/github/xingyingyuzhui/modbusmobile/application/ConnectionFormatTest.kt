package io.github.xingyingyuzhui.modbusmobile.application

import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionFormatTest {
    @Test
    fun wireLabelUsesBaudAndFraming() {
        assertEquals("9600 8N1", SerialSettings().wireLabel())
        assertEquals(
            "19200 8E1",
            wireLabel(19200, 8, Parity.EVEN, 1),
        )
        assertEquals("115200 7O2", wireLabel(115200, 7, Parity.ODD, 2))
    }

    @Test
    fun presetsMatchOnlyTheWrittenTrio() {
        assertEquals(SerialPreset.N9600, matchingPreset(9600, 8, Parity.NONE, 1))
        assertEquals(SerialPreset.N19200, matchingPreset(19200, 8, Parity.NONE, 1))
        assertEquals(SerialPreset.N115200, matchingPreset(115200, 8, Parity.NONE, 1))
        assertNull(matchingPreset(19200, 8, Parity.EVEN, 1))
        assertNull(matchingPreset(9600, 7, Parity.NONE, 1))
        assertNull(matchingPreset(38400, 8, Parity.NONE, 1))
    }

    @Test
    fun connectActionMatchesTheButtonSentence() {
        assertEquals("连接为主站 · 9600 8N1", connectAction("主站", "9600 8N1"))
    }

    @Test
    fun timeoutAndGapMustBePositiveIntegers() {
        assertTrue(validateTimeoutGap("1000", "20").ok)
        assertTrue(validateTimeoutGap("1", "1").ok)
        assertTrue(validateTimeoutGap("120000", "20").ok)
        assertEquals(InputError.TIMEOUT_RANGE, validateTimeoutGap("0", "20").timeoutError)
        assertEquals(InputError.TIMEOUT_RANGE, validateTimeoutGap("120001", "20").timeoutError)
        assertEquals(InputError.TIMEOUT_RANGE, validateTimeoutGap("1.5", "20").timeoutError)
        assertEquals(InputError.TIMEOUT_RANGE, validateTimeoutGap("", "20").timeoutError)
        assertEquals(InputError.GAP_POSITIVE, validateTimeoutGap("1000", "0").gapError)
        assertEquals(InputError.GAP_POSITIVE, validateTimeoutGap("1000", "-3").gapError)
        assertEquals(InputError.GAP_POSITIVE, validateTimeoutGap("1000", "x").gapError)
        assertTrue(validateTimeoutGap(" 1000 ", " 20 ").ok)
    }

    @Test
    fun modiconAddressZeroIsTheDocumentedBase() {
        assertEquals("40001", modiconLabel(RegisterArea.HOLDING_REGISTER, 0))
        assertEquals("40002", modiconLabel(RegisterArea.HOLDING_REGISTER, 1))
        assertEquals("30001", modiconLabel(RegisterArea.INPUT_REGISTER, 0))
        assertEquals("00001", modiconLabel(RegisterArea.COIL, 0))
        assertEquals("10001", modiconLabel(RegisterArea.DISCRETE_INPUT, 0))
    }
}
