package io.github.xingyingyuzhui.modbusmobile.core.rtu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class EchoTrackerTest {
    @Test
    fun autoDetectsAfterTwoHitsOrTwoMisses() {
        val echo = tracker(EchoMode.AUTO)
        assertNull(echo.detected)
        echo.arm(byteArrayOf(1, 2, 3), nowNs = 0)
        echo.consume()
        assertNull(echo.detected)
        echo.arm(byteArrayOf(1, 2, 3), nowNs = 0)
        echo.consume()
        assertEquals(true, echo.detected)

        val quiet = tracker(EchoMode.AUTO)
        quiet.arm(byteArrayOf(1, 2), nowNs = 0)
        quiet.expire(100)
        assertNull(quiet.detected)
        quiet.arm(byteArrayOf(1, 2), nowNs = 200)
        quiet.expire(400)
        assertEquals(false, quiet.detected)
        quiet.arm(byteArrayOf(9, 9), nowNs = 500)
        assertIs<EchoMatch.Idle>(quiet.match(windowOf(9, 9)))
    }

    @Test
    fun expireDisarmsWithoutMatchingLaterBytes() {
        val echo = tracker(EchoMode.ON)
        echo.arm(byteArrayOf(0x01, 0x06, 0x00), nowNs = 1_000)
        echo.expire(1_000 + 3 + 50)
        assertIs<EchoMatch.Idle>(echo.match(windowOf(0x01, 0x06, 0x00)))
    }

    @Test
    fun mismatchDisarmsImmediately() {
        val echo = tracker(EchoMode.ON)
        val frame = byteArrayOf(0x01, 0x06, 0x00, 0x00)
        echo.arm(frame, nowNs = 0)
        assertIs<EchoMatch.Partial>(echo.match(windowOf(0x01, 0x06)))
        assertIs<EchoMatch.Mismatch>(echo.match(windowOf(0x01, 0x03)))
        assertIs<EchoMatch.Idle>(echo.match(windowOf(0x01, 0x06, 0x00, 0x00)))
    }

    private fun tracker(mode: EchoMode) = EchoTracker(mode, wireNs = { bytes -> bytes.toLong() }, slackNs = 50)

    private fun windowOf(vararg bytes: Int) = object : RxWindow {
        override val size: Int = bytes.size
        override fun get(index: Int): Int = bytes[index]
    }
}
