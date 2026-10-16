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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (T) -> Unit,
) {
    val colors = LocalModbusColors.current
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.surface2, RoundedCornerShape(12.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { option ->
            val on = option == selected
            val shape = RoundedCornerShape(9.dp)
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = MinTouch)
                    .clip(shape)
                    .background(if (on) if (colors.isDark) colors.surface3 else colors.surface1 else androidx.compose.ui.graphics.Color.Transparent)
                    .then(if (on) Modifier.border(1.dp, colors.line, shape) else Modifier)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                label(option)
            }
        }
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
) {
    if (scroll) {
        Row(
            modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { option ->
                SelectChip(label(option), isSelected(option)) { onToggle(option) }
            }
        }
    } else {
        FlowRow(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { option ->
                SelectChip(label(option), isSelected(option)) { onToggle(option) }
            }
        }
    }
}

@Composable
fun SelectChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(ChipRadius)
    Box(
        Modifier
            .heightIn(min = MinTouch)
            .clip(shape)
            .background(if (selected) colors.wash(colors.accent) else colors.surface1)
            .border(1.dp, if (selected) colors.accent else colors.line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) colors.accentText else colors.muted, fontSize = 13.sp)
    }
}

@Composable
fun FieldTile(
    label: String,
    value: String,
    sub: String? = null,
    modifier: Modifier = Modifier,
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
            Text(value, style = Mono.copy(fontSize = 17.sp), color = colors.text)
            if (sub != null) {
                Text(sub, color = colors.dim, fontSize = 11.sp, fontFamily = JetBrainsMono, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
fun NumberInput(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    onValue: (String) -> Unit,
) {
    val colors = LocalModbusColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .background(colors.surface2, RoundedCornerShape(FieldRadius))
                .padding(horizontal = 10.dp, vertical = 7.dp),
        ) {
            Text(label, color = colors.muted, fontSize = 11.sp)
            BasicTextField(
                value = value,
                onValueChange = onValue,
                textStyle = Mono.copy(color = colors.text, fontSize = 17.sp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
        }
        if (error != null) {
            Text(error, color = colors.error, fontSize = 12.sp)
        }
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
