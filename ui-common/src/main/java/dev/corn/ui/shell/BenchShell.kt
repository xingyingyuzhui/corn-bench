package dev.corn.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.corn.ui.mode.BrandMarkOrSwitcher
import dev.corn.ui.theme.BenchRadius
import dev.corn.ui.theme.BenchSize
import dev.corn.ui.theme.BenchType
import dev.corn.ui.theme.LocalBenchColors

/**
 * 与被投影元素同心的阴影：按岛的圆角逐层外扩（每层半径 = [radius] + 外扩量），阴影轮廓永远贴合圆角。
 * 不用 Compose 的 `shadow()`：它的 spot 阴影在圆角处有偏移和拉伸。（做法沿用 Corn Modbus。）
 */
fun Modifier.islandShadow(radius: Dp, dark: Boolean): Modifier = drawBehind {
    val total = if (dark) 0.55f else 0.16f
    val base = if (dark) Color.Black else Color(0xFF1C1A15)
    val layers = 28
    val extent = 14.dp.toPx()
    val dy = 10.dp.toPx()
    val perLayer = 1f - Math.pow((1f - total).toDouble(), 1.0 / layers).toFloat()
    for (i in 0 until layers) {
        val grow = extent * (1f - 2f * i / (layers - 1))
        val r = (radius.toPx() + grow).coerceAtLeast(0f)
        drawRoundRect(
            color = base.copy(alpha = perLayer),
            topLeft = Offset(-grow, -grow + dy),
            size = Size(size.width + 2 * grow, size.height + 2 * grow),
            cornerRadius = CornerRadius(r),
        )
    }
}

/**
 * dock 底边到导航 inset 上沿的间距（沿用 Corn Modbus 规则 #9：贴近手势条上沿约 8–12dp）。
 * 手势导航 inset ≤ 32dp 时取 0；三键导航取 8；没有导航 inset 时留 12。
 */
fun dockBottomGap(navBottom: Dp): Dp = when {
    navBottom <= 0.dp -> 12.dp
    navBottom <= 32.dp -> 0.dp
    else -> 8.dp
}

data class BenchNavItem(val key: String, val label: String, val icon: ImageVector)

const val BenchDockTag = "bench-dock"
const val BenchRailTag = "bench-rail"

/** 竖屏浮动 dock（Flashing 底部岛的尺寸：高 68、圆角 28、内边距 6、选中块 22）。 */
@Composable
fun BenchFloatingDock(
    items: List<BenchNavItem>,
    selected: String?,
    onSelect: (BenchNavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBenchColors.current
    val shape = RoundedCornerShape(BenchRadius.island)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .testTag(BenchDockTag)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .height(BenchSize.dock)
                .islandShadow(BenchRadius.island, colors.isDark)
                .clip(shape)
                .background(colors.island)
                .border(1.dp, colors.line, shape)
                .padding(BenchRadius.islandPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                NavCell(item, item.key == selected, { onSelect(item) }, Modifier.weight(1f).fillMaxHeight(), 21.dp, 11)
            }
        }
    }
}

/**
 * 横屏侧栏：顶部是品牌切换器（外壳提供时），下面是各页入口。
 */
@Composable
fun BenchSideRail(
    items: List<BenchNavItem>,
    selected: String?,
    onSelect: (BenchNavItem) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable ColumnScope.() -> Unit = { BrandMarkOrSwitcher() },
) {
    val colors = LocalBenchColors.current
    val shape = RoundedCornerShape(BenchRadius.xl)
    Column(
        modifier
            .testTag(BenchRailTag)
            .width(BenchSize.rail)
            .fillMaxHeight()
            .islandShadow(BenchRadius.xl, colors.isDark)
            .clip(shape)
            .background(colors.island)
            .border(1.dp, colors.line, shape)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        header()
        Spacer(Modifier.weight(1f))
        items.forEach { item ->
            NavCell(item, item.key == selected, { onSelect(item) }, Modifier.size(BenchSize.railItem), 20.dp, 10)
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun NavCell(item: BenchNavItem, active: Boolean, onClick: () -> Unit, modifier: Modifier, icon: Dp, label: Int) {
    val colors = LocalBenchColors.current
    val tint = if (active) colors.accent else colors.muted
    Column(
        modifier
            .clip(RoundedCornerShape(BenchRadius.islandItem))
            .background(if (active) colors.accentContainer else Color.Transparent)
            .semantics {
                role = Role.Tab
                this.selected = active
            }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(icon))
        Text(item.label, color = tint, fontSize = label.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
    }
}

/**
 * 品牌页头（Flashing `.top` 的尺寸：左右 18、上 6 下 10、间距 10、标题 19 Bold、副标题 11.5）。
 * 左上角在外壳里是模式切换器，否则是玉米图标。只给模式根页用；二级页用各模块自己的返回页头。
 */
@Composable
fun BenchTopBar(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    showBrand: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = LocalBenchColors.current
    Row(
        modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showBrand) BrandMarkOrSwitcher()
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.text, style = BenchType.headline, maxLines = 1)
            subtitle?.let { Text(it, color = colors.muted, style = BenchType.caption, maxLines = 1) }
        }
        actions()
    }
}
