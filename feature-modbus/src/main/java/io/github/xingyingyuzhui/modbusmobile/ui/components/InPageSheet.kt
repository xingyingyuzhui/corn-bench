package io.github.xingyingyuzhui.modbusmobile.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalDockInsets
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors

/**
 * 页内浮层：遮罩 + 贴底面板（注意事项 #28：带输入框的界面不用系统对话框）。
 * 点遮罩、系统返回键都调 [onDismiss]；面板坐在悬浮 dock 上方，键盘弹出时跟着上移。
 * 调用方把它放在页面最外层的 Box 里，盖住整页。
 */
@Composable
fun InPageSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String = "in-page-sheet",
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalModbusColors.current
    BackHandler(onBack = onDismiss)
    val dock = LocalDockInsets.current
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    Box(modifier.fillMaxSize().testTag(tag)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (colors.isDark) 0.6f else 0.38f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .testTag("$tag-scrim"),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = dock.bottom)
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surface1)
                .border(1.dp, colors.line, shape)
                // 吃掉面板里的点击，不让它穿到遮罩上把浮层关掉。
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                // 没有 dock 时（编辑模式、全屏页）自己让开系统导航条；有 dock 时 dock.bottom 已经含导航条
                .then(
                    if (dock.bottom == 0.dp) {
                        Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                    } else {
                        Modifier.imePadding()
                    },
                )
                .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(colors.line))
            Text(title, color = colors.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("$tag-title"))
            content()
        }
    }
}
