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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(ButtonRadius)
    Box(
        modifier
            .fillMaxWidth()
            .height(if (compact) 44.dp else 52.dp)
            .clip(shape)
            .background(if (enabled) colors.accent else colors.surface3)
            .semantics { role = Role.Button }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) colors.onAccent else colors.dim,
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
    icon: (@Composable () -> Unit)? = null,
) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(ButtonRadius)
    Box(
        modifier
            .fillMaxWidth()
            .then(if (compact) Modifier.height(44.dp) else Modifier.heightIn(min = MinTouch))
            .clip(shape)
            .background(if (active) colors.wash(colors.accent) else colors.surface1)
            .border(if (active) 1.5.dp else 1.dp, if (active) colors.accent else colors.line, shape)
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

@Composable
fun SmallButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .heightIn(min = MinTouch)
            .clip(shape)
            .border(1.dp, colors.line, shape)
            .semantics { role = Role.Button }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = colors.text, fontSize = 13.sp)
    }
}
