package io.github.xingyingyuzhui.modbusmobile.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.AccentColor
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.ui.theme.CardRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ChipRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.FieldRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.JetBrainsMono
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.MinTouch
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import io.github.xingyingyuzhui.modbusmobile.ui.theme.Mono

@Composable
fun ModbusCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(CardRadius)
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface1, shape)
            .border(1.dp, colors.line, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, trailing: (@Composable () -> Unit)? = null) {
    val colors = LocalModbusColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text, color = colors.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** 分段控件的单项高度：稿面单行分段整体 42（项 36）、sm 档 37（项 31）。默认保持 48dp 触控高度，旧页面不变。 */
val SegItemDesign = 36.dp
val SegItemSmall = 31.dp

@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = MinTouch,
    label: @Composable (T) -> Unit,
) {
    val colors = LocalModbusColors.current
    Row(
        modifier
            .fillMaxWidth()
            // 底板只画稿面高度（项高 + 上下内边距 3），居中；项自己补到 48dp 的触控区域落在底板上下的透明带里。
            .drawBehind {
                val height = (itemHeight + 6.dp).toPx().coerceAtMost(size.height)
                drawRoundRect(
                    color = colors.surface2,
                    topLeft = Offset(0f, (size.height - height) / 2f),
                    size = Size(size.width, height),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                )
            }
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            val on = option == selected
            val shape = RoundedCornerShape(9.dp)
            // 稿面选中块：深色 s3 + 内描边 #3A4658；浅色 s1 + 1px line 环
            val ring = if (colors.isDark) androidx.compose.ui.graphics.Color(0xFF3A4658) else colors.line
            Box(
                Modifier
                    .weight(1f)
                    // 自写的 Box + clickable 不会自动补触控目标，这里显式补到 48dp；视觉块仍是 itemHeight。
                    .minimumInteractiveComponentSize()
                    .heightIn(min = itemHeight)
                    .clip(shape)
                    .background(if (on) if (colors.isDark) colors.surface3 else colors.surface1 else androidx.compose.ui.graphics.Color.Transparent)
                    .then(if (on) Modifier.border(1.dp, ring, shape) else Modifier)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                label(option)
            }
        }
    }
}

/** 稿面尺寸的文字分段：项 14px（sm 12.5px），选中 600 + 主文字色，未选 muted。 */
@Composable
fun <T> SegmentedTextControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    text: @Composable (T) -> String,
) {
    val colors = LocalModbusColors.current
    SegmentedControl(
        options,
        selected,
        onSelect,
        modifier,
        itemHeight = if (small) SegItemSmall else SegItemDesign,
    ) { option ->
        val on = option == selected
        Text(
            text(option),
            color = if (on) colors.text else colors.muted,
            fontSize = if (small) 12.5.sp else 14.sp,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipRow(
    options: List<T>,
    isSelected: (T) -> Boolean,
    label: @Composable (T) -> String,
    onToggle: (T) -> Unit,
    modifier: Modifier = Modifier,
    scroll: Boolean = false,
    xs: Boolean = false,
) {
    if (scroll) {
        Row(
            modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { option ->
                SelectChip(label(option), isSelected(option), xs = xs) { onToggle(option) }
            }
        }
    } else {
        FlowRow(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { option ->
                SelectChip(label(option), isSelected(option), xs = xs) { onToggle(option) }
            }
        }
    }
}

/** [xs] = 稿面 xs 档：高 25、字 12、内边距 3/8。视觉保持稿面尺寸，触控区域用 minimumInteractiveComponentSize 补到 48dp。 */
@Composable
fun SelectChip(text: String, selected: Boolean, xs: Boolean = false, onClick: () -> Unit) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(ChipRadius)
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = if (xs) 25.dp else MinTouch)
            .clip(shape)
            .background(if (selected) colors.wash(colors.accent) else colors.surface1)
            .border(1.dp, if (selected) colors.accent else colors.line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = if (xs) 8.dp else 12.dp, vertical = if (xs) 3.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) colors.accentText else colors.muted, fontSize = if (xs) 12.sp else 13.sp)
    }
}

