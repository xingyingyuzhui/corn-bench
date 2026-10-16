package io.github.xingyingyuzhui.modbusmobile.application.panel

import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleHistoryTest {
    private fun def(id: String = "a", address: Int = 0) =
        PointDefinition(id, id, 1, RegisterArea.HOLDING_REGISTER, address, DataType.U16)

    private fun tracked(d: PointDefinition = def(), retentionMs: Long = 60_000, maxPoints: Int = 1000) =
        mapOf(d.id to SampleHistory.Tracked(d, retentionMs, maxPoints))

    @Test fun onlyTrackedPointsAreRecorded() {
        val h = SampleHistory()
        h.record("a", 1_000, 1.0)
        assertEquals(0, h.count("a"))
        h.track(tracked())
        h.record("a", 1_000, 1.0)
        h.record("other", 1_000, 1.0)
        assertEquals(1, h.count("a"))
        assertFalse(h.isTracked("other"))
    }

    @Test fun readReturnsAscendingSamplesAndTheOneBeforeTheWindow() {
        val h = SampleHistory()
        h.track(tracked())
        listOf(1_000L to 1.0, 2_000L to 2.0, 3_000L to 3.0, 4_000L to 4.0).forEach { h.record("a", it.first, it.second) }
        val s = h.read("a", fromMs = 2_500)
        assertEquals(listOf(2_000L, 3_000L, 4_000L), s.times.toList())
        assertEquals(listOf(2.0, 3.0, 4.0), s.values.toList())
        assertEquals("窗口起点早于全部样本时全返回", 4, h.read("a", 0).size)
        assertEquals(0, h.read("missing").size)
    }

    @Test fun maxPointsDropsTheOldest() {
        val h = SampleHistory()
        h.track(tracked(maxPoints = 100))
        for (i in 0 until 250) h.record("a", i * 10L, i.toDouble())
        val s = h.read("a")
        assertEquals(100, s.size)
        assertEquals(150.0, s.values.first(), 0.0)
        assertEquals(249.0, s.values.last(), 0.0)
        assertTrue("时间保持升序", s.times.toList() == s.times.sorted())
    }

    @Test fun retentionDropsSamplesOlderThanTheLimit() {
        val h = SampleHistory()
        h.track(tracked(retentionMs = 10_000))
        for (i in 0..30) h.record("a", i * 1_000L, i.toDouble())
        val s = h.read("a")
        // 最新 30 000，保留 10 s：20 000 之前的丢掉（再多留紧挨边界的一个）
        assertTrue(s.times.first() >= 19_000)
        assertEquals(30_000L, s.times.last())
    }

    @Test fun failureMarkersAreRecordedOncePerRun() {
        val h = SampleHistory()
        h.track(tracked())
        h.record("a", 1_000, 5.0)
        h.recordFailure("a", 2_000)
        h.recordFailure("a", 3_000)
        h.recordFailure("a", 4_000)
        assertEquals(2, h.count("a"))
        h.record("a", 5_000, 6.0)
        h.recordFailure("a", 6_000)
        val s = h.read("a")
        assertEquals(4, s.size)
        assertTrue(s.isFailure(1))
        assertTrue(s.isFailure(3))
        assertFalse(s.isFailure(2))
    }

    @Test fun nonFiniteValuesAndBackwardsClockAreIgnored() {
        val h = SampleHistory()
        h.track(tracked())
        h.record("a", 2_000, 1.0)
        h.record("a", 3_000, Double.NaN)
        h.record("a", 3_000, Double.POSITIVE_INFINITY)
        h.record("a", 1_000, 9.0)
        h.recordFailure("a", 500)
        assertEquals(1, h.count("a"))
    }

    @Test fun changingTheDefinitionClearsThatPointButKeepsTheOthers() {
        val h = SampleHistory()
        val a = def("a", 0)
        val b = def("b", 1)
        h.track(mapOf("a" to SampleHistory.Tracked(a, 60_000, 100), "b" to SampleHistory.Tracked(b, 60_000, 100)))
        h.record("a", 1_000, 1.0)
        h.record("b", 1_000, 2.0)
        // a 的地址改了；b 没动
        h.track(mapOf("a" to SampleHistory.Tracked(a.copy(address = 5), 60_000, 100), "b" to SampleHistory.Tracked(b, 60_000, 100)))
        assertEquals(0, h.count("a"))
        assertEquals(1, h.count("b"))
        // 只改保留上限不清样本
        h.track(mapOf("b" to SampleHistory.Tracked(b, 120_000, 500)))
        assertEquals(1, h.count("b"))
        assertFalse("不再登记的点位被释放", h.isTracked("a"))
    }

    @Test fun shrinkingMaxPointsTrimsExistingSamples() {
        val h = SampleHistory()
        h.track(tracked(maxPoints = 1000))
        for (i in 0 until 500) h.record("a", i * 10L, i.toDouble())
        h.track(tracked(maxPoints = 100))
        assertEquals(100, h.count("a"))
    }

    @Test fun clearEmptiesEverything() {
        val h = SampleHistory()
        h.track(tracked())
        h.record("a", 1_000, 1.0)
        h.clear()
        assertEquals(0, h.count("a"))
        h.record("a", 2_000, 2.0)
        assertEquals(1, h.count("a"))
    }
}
