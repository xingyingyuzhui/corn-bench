package dev.corn.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 第 2 层：语义色。字段名就是用途；两个功能模块的旧颜色对象（Flashing `CornColors`、Modbus `ModbusColors`）
 * 第一期都从这里取值，不再各自写字面色值。深浅两套值同时存在，第一期 App 只开放深色。
 */
data class BenchColors(
    val isDark: Boolean,
    // 表面与线
    val bg: Color,
    val navBackground: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val line: Color,
    val rowLine: Color,
    /** 控件边界（开关关闭描边、单选环）：对 surface1 ≥ 3:1。 */
    val outlineControl: Color,
    /** 浮动 dock / rail 的底。 */
    val island: Color,
    val scrim: Color,
    // 文字
    val text: Color,
    /** 次要信息文字（Flashing 的 Dim）。 */
    val muted: Color,
    /** 禁用、占位、装饰（Flashing 的 Mute）；不用于有效信息文字。 */
    val dim: Color,
    // 品牌强调色
    val accent: Color,
    val accentDeep: Color,
    val onAccent: Color,
    /** surface 上的强调色文字 / 链接。 */
    val accentText: Color,
    val accentBorder: Color,
    val accentContainer: Color,
    // 状态色
    val success: Color,
    val successContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val dangerFill: Color,
    val onDanger: Color,
    val info: Color,
    val infoContainer: Color,
    /** 预警 / 写入未知：必须配图标 + 文字，不单靠颜色。 */
    val warning: Color,
    // 报文语义色（日志、帧着色）
    val tx: Color,
    val txText: Color,
    val rx: Color,
    val foreign: Color,
    val echo: Color,
    val ambiguous: Color,
    val dropped: Color,
    /** 语义色浅底的统一透明度。 */
    val washAlpha: Float,
) {
    fun wash(color: Color): Color = color.copy(alpha = washAlpha)
}

private val Dark = with(BenchPalette) {
    BenchColors(
        isDark = true,
        bg = Charcoal950,
        navBackground = Charcoal925,
        surface1 = Charcoal900,
        surface2 = Charcoal850,
        surface3 = Charcoal800,
        line = Charcoal750,
        rowLine = Charcoal825,
        outlineControl = Ash500,
        island = Charcoal850.copy(alpha = 0.94f),
        scrim = Color(0xCC000000),
        text = Cream50,
        muted = Stone400,
        dim = Stone600,
        accent = Corn400,
        accentDeep = Corn500,
        onAccent = Corn950,
        accentText = Corn400,
        accentBorder = Corn400,
        accentContainer = Color(0x1FF7C948),
        success = Leaf400,
        successContainer = Color(0x2486A96F),
        danger = Red400,
        dangerContainer = Color(0x21EF6B5B),
        dangerFill = Red600,
        onDanger = Color(0xFFFFFFFF),
        info = Sky400,
        infoContainer = Color(0x1F7FB0E8),
        warning = Corn400,
        tx = Teal400,
        txText = Teal400,
        rx = Leaf400,
        foreign = Sky400,
        echo = Violet400,
        ambiguous = Pink400,
        dropped = Taupe400,
        washAlpha = 0.10f,
    )
}

private val Light = with(BenchPalette) {
    BenchColors(
        isDark = false,
        bg = Paper50,
        navBackground = Paper0,
        surface1 = Paper0,
        surface2 = Paper100,
        surface3 = Paper200,
        line = Paper300,
        rowLine = Paper150,
        outlineControl = Ink500,
        island = Paper0.copy(alpha = 0.95f),
        scrim = Color(0x611C1A15),
        text = Ink900,
        muted = Ink600,
        dim = Ink400,
        accent = Corn400,
        accentDeep = Corn500,
        onAccent = Corn950,
        accentText = Corn700,
        accentBorder = Corn700,
        accentContainer = Color(0x29F7C948),
        success = Leaf700,
        successContainer = Color(0x1A3F6B2A),
        danger = Red700,
        dangerContainer = Color(0x14C4372A),
        dangerFill = Red700,
        onDanger = Color(0xFFFFFFFF),
        info = Sky700,
        infoContainer = Color(0x142F6DB5),
        warning = Corn700,
        tx = Teal700,
        txText = Teal700,
        rx = Leaf700,
        foreign = Sky700,
        echo = Violet700,
        ambiguous = Pink700,
        dropped = Taupe700,
        washAlpha = 0.07f,
    )
}

/** 深色 = 炭黑 + 玉米（第一期唯一开放的主题）；浅色为暂定值。 */
fun benchColors(dark: Boolean): BenchColors = if (dark) Dark else Light

val LocalBenchColors = staticCompositionLocalOf { benchColors(dark = true) }