@Composable
fun FieldTile(
    label: String,
    value: String,
    sub: String? = null,
    modifier: Modifier = Modifier,
    mono: Boolean = true,
) {
    val colors = LocalModbusColors.current
    Column(
        modifier
            .heightIn(min = 50.dp)
            .background(colors.surface2, RoundedCornerShape(FieldRadius))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(label, color = colors.muted, fontSize = 11.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                color = colors.text,
                fontSize = if (mono) 17.sp else 15.sp,
                fontFamily = if (mono) JetBrainsMono else FontFamily.Default,
            )
            if (sub != null) {
                Text(sub, color = colors.dim, fontSize = 11.sp, fontFamily = JetBrainsMono, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

/**
 * 输入格。[sub] 是右侧小字（例如 40001 参考号）；获得焦点时画 1.5dp 强调色描边（稿面 .f.on）。
 *
 * - 整个格子都能点（不只是文字那一行）：点格子任何位置都聚焦输入框。
 * - 聚焦时全选，键入的新数字直接替换旧值（123 不会变成 0123）。
 * - 值正好是“0”时再键入数字，丢掉前导 0（外部改值、软键盘把光标放在 0 后面的情况）。
 */
@Composable
fun NumberInput(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    sub: String? = null,
    minHeight: Dp = 50.dp,
    keyboardType: KeyboardType = KeyboardType.Number,
    onValue: (String) -> Unit,
) {
    val colors = LocalModbusColors.current
    var focused by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue(value)) }
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    val requester = remember { FocusRequester() }
    val shape = RoundedCornerShape(FieldRadius)
    LaunchedEffect(focused) {
        if (focused) {
            // 点击落点会在聚焦之后才把光标放下，等下一帧再全选，才不会被覆盖。
            withFrameNanos { }
            field = field.copy(selection = TextRange(0, field.text.length))
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .clip(shape)
                .background(colors.surface2, shape)
                .then(if (focused) Modifier.border(1.5.dp, colors.accent, shape) else Modifier)
                .pointerInput(Unit) { detectTapGestures { requester.requestFocus() } }
                .padding(horizontal = 10.dp, vertical = 7.dp),
        ) {
            Text(label, color = colors.muted, fontSize = 11.sp, maxLines = 1)
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.Bottom) {
                BasicTextField(
                    value = field,
                    onValueChange = { next ->
                        val text = dropLeadingZero(field.text, next.text)
                        field = if (text == next.text) next else TextFieldValue(text, TextRange(text.length))
                        if (text != value) onValue(text)
                    },
                    textStyle = Mono.copy(color = colors.text, fontSize = 17.sp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(requester)
                        .semantics { contentDescription = label }
                        .onFocusChanged { focused = it.isFocused },
                )
                if (sub != null) {
                    Text(sub, color = colors.dim, fontSize = 11.sp, fontFamily = JetBrainsMono, maxLines = 1)
                }
            }
        }
        if (error != null) {
            Text(error, color = colors.error, fontSize = 12.sp)
        }
    }
}

/** 旧值正好是“0”、新值是“0”后面又跟了数字（“01”“07B”）时，去掉那个前导 0。其它情况原样返回。 */
internal fun dropLeadingZero(previous: String, next: String): String =
    if (previous == "0" && next.length > 1 && next[0] == '0' && next[1] != 'x' && next[1] != 'X' &&
        next.all { it.isLetterOrDigit() }
    ) {
        val rest = next.drop(1)
        if (rest.all { it == '0' }) "0" else rest
    } else {
        next
    }

/** 与输入格同形的可点击格（稿面“格式 十进制”、线圈值格）。 */
@Composable
fun ChoiceTile(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 50.dp,
    mono: Boolean = false,
    valueColor: androidx.compose.ui.graphics.Color = LocalModbusColors.current.text,
) {
    val colors = LocalModbusColors.current
    Column(
        modifier
            .heightIn(min = minHeight)
            .clip(RoundedCornerShape(FieldRadius))
            .background(colors.surface2)
            .semantics { role = androidx.compose.ui.semantics.Role.Button }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(label, color = colors.muted, fontSize = 11.sp, maxLines = 1)
        Text(
            value,
            color = valueColor,
            fontSize = if (mono) 17.sp else 15.sp,
            fontFamily = if (mono) JetBrainsMono else FontFamily.Default,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

enum class SettingTrailing { SWITCH, CHEVRON, LOCKED }

@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: SettingTrailing = SettingTrailing.CHEVRON,
    checked: Boolean = false,
    selected: Boolean = false,
    onChecked: (Boolean) -> Unit = {},
    onClick: () -> Unit = {},
) {
    val colors = LocalModbusColors.current
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = MinTouch)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.wash(colors.accent) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(enabled = trailing != SettingTrailing.LOCKED, onClick = {
                if (trailing == SettingTrailing.SWITCH) onChecked(!checked) else onClick()
            })
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = colors.text, fontSize = 14.sp)
            if (subtitle != null) Text(subtitle, color = colors.muted, fontSize = 12.sp)
        }
        when (trailing) {
            SettingTrailing.SWITCH -> Switch(
                checked = checked,
                onCheckedChange = onChecked,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = colors.accent,
                    checkedThumbColor = colors.onAccent,
                ),
            )
            SettingTrailing.CHEVRON -> Text("›", color = colors.dim, fontSize = 18.sp)
            SettingTrailing.LOCKED -> Text(stringResource(R.string.setting_locked), color = colors.dim, fontSize = 13.sp)
        }
    }
}

