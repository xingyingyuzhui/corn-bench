package io.github.xingyingyuzhui.modbusmobile.application.panel

/*
 * 曲线组件向导的表单（纯 Kotlin，输入框都是字符串）。一个系列的“数据点位”直接复用 [WidgetForm]（数值型）：
 * 数据源 / 换算 / 单位 / 轮询周期都由它校验；图例名称就是它的 name。
 */

enum class CurveField { SERIES, WINDOW, RETENTION, MAX_POINTS, LEFT_MIN, LEFT_MAX, RIGHT_MIN, RIGHT_MAX }

data class SeriesForm(
    val pointId: String,
    val point: WidgetForm,
    val colorIndex: Int = 0,
    val lineStyle: LineStyle = LineStyle.SOLID,
    val width: Int = 2,
    val axis: YAxis = YAxis.LEFT,
    val showPoints: Boolean = false,
    val fill: Boolean = false,
    val step: Boolean = false,
) {
    /** 图例名称。 */
    val name: String get() = point.name

    fun build(): FormResult = point.build("series", pointId)

    fun toSeries(): CurveSeries? {
        val p = build().widget?.point ?: return null
        return CurveSeries(p, colorIndex, lineStyle, width.coerceIn(1, 3), axis, showPoints, fill, step)
    }
}

class CurveResult(
    val widget: Widget?,
    val errors: Map<CurveField, FormError>,
    /** 数据点位不合法的系列下标（图例名为空也算）。 */
    val badSeries: Set<Int>,
) {
    val ok: Boolean get() = widget != null && errors.isEmpty() && badSeries.isEmpty()
}

