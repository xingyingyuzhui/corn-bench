package io.github.xingyingyuzhui.modbusmobile.application

import org.junit.Assert.assertEquals
import org.junit.Test

class UiSettingsRoundTripTest {
    @Test
    fun preferenceMapRoundTripsEveryField() {
        val original = UiSettings(
            theme = ThemeMode.DARK,
            accent = AccentColor.TEAL,
            fontScale = FontScale.EXTRA,
            density = DensityMode.COMPACT,
            keepScreenOn = true,
            landscapeCurveFullscreen = true,
            vibration = true,
            addressStyle = AddressStyle.MODICON,
            numberFormat = NumberFormat.F32,
            hexPrefix = true,
            timeStyle = TimeStyle.CLOCK,
        )
        assertEquals(original, uiSettingsFrom(original.toPreferenceMap()))
    }

    @Test
    fun unknownValuesFallBackToDefaults() {
        val parsed = uiSettingsFrom(
            mapOf(
                UiSettingKeys.THEME to "SEPIA",
                UiSettingKeys.ACCENT to "ORANGE",
                UiSettingKeys.KEEP to "maybe",
                UiSettingKeys.FONT to FontScale.LARGE.name,
            ),
        )
        assertEquals(ThemeMode.SYSTEM, parsed.theme)
        assertEquals(AccentColor.AMBER, parsed.accent)
        assertEquals(false, parsed.keepScreenOn)
        assertEquals(FontScale.LARGE, parsed.fontScale)
        assertEquals(false, parsed.landscapeCurveFullscreen)
    }

    @Test
    fun curveFullscreenDefaultsOff() {
        assertEquals(false, UiSettings().landscapeCurveFullscreen)
    }
}
