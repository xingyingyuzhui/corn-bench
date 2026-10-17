package io.github.xingyingyuzhui.modbusmobile.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
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
import androidx.compose.ui.unit.em
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
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalSpacing
import io.github.xingyingyuzhui.modbusmobile.ui.theme.StrokeHairline
import io.github.xingyingyuzhui.modbusmobile.ui.theme.MinTouch
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import io.github.xingyingyuzhui.modbusmobile.ui.theme.Mono

@Composable
fun ModbusCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(LocalSpacing.current.card),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalModbusColors.current
    val spacing = LocalSpacing.current
    val shape = RoundedCornerShape(CardRadius)
    // §6.3 / §13.2：卡片内边距只有 space.card 12 一种（AL-04），内容左缘 = 卡片外缘 + 12 = K1（K0 + 12）。
    // 原来为了模仿稿面 border-box 多补的 1dp 去掉：K1 以外缘量，1dp 描边画在内边距里。
    // contentPadding 只留给通栏内容（例如卡内列表行自己带 12 水平内边距时传 0）。
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface1, shape)
            .border(StrokeHairline, colors.line, shape)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(spacing.s),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, trailing: (@Composable () -> Unit)? = null) {
    val colors = LocalModbusColors.current
    // 稿面 `.lbl`：12px、行高 ≈ 17（1.4 倍）、下边距 8；不用 Material 默认的 24sp 行高。
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("section-label").fillMaxWidth()) {
        Text(text, color = colors.muted, fontSize = 12.sp, lineHeight = 1.4.em, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** 分段控件的单项高度：稿面单行分段整体 42（项 36）、sm 档 37（项 31）。默认保持 48dp 触控高度，旧页面不变。 */
val SegItemDesign = 36.dp
val SegItemSmall = 31.dp

/** 稿面 `.seg`：外层圆角 12、内边距 3、项间距 3；选中块圆角 = 外层圆角 − 内边距 = 9（同心）。 */
val SegContainerRadius = 12.dp
val SegPadding = 3.dp
val SegGap = 3.dp
val SegItemRadius = SegContainerRadius - SegPadding

const val SegContainerTag = "seg-container"
const val SegItemTag = "seg-item"

@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = SegItemDesign,
    label: @Composable (T) -> Unit,
) {
    val colors = LocalModbusColors.current
    // 外层 Box 只负责 48dp 的触控占位（居中）；可见的底板就是内层 Row 本身：底板 = 项高 + 上下内边距，
    // 底板高度由项真实高度决定（双行文字的项会让底板跟着变高），不再单独按“稿面高度”另画，
    // 所以选中块与底板之间永远是均匀的 3dp，圆角同心。
    Box(modifier.fillMaxWidth().minimumInteractiveComponentSize(), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .testTag(SegContainerTag)
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(colors.surface2, RoundedCornerShape(SegContainerRadius))
                .padding(SegPadding),
            horizontalArrangement = Arrangement.spacedBy(SegGap),
        ) {
            options.forEach { option ->
                val on = option == selected
                val shape = RoundedCornerShape(SegItemRadius)
                // 稿面选中块：深色 s3 + 内描边 #3A4658；浅色 s1 + 1px line 环
                val ring = if (colors.isDark) androidx.compose.ui.graphics.Color(0xFF3A4658) else colors.line
                Box(
                    Modifier
                        .testTag(SegItemTag)
                        .weight(1f)
                        .fillMaxHeight()
                        .heightIn(min = itemHeight)
                        .clip(shape)
                        .background(if (on) if (colors.isDark) colors.surface3 else colors.surface1 else androidx.compose.ui.graphics.Color.Transparent)
                        .then(if (on) Modifier.border(1.dp, ring, shape) else Modifier)
                        .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(option) })
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    label(option)
                }
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
    design: Boolean = true,
    description: (T) -> String? = { null },
) {
    if (scroll) {
        Row(
            modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { option ->
                SelectChip(label(option), isSelected(option), xs = xs, design = design, description = description(option)) { onToggle(option) }
            }
        }
    } else {
        FlowRow(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { option ->
                SelectChip(label(option), isSelected(option), xs = xs, design = design, description = description(option)) { onToggle(option) }
            }
        }
    }
}

/**
 * 只把高度补到 [min]（默认 48dp），宽度不动。chip 成排放时不能像 minimumInteractiveComponentSize 那样把窄 chip（“…”）也撑到 48 宽，
 * 否则 4 个预设挤不进一行。窄 chip 的横向热区靠 Compose 默认的触控目标扩展（真机未验证）。
 */
private fun Modifier.minimumTouchHeight(min: Dp = MinTouch): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val height = maxOf(placeable.height, min.roundToPx())
    layout(placeable.width, height) { placeable.placeRelative(0, (height - placeable.height) / 2) }
}

