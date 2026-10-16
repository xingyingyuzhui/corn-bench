package io.github.xingyingyuzhui.modbusmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ButtonRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.MinTouch

/** [compact] = 稿面 .btn.sm：高 44、字 14。默认仍是高 52、字 16 的主按钮。 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    /** 警示：红底白字（广播写入等不可撤回的操作，不再和普通写入同为琥珀色）。 */
    danger: Boolean = false,
) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(ButtonRadius)
    Box(
        modifier
            .fillMaxWidth()
            .then(if (compact) Modifier.minimumInteractiveComponentSize() else Modifier)
            .height(if (compact) 44.dp else 52.dp)
            .clip(shape)
            .background(if (!enabled) colors.surface3 else if (danger) colors.error else colors.accent)
            .semantics { role = Role.Button }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (!enabled) colors.dim else if (danger) androidx.compose.ui.graphics.Color.White else colors.onAccent,
            fontWeight = FontWeight.Bold,
            fontSize = if (compact) 14.sp else 16.sp,
            maxLines = 1,
        )
    }
}

/**
 * [compact] = 稿面 .btn.sm（高 44）。[icon] 画在文字前；[active] 表示开关已开（强调色描边 + 浅底）。
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    fontSize: TextUnit? = null,
    active: Boolean = false,
    /** 固定高度（和同一行的主按钮等高，例如确认弹层的 52）。 */
    height: androidx.compose.ui.unit.Dp? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(ButtonRadius)
    Box(
        modifier
            .fillMaxWidth()
            .then(
                when {
                    height != null -> Modifier.height(height)
                    compact -> Modifier.minimumInteractiveComponentSize().height(44.dp)
                    else -> Modifier.height(52.dp) // 稿面 .btn 默认高 52（G#10）
                },
            )
            .clip(shape)
            .background(if (active) colors.wash(colors.accent) else colors.surface1)
            .border(1.5.dp, if (active) colors.accent else colors.line, shape) // 稿面次按钮描边 1.5
            .semantics { role = Role.Button }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                icon()
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text,
                color = if (!enabled) colors.dim else if (active) colors.accentText else colors.text,
                fontWeight = FontWeight.SemiBold,
                fontSize = fontSize ?: if (compact) 14.sp else 16.sp,
                maxLines = 1,
            )
        }
    }
}

/** 稿面 .btn.xs：高 32、字 12.5、圆角 10；触控高度用 minimumInteractiveComponentSize 补到 48。 */
@Composable
fun SmallButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 稿面 .btn.pri.xs：强调色填充（页头的“完成”）。 */
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .height(32.dp)
            .clip(shape)
            .then(
                if (primary) Modifier.background(if (enabled) colors.accent else colors.surface3) else Modifier.border(1.5.dp, colors.line, shape),
            )
            .semantics { role = Role.Button }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (primary) (if (enabled) colors.onAccent else colors.dim) else colors.text,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 行内的文字链接（“＋ 添加”“清空”）。视觉是一行小字，触控区域至少 48×48，文字靠右。 */
@Composable
fun TextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = LocalModbusColors.current.muted,
) {
    Box(
        modifier
            .sizeIn(minWidth = MinTouch, minHeight = MinTouch)
            .semantics { role = Role.Button }
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(text, color = color, fontSize = 12.sp)
    }
}
