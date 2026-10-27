package io.github.xingyingyuzhui.modbusmobile.application.panel

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * 曲线的纯函数：把样本历史变成“可以画”的帧（连续线段 + 缺测时段 + 坐标轴范围）。不依赖 Android，可单测。
 */
object CurveMath {
    /** 一段连续的线（时间升序，没有缺测）。 */
    class Segment(val times: LongArray, val values: DoubleArray) {
        val size: Int get() = times.size
    }

    class Axis(val min: Double, val max: Double, val step: Double, val auto: Boolean) {
        val span: Double get() = max - min
    }

    class SeriesFrame(
        val series: CurveSeries,
        val segments: List<Segment>,
        /** 缺测时段（毫秒，闭区间，已按窗口裁剪）。 */
        val gaps: List<LongRange>,
        val latest: Double?,
        val min: Double?,
        val max: Double?,
        /** 光标取最近样本时允许的最大时间距离。 */
        val reachMs: Long,
    )

    class Frame(
        val fromMs: Long,
        val toMs: Long,
        val series: List<SeriesFrame>,
        val left: Axis?,
        val right: Axis?,
    ) {
        val hasData: Boolean get() = series.any { it.segments.isNotEmpty() }
    }

    /** 光标读数：每个系列离光标时间最近的样本；[value] 为空 = 这个时刻附近没有数据。 */
    class CursorValue(val series: CurveSeries, val timeMs: Long?, val value: Double?)

    const val GRID_INTERVALS = 3
    private const val DECIMATE_BUCKETS = 600

    /** 两个相邻样本间隔超过这个值，就认为中间没有采到数据。 */
    fun gapMs(pollMs: Int): Long = max(3L * pollMs, 3000L)

    fun frame(config: CurveConfig, nowMs: Long, read: (pointId: String, fromMs: Long) -> SampleHistory.Slice): Frame {
        val to = nowMs
        val from = nowMs - config.windowMs
        val frames = config.series.map { s ->
            val raw = read(s.point.id, from)
            val snapshot = config.sampling == SamplingMode.SNAPSHOT
            val gap = if (snapshot) 3L * config.snapshotStepMs else gapMs(s.point.pollMs)
            val samples = if (snapshot) resample(raw, from, to, config.snapshotStepMs, gap) else raw
            seriesFrame(s, samples, from, to, gap)
        }
        return Frame(from, to, frames, axis(config.left, frames, YAxis.LEFT), axis(config.right, frames, YAxis.RIGHT))
    }

    private fun axis(range: AxisRange, frames: List<SeriesFrame>, side: YAxis): Axis? {
        val mine = frames.filter { it.series.axis == side }
        if (mine.isEmpty()) return null
        if (!range.auto && range.min != null && range.max != null && range.min < range.max) {
            return Axis(range.min, range.max, (range.max - range.min) / GRID_INTERVALS, auto = false)
        }
        val lo = mine.mapNotNull { it.min }.minOrNull() ?: return null
        val hi = mine.mapNotNull { it.max }.maxOrNull() ?: return null
        return niceRange(lo, hi)
    }

    /** 自动坐标轴：给数据范围留 5% 余量，再取到“好看”的刻度；至少有一格高度。 */
    fun niceRange(minValue: Double, maxValue: Double): Axis {
        var lo = minValue
        var hi = maxValue
        if (hi - lo < 1e-12) {
            val pad = if (abs(lo) < 1e-12) 1.0 else abs(lo) * 0.1
            lo -= pad
            hi += pad
        } else {
            val pad = (hi - lo) * 0.05
            lo -= pad
            hi += pad
        }
        val step = niceStep((hi - lo) / GRID_INTERVALS)
        val niceLo = floor(lo / step + 1e-9) * step
        var niceHi = ceil(hi / step - 1e-9) * step
        if (niceHi - niceLo < step) niceHi = niceLo + step
        return Axis(round6(niceLo), round6(niceHi), step, auto = true)
    }

    private fun round6(v: Double) = Math.round(v * 1e6) / 1e6

    fun niceStep(raw: Double): Double {
        if (raw <= 0.0 || !raw.isFinite()) return 1.0
        val exp = floor(log10(raw))
        val base = 10.0.pow(exp)
        val f = raw / base
        val nice = when {
            f <= 1.0 + 1e-9 -> 1.0
            f <= 2.0 + 1e-9 -> 2.0
            f <= 2.5 + 1e-9 -> 2.5
            f <= 5.0 + 1e-9 -> 5.0
            else -> 10.0
        }
        return nice * base
    }

    /** 坐标轴刻度文字：小数位跟着刻度步长走（步长 0.25 → 2 位；步长 10 → 0 位）。 */
    fun axisLabel(value: Double, step: Double): String {
        val decimals = if (step >= 1.0 - 1e-9) 0 else min(6, ceil(-log10(step) - 1e-9).toInt().coerceAtLeast(0))
        return ValueCodec.format(if (abs(value) < 1e-9) 0.0 else value, decimals).orEmpty()
    }

    private class Builder {
        var times = LongArray(16)
        var values = DoubleArray(16)
        var size = 0
        fun add(t: Long, v: Double) {
            if (size == times.size) {
                times = times.copyOf(size * 2)
                values = values.copyOf(size * 2)
            }
            times[size] = t
            values[size] = v
            size++
        }

        fun build() = Segment(times.copyOf(size), values.copyOf(size))
    }

