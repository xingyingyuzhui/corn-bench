package dev.corn.ui.mode

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.corn.ui.R
import dev.corn.ui.theme.BenchSize
import dev.corn.ui.theme.LocalBenchColors

/**
 * 外壳提供的“打开模式切换面板”。null = 不在 Corn Bench 外壳里（单测、预览、二级页），此时页头照旧只画图标、不可点。
 * 外壳只在模式根页提供；二级页不放切换器。
 */
val LocalModeSwitcher = staticCompositionLocalOf<(() -> Unit)?> { null }

const val BrandSwitcherTag = "brand-switcher"

/** 玉米品牌标（30dp）。 */
@Composable
fun CornMark(modifier: Modifier = Modifier, size: Dp = BenchSize.brandMark) {
    Image(painterResource(R.drawable.bench_corn_mark), contentDescription = null, modifier = modifier.size(size))
}

/**
 * 页头左上角的模式切换器：玉米图标 30dp + 小 ▾。
 *
 * 视觉块只有 30 + 2 + 10 = 42 × 30dp，布局上也只占这么大（页头高度、标题位置都不变）；
 * 48dp 热区用 requiredSize 居中压在视觉块上，向四周各溢出一点，不撑高页头。
 */
@Composable
fun BrandSwitcher(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String = "切换模式",
) {
    val colors = LocalBenchColors.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            CornMark()
            Text("▾", color = colors.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.size(BenchSize.brandChevron, 14.dp))
        }
        Box(
            Modifier
                .requiredSize(BenchSize.touch)
                .clip(RoundedCornerShape(12.dp))
                .semantics {
                    contentDescription = description
                    role = Role.Button
                }
                .clickable(onClick = onClick)
                .testTag(BrandSwitcherTag),
        )
    }
}

/** 有外壳切换器时画 [BrandSwitcher]，否则只画玉米图标（与旧版页头一致）。 */
@Composable
fun BrandMarkOrSwitcher(modifier: Modifier = Modifier, fallbackSize: Dp = BenchSize.brandMark) {
    val switcher = LocalModeSwitcher.current
    if (switcher != null) BrandSwitcher(switcher, modifier) else CornMark(modifier, fallbackSize)
}
