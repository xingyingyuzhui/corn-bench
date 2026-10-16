package io.github.xingyingyuzhui.modbusmobile.ui.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.AddressStyle
import io.github.xingyingyuzhui.modbusmobile.application.DensityMode
import io.github.xingyingyuzhui.modbusmobile.application.FontScale
import io.github.xingyingyuzhui.modbusmobile.application.NumberFormat
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.TimeStyle
import io.github.xingyingyuzhui.modbusmobile.ui.LocalSettingsRepository
import io.github.xingyingyuzhui.modbusmobile.ui.components.ColorSwatchRow
import io.github.xingyingyuzhui.modbusmobile.ui.components.ModbusCard
import io.github.xingyingyuzhui.modbusmobile.ui.components.SegmentedControl
import io.github.xingyingyuzhui.modbusmobile.ui.components.SessionStrip
import io.github.xingyingyuzhui.modbusmobile.ui.components.SettingRow
import io.github.xingyingyuzhui.modbusmobile.ui.components.SettingTrailing
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.ShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.screenFrame
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalSpacing

private enum class MineGroup(val label: Int) { DISPLAY(R.string.mine_display), SESSION(R.string.mine_session) }

@Composable
fun MineScreen() {
    val repository = LocalSettingsRepository.current
    val model = viewModel { MineViewModel(repository) }
    val settings by model.settings.collectAsStateWithLifecycle()
    val colors = LocalModbusColors.current
    val spacing = LocalSpacing.current
    val landscape = LocalShellMode.current == ShellMode.LANDSCAPE
    var groupName by rememberSaveable { mutableStateOf(MineGroup.DISPLAY.name) }
    val group = MineGroup.entries.firstOrNull { it.name == groupName } ?: MineGroup.DISPLAY
    Column(Modifier.screenFrame().padding(top = spacing.gapS), verticalArrangement = Arrangement.spacedBy(spacing.gapS)) {
        Text(stringResource(R.string.title_mine), color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        SessionStrip()
        if (landscape) {
            Row(Modifier.weight(1f).fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(spacing.gapM)) {
                Column(Modifier.weight(0.4f), verticalArrangement = Arrangement.spacedBy(spacing.gapS)) {
                    MineGroup.entries.forEach { item ->
                        SettingRow(
                            title = stringResource(item.label),
                            trailing = SettingTrailing.CHEVRON,
                            selected = item == group,
                            onClick = { groupName = item.name },
                        )
                    }
                }
                Column(Modifier.weight(0.6f).verticalScroll(rememberScrollState())) {
                    if (group == MineGroup.DISPLAY) DisplaySettings(model, settings) else SessionSettings(model, settings)
                }
            }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(spacing.gapM)) {
                Text(stringResource(R.string.mine_display), color = colors.muted, fontSize = 12.sp)
                DisplaySettings(model, settings)
                Text(stringResource(R.string.mine_session), color = colors.muted, fontSize = 12.sp)
                SessionSettings(model, settings)
            }
        }
    }
}

@Composable
private fun DisplaySettings(model: MineViewModel, settings: io.github.xingyingyuzhui.modbusmobile.application.UiSettings) {
    val colors = LocalModbusColors.current
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.gapS)) {
        Text(stringResource(R.string.setting_theme), color = colors.muted, fontSize = 12.sp)
        SegmentedControl(ThemeMode.entries, settings.theme, model::setTheme) { mode ->
            Text(themeName(mode), color = colors.text, fontSize = 13.sp)
        }
        Text(stringResource(R.string.setting_accent), color = colors.muted, fontSize = 12.sp)
        ColorSwatchRow(settings.accent, onSelect = model::setAccent)
        Text(stringResource(R.string.setting_font), color = colors.muted, fontSize = 12.sp)
        SegmentedControl(FontScale.entries, settings.fontScale, model::setFont) { scale ->
            Text(fontName(scale), color = colors.text, fontSize = 13.sp)
        }
        Text(stringResource(R.string.setting_density), color = colors.muted, fontSize = 12.sp)
        SegmentedControl(DensityMode.entries, settings.density, model::setDensity) { mode ->
            Text(densityName(mode), color = colors.text, fontSize = 13.sp)
        }
        Text(stringResource(R.string.setting_address), color = colors.muted, fontSize = 12.sp)
        SegmentedControl(AddressStyle.entries, settings.addressStyle, model::setAddress) { style ->
            Text(addressName(style), color = colors.text, fontSize = 13.sp)
        }
        Text(stringResource(R.string.setting_number), color = colors.muted, fontSize = 12.sp)
        SegmentedControl(NumberFormat.entries, settings.numberFormat, model::setNumber) { format ->
            Text(format.name, color = colors.text, fontSize = 13.sp)
        }
        ModbusCard {
            SettingRow(
                title = stringResource(R.string.setting_hex),
                trailing = SettingTrailing.SWITCH,
                checked = settings.hexPrefix,
                onChecked = model::setHexPrefix,
            )
        }
        Text(stringResource(R.string.setting_time), color = colors.muted, fontSize = 12.sp)
        SegmentedControl(TimeStyle.entries, settings.timeStyle, model::setTime) { style ->
            Text(timeName(style), color = colors.text, fontSize = 13.sp)
        }
        Text(stringResource(R.string.time_hint), color = colors.dim, fontSize = 12.sp)
    }
}

@Composable
private fun SessionSettings(model: MineViewModel, settings: io.github.xingyingyuzhui.modbusmobile.application.UiSettings) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ModbusCard {
            SettingRow(
                stringResource(R.string.setting_keep_screen),
                subtitle = stringResource(R.string.keep_screen_hint),
                trailing = SettingTrailing.SWITCH,
                checked = settings.keepScreenOn,
                onChecked = model::setKeepScreen,
            )
            SettingRow(
                stringResource(R.string.setting_vibration),
                subtitle = stringResource(R.string.vibration_hint),
                trailing = SettingTrailing.SWITCH,
                checked = settings.vibration,
                onChecked = model::setVibration,
            )
            SettingRow(
                stringResource(R.string.setting_landscape_curve),
                subtitle = stringResource(R.string.landscape_curve_hint),
                trailing = SettingTrailing.SWITCH,
                checked = settings.landscapeCurveFullscreen,
                onChecked = model::setLandscapeCurve,
            )
        }
    }
}

@Composable
private fun themeName(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    },
)

@Composable
private fun fontName(scale: FontScale): String = stringResource(
    when (scale) {
        FontScale.STANDARD -> R.string.font_standard
        FontScale.LARGE -> R.string.font_large
        FontScale.EXTRA -> R.string.font_extra
    },
)

@Composable
private fun densityName(mode: DensityMode): String = stringResource(
    when (mode) {
        DensityMode.COMFORTABLE -> R.string.density_comfortable
        DensityMode.COMPACT -> R.string.density_compact
    },
)

@Composable
private fun addressName(style: AddressStyle): String = stringResource(
    when (style) {
        AddressStyle.ZERO_BASED -> R.string.address_zero
        AddressStyle.MODICON -> R.string.address_modicon
    },
)

@Composable
private fun timeName(style: TimeStyle): String = stringResource(
    when (style) {
        TimeStyle.ELAPSED -> R.string.time_elapsed
        TimeStyle.CLOCK -> R.string.time_clock
    },
)
