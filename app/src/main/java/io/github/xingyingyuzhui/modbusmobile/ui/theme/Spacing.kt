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
