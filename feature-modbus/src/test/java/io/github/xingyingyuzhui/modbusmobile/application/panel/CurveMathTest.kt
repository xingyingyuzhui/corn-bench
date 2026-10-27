package io.github.xingyingyuzhui.modbusmobile.application.panel

import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurveMathTest {
    private fun point(id: String, poll: Int = 1000, decimals: Int? = null, unit: String = "") = PointDefinition(
        id, id, 1, RegisterArea.HOLDING_REGISTER, 0, DataType.U16, conversion = Conversion(1.0, 0.0, decimals), unitLabel = unit, pollMs = poll,
    )

    private fun series(id: String, axis: YAxis = YAxis.LEFT, poll: Int = 1000) = CurveSeries(point(id, poll), axis = axis)

    private fun slice(vararg s: Pair<Long, Double>) = SampleHistory.Slice(s.map { it.first }.toLongArray(), s.map { it.second }.toDoubleArray())

    private val NaN = Double.NaN

    private fun frame(config: CurveConfig, now: Long, data: Map<String, SampleHistory.Slice>) =
        CurveMath.frame(config, now) { id, _ -> data[id] ?: SampleHistory.Slice.EMPTY }

    // ---- 线段 / 缺测 ----

    @Test fun continuousSamplesFormOneSegmentWithoutGaps() {
        val s = series("a")
        val f = CurveMath.seriesFrame(s, slice(0L to 1.0, 1000L to 2.0, 2000L to 3.0), 0, 2500, 3000)
        assertEquals(1, f.segments.size)
        assertEquals(3, f.segments[0].size)
        assertTrue(f.gaps.isEmpty())
        assertEquals(3.0, f.latest!!, 0.0)
        assertEquals(1.0, f.min!!, 0.0)
        assertEquals(3.0, f.max!!, 0.0)
    }

    @Test fun failureMarkerSplitsTheLineAndMakesAGapUntilTheNextGoodSample() {
        val s = series("a")
        val f = CurveMath.seriesFrame(s, slice(0L to 1.0, 1000L to 2.0, 2000L to NaN, 6000L to 3.0, 7000L to 4.0), 0, 8000, 3000)
        assertEquals(2, f.segments.size)
        assertEquals(listOf(1000L..6000L), f.gaps)
        assertEquals(2, f.segments[1].size)
    }

    @Test fun aTimeHoleLongerThanThreeCyclesIsAGapEvenWithoutAFailureMarker() {
        val s = series("a")
        val f = CurveMath.seriesFrame(s, slice(0L to 1.0, 1000L to 2.0, 20_000L to 3.0), 0, 21_000, CurveMath.gapMs(1000))
        assertEquals(2, f.segments.size)
        assertEquals(listOf(1000L..20_000L), f.gaps)
    }

    @Test fun staleTailAfterTheLastSampleIsAGapUpToNow() {
        val s = series("a")
        val f = CurveMath.seriesFrame(s, slice(0L to 1.0, 1000L to 2.0), 0, 30_000, CurveMath.gapMs(1000))
        assertEquals(listOf(1000L..30_000L), f.gaps)
    }

    @Test fun trailingFailureMarkerRunsTheGapToNow() {
        val s = series("a")
        val f = CurveMath.seriesFrame(s, slice(0L to 1.0, 1000L to NaN), 0, 5000, 3000)
        assertEquals(listOf(0L..5000L), f.gaps)
        assertEquals(1, f.segments.size)
    }

    @Test fun noSamplesIsNotAGap() {
        val f = CurveMath.seriesFrame(series("a"), SampleHistory.Slice.EMPTY, 0, 60_000, 3000)
        assertTrue(f.segments.isEmpty() && f.gaps.isEmpty())
        assertNull(f.latest)
    }

    @Test fun gapsAreClippedToTheWindow() {
        val s = series("a")
        val f = CurveMath.seriesFrame(s, slice(0L to 1.0, 1000L to NaN, 50_000L to 2.0), 10_000, 60_000, 20_000)
        assertEquals(listOf(10_000L..50_000L), f.gaps)
    }

    @Test fun rangeOnlyCountsTheWindowPlusTheOnePointBeforeIt() {
        val s = series("a")
        // 窗口 [10000, 20000]：0 和 1000 的巨大值在窗外，2000 紧挨窗口之前——只有它参与量程
        val f = CurveMath.seriesFrame(s, slice(0L to 1000.0, 1000L to 900.0, 9000L to 5.0, 11_000L to 6.0, 12_000L to 7.0), 10_000, 20_000, 60_000)
        assertEquals(5.0, f.min!!, 0.0)
        assertEquals(7.0, f.max!!, 0.0)
    }

    // ---- 抽稀 ----

    @Test fun decimateKeepsExtremesOfEveryBucket() {
        val n = 20_000
        val times = LongArray(n) { it.toLong() }
        val values = DoubleArray(n) { 10.0 }
        values[7_777] = 99.0 // 尖峰
        values[12_345] = -50.0
        val d = CurveMath.decimate(CurveMath.Segment(times, values), 0, n.toLong(), buckets = 100)
        assertTrue(d.size < n / 10)
        assertTrue(d.values.any { it == 99.0 })
        assertTrue(d.values.any { it == -50.0 })
        assertEquals(times.first(), d.times.first())
        assertEquals(times.last(), d.times.last())
        assertTrue("时间保持升序", d.times.toList() == d.times.sorted())
    }

    @Test fun decimateLeavesSmallSegmentsAlone() {
        val seg = CurveMath.Segment(longArrayOf(1, 2, 3), doubleArrayOf(1.0, 2.0, 3.0))
        assertTrue(CurveMath.decimate(seg, 0, 10) === seg)
    }

    // ---- 坐标轴 ----

    @Test fun niceStepPicksOneTwoTwoPointFiveFiveTen() {
        assertEquals(1.0, CurveMath.niceStep(0.9), 1e-12)
        assertEquals(2.0, CurveMath.niceStep(1.5), 1e-12)
        assertEquals(2.5, CurveMath.niceStep(2.4), 1e-12)
        assertEquals(5.0, CurveMath.niceStep(3.0), 1e-12)
        assertEquals(10.0, CurveMath.niceStep(7.0), 1e-12)
        assertEquals(0.05, CurveMath.niceStep(0.04), 1e-12)
    }

    @Test fun niceRangeCoversTheDataWithRoundBounds() {
        val a = CurveMath.niceRange(12.0, 28.0)
        assertTrue(a.min <= 12.0 && a.max >= 28.0)
        assertEquals(0.0, a.min % a.step, 1e-9)
        assertEquals(0.0, a.max % a.step, 1e-9)
        assertTrue(a.auto)
    }

    @Test fun niceRangeOfAFlatLineStillHasHeight() {
        val a = CurveMath.niceRange(5.0, 5.0)
        assertTrue(a.min < 5.0 && a.max > 5.0)
        val z = CurveMath.niceRange(0.0, 0.0)
        assertTrue(z.min < 0.0 && z.max > 0.0)
    }

    @Test fun axisLabelDecimalsFollowTheStep() {
        assertEquals("30", CurveMath.axisLabel(30.0, 10.0))
        assertEquals("0.5", CurveMath.axisLabel(0.5, 0.1))
        assertEquals("0.25", CurveMath.axisLabel(0.25, 0.05))
        assertEquals("0", CurveMath.axisLabel(1e-12, 1.0))
    }

    @Test fun manualAxisIsUsedAsIs() {
        val config = CurveConfig(listOf(series("a")), left = AxisRange(false, 10.0, 30.0))
        val f = frame(config, 10_000, mapOf("a" to slice(9_000L to 100.0)))
        assertEquals(10.0, f.left!!.min, 0.0)
        assertEquals(30.0, f.left!!.max, 0.0)
        assertTrue(!f.left!!.auto)
        assertNull("没有系列在右轴就没有右轴", f.right)
    }

    @Test fun twoAxesAreComputedFromTheirOwnSeries() {
        val config = CurveConfig(listOf(series("t"), series("p", YAxis.RIGHT)), windowSec = 60)
        val f = frame(config, 60_000, mapOf("t" to slice(10_000L to 20.0, 30_000L to 28.0), "p" to slice(10_000L to 0.5, 30_000L to 0.9)))
        assertTrue(f.left!!.min <= 20.0 && f.left!!.max >= 28.0)
        assertTrue(f.right!!.max <= 2.0 && f.right!!.min >= 0.0)
        assertTrue(f.hasData)
    }

    // ---- 定时快照 ----

    @Test fun resampleTakesTheLatestValueAtEveryStepAndMarksStaleOnes() {
        val src = slice(0L to 1.0, 1_000L to 2.0, 2_000L to 3.0, 30_000L to 4.0)
        val r = CurveMath.resample(src, 0, 31_000, 5_000, gapMs = 6_000)
        // 5000 → 3.0（距 2000 是 3000 ≤ 6000），10000 → 距 2000 为 8000 > 6000 = 缺测，之后连续缺测只记一个，30000 → 4.0
        assertEquals(listOf(0L, 5_000L, 10_000L, 30_000L), r.times.toList())
        assertEquals(1.0, r.values[0], 0.0)
        assertEquals(3.0, r.values[1], 0.0)
        assertTrue(r.values[2].isNaN())
        assertEquals(4.0, r.values[3], 0.0)
    }

    @Test fun resampleDoesNotInventPointsBeforeTheFirstSample() {
        val r = CurveMath.resample(slice(12_000L to 1.0), 0, 20_000, 5_000, 6_000)
        assertEquals(listOf(15_000L, 20_000L), r.times.toList())
    }

    @Test fun snapshotModeFrameUsesSharedTimestamps() {
        val config = CurveConfig(listOf(series("a"), series("b")), windowSec = 60, sampling = SamplingMode.SNAPSHOT)
        val f = frame(
            config, 60_000,
            mapOf(
                "a" to slice(0L to 1.0, 1_300L to 2.0, 2_700L to 3.0, 4_100L to 4.0),
                "b" to slice(500L to 10.0, 2_100L to 20.0, 3_900L to 30.0),
            ),
        )
        val ta = f.series[0].segments.flatMap { it.times.toList() }
        val tb = f.series[1].segments.flatMap { it.times.toList() }
        assertTrue(ta.all { it % 1000 == 0L })
        assertEquals("两个系列在同一批时刻取值", ta.intersect(tb.toSet()).sorted(), tb.filter { it in ta })
    }

    // ---- 游标 ----

    @Test fun cursorFindsTheNearestSampleWithinReach() {
        val config = CurveConfig(listOf(series("a")), windowSec = 60)
        val f = frame(config, 60_000, mapOf("a" to slice(10_000L to 1.0, 11_000L to 2.0, 12_000L to 3.0)))
        val v = CurveMath.cursor(f, 11_400)[0]
        assertEquals(11_000L, v.timeMs)
        assertEquals(2.0, v.value!!, 0.0)
    }

    @Test fun cursorInAGapOrBeyondReachHasNoValue() {
        val config = CurveConfig(listOf(series("a")), windowSec = 60)
        val f = frame(config, 60_000, mapOf("a" to slice(10_000L to 1.0, 11_000L to 2.0)))
        val far = CurveMath.cursor(f, 40_000)[0]
        assertNull(far.value)
        assertNull(far.timeMs)
    }

    @Test fun windowLabelParts() {
        assertEquals(1 to 'm', CurveMath.windowLabelParts(60))
        assertEquals(1 to 'h', CurveMath.windowLabelParts(3600))
        assertEquals(90 to 'm', CurveMath.windowLabelParts(5400))
        assertEquals(10 to 's', CurveMath.windowLabelParts(10))
        assertNotNull(CurveConfig(listOf(series("a"))).snapshotStepMs)
        assertEquals(1000L, CurveConfig(listOf(series("a", poll = 100))).snapshotStepMs)
        assertEquals(5000L, CurveConfig(listOf(series("a", poll = 5000))).snapshotStepMs)
    }
}
