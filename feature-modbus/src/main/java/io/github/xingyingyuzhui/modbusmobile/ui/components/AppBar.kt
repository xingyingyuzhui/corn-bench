package io.github.xingyingyuzhui.modbusmobile.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalSpacing
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusSize
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusType

/**
 * 页头（§13.2 K0）：高 size.header 52，标题 headline 20/28，左缘就是 K0（页面 keyline，不再 +2，AL-02）；
 * 右侧图标钮可见 36（size.headerIconBox），触控补到 48。
 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = LocalModbusColors.current
    val spacing = LocalSpacing.current
    Row(
        modifier.fillMaxWidth().height(ModbusSize.header),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.s),
    ) {
        // 页头最左边是玉米标：在外壳里它同时是模式切换器（48dp 热区，不撑高 52dp 页头）。
        dev.corn.ui.mode.BrandMarkOrSwitcher()
        Text(title, color = colors.text, style = ModbusType.headline, modifier = Modifier.weight(1f))
        actions()
    }
}

/** 可见 36×36（size.headerIconBox）圆形图标钮；触控区域用 minimumInteractiveComponentSize 补到 48×48（页头本身高 52，不会变高）。 */
@Composable
fun HeaderIconButton(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .size(ModbusSize.headerIconBox)
            .clip(CircleShape)
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * 二级页页头（§13.2 页头前导图标，AL-03）。放在和正文同一个 K0 容器里，调用方**不要**再自己减边距。
 *
 * - 前导图标（返回 / 关闭）占一个 20dp（size.icon.m）的布局位，左缘 = K0，所以字形盒左缘落在 K0；
 * - 48dp 触控盒用 requiredSize 居中压在这个 20dp 位上，起点自然是 K0 − (48 − 20) ÷ 2 = K0 − 14，不用 offset / 负 padding；
 * - 标题左缘 = K0 + 20 + 12（space.card）= K0 + 32。
 * 触控盒左侧 14dp 落在父容器外：Compose 不对父容器外的部分做命中测试，那 14dp 点不到（贴近屏幕边缘，审稿时说明）。
 */
@Composable
fun SubPageHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    leadingDescription: String = androidx.compose.ui.res.stringResource(io.github.xingyingyuzhui.modbusmobile.R.string.cd_back),
    leadingTag: String = "sub-back",
    leadingIcon: @Composable (androidx.compose.ui.graphics.Color) -> Unit = { BackIcon(it, size = ModbusSize.iconM) },
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = LocalModbusColors.current
    val spacing = LocalSpacing.current
    Row(
        modifier.fillMaxWidth().height(ModbusSize.header),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.card),
    ) {
        Box(Modifier.size(ModbusSize.iconM), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .requiredSize(ModbusSize.touch)
                    .clip(CircleShape)
                    .semantics {
                        contentDescription = leadingDescription
                        role = Role.Button
                    }
                    .clickable(onClick = onBack)
                    .testTag(leadingTag),
                contentAlignment = Alignment.Center,
            ) { leadingIcon(colors.text) }
        }
        Text(title, color = colors.text, style = ModbusType.headline, modifier = Modifier.weight(1f).testTag("sub-title"))
        trailing()
    }
}
