package dev.corn.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 密度档：间距按表取值，不按倍率乘。 */
enum class BenchDensity { COMFORTABLE, COMPACT }

/**
 * 间距（第 3 层，组件尺寸之一）。默认值取 Corn Flashing 的卡片节奏：页边距 16、卡片间 12、卡内边距 14。
 * page / group / s 随密度查表，其余不随密度变。
 */
data class BenchSpacing(
    /** 屏幕左右边距。 */
    val page: Dp,
    /** 卡片之间、组与组之间。 */
    val group: Dp,
    /** 组内元素之间。 */
    val s: Dp,
) {
    val xs: Dp get() = 4.dp

    /** 卡片内边距（Flashing CornCard）。 */
    val card: Dp get() = 14.dp

    val section: Dp get() = 24.dp
    val empty: Dp get() = 32.dp

    /** 最后一个元素底边到 dock 顶的距离。 */
    val dockGap: Dp get() = 16.dp

    companion object {
        val Comfortable = BenchSpacing(page = 16.dp, group = 12.dp, s = 8.dp)
        val Compact = BenchSpacing(page = 16.dp, group = 8.dp, s = 4.dp)

        fun of(density: BenchDensity): BenchSpacing = when (density) {
            BenchDensity.COMFORTABLE -> Comfortable
            BenchDensity.COMPACT -> Compact
        }
    }
}

val LocalBenchSpacing = staticCompositionLocalOf { BenchSpacing.Comfortable }

/**
 * 圆角（Flashing 的 8 / 12 / 16 / 20 / 24 五档 + 组件专用档）。
 * 嵌套元素同心：内层圆角 = 外层圆角 − 内边距（例如 dock 28 − 6 = 选中块 22）。
 */
object BenchRadius {
    val xs = 8.dp
    val s = 12.dp
    val m = 16.dp
    val l = 20.dp
    val xl = 24.dp

    /** 卡片（Flashing CornCard 18）。 */
    val card = 18.dp

    /** 按钮（Flashing CornButton 默认 16）。 */
    val button = 16.dp

    /** 浮动 dock / rail（Flashing 底部导航岛 28）。 */
    val island = 28.dp

    /** dock 内边距 6：选中块 = 28 − 6 = 22。 */
    val islandPadding = 6.dp
    val islandItem = island - islandPadding

    /** 底部面板顶角。 */
    val sheet = 24.dp

    /** 胶囊。 */
    val pill = 99.dp
}

/** 尺寸（第 3 层）。 */
object BenchSize {
    /** 所有可点元素的最小触控区。 */
    val touch = 48.dp

    /** 页头左上角玉米图标。 */
    val brandMark = 30.dp

    /** 玉米图标右侧 ▾ 的字号区。 */
    val brandChevron = 10.dp

    val iconButton = 36.dp
    val button = 52.dp
    val dock = 68.dp
    val rail = 64.dp
    val railItem = 50.dp

    /** 模式卡最小高。 */
    val modeCard = 96.dp
}

private val NoTrim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun style(size: TextUnit, line: TextUnit, weight: FontWeight, family: FontFamily? = null, spacing: TextUnit = TextUnit.Unspecified) =
    TextStyle(fontSize = size, lineHeight = line, fontWeight = weight, fontFamily = family, letterSpacing = spacing, lineHeightStyle = NoTrim)

/** 字号层级。数值取自 Flashing 页面（页头 19 Bold、副标题 11.5、正文 13、标签 11 / 600）。 */
object BenchType {
    val display = style(26.sp, 32.sp, FontWeight.Bold)
    val headline = style(19.sp, 26.sp, FontWeight.Bold)
    val title = style(17.sp, 24.sp, FontWeight.Bold)
    val titleS = style(15.sp, 20.sp, FontWeight.Bold)
    val body = style(13.sp, 18.sp, FontWeight.Normal)
    val bodyStrong = body.copy(fontWeight = FontWeight.SemiBold)
    val caption = style(11.5.sp, 16.sp, FontWeight.Normal)
    val label = style(11.sp, 16.sp, FontWeight.SemiBold, spacing = 0.8.sp)
    val button = style(15.sp, 20.sp, FontWeight.Bold)
    val mono = style(13.sp, 18.sp, FontWeight.Medium, FontFamily.Monospace)
    val numeric = style(22.sp, 28.sp, FontWeight.Bold, FontFamily.Monospace, (-0.5).sp)
}