/**
 * [xs] = 稿面 xs 档：高 25、字 12、内边距 3/8。
 * [design] = 稿面普通 chip：高 33、字 12.5、内边距 9（G#1，只在显式开启的页面生效，全局默认仍是 48dp）。
 * [description] 给只有符号的 chip（例如“…”）补读屏文字。
 * 视觉保持稿面尺寸，触控高度用 [minimumTouchHeight] 补到 48dp（真机未验证）。
 */
@Composable
fun SelectChip(
    text: String,
    selected: Boolean,
    xs: Boolean = false,
    design: Boolean = true,
    description: String? = null,
    /** 窄文字的芯片也要有可点的宽度（触控 ≥48dp）。 */
    minWidth: Dp = 0.dp,
    onClick: () -> Unit,
) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(ChipRadius)
    val minHeight = if (xs) 25.dp else if (design) 33.dp else MinTouch
    val horizontal = if (xs) 8.dp else if (design) 9.dp else 12.dp
    Box(
        Modifier
            .minimumTouchHeight()
            .widthIn(min = minWidth)
            .heightIn(min = minHeight)
            .clip(shape)
            .background(if (selected) colors.wash(colors.accent) else colors.surface1)
            .border(1.dp, if (selected) colors.accent else colors.line, shape)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = horizontal, vertical = if (xs) 3.dp else if (design) 6.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) colors.accentText else colors.muted,
            fontSize = if (xs) 12.sp else if (design) 12.5.sp else 13.sp,
        )
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
        Text(label, color = colors.muted, fontSize = 11.sp, lineHeight = 13.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                color = colors.text,
                fontSize = if (mono) 17.sp else 15.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
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
                    textStyle = Mono.copy(color = colors.text, fontSize = 17.sp, lineHeight = 24.sp),
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
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (mono) JetBrainsMono else FontFamily.Default,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

const val SwitchVisualTag = "switch-visual"

enum class SettingTrailing { SWITCH, CHEVRON, LOCKED, NONE }

/** 稿面开关：42×24 轨道，20dp 白色滑块（G#5）。只负责画，点击由所在行处理（整行 ≥48dp 可点）。 */
@Composable
fun ModbusSwitchVisual(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalModbusColors.current
    Box(
        modifier
            .testTag(SwitchVisualTag)
            .size(width = 42.dp, height = 24.dp)
            .clip(CircleShape)
            .background(if (checked) colors.accent else colors.surface3),
    ) {
        Box(
            Modifier
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(2.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(androidx.compose.ui.graphics.Color.White),
        )
    }
}

/** 行首 30×30 图标块（稿面 b3-1 列表行）。 */
@Composable
fun SettingIconBlock(content: @Composable () -> Unit) {
    val colors = LocalModbusColors.current
    Box(
        Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(colors.surface2),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: SettingTrailing = SettingTrailing.CHEVRON,
    checked: Boolean = false,
    selected: Boolean = false,
    value: String? = null,
    icon: (@Composable () -> Unit)? = null,
    onChecked: (Boolean) -> Unit = {},
    onClick: () -> Unit = {},
) {
    val colors = LocalModbusColors.current
    val base = modifier
        .fillMaxWidth()
        .heightIn(min = MinTouch)
        .clip(RoundedCornerShape(12.dp))
        .background(if (selected) colors.wash(colors.accent) else androidx.compose.ui.graphics.Color.Transparent)
    val interactive = when (trailing) {
        SettingTrailing.SWITCH -> base.toggleable(value = checked, role = Role.Switch, onValueChange = onChecked)
        SettingTrailing.LOCKED, SettingTrailing.NONE -> base
        SettingTrailing.CHEVRON -> base.clickable(role = Role.Button, onClick = onClick)
    }
    Row(
        interactive.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) SettingIconBlock(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                color = if (selected) colors.accentText else colors.text,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (subtitle != null) Text(subtitle, color = colors.muted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        if (value != null) {
            Text(value, color = colors.dim, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 1)
        }
        when (trailing) {
            SettingTrailing.SWITCH -> ModbusSwitchVisual(checked)
            SettingTrailing.CHEVRON -> Text("›", color = colors.dim, fontSize = 18.sp)
            SettingTrailing.LOCKED -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.setting_always), color = colors.dim, fontSize = 13.sp)
                LockIcon(colors.dim)
            }
            SettingTrailing.NONE -> Unit
        }
    }
}

/** 设置行的容器卡片：无内边距、行间不留空隙，行与行之间自己放 [RowDivider]。 */
@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(CardRadius)
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface1, shape)
            .border(1.dp, colors.line, shape)
            .clip(shape)
            .padding(1.dp),
        content = content,
    )
}

/** 卡片内行与行之间的分隔线。 */
@Composable
fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalModbusColors.current.line))
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
                // 选中：外圈描边 + 2dp 间隙（稿面 b3-2 强调色圆点），圆点本身 26dp。
                Box(
                    Modifier
                        .size(34.dp)
                        .then(if (selected == accent) Modifier.border(2.dp, colors.text, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(androidx.compose.ui.graphics.Color(accent.argb.toInt())),
                    )
                }
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
