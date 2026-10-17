package io.github.xingyingyuzhui.modbusmobile.application.panel

/** 组件类型。文件里出现不认识的类型时原样保留（见 [Widget.raw]），不丢、不崩。 */
object WidgetKinds {
    const val NUMBER = "number"
    const val LAMP = "lamp"
    const val STATUS_TEXT = "status_text"
    const val PROGRESS = "progress"
    const val CURVE = "curve"
    const val GAUGE = "gauge"

    /** 可写组件（P8b）：开关 = 切换一个线圈 / 寄存器位；按钮 = 一键写入预设值。写设备永远经确认弹窗。 */
    const val SWITCH = "switch"
    const val BUTTON = "button"
    val known = setOf(NUMBER, LAMP, STATUS_TEXT, PROGRESS, CURVE, GAUGE, SWITCH, BUTTON)

    /** 会写设备的类型：编辑模式 / 向导预览里不可操作，从站模式下禁用。 */
    val writable = setOf(SWITCH, BUTTON)
}

enum class StateTone { GREEN, AMBER, RED, BLUE, GRAY }

/** 状态文本组件的取值映射：原始整数值 → 文字 + 颜色。 */
data class StateMapping(val value: Long, val label: String, val tone: StateTone = StateTone.BLUE)

/**
 * 面板上的一个组件。[raw] 非空表示“这个组件现在读不懂”（类型是更新版本才有的、或字段不合法）：
 * 界面不渲染它，但保存时把 [raw] 原样写回，等换回新版本还能用。
 */
data class Widget(
    val id: String,
    val kind: String,
    val title: String = "",
    val point: PointDefinition? = null,
    /** 占几列 / 几行（1..2）。 */
    val spanColumns: Int = 1,
    val spanRows: Int = 1,
    /** 指示灯：为“开”/“关”时显示的文字，空 = 用默认的“开”/“关”。 */
    val onLabel: String = "",
    val offLabel: String = "",
    /** 状态文本：取值映射；没有匹配的值显示“未定义（值）”。 */
    val states: List<StateMapping> = emptyList(),
    /** 按钮：卡片上的按钮文字（空 = “写入”）。 */
    val buttonLabel: String = "",
    /** 按钮：要写入的预设值（线圈 1 / 0；寄存器为数值，按点位类型编码，不做缩放换算）。 */
    val writeValue: Double? = null,
    /** 曲线组件的配置（[kind] == [WidgetKinds.CURVE] 时非空；此时 [point] 为空，点位在各系列里）。 */
    val curve: CurveConfig? = null,
    val raw: String? = null,
) {
    val isUsable: Boolean
        get() = raw == null && kind in WidgetKinds.known && when (kind) {
            WidgetKinds.CURVE -> curve != null && curve.series.isNotEmpty()
            else -> point != null && WidgetWrites.problem(this) == null
        }

    /** 这个组件要轮询的全部点位。按钮不读（它的点位只用来写），所以不进轮询。 */
    val points: List<PointDefinition>
        get() = when (kind) {
            WidgetKinds.CURVE -> curve?.series?.map { it.point }.orEmpty()
            WidgetKinds.BUTTON -> emptyList()
            else -> listOfNotNull(point)
        }

    /** 界面上显示的组件名：自定义标题 > 曲线的“系列名 / 系列名” > 点位名。 */
    val displayTitle: String
        get() = title.ifBlank { if (kind == WidgetKinds.CURVE) curve?.series?.joinToString(" / ") { it.point.name }.orEmpty() else point?.name.orEmpty() }

    /** 最少占几列（曲线至少 2 列才画得下图例和坐标）。 */
    val minColumns: Int get() = if (kind == WidgetKinds.CURVE) 2 else 1
}

data class Panel(
    val id: String,
    val name: String,
    /** 竖屏列数（2..4）。 */
    val columns: Int = 2,
    val widgets: List<Widget> = emptyList(),
)

data class Project(
    val id: String,
    val name: String,
    val panels: List<Panel> = emptyList(),
) {
    /** 所有点位（含重复引用），供轮询计划使用（P3）。 */
    val usablePoints: List<PointDefinition>
        get() = panels.flatMap { p -> p.widgets.filter { it.isUsable }.flatMap { it.points } }

    companion object {
        const val FORMAT_VERSION = 1
        const val DEFAULT_ID = "default"
        fun empty(name: String = "我的工程") = Project(DEFAULT_ID, name)
    }
}
