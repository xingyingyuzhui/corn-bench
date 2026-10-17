package io.github.xingyingyuzhui.modbusmobile.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.github.xingyingyuzhui.modbusmobile.application.AccentColor

/**
 * 颜色 token（设计规范_v2 §4，值与 T-20261018-and-03_tokens_v2.json 一致，DesignTokensTest 比对）。
 * 字段名就是语义名；各屏只从这里取色，不写字面 Color(0x…)。
 */
data class ModbusColors(
    val isDark: Boolean,
    // §4.1 表面与线
    val bg: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val line: Color,
    val rowLine: Color,
    /** 控件边界：开关关闭轨道描边、单选环、分段选中环（对 surface1 ≥3:1）。新增。 */
    val outlineControl: Color,
    val island: Color,
    /** 所有弹层遮罩。新增。 */
    val scrim: Color,
    // §4.2 文字
    val text: Color,
    val muted: Color,
    /** 只用于禁用文字、占位“—”、装饰图标、图表网格；不用于有效信息文字。 */
    val dim: Color,
    // §4.3 语义色
    val tx: Color,
    val txText: Color,
    val rxLocal: Color,
    val error: Color,
    val foreign: Color,
    val echo: Color,
    val ambiguous: Color,
    val dropped: Color,
    /** 预警、写入未知；必须配 WarnIcon + 文字。新增。 */
    val warning: Color,
    /** 刚刚完成的操作成功（别名 = rxLocal）。持续正常不着色。新增。 */
    val success: Color,
    /** 危险主按钮填充。新增。 */
    val dangerFill: Color,
    /** 危险主按钮文字。新增。 */
    val onDanger: Color,
    /** 语义浅底统一透明度（深 .10 / 浅 .07）。 */
    val washAlpha: Float,
    // §4.4 强调色（按主题取值）
    /** accent.fill：主按钮、开关开、选中填充。 */
    val accent: Color,
    /** accent.on：fill 上的文字。 */
    val onAccent: Color,
    /** accent.text：surface 上的选中文字 / 链接。 */
    val accentText: Color,
    /** accent.border：焦点环 / 选中描边。 */
    val accentBorder: Color,
) {
    /** 语义色浅底：全 App 只此一档（§4.3 washAlpha），文字用同一语义色。 */
    fun wash(color: Color): Color = color.copy(alpha = washAlpha)
}

/** 浅色主题固定值（§4.1–4.3）。 */
private object LightTokens {
    val bg = Color(0xFFF4F6F9)
    val surface1 = Color(0xFFFFFFFF)
    val surface2 = Color(0xFFEEF1F5)
    val surface3 = Color(0xFFE2E7EE)
    val line = Color(0xFFDCE2EA)
    val rowLine = Color(0xFFEDF0F4)
    val outlineControl = Color(0xFF7A8494)
    val island = Color(0xFFFFFFFF).copy(alpha = 0.95f)
    val scrim = Color(0xFF0A1423).copy(alpha = 0.38f)
    val text = Color(0xFF121A24)
    val muted = Color(0xFF5A6574)
    val dim = Color(0xFF7A8494)
    val tx = Color(0xFFF5A524)
    val txText = Color(0xFF955800)
    val rxLocal = Color(0xFF177334)
    val error = Color(0xFFCF222E)
    val foreign = Color(0xFF0969DA)
    val echo = Color(0xFF7A48D6)
    val ambiguous = Color(0xFFB3347F)
    val dropped = Color(0xFF656D76)
    val warning = Color(0xFF955800)
    val success = Color(0xFF177334)
    val dangerFill = Color(0xFFCF222E)
    val onDanger = Color(0xFFFFFFFF)
    const val WASH_ALPHA = 0.07f
}

/** 深色主题固定值（§4.1–4.3）。 */
private object DarkTokens {
    val bg = Color(0xFF0D1117)
    val surface1 = Color(0xFF151B23)
    val surface2 = Color(0xFF1C2430)
    val surface3 = Color(0xFF253041)
    val line = Color(0xFF2B3544)
    val rowLine = Color(0xFF1E2631)
    val outlineControl = Color(0xFF6E7A8A)
    val island = Color(0xFF1C2430).copy(alpha = 0.94f)
    val scrim = Color(0xFF010306).copy(alpha = 0.60f)
    val text = Color(0xFFE6EDF3)
    val muted = Color(0xFF8B98A8)
    val dim = Color(0xFF6E7A8A)
    val tx = Color(0xFFF5A524)
    val txText = Color(0xFFF5A524)
    val rxLocal = Color(0xFF3FB950)
    val error = Color(0xFFF85149)
    val foreign = Color(0xFF58A6FF)
    val echo = Color(0xFFA371F7)
    val ambiguous = Color(0xFFDB61A2)
    val dropped = Color(0xFF8B949E)
    val warning = Color(0xFFF5A524)
    val success = Color(0xFF3FB950)
    val dangerFill = Color(0xFFDA3633)
    val onDanger = Color(0xFFFFFFFF)
    const val WASH_ALPHA = 0.10f
}

private fun argb(value: Long): Color = Color(value.toInt())

fun modbusColors(dark: Boolean, accent: AccentColor): ModbusColors {
    val tones = accent.tones(dark)
    return if (dark) {
        DarkTokens.run {
            ModbusColors(
                isDark = true,
                bg = bg, surface1 = surface1, surface2 = surface2, surface3 = surface3,
                line = line, rowLine = rowLine, outlineControl = outlineControl, island = island, scrim = scrim,
                text = text, muted = muted, dim = dim,
                tx = tx, txText = txText, rxLocal = rxLocal, error = error, foreign = foreign, echo = echo,
                ambiguous = ambiguous, dropped = dropped, warning = warning, success = success,
                dangerFill = dangerFill, onDanger = onDanger, washAlpha = WASH_ALPHA,
                accent = argb(tones.fill), onAccent = argb(tones.on),
                accentText = argb(tones.text), accentBorder = argb(tones.border),
            )
        }
    } else {
        LightTokens.run {
            ModbusColors(
                isDark = false,
                bg = bg, surface1 = surface1, surface2 = surface2, surface3 = surface3,
                line = line, rowLine = rowLine, outlineControl = outlineControl, island = island, scrim = scrim,
                text = text, muted = muted, dim = dim,
                tx = tx, txText = txText, rxLocal = rxLocal, error = error, foreign = foreign, echo = echo,
                ambiguous = ambiguous, dropped = dropped, warning = warning, success = success,
                dangerFill = dangerFill, onDanger = onDanger, washAlpha = WASH_ALPHA,
                accent = argb(tones.fill), onAccent = argb(tones.on),
                accentText = argb(tones.text), accentBorder = argb(tones.border),
            )
        }
    }
}

val LocalModbusColors = staticCompositionLocalOf { modbusColors(dark = true, AccentColor.DEFAULT) }
