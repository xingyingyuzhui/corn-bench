package io.github.xingyingyuzhui.modbusmobile.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import dev.corn.ui.theme.benchColors
import io.github.xingyingyuzhui.modbusmobile.application.AccentColor

/**
 * 颜色 token。Corn Bench 第一期起，值全部来自 :ui-common 的语义 token（[benchColors]），
 * 本类型只保留 Corn Modbus 的字段名，让各屏 246 处调用点不需要改名。
 *
 * 浅色一档也走 :ui-common 的暂定浅色（第一期界面只开放深色，浅色代码留给第二期）。
 * [AccentColor] 参数为了兼容旧调用点保留，但不再生效：强调色固定为玉米色（token.accent）。
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

/**
 * 把 :ui-common 的语义 token 映射到 Corn Modbus 的字段名。
 * [accent] 第一期不再生效（强调色固定为玉米色）；保留参数只是为了避免改所有预览 / 测试调用点。
 */
@Suppress("UNUSED_PARAMETER")
fun modbusColors(dark: Boolean, accent: AccentColor = AccentColor.DEFAULT): ModbusColors {
    val t = benchColors(dark)
    return ModbusColors(
        isDark = t.isDark,
        bg = t.bg,
        surface1 = t.surface1,
        surface2 = t.surface2,
        surface3 = t.surface3,
        line = t.line,
        rowLine = t.rowLine,
        outlineControl = t.outlineControl,
        island = t.island,
        scrim = t.scrim,
        text = t.text,
        muted = t.muted,
        dim = t.dim,
        tx = t.tx,
        txText = t.txText,
        rxLocal = t.rx,
        error = t.danger,
        foreign = t.foreign,
        echo = t.echo,
        ambiguous = t.ambiguous,
        dropped = t.dropped,
        warning = t.warning,
        success = t.success,
        dangerFill = t.dangerFill,
        onDanger = t.onDanger,
        washAlpha = t.washAlpha,
        accent = t.accent,
        onAccent = t.onAccent,
        accentText = t.accentText,
        accentBorder = t.accentBorder,
    )
}

val LocalModbusColors = staticCompositionLocalOf { modbusColors(dark = true) }
