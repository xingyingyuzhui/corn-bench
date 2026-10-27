package io.github.xingyingyuzhui.modbusmobile.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.xingyingyuzhui.modbusmobile.application.DensityMode

/**
 * 间距刻度（设计规范_v2 §2，tokens_v2.json spacing）。两层：组件级 4dp 网格（xs 4 / s 8 / card 12），
 * 布局级 8dp 网格（page 16 / group 16 / section 24 / empty 32 / dockGap 16）。v1 的 2、6 两档和两个 v1 旧名已删除（CL-16 已迁到 s / group）。
 * page / group / s 随密度查表（§2.3）：标准 16 / 16 / 8，紧凑 16 / 8 / 4（屏幕边距不缩）；其余档位不随密度变。
 */
data class ModbusSpacing(
    /** space.page：屏幕左右边距 K0；Sheet 水平内边距。标准 16 / 紧凑 16。 */
    val page: Dp,
    /** space.group：卡片之间（纵向堆叠）、组与组之间、SectionLabel 上方。标准 16 / 紧凑 8。 */
    val group: Dp,
    /** space.s：组内元素（卡内行、并排按钮、chip 之间、badge 与文字）；面板网格卡片间距。标准 8 / 紧凑 4。 */
    val s: Dp,
) {
    /** space.xs = 4：标签与值、输入格与错误文字、图标与文字（≤16dp 图标）。 */
    val xs: Dp get() = 4.dp

    /** space.card = 12：卡片内边距、列表行水平内边距、Sheet 内行。 */
    val card: Dp get() = 12.dp

    /** space.section = 24：长表单（向导）分区之间；页头与第一组之间。 */
    val section: Dp get() = 24.dp

    /** space.empty = 32：空态块上下留白。 */
    val empty: Dp get() = 32.dp

    /** space.dockGap = 16：最后一个元素底边到 Dock 顶的距离（所有页统一）。 */
    val dockGap: Dp get() = 16.dp

    companion object {
        val Comfortable = ModbusSpacing(page = 16.dp, group = 16.dp, s = 8.dp)
        val Compact = ModbusSpacing(page = 16.dp, group = 8.dp, s = 4.dp)

        fun of(mode: DensityMode): ModbusSpacing = when (mode) {
            DensityMode.COMFORTABLE -> Comfortable
            DensityMode.COMPACT -> Compact
        }
    }
}

val LocalSpacing = staticCompositionLocalOf { ModbusSpacing.Comfortable }

// §5.1 圆角（v2，只允许这些）
/** radius.badge = 4（v1 / 代码 6）。 */
val BadgeRadius = 4.dp

/** radius.small = 8：SmallButton（32 高）、设置图标底块、BitPicker 位格、分段选中块。 */
val SmallRadius = 8.dp

/** radius.field = 12：输入格、FieldTile、ChoiceTile、统计格（代码 10）。 */
val FieldRadius = 12.dp

/** radius.row = 12：设备行、设置行选中底、分段外框。 */
val RowRadius = 12.dp

/** radius.button = 12：主/次按钮、SessionStrip、菜单、UndoBar（代码 14）。 */
val ButtonRadius = 12.dp

/** radius.card = 16：卡片、寄存器表、日志容器。 */
val CardRadius = 16.dp

/** radius.chip = 16：chip 一律胶囊（xs 28 高时实际 14，按“胶囊”豁免）。 */
val ChipRadius = 16.dp

/** radius.sheet = 24：底部弹层顶角。 */
val SheetRadius = 24.dp

/** radius.island = 24：Dock / Rail。 */
val IslandRadius = 24.dp

/** size.touch：所有可点元素最小触控区。 */
val MinTouch = 48.dp

// §5.2 描边（不受 2dp 网格限制）
/** stroke.hairline = 1：卡片描边、分隔线、chip 描边。 */
val StrokeHairline = 1.dp

/** stroke.control = 1.5：次按钮描边、焦点环、出错输入格、开关关闭轨道描边。 */
val StrokeControl = 1.5.dp

