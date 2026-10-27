package io.github.xingyingyuzhui.modbusmobile.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xingyingyuzhui.modbusmobile.ui.theme.IslandRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusSize

/** 手势导航时系统导航栏 inset 很矮（≤ 32dp，手势条本身在 inset 里）；三键导航是 48dp 的实心栏。 */
val GestureNavMaxInset = 32.dp

/**
 * dock 底边到导航 inset 上沿的间距。
 * 手势导航：手势条顶部约在 inset 上沿下方 12dp（实机截图量出：dock 到手势条上沿 26dp = 旧间距 14 + 12），
 * 所以间距取 0，dock 到手势条上沿约 12dp，落在老板要求的 8–12dp；三键导航：dock 离导航栏上沿 8dp。
 */
fun dockBottomGap(navBottom: Dp): Dp = when {
    navBottom <= 0.dp -> 12.dp // 没有导航 inset（全面屏隐藏导航栏等）：保持一点底边距
    navBottom <= GestureNavMaxInset -> 0.dp
    else -> 8.dp
}

/**
 * 稿面 `--shadow: 0 10px 30px rgba(0,0,0,.55)`（浅色 `0 10px 28px rgba(20,32,50,.16)`）。
 * 用与岛一致的圆角矩形逐层外扩（每层半径 = [radius] + 外扩量，同心）叠出来，阴影轮廓永远与 dock 圆角一致；
 * 不用 Compose 的 `shadow()`：它的 spot 阴影有偏移和拉伸，圆角处看起来不贴合。
 */
fun Modifier.islandShadow(radius: Dp, dark: Boolean): Modifier = drawBehind {
    val total = if (dark) 0.55f else 0.16f
    val base = if (dark) Color.Black else Color(0xFF142032)
    val layers = 28
    val extent = 14.dp.toPx()
    val dy = 10.dp.toPx()
    // 每层 alpha 使 layers 层叠加后中心恰为 total
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

const val DockTag = "dock"

@Composable
fun FloatingDock(
    current: String?,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(IslandRadius)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .testTag(DockTag)
                .fillMaxWidth(0.9f)
                .height(64.dp)
                .islandShadow(IslandRadius, colors.isDark)
                .clip(shape)
                .background(colors.island)
                .border(1.dp, colors.line, shape)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Destination.entries.forEach { destination ->
                DockItem(
                    destination = destination,
                    selected = current == destination.route,
                    onClick = { onSelect(destination) },
                    modifier = Modifier.weight(1f).height(52.dp),
                    iconSize = 22.dp,
                    labelSize = 10.5.sp,
                    radius = 18.dp,
                )
            }
        }
    }
}

@Composable
fun FloatingRail(
    current: String?,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(IslandRadius)
    Column(
        modifier
            .width(ModbusSize.rail)
            .fillMaxHeight()
            .islandShadow(IslandRadius, colors.isDark)
            .clip(shape)
            .background(colors.island)
            .border(1.dp, colors.line, shape)
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Destination.entries.forEach { destination ->
            DockItem(
                destination = destination,
                selected = current == destination.route,
                onClick = { onSelect(destination) },
                modifier = Modifier.size(50.dp),
                iconSize = 20.dp,
                labelSize = 9.5.sp,
                radius = 16.dp,
            )
        }
    }
}

@Composable
private fun DockItem(
    destination: Destination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    iconSize: androidx.compose.ui.unit.Dp,
    labelSize: androidx.compose.ui.unit.TextUnit,
    radius: androidx.compose.ui.unit.Dp,
) {
    val colors = LocalModbusColors.current
    val label = stringResource(destination.label)
    val tint = if (selected) colors.accentText else colors.dim
    Column(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(if (selected) colors.wash(colors.accent) else Color.Transparent)
            .semantics {
                role = Role.Tab
                this.selected = selected
            }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painterResource(destination.icon),
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
        Text(label, color = tint, fontSize = labelSize, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