data class CurveForm(
    val series: List<SeriesForm> = emptyList(),
    /** 时间窗口（秒）；null = 自定义，用 [customMinutes]。 */
    val windowSec: Int? = 60,
    val customMinutes: String = "",
    val sampling: SamplingMode = SamplingMode.EVERY_SAMPLE,
    val retentionHours: String = "1",
    val maxPoints: String = CurveConfig.DEFAULT_MAX_POINTS.toString(),
    val leftAuto: Boolean = true,
    val leftMin: String = "",
    val leftMax: String = "",
    val rightAuto: Boolean = true,
    val rightMin: String = "",
    val rightMax: String = "",
    val grid: Boolean = true,
    val legend: LegendPosition = LegendPosition.TOP,
    val gaps: GapStyle = GapStyle.HATCH,
    val cursor: Boolean = true,
    val spanColumns: Int = 2,
    val spanRows: Int = 1,
) {
    fun addSeries(pointId: String): CurveForm {
        if (series.size >= CurveConfig.MAX_SERIES) return this
        val used = series.map { it.colorIndex }.toSet()
        val color = (0 until CurveSeries.COLOR_COUNT).firstOrNull { it !in used } ?: (series.size % CurveSeries.COLOR_COUNT)
        val n = series.size + 1
        return copy(
            series = series + SeriesForm(
                pointId = pointId,
                point = WidgetForm(kind = WidgetKinds.NUMBER, name = "系列 $n"),
                colorIndex = color,
            ),
        )
    }

    fun updateSeries(index: Int, transform: (SeriesForm) -> SeriesForm): CurveForm =
        if (index !in series.indices) this else copy(series = series.toMutableList().also { it[index] = transform(it[index]) })

    fun removeSeries(index: Int): CurveForm =
        if (index !in series.indices) this else copy(series = series.filterIndexed { i, _ -> i != index })

    /** 这条轴（左 / 右）上第一个系列的单位，用来写“左 Y 轴 · ℃”。 */
    fun axisUnit(side: YAxis): String = series.firstOrNull { it.axis == side && it.point.unitLabel.isNotBlank() }?.point?.unitLabel.orEmpty()

    fun usesAxis(side: YAxis): Boolean = series.any { it.axis == side }

    /** 向导第 2 步「数据」的校验：坐标轴（在第 3 步「显示」里）先按自动算。 */
    fun buildData(widgetId: String): CurveResult = copy(leftAuto = true, rightAuto = true).build(widgetId)

    fun build(widgetId: String): CurveResult {
        val errors = LinkedHashMap<CurveField, FormError>()
        val seriesResults = series.map { it.build() }
        val bad = seriesResults.withIndex().filter { !it.value.ok }.map { it.index }.toSet()
        if (series.isEmpty()) errors[CurveField.SERIES] = FormError.REQUIRED
        if (series.size > CurveConfig.MAX_SERIES) errors[CurveField.SERIES] = FormError.TOO_MANY

        val window: Int? = windowSec ?: run {
            val t = customMinutes.trim()
            val v = t.toIntOrNull()
            when {
                t.isEmpty() -> { errors[CurveField.WINDOW] = FormError.REQUIRED; null }
                v == null -> { errors[CurveField.WINDOW] = if (t.toDoubleOrNull() != null) FormError.NOT_INTEGER else FormError.NOT_A_NUMBER; null }
                v !in 1..(CurveConfig.MAX_WINDOW_SEC / 60) -> { errors[CurveField.WINDOW] = FormError.OUT_OF_RANGE; null }
                else -> v * 60
            }
        }

        val retention: Int? = retentionHours.trim().let { t ->
            val v = t.toDoubleOrNull()
            when {
                t.isEmpty() -> { errors[CurveField.RETENTION] = FormError.REQUIRED; null }
                v == null || !v.isFinite() -> { errors[CurveField.RETENTION] = FormError.NOT_A_NUMBER; null }
                else -> {
                    val minutes = Math.round(v * 60).toInt()
                    if (minutes !in CurveConfig.MIN_RETENTION_MIN..CurveConfig.MAX_RETENTION_MIN) { errors[CurveField.RETENTION] = FormError.OUT_OF_RANGE; null } else minutes
                }
            }
        }
        if (window != null && retention != null && window > retention * 60) errors[CurveField.RETENTION] = FormError.EXCEEDS_RETENTION

        val points: Int? = maxPoints.trim().let { t ->
            val v = t.toIntOrNull()
            when {
                t.isEmpty() -> { errors[CurveField.MAX_POINTS] = FormError.REQUIRED; null }
                v == null -> { errors[CurveField.MAX_POINTS] = if (t.toDoubleOrNull() != null) FormError.NOT_INTEGER else FormError.NOT_A_NUMBER; null }
                v !in CurveConfig.MIN_MAX_POINTS..CurveConfig.MAX_MAX_POINTS -> { errors[CurveField.MAX_POINTS] = FormError.OUT_OF_RANGE; null }
                else -> v
            }
        }

        fun axis(auto: Boolean, min: String, max: String, fMin: CurveField, fMax: CurveField, side: YAxis): AxisRange {
            if (auto || !usesAxis(side)) return AxisRange()
            val lo = min.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
            val hi = max.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
            if (lo == null) errors[fMin] = if (min.isBlank()) FormError.REQUIRED else FormError.NOT_A_NUMBER
            if (hi == null) errors[fMax] = if (max.isBlank()) FormError.REQUIRED else FormError.NOT_A_NUMBER
            if (lo != null && hi != null && !(lo < hi)) errors[fMax] = FormError.RANGE_ORDER
            return AxisRange(false, lo, hi)
        }
        val left = axis(leftAuto, leftMin, leftMax, CurveField.LEFT_MIN, CurveField.LEFT_MAX, YAxis.LEFT)
        val right = axis(rightAuto, rightMin, rightMax, CurveField.RIGHT_MIN, CurveField.RIGHT_MAX, YAxis.RIGHT)

        if (errors.isNotEmpty() || bad.isNotEmpty() || window == null || retention == null || points == null) return CurveResult(null, errors, bad)
        val config = CurveConfig(
            series = series.mapNotNull { it.toSeries() },
            windowSec = window, sampling = sampling, retentionMinutes = retention, maxPoints = points,
            left = left, right = right, grid = grid, legend = legend, gaps = gaps, cursor = cursor,
        )
        val widget = Widget(
            id = widgetId, kind = WidgetKinds.CURVE, curve = config,
            spanColumns = 2, spanRows = spanRows.coerceIn(1, 2),
        )
        return CurveResult(widget, emptyMap(), emptySet())
    }

    /** 向导预览用：已经能成立的系列 + 当前的显示设置（别的字段不合法也不影响画图，窗口不合法就用 60 秒）。 */
    fun previewConfig(): CurveConfig? {
        val list = series.mapNotNull { it.toSeries() }
        if (list.isEmpty()) return null
        val result = build("preview")
        result.widget?.curve?.let { return it }
        val window = windowSec ?: customMinutes.trim().toIntOrNull()?.takeIf { it in 1..(CurveConfig.MAX_WINDOW_SEC / 60) }?.times(60) ?: 60
        return CurveConfig(
            series = list, windowSec = window, sampling = sampling, grid = grid, legend = legend, gaps = gaps, cursor = cursor,
        )
    }

    companion object {
        val WindowPresets = listOf(60, 300, 600, 3600)

        fun fromWidget(widget: Widget): CurveForm? {
            val c = widget.curve ?: return null
            if (!widget.isUsable) return null
            fun d(v: Double?) = v?.let { ValueCodec.format(it, null) }.orEmpty()
            val preset = c.windowSec.takeIf { it in WindowPresets }
            return CurveForm(
                series = c.series.mapNotNull { s ->
                    val f = WidgetForm.fromWidget(Widget("series", WidgetKinds.NUMBER, point = s.point)) ?: return@mapNotNull null
                    SeriesForm(s.point.id, f.copy(name = s.point.name), s.colorIndex, s.lineStyle, s.width, s.axis, s.showPoints, s.fill, s.step)
                },
                windowSec = preset,
                customMinutes = if (preset == null) (c.windowSec / 60).toString() else "",
                sampling = c.sampling,
                retentionHours = ValueCodec.format(c.retentionMinutes / 60.0, null).orEmpty(),
                maxPoints = c.maxPoints.toString(),
                leftAuto = c.left.auto, leftMin = d(c.left.min), leftMax = d(c.left.max),
                rightAuto = c.right.auto, rightMin = d(c.right.min), rightMax = d(c.right.max),
                grid = c.grid, legend = c.legend, gaps = c.gaps, cursor = c.cursor,
                spanColumns = 2, spanRows = widget.spanRows,
            )
        }
    }
}
