package io.github.xingyingyuzhui.modbusmobile.application

import io.github.xingyingyuzhui.modbusmobile.application.history.HistoryInterval
import io.github.xingyingyuzhui.modbusmobile.application.history.HistoryPolicy
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
            logCrcDim = false,
            coilExtraWarning = false,
            defaultResponseTimeoutMs = 2_500,
            defaultInterRequestMs = 35,
            defaultBroadcastWaitMs = 400,
            defaultEcho = EchoMode.AUTO,
            historyInterval = HistoryInterval.SEC_60,
            historyRetentionDays = 7,
            historyCapacityMb = 64,
        )
        assertEquals(original, uiSettingsFrom(original.toPreferenceMap()))
    }

    @Test
    fun newFieldsDefaultToTheDesignValues() {
        val d = UiSettings()
        assertEquals(true, d.logCrcDim)
        assertEquals(true, d.coilExtraWarning)
        assertEquals(1_000L, d.defaultResponseTimeoutMs)
        assertEquals(20L, d.defaultInterRequestMs)
        assertEquals(150L, d.defaultBroadcastWaitMs)
        assertEquals(EchoMode.OFF, d.defaultEcho)
        assertEquals(HistoryInterval.SEC_5, d.historyInterval)
        assertEquals(HistoryPolicy.DEFAULT_RETENTION_DAYS, d.historyRetentionDays)
        assertEquals(HistoryPolicy.DEFAULT_CAPACITY_MB, d.historyCapacityMb)
        assertEquals(d, uiSettingsFrom(emptyMap()))
    }

    @Test
    fun outOfRangeCommDefaultsFallBackInsteadOfBreakingTheConnectionPage() {
        val parsed = uiSettingsFrom(
            mapOf(
                UiSettingKeys.DEF_TIMEOUT to "0",
                UiSettingKeys.DEF_INTERVAL to "-5",
                UiSettingKeys.DEF_BROADCAST to "999999",
                UiSettingKeys.DEF_ECHO to "MAYBE",
            ),
        )
        assertEquals(1_000L, parsed.defaultResponseTimeoutMs)
        assertEquals(20L, parsed.defaultInterRequestMs)
        assertEquals(150L, parsed.defaultBroadcastWaitMs)
        assertEquals(EchoMode.OFF, parsed.defaultEcho)
        // 广播等待可以是 0
        assertEquals(0L, uiSettingsFrom(mapOf(UiSettingKeys.DEF_BROADCAST to "0")).defaultBroadcastWaitMs)
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

    /** 第一期只开放深色：旧数据里的 SYSTEM / LIGHT 读出来都要归一成 DARK，其余字段不动。 */
    @Test
    fun phase1NormalizesThemeToDarkAndKeepsEverythingElse() {
        val parsed = uiSettingsFrom(
            mapOf(
                UiSettingKeys.THEME to ThemeMode.LIGHT.name,
                UiSettingKeys.ACCENT to AccentColor.TEAL.name,
                UiSettingKeys.FONT to FontScale.LARGE.name,
            ),
        )
        assertEquals(ThemeMode.LIGHT, parsed.theme)
        val normalized = parsed.forPhase1()
        assertEquals(ThemeMode.DARK, normalized.theme)
        assertEquals(AccentColor.TEAL, normalized.accent)
        assertEquals(FontScale.LARGE, normalized.fontScale)
        assertEquals(ThemeMode.DARK, UiSettings().forPhase1().theme)
        assertEquals(ThemeMode.DARK, UiSettings(theme = ThemeMode.SYSTEM).forPhase1().theme)
    }
}
