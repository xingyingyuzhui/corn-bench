package io.github.xingyingyuzhui.modbusmobile.ui.mine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.xingyingyuzhui.modbusmobile.application.AccentColor
import io.github.xingyingyuzhui.modbusmobile.application.AddressStyle
import io.github.xingyingyuzhui.modbusmobile.application.DensityMode
import io.github.xingyingyuzhui.modbusmobile.application.FontScale
import io.github.xingyingyuzhui.modbusmobile.application.NumberFormat
import io.github.xingyingyuzhui.modbusmobile.application.SettingsRepository
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.TimeStyle
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import kotlinx.coroutines.launch

class MineViewModel(private val repository: SettingsRepository) : ViewModel() {
    val settings = repository.settings

    fun setTheme(mode: ThemeMode) = edit { it.copy(theme = mode) }
    fun setAccent(accent: AccentColor) = edit { it.copy(accent = accent) }
    fun setFont(scale: FontScale) = edit { it.copy(fontScale = scale) }
    fun setDensity(mode: DensityMode) = edit { it.copy(density = mode) }
    fun setAddress(style: AddressStyle) = edit { it.copy(addressStyle = style) }
    fun setNumber(format: NumberFormat) = edit { it.copy(numberFormat = format) }
    fun setHexPrefix(enabled: Boolean) = edit { it.copy(hexPrefix = enabled) }
    fun setTime(style: TimeStyle) = edit { it.copy(timeStyle = style) }
    fun setKeepScreen(enabled: Boolean) = edit { it.copy(keepScreenOn = enabled) }
    fun setVibration(enabled: Boolean) = edit { it.copy(vibration = enabled) }
    fun setLandscapeCurve(enabled: Boolean) = edit { it.copy(landscapeCurveFullscreen = enabled) }

    private fun edit(transform: (UiSettings) -> UiSettings) {
        viewModelScope.launch { repository.update(transform) }
    }
}