@Composable
fun ColorSwatchRow(
    selected: AccentColor,
    modifier: Modifier = Modifier,
    onSelect: (AccentColor) -> Unit,
) {
    val colors = LocalModbusColors.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        AccentColor.entries.forEach { accent ->
            val label = stringResource(R.string.cd_accent, accent.name)
            Box(
                Modifier
                    .size(MinTouch)
                    .semantics { contentDescription = label }
                    .clickable { onSelect(accent) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Color(accent.argb.toInt()))
                        .then(
                            if (selected == accent) {
                                Modifier.border(2.dp, colors.text, CircleShape)
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }
    }
}

@Composable
fun BitPicker16(
    bits: Int,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit,
) {
    val colors = LocalModbusColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(2) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(8) { column ->
                    val bit = 15 - (row * 8 + column)
                    val on = ((bits shr bit) and 1) == 1
                    val shape = RoundedCornerShape(8.dp)
                    val bitLabel = stringResource(R.string.cd_bit, bit)
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = MinTouch)
                            .clip(shape)
                            .background(if (on) colors.wash(colors.accent) else colors.surface2)
                            .border(1.dp, if (on) colors.accent else colors.line, shape)
                            .semantics { contentDescription = bitLabel }
                            .clickable { onChange(bits xor (1 shl bit)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (on) "1" else "0",
                            color = if (on) colors.accentText else colors.muted,
                            fontFamily = JetBrainsMono,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "深色", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ControlsDark() = ControlsPreview(true)

@Preview(name = "浅色", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun ControlsLight() = ControlsPreview(false)

@Composable
private fun ControlsPreview(dark: Boolean) {
    ModbusTheme(UiSettings(theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SegmentedControl(listOf("主站", "从站"), "主站", {}) { Text(it, fontWeight = FontWeight.SemiBold) }
            ChipRow(listOf("9600 8N1", "19200 8N1"), { it.startsWith("9600") }, { it }, {})
            FieldTile("起始地址", "0", "40001")
            SettingRow("屏幕常亮", trailing = SettingTrailing.SWITCH, checked = true)
            SettingRow("主题", trailing = SettingTrailing.CHEVRON)
            SettingRow("写入前确认", trailing = SettingTrailing.LOCKED)
            ColorSwatchRow(AccentColor.AMBER) {}
            BitPicker16(0x00A5) {}
        }
    }
}
