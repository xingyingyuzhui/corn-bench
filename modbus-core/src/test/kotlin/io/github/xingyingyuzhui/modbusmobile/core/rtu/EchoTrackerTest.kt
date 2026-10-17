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
    fun singleWriteFramesDoNotVoteInAuto() {
        // 没有回显的总线上，从站对 FC06 的应答和请求一模一样：会被当成回显吞掉，但不能因此判成“有回显”。
        val echo = tracker(EchoMode.AUTO)
        val fc06 = byteArrayOf(1, 6, 0, 0, 0, 42, 0, 0)
        repeat(3) {
            echo.arm(fc06, nowNs = 0)
            echo.consume()
        }
        assertNull(echo.detected, "FC05/06 不参与判定")
        repeat(3) {
            echo.arm(byteArrayOf(1, 5, 0, 0, 0xFF.toByte(), 0, 0, 0), nowNs = 0)
            echo.expire(1_000)
        }
        assertNull(echo.detected)
        // 读请求照常参与
        echo.arm(byteArrayOf(1, 3, 0, 0, 0, 1, 0, 0), nowNs = 0)
        echo.consume()
        echo.arm(byteArrayOf(1, 3, 0, 0, 0, 1, 0, 0), nowNs = 0)
        echo.consume()
        assertEquals(true, echo.detected)
    }

    @Test
    fun restartDeadlineCountsFromAfterTheWrite() {
        val echo = tracker(EchoMode.ON)
        echo.arm(byteArrayOf(1, 3, 0), nowNs = 0) // 截止 = 0 + 3 + 50
        echo.restartDeadline(nowNs = 1_000)        // 写调用 1000 才返回：截止 = 1053
        echo.expire(500)
        assertIs<EchoMatch.Full>(echo.match(windowOf(1, 3, 0)), "写返回后才开始等回显")
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
