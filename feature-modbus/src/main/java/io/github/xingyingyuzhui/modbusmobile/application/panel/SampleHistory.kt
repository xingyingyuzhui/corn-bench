package io.github.xingyingyuzhui.modbusmobile.application.panel

/**
 * 曲线用的样本历史：只放内存（退出 App 即丢，不落盘、不后台采集），按点位 id 分别存成环形缓冲。
 *
 * - 值是工程值（已按换算 / 缩放算好）；时间是墙钟毫秒。
 * - 读取失败（超时 / 异常 / 无效值）记成“失败标记”（值为 NaN）：同一次连续失败只记第一次，
 *   画图时标记到下一个有效样本之间是缺测段。
 * - 只记录被 [track] 登记过的点位（曲线里用到的），其它点位不占内存；点位定义改了（地址 / 类型 / 换算…）会清掉它的旧样本。
 * - 线程安全：轮询协程写、界面读，全部在同一把锁里。
 */
class SampleHistory {
    /** 登记一个要记录的点位：[definition] 变了就清空旧样本；[retentionMs] / [maxPoints] 是保留上限。 */
    data class Tracked(val definition: PointDefinition, val retentionMs: Long, val maxPoints: Int)

    /** 一段样本（升序）。[values] 里 NaN = 失败标记。 */
    class Slice(val times: LongArray, val values: DoubleArray) {
        val size: Int get() = times.size
        fun isFailure(i: Int) = values[i].isNaN()

        companion object {
            val EMPTY = Slice(LongArray(0), DoubleArray(0))
        }
    }

    private class Ring(var definition: PointDefinition, var retentionMs: Long, var maxPoints: Int) {
        var times = LongArray(INITIAL)
        var values = DoubleArray(INITIAL)
        var head = 0
        var size = 0

        fun timeAt(i: Int) = times[(head + i) % times.size]
        fun valueAt(i: Int) = values[(head + i) % times.size]

        fun add(t: Long, v: Double) {
            if (size == times.size) {
                if (size < maxPoints) grow() else dropOldest(1)
            }
            val index = (head + size) % times.size
            times[index] = t
            values[index] = v
            size++
            trim(t)
        }

        private fun grow() {
            val capacity = minOf(maxOf(times.size * 2, INITIAL), maxOf(maxPoints, 1))
            val newTimes = LongArray(capacity)
            val newValues = DoubleArray(capacity)
            for (i in 0 until size) {
                newTimes[i] = timeAt(i)
                newValues[i] = valueAt(i)
            }
            times = newTimes
            values = newValues
            head = 0
        }

        fun dropOldest(n: Int) {
            val k = minOf(n, size)
            head = (head + k) % times.size
            size -= k
        }

        /** 丢掉超出保留时长、超出点数上限的最旧样本（至少保留一个，画线要有“窗口左边的那个点”）。 */
        fun trim(now: Long) {
            while (size > maxPoints && size > 1) dropOldest(1)
            while (size > 1 && timeAt(1) < now - retentionMs) dropOldest(1)
        }

        fun lastIsFailure() = size > 0 && valueAt(size - 1).isNaN()
    }

    private val lock = Any()
    private val rings = LinkedHashMap<String, Ring>()

    fun track(tracked: Map<String, Tracked>) = synchronized(lock) {
        rings.keys.retainAll(tracked.keys)
        for ((id, t) in tracked) {
            val ring = rings[id]
            if (ring == null || ring.definition != t.definition) {
                rings[id] = Ring(t.definition, t.retentionMs, t.maxPoints)
            } else {
                ring.retentionMs = t.retentionMs
                ring.maxPoints = t.maxPoints
                ring.trim(if (ring.size > 0) ring.timeAt(ring.size - 1) else 0L)
            }
        }
    }

    fun isTracked(pointId: String): Boolean = synchronized(lock) { pointId in rings }

    fun record(pointId: String, timeMs: Long, value: Double) = synchronized(lock) {
        if (!value.isFinite()) return@synchronized
        val ring = rings[pointId] ?: return@synchronized
        // 时钟被调回时丢弃这个样本，保证时间升序
        if (ring.size > 0 && timeMs < ring.timeAt(ring.size - 1)) return@synchronized
        ring.add(timeMs, value)
    }

    /** 记一次读取失败。同一段连续失败只记第一次。 */
    fun recordFailure(pointId: String, timeMs: Long) = synchronized(lock) {
        val ring = rings[pointId] ?: return@synchronized
        if (ring.lastIsFailure()) return@synchronized
        if (ring.size > 0 && timeMs < ring.timeAt(ring.size - 1)) return@synchronized
        ring.add(timeMs, Double.NaN)
    }

    fun count(pointId: String): Int = synchronized(lock) { rings[pointId]?.size ?: 0 }

    /** 时间 ≥ [fromMs] 的样本，外加它前面的最后一个（让线从窗口左边进来）。 */
    fun read(pointId: String, fromMs: Long = Long.MIN_VALUE): Slice = synchronized(lock) {
        val ring = rings[pointId] ?: return@synchronized Slice.EMPTY
        if (ring.size == 0) return@synchronized Slice.EMPTY
        // 二分找第一个 time ≥ from
        var lo = 0
        var hi = ring.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (ring.timeAt(mid) < fromMs) lo = mid + 1 else hi = mid
        }
        val start = maxOf(0, lo - 1)
        val n = ring.size - start
        Slice(LongArray(n) { ring.timeAt(start + it) }, DoubleArray(n) { ring.valueAt(start + it) })
    }

    fun clear() = synchronized(lock) { rings.values.forEach { it.size = 0; it.head = 0 } }
}

private const val INITIAL = 64
