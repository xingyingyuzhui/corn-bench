package io.github.xingyingyuzhui.modbusmobile.application

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AccentColor(val argb: Long, val onAccentArgb: Long) {
    AMBER(0xFFF5A524, 0xFF1A1200),
    BLUE(0xFF2F81F7, 0xFFFFFFFF),
    GREEN(0xFF2DA44E, 0xFFFFFFFF),
    TEAL(0xFF0E8A8A, 0xFFFFFFFF),
    PURPLE(0xFF8250DF, 0xFFFFFFFF),
}

enum class FontScale(val multiplier: Float) {
    STANDARD(1f),
    LARGE(1.15f),
    EXTRA(1.3f),
}

enum class DensityMode(val scale: Float) {
    COMFORTABLE(1f),
    COMPACT(0.75f),
}

/** 地址显示：只显示 0 基地址 / 只显示参考号（40001）/ 两者（0 基为主，参考号小字）。默认“两者”，与稿面一致。 */
enum class AddressStyle { ZERO_BASED, MODICON, BOTH }

enum class NumberFormat { U16, S16, HEX, F32 }

enum class TimeStyle { ELAPSED, CLOCK }

data class UiSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentColor = AccentColor.AMBER,
    val fontScale: FontScale = FontScale.STANDARD,
    val density: DensityMode = DensityMode.COMFORTABLE,
    val keepScreenOn: Boolean = false,
    /** 默认关。横屏截图和日常翻页不会被自动拉进曲线页。 */
    val landscapeCurveFullscreen: Boolean = false,
    /**
     * 先只保存。写入确认才是第一次震动，那是工作台阶段的事。
     */
    val vibration: Boolean = false,
    val addressStyle: AddressStyle = AddressStyle.BOTH,
    val numberFormat: NumberFormat = NumberFormat.U16,
    /** 默认开：稿面的 HEX 一律写成 0x007B。 */
    val hexPrefix: Boolean = true,
    /**
     * 时钟样式先只保存。报文只有 monotonicNs，日志仍显示相对毫秒。
     */
    val timeStyle: TimeStyle = TimeStyle.ELAPSED,
)

internal object UiSettingKeys {
    const val THEME = "theme"
    const val ACCENT = "accent"
    const val FONT = "font_scale"
    const val DENSITY = "density"
    const val KEEP = "keep_screen_on"
    const val CURVE = "landscape_curve"
    const val VIBRATION = "vibration"
    const val ADDRESS = "address_style"
    const val NUMBER = "number_format"
    const val HEX = "hex_prefix"
    const val TIME = "time_style"

    val booleans = setOf(KEEP, CURVE, VIBRATION, HEX)
}

fun UiSettings.toPreferenceMap(): Map<String, String> = mapOf(
    UiSettingKeys.THEME to theme.name,
    UiSettingKeys.ACCENT to accent.name,
    UiSettingKeys.FONT to fontScale.name,
    UiSettingKeys.DENSITY to density.name,
    UiSettingKeys.KEEP to keepScreenOn.toString(),
    UiSettingKeys.CURVE to landscapeCurveFullscreen.toString(),
    UiSettingKeys.VIBRATION to vibration.toString(),
    UiSettingKeys.ADDRESS to addressStyle.name,
    UiSettingKeys.NUMBER to numberFormat.name,
    UiSettingKeys.HEX to hexPrefix.toString(),
    UiSettingKeys.TIME to timeStyle.name,
)

fun uiSettingsFrom(map: Map<String, String>): UiSettings = UiSettings(
    theme = map.enum(UiSettingKeys.THEME, ThemeMode.SYSTEM),
    accent = map.enum(UiSettingKeys.ACCENT, AccentColor.AMBER),
    fontScale = map.enum(UiSettingKeys.FONT, FontScale.STANDARD),
    density = map.enum(UiSettingKeys.DENSITY, DensityMode.COMFORTABLE),
    keepScreenOn = map.bool(UiSettingKeys.KEEP, false),
    landscapeCurveFullscreen = map.bool(UiSettingKeys.CURVE, false),
    vibration = map.bool(UiSettingKeys.VIBRATION, false),
    addressStyle = map.enum(UiSettingKeys.ADDRESS, AddressStyle.BOTH),
    numberFormat = map.enum(UiSettingKeys.NUMBER, NumberFormat.U16),
    hexPrefix = map.bool(UiSettingKeys.HEX, true),
    timeStyle = map.enum(UiSettingKeys.TIME, TimeStyle.ELAPSED),
)

private inline fun <reified T : Enum<T>> Map<String, String>.enum(key: String, default: T): T {
    val raw = this[key] ?: return default
    return enumValues<T>().firstOrNull { it.name == raw } ?: default
}

private fun Map<String, String>.bool(key: String, default: Boolean): Boolean =
    this[key]?.toBooleanStrictOrNull() ?: default
