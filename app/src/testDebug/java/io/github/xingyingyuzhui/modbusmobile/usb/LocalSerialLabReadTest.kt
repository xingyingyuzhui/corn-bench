package io.github.xingyingyuzhui.modbusmobile.usb

import java.io.EOFException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LocalSerialLabReadTest {
    @Test fun idlePollReturnsZeroWithoutCallingRead() {
        var readCalled = false
        repeat(3) {
            assertEquals(0, readLabBytes(ByteArray(8), 1_000, { timeout ->
                assertEquals(1_000, timeout)
                false
            }, {
                readCalled = true
                error("Idle socket must not enter a blocking read")
            }))
        }
        assertFalse(readCalled)
    }

    @Test fun readablePollReadsRawBytes() {
        val dest = ByteArray(3)
        assertEquals(3, readLabBytes(dest, 1_000, { true }, {
            byteArrayOf(1, 3, 2).copyInto(it)
            3
        }))
        assertArrayEquals(byteArrayOf(1, 3, 2), dest)
    }

    @Test(expected = EOFException::class)
    fun peerHangupStillFailsRatherThanBecomingIdleTimeout() {
        readLabBytes(ByteArray(8), 1_000, { true }, { -1 })
    }
}
