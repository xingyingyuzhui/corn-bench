package io.github.xingyingyuzhui.modbusmobile.application.panel

/** 组件类型。文件里出现不认识的类型时原样保留（见 [Widget.raw]），不丢、不崩。 */
object WidgetKinds {
    const val NUMBER = "number"
    const val LAMP = "lamp"
    const val STATUS_TEXT = "status_text"
    const val PROGRESS = "progress"
    val known = setOf(NUMBER, LAMP, STATUS_TEXT, PROGRESS)
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
    val raw: String? = null,
) {
    val isUsable: Boolean get() = raw == null && point != null && kind in WidgetKinds.known
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
        get() = panels.flatMap { p -> p.widgets.filter { it.isUsable }.mapNotNull { it.point } }

    companion object {
        const val FORMAT_VERSION = 1
        const val DEFAULT_ID = "default"
        fun empty(name: String = "我的工程") = Project(DEFAULT_ID, name)
    }
}