    internal fun seriesFrame(series: CurveSeries, samples: SampleHistory.Slice, from: Long, to: Long, gapMs: Long): SeriesFrame {
        val segments = ArrayList<Segment>()
        val gaps = ArrayList<LongRange>()
        var cur = Builder()
        var gapStart: Long? = null
        var lastGood: Long? = null
        fun finish() {
            if (cur.size > 0) segments += decimate(cur.build(), from, to)
            cur = Builder()
        }
        for (i in 0 until samples.size) {
            val t = samples.times[i]
            val v = samples.values[i]
            if (v.isNaN()) {
                finish()
                if (gapStart == null) gapStart = lastGood ?: t
            } else {
                val start = gapStart
                if (start != null) {
                    gaps += start..t
                    gapStart = null
                } else if (lastGood != null && t - lastGood > gapMs) {
                    finish()
                    gaps += lastGood..t
                }
                cur.add(t, v)
                lastGood = t
            }
        }
        finish()
        val tailStart = gapStart
        if (tailStart != null) gaps += tailStart..to
        else if (lastGood != null && to - lastGood > gapMs) gaps += lastGood..to
        val clipped = gaps.mapNotNull { g ->
            val s = max(g.first, from)
            val e = min(g.last, to)
            if (e > s) s..e else null
        }
        // 只算窗口里的数据（含左边那一个点）
        var lo: Double? = null
        var hi: Double? = null
        var latest: Double? = null
        for (seg in segments) {
            for (i in 0 until seg.size) {
                val inWindow = seg.times[i] >= from
                val lastBefore = !inWindow && i + 1 < seg.size && seg.times[i + 1] >= from
                if (!inWindow && !lastBefore) continue
                val v = seg.values[i]
                lo = if (lo == null) v else min(lo, v)
                hi = if (hi == null) v else max(hi, v)
            }
            if (seg.size > 0) latest = seg.values[seg.size - 1]
        }
        return SeriesFrame(series, segments, clipped, latest, lo, hi, gapMs)
    }

    /** 点太多时按时间桶保留每桶的第一个 / 最小 / 最大 / 最后一个（尖峰不丢）。 */
    fun decimate(segment: Segment, from: Long, to: Long, buckets: Int = DECIMATE_BUCKETS): Segment {
        if (segment.size <= buckets * 4) return segment
        val span = max(1L, to - from).toDouble()
        val keep = BooleanArray(segment.size)
        var i = 0
        while (i < segment.size) {
            val b = bucketOf(segment.times[i], from, span, buckets)
            var j = i
            var minIdx = i
            var maxIdx = i
            while (j + 1 < segment.size && bucketOf(segment.times[j + 1], from, span, buckets) == b) {
                j++
                if (segment.values[j] < segment.values[minIdx]) minIdx = j
                if (segment.values[j] > segment.values[maxIdx]) maxIdx = j
            }
            keep[i] = true; keep[j] = true; keep[minIdx] = true; keep[maxIdx] = true
            i = j + 1
        }
        val n = keep.count { it }
        val times = LongArray(n)
        val values = DoubleArray(n)
        var k = 0
        for (idx in 0 until segment.size) if (keep[idx]) {
            times[k] = segment.times[idx]
            values[k] = segment.values[idx]
            k++
        }
        return Segment(times, values)
    }

    private fun bucketOf(t: Long, from: Long, span: Double, buckets: Int): Int =
        (((t - from) / span) * buckets).toInt().coerceIn(-1, buckets)

    /**
     * 定时快照：在 [from, to] 内按 [stepMs] 的整数倍时刻取“当时最新的有效值”；那个值比 [gapMs] 还旧、
     * 或最新一条是失败标记，这一格记缺测（连续缺测只记一个标记）。第一个样本出现之前不记。
     */
    fun resample(source: SampleHistory.Slice, from: Long, to: Long, stepMs: Long, gapMs: Long): SampleHistory.Slice {
        if (source.size == 0 || stepMs <= 0) return SampleHistory.Slice.EMPTY
        val times = ArrayList<Long>()
        val values = ArrayList<Double>()
        var t = ceil(from.toDouble() / stepMs).toLong() * stepMs
        var i = -1
        while (t <= to) {
            while (i + 1 < source.size && source.times[i + 1] <= t) i++
            if (i >= 0) {
                val v = source.values[i]
                val stale = v.isNaN() || t - source.times[i] > gapMs
                val out = if (stale) Double.NaN else v
                if (!(out.isNaN() && values.isNotEmpty() && values.last().isNaN())) {
                    times += t
                    values += out
                }
            }
            t += stepMs
        }
        return SampleHistory.Slice(times.toLongArray(), values.toDoubleArray())
    }

    /** 光标在 [timeMs] 时每个系列的读数。 */
    fun cursor(frame: Frame, timeMs: Long): List<CursorValue> = frame.series.map { sf ->
        var bestT: Long? = null
        var bestV: Double? = null
        var bestD = Long.MAX_VALUE
        for (seg in sf.segments) {
            var lo = 0
            var hi = seg.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (seg.times[mid] < timeMs) lo = mid + 1 else hi = mid
            }
            for (idx in intArrayOf(lo - 1, lo)) {
                if (idx in 0 until seg.size) {
                    val d = abs(seg.times[idx] - timeMs)
                    if (d < bestD) { bestD = d; bestT = seg.times[idx]; bestV = seg.values[idx] }
                }
            }
        }
        if (bestT != null && bestD <= sf.reachMs && bestT >= frame.fromMs) CursorValue(sf.series, bestT, bestV) else CursorValue(sf.series, null, null)
    }

    /** 窗口长度的人话：10 秒、1 分钟、10 分钟、1 小时、90 分钟。 */
    fun windowLabelParts(windowSec: Int): Pair<Int, Char> = when {
        windowSec % 3600 == 0 -> windowSec / 3600 to 'h'
        windowSec % 60 == 0 -> windowSec / 60 to 'm'
        else -> windowSec to 's'
    }
}
