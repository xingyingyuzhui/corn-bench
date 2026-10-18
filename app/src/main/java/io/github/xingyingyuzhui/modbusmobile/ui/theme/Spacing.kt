package io.github.xingyingyuzhui.modbusmobile.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.xingyingyuzhui.modbusmobile.application.DensityMode

/**
 * 间距刻度（设计规范_v1 §2）。基准 2dp 网格；禁止 3、5、7、9、11、13dp。
 * page / gapS / gapM 随密度查表（§2.3，不再 ×0.75）；其余档位不随密度变（卡片内边距、触控区、按钮高度不变）。
 */
data class ModbusSpacing(
    /** space.page：屏幕左右边距；弹层水平内边距。标准 14 / 紧凑 12。 */
    val page: Dp,
    /** space.m：组内元素间距。标准 8 / 紧凑 6。 */
    val gapS: Dp,
    /** space.l：同屏组与组之间（卡片之间）。标准 10 / 紧凑 8。 */
    val gapM: Dp,
) {
    /** space.2xs = 2：图标与文字微调、表头文字内边距；不用于布局分组。 */
    val xxs: Dp get() = 2.dp

    /** space.xs = 4：标签与值、输入格与错误文字之间。 */
    val xs: Dp get() = 4.dp

    /** space.s = 6：chip 之间、badge 与文字之间、SectionLabel 与内容之间。 */
    val s: Dp get() = 6.dp

    /** space.xl = 12：卡片内边距、列表行水平内边距。 */
    val card: Dp get() = 12.dp

    /** space.2xl = 16：长表单（向导）分区之间。 */
    val section: Dp get() = 16.dp

    /** space.3xl = 24：空态块上下留白。 */
    val xxl: Dp get() = 24.dp

    companion object {
        val Comfortable = ModbusSpacing(page = 14.dp, gapS = 8.dp, gapM = 10.dp)
        val Compact = ModbusSpacing(page = 12.dp, gapS = 6.dp, gapM = 8.dp)

        fun of(mode: DensityMode): ModbusSpacing = when (mode) {
            DensityMode.COMFORTABLE -> Comfortable
            DensityMode.COMPACT -> Compact
        }
    }
}

val LocalSpacing = staticCompositionLocalOf { ModbusSpacing.Comfortable }

// §5.1 圆角（只允许这些）
/** radius.badge */
val BadgeRadius = 6.dp

/** radius.iconBlock：设置行图标底块、BitPicker 位格。 */
val IconBlockRadius = 8.dp

/** radius.field：输入格、ChoiceTile、SmallButton。 */
val FieldRadius = 10.dp

/** radius.row：设备行、设置行选中底、统计格、分段外框。新增常量。 */
val RowRadius = 12.dp

/** radius.button：主/次按钮、SessionStrip、菜单。 */
val ButtonRadius = 14.dp

/** radius.card：卡片、寄存器表、日志容器。 */
val CardRadius = 16.dp

/** radius.chip：chip（胶囊）。 */
val ChipRadius = 16.dp

/** radius.sheet：所有底部弹层顶角（InPageSheet/RowEntrySheet 22→24 在 CL-06 接入）。新增常量。 */
val SheetRadius = 24.dp

/** radius.island：Dock / Rail。 */
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
 * 尺寸 token（设计规范_v1 §8.1 尺寸表）。界面文件里的尺寸只从这里取，不写字面 .dp。
 */
object ModbusSize {
    /** size.touch = 48 */
    val touch = MinTouch

    /** size.button = 52：主/次按钮高。 */
    val button = 52.dp

    /** size.buttonCompact = 44：紧凑按钮高。 */
    val buttonCompact = 44.dp

    /** size.buttonSmall = 32：SmallButton 视觉高（触控补到 48）。 */
    val buttonSmall = 32.dp

    /** size.header = 52：页头高。 */
    val header = 52.dp

    /** size.sessionStrip = 56：会话条最小高。 */
    val sessionStrip = 56.dp

    /** size.dock = 64 */
    val dock = 64.dp

    /** size.rail = 62 */
    val rail = 62.dp

    /** size.icon.s = 14：行内小图标（✎、锁、警告）。 */
    val iconS = 14.dp

    /** size.icon.m = 16：页头图标钮内图标、结果条图标。 */
    val iconM = 16.dp

    /** size.icon.l = 20：设备图标、Rail 图标。 */
    val iconL = 20.dp

    /** size.icon.nav = 22：Dock 图标。 */
    val iconNav = 22.dp

    /** size.progress = 16：按钮内 loading 圈。 */
    val progress = 16.dp

    /** loading 圈描边 2dp（§8.1 size.progress 备注）。 */
    val progressStroke = 2.dp

    /** SmallButton 内 loading 圈 12dp（§6.1 表）。 */
    val progressSmall = 12.dp
}

/** 动效时长 token（设计规范_v1 §8.1），毫秒。 */
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