/** stroke.selected = 1.5：选中卡片描边（chip 选中用 [StrokeHairline]）。 */
val StrokeSelected = 1.5.dp

/**
 * 尺寸 token（tokens_v2.json size）。界面文件里的尺寸只从这里取，不写字面 .dp。
 */
object ModbusSize {
    /** size.touch = 48：所有可点元素最小触控区（宽和高都 ≥48）。 */
    val touch = MinTouch

    /** size.button = 52：主按钮高。 */
    val button = 52.dp

    /** size.buttonCompact = 44：紧凑按钮视觉高（触控补到 48）。 */
    val buttonCompact = 44.dp

    /** size.buttonSmall = 32：SmallButton 视觉高（触控补到 48）。 */
    val buttonSmall = 32.dp

    /** size.header = 52：页头高。 */
    val header = 52.dp

    /** size.headerIconBox = 36：页头图标钮可见圆（触控补到 48）。 */
    val headerIconBox = 36.dp

    /** size.sessionStrip = 56：会话条最小高。 */
    val sessionStrip = 56.dp

    /** size.dock = 64：Dock 高。 */
    val dock = 64.dp

    /** size.rail = 64：Rail 宽（dockStart = 12 + 64 + 12 = 88）。 */
    val rail = 64.dp

    /** size.tableHeader = 32：寄存器表表头高。 */
    val tableHeader = 32.dp

    /** size.tableRow = 40：寄存器表行高。 */
    val tableRow = 40.dp

    /** 横屏寄存器表行高 36（size.tableRow 备注“横屏 36 沿用”）。 */
    val tableRowLandscape = 36.dp

    /** size.chip = 32：chip 视觉高。 */
    val chip = 32.dp

    /** size.chipXs = 28：xs chip 视觉高（触控补到 48×48）。 */
    val chipXs = 28.dp

    /** size.segItem = 36：分段项高。 */
    val segItem = 36.dp

    /** 小号分段项高 32（size.segItem 备注“小号 32”）。 */
    val segItemSmall = 32.dp

    /** size.switch：开关 44×24。 */
    val switchWidth = 44.dp
    val switchHeight = 24.dp

    /** size.iconBlock = 32：设置行图标底块。 */
    val iconBlock = 32.dp

    /** size.fieldTile = 56：8 + 16（label）+ 24（值）+ 8。 */
    val fieldTile = 56.dp

    /** size.badgeHeight = 20：Badge 固定高 20（左右内边距 8）。 */
    val badgeHeight = 20.dp

    /** size.statusDot = 8：状态点。 */
    val statusDot = 8.dp

    /** size.changeDot = 8：寄存器值变化静态标记。 */
    val changeDot = 8.dp

    /** size.icon.s = 16：行内小图标盒。 */
    val iconS = 16.dp

    /** size.icon.m = 20：页头返回图标盒。 */
    val iconM = 20.dp

    /** size.icon.l = 20：设备图标、Rail 图标。 */
    val iconL = 20.dp

    /** size.icon.nav = 24：Dock 图标盒。 */
    val iconNav = 24.dp

    /** size.progress = 16：按钮内 loading 圈。 */
    val progress = 16.dp

    /** loading 圈描边 2dp（size.progress 备注 stroke 2dp）。 */
    val progressStroke = 2.dp

    /** SmallButton 内 loading 圈 12dp（规范 §6.1 表）。 */
    val progressSmall = 12.dp
}

/** 动效时长 token（tokens_v2.json motion），毫秒。 */
object ModbusMotion {
    const val INSTANT = 0
    const val FAST = 100
    const val STANDARD = 200
    const val ENTER = 250
    const val EXIT = 150
    const val FLASH = 2000

    /** 撤销条停留时长。 */
    const val UNDO = 5000L

    /** 结果条成功态自动收起（§6.9 / §4.5 “3s 后收起”）。 */
    const val SUCCESS_DISMISS = 3000L
}
