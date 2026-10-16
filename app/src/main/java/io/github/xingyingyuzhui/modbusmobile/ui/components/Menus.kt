package io.github.xingyingyuzhui.modbusmobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors

/** ⋮ 菜单的容器：设计令牌里的 surface1 底 + 1dp line 描边 + 圆角 14，不用 Material 默认的浅紫色底。稿面没有画菜单，样式取自令牌。 */
@Composable
fun ModbusMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalModbusColors.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        containerColor = colors.surface1,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, colors.line),
        content = content,
    )
}

/** 菜单项：文字用 text 色，当前项（[selected]）用强调色文字 + 半粗。 */
@Composable
fun ModbusMenuItem(text: String, onClick: () -> Unit, selected: Boolean = false) {
    val colors = LocalModbusColors.current
    DropdownMenuItem(
        text = {
            Text(
                text,
                color = if (selected) colors.accentText else colors.text,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        },
        onClick = onClick,
        colors = MenuDefaults.itemColors(textColor = colors.text),
    )
}
