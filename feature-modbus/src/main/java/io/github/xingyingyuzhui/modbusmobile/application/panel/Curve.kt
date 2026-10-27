package io.github.xingyingyuzhui.modbusmobile.application.panel

/*
 * 曲线组件的配置模型（稿面 b2-5 / b2-6）。纯 Kotlin。
 * 一条曲线组件 = 若干“系列”（每个系列是一个点位 + 样式）+ 时间窗口 / 取点 / 坐标轴 / 显示设置。
 */

enum class LineStyle { SOLID, DASH, DOT }

enum class YAxis { LEFT, RIGHT }

/** 取点方式：EVERY_SAMPLE = 每个有效读数一个点；SNAPSHOT = 所有系列按同一时刻取值（定时快照）。 */
enum class SamplingMode { EVERY_SAMPLE, SNAPSHOT }

enum class LegendPosition { TOP, BOTTOM, HIDDEN }

/** 缺测画法：HATCH = 断开 + 斜纹标出缺测时段；BREAK = 只断开。 */
enum class GapStyle { HATCH, BREAK }

/** 坐标轴范围：[auto] 时按当前数据算；否则用 [min]..[max]（保证 min < max）。 */
data class AxisRange(val auto: Boolean = true, val min: Double? = null, val max: Double? = null) {
    val isValid: Boolean get() = auto || (min != null && max != null && min.isFinite() && max.isFinite() && min < max)
}

/**
 * 一个系列。[colorIndex] 是 0..[COLOR_COUNT]-1 的色板序号（颜色值由界面按主题决定，文件里只存序号）；
 * [point] 的 id 在整个工程内唯一，轮询 / 样本历史都按它认。
 */
data class CurveSeries(
    val point: PointDefinition,
    val colorIndex: Int = 0,
    val lineStyle: LineStyle = LineStyle.SOLID,
    /** 线宽 1..3（dp）。 */
    val width: Int = 2,
    val axis: YAxis = YAxis.LEFT,
    val showPoints: Boolean = false,
    val fill: Boolean = false,
    val step: Boolean = false,
) {
    companion object {
        const val COLOR_COUNT = 8
    }
}

data class CurveConfig(
    val series: List<CurveSeries>,
    /** 时间窗口（秒）。 */
    val windowSec: Int = 60,
    val sampling: SamplingMode = SamplingMode.EVERY_SAMPLE,
    /** 内存里保留多久的样本（分钟）；退出 App 即丢，不落盘。 */
    val retentionMinutes: Int = 60,
    /** 每个系列最多保留多少个样本。 */
    val maxPoints: Int = DEFAULT_MAX_POINTS,
    val left: AxisRange = AxisRange(),
    val right: AxisRange = AxisRange(),
    val grid: Boolean = true,
    val legend: LegendPosition = LegendPosition.TOP,
    val gaps: GapStyle = GapStyle.HATCH,
    /** 点按 / 拖动图面显示游标读数。 */
    val cursor: Boolean = true,
) {
    val windowMs: Long get() = windowSec * 1000L

    /** 定时快照的间隔：各系列轮询周期的最大值，至少 1 秒（见 decisions D44）。 */
    val snapshotStepMs: Long get() = maxOf(1000L, series.maxOfOrNull { it.point.pollMs.toLong() } ?: 1000L)

    fun issues(): List<CurveIssue> {
        val list = LinkedHashSet<CurveIssue>()
        if (series.isEmpty()) list += CurveIssue.NO_SERIES
        if (series.size > MAX_SERIES) list += CurveIssue.TOO_MANY_SERIES
        if (series.map { it.point.id }.toSet().size != series.size) list += CurveIssue.DUPLICATE_POINT_ID
        if (series.any { it.colorIndex !in 0 until CurveSeries.COLOR_COUNT }) list += CurveIssue.BAD_COLOR
        if (series.any { it.width !in 1..3 }) list += CurveIssue.BAD_WIDTH
        if (series.any { it.point.validate().isNotEmpty() }) list += CurveIssue.BAD_POINT
        if (windowSec !in MIN_WINDOW_SEC..MAX_WINDOW_SEC) list += CurveIssue.WINDOW_RANGE
        if (retentionMinutes !in MIN_RETENTION_MIN..MAX_RETENTION_MIN) list += CurveIssue.RETENTION_RANGE
        if (windowSec > retentionMinutes * 60) list += CurveIssue.WINDOW_EXCEEDS_RETENTION
        if (maxPoints !in MIN_MAX_POINTS..MAX_MAX_POINTS) list += CurveIssue.POINTS_RANGE
        if (!left.isValid || !right.isValid) list += CurveIssue.AXIS_RANGE
        return list.toList()
    }

    companion object {
        const val MAX_SERIES = 8
        const val MIN_WINDOW_SEC = 10
        const val MAX_WINDOW_SEC = 24 * 3600
        const val MIN_RETENTION_MIN = 1
        const val MAX_RETENTION_MIN = 24 * 60
        const val DEFAULT_MAX_POINTS = 20_000
        const val MIN_MAX_POINTS = 100
        const val MAX_MAX_POINTS = 100_000
    }
}

enum class CurveIssue {
    NO_SERIES, TOO_MANY_SERIES, DUPLICATE_POINT_ID, BAD_COLOR, BAD_WIDTH, BAD_POINT, WINDOW_RANGE, RETENTION_RANGE,
    WINDOW_EXCEEDS_RETENTION, POINTS_RANGE, AXIS_RANGE,
}
