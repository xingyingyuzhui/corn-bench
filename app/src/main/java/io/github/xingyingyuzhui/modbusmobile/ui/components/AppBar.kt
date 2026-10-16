package io.github.xingyingyuzhui.modbusmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors

/** 稿面页头 .appbar：高 52，标题 20/700，右侧图标钮 36×36。 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = LocalModbusColors.current
    Row(
        modifier.fillMaxWidth().height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 稿面标题 x=16，页面左右边距是 14，所以再补 2。
        Text(title, color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 2.dp))
        actions()
    }
}

/** 36×36 圆形图标钮；触控区域用 minimumInteractiveComponentSize 补到 48×48（页头本身高 52，不会变高）。 */
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
            .size(36.dp)
            .clip(CircleShape)
            .background(androidx.compose.ui.graphics.Color.Transparent)
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

/** 二级页脚手架：← 返回 + 标题（稿面 b3-2）。返回钮的触控区域 48×48，页头高度仍是 52。 */
@Composable
fun SubPageHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = LocalModbusColors.current
    Row(
        modifier.fillMaxWidth().height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        HeaderIconButton(
            androidx.compose.ui.res.stringResource(io.github.xingyingyuzhui.modbusmobile.R.string.cd_back),
            onBack,
            Modifier.testTag("sub-back"),
        ) { BackIcon(colors.text) }
        Text(title, color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).testTag("sub-title"))
        trailing()
    }
}
