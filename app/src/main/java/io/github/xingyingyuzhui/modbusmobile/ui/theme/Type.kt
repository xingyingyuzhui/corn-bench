package io.github.xingyingyuzhui.modbusmobile.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.github.xingyingyuzhui.modbusmobile.R

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    // G#9：稿面数值/统计字 weight 600。由官方可变字体实例化出 wght=600（OFL，许可见 licenses/）。
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
)

/** §3.2-4：行高写进 TextStyle，不裁顶（中文 1.3× 时不被裁）。 */
private val NoTrim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun sans(size: TextUnit, line: TextUnit, weight: FontWeight) = TextStyle(
    fontSize = size,
    lineHeight = line,
    fontWeight = weight,
    lineHeightStyle = NoTrim,
)

private fun mono(size: TextUnit, line: TextUnit, weight: FontWeight) = TextStyle(
    fontFamily = JetBrainsMono,
    fontSize = size,
    lineHeight = line,
    fontWeight = weight,
    lineHeightStyle = NoTrim,
)

/**
 * 字号层级（设计规范_v2 §3.1，19 档；tokens_v2.json type）。中文用系统字体；数字、地址、HEX 用 JetBrains Mono。
 * 最小字号 11sp；字重只用 400 / 500（仅等宽）/ 600 / 700。行高都是 4 的倍数。
 * 现有各屏字号先不替换（CL-14 机械替换），本对象只是落点；映射见 §3.4。
 * [bodyStrong] / [labelStrong] / [captionStrong] 是同一档的 600 字重（v2 表里“Regular / SemiBold”），不算新档。
 */
object ModbusType {
    /** 20/28 Bold：PageHeader / SubPageHeader / 编辑页标题。 */
    val headline = sans(20.sp, 28.sp, FontWeight.Bold)

    /** 18/24 Bold：弹层与对话框标题（v1 曾定 17，v2 改回 18）。 */
    val titleL = sans(18.sp, 24.sp, FontWeight.Bold)

    /** 16/24 Bold：横屏详情页标题、帧详情标题、曲线全屏标题。 */
    val titleM = sans(16.sp, 24.sp, FontWeight.Bold)

    /** 15/20 SemiBold：提示卡标题、开关/灯状态文字、空态标题。 */
    val titleS = sans(15.sp, 20.sp, FontWeight.SemiBold)

    /** 14/20 Regular：正文、设置行、设备名。 */
    val body = sans(14.sp, 20.sp, FontWeight.Normal)

    /** 14/20 SemiBold：body 的 600 字重。 */
    val bodyStrong = body.copy(fontWeight = FontWeight.SemiBold)

    /** 13/20 Regular：说明段落、空态正文、设置行右侧值（原 13/18、13/19、13.5）。 */
    val bodyS = sans(13.sp, 20.sp, FontWeight.Normal)

    /** 12/16 Regular：SectionLabel、chip、SmallButton、输入错误、提示（原 12.5/17、11.5）。 */
    val label = sans(12.sp, 16.sp, FontWeight.Normal)

    /** 12/16 SemiBold：label 的 600 字重（SmallButton、TextLink）。 */
    val labelStrong = label.copy(fontWeight = FontWeight.SemiBold)

    /** 11/16 Regular：输入格标签、表头、Dock 标签；全 App 最小字号（原 10、10.5、9.5；行高 14/15）。 */
    val caption = sans(11.sp, 16.sp, FontWeight.Normal)

    /** 11/16 SemiBold：badge。 */
    val captionStrong = caption.copy(fontWeight = FontWeight.SemiBold)

    /** 22/28 Bold：状态文字组件的主值（“制冷”“运行”）。 */
    val statusText = sans(22.sp, 28.sp, FontWeight.Bold)

    /** 16/24 Bold：52dp 主/次按钮。 */
    val button = sans(16.sp, 24.sp, FontWeight.Bold)

    /** 14/20 Bold：44dp 紧凑按钮（次要场合 SemiBold 用 copy）。 */
    val buttonCompact = sans(14.sp, 20.sp, FontWeight.Bold)

    /** 26/32 SemiBold Mono：面板数值卡主值、向导预览值。 */
    val monoDisplay = mono(26.sp, 32.sp, FontWeight.SemiBold)

    /** 22/28 SemiBold Mono：写入确认 当前→新值、曲线全屏当前值。 */
    val monoValueL = mono(22.sp, 28.sp, FontWeight.SemiBold)

    /** 20/24 SemiBold Mono：仪表中心值（v2 新增，v1 曾并入 22，撤回）。 */
    val monoGauge = mono(20.sp, 24.sp, FontWeight.SemiBold)

    /** 17/24 SemiBold Mono：输入格值、FieldTile、结果表值、统计格（行高 22→24）。 */
    val monoValueM = mono(17.sp, 24.sp, FontWeight.SemiBold)

    /** 15/20 SemiBold Mono：面板卡内数值标题。 */
    val monoTitle = mono(15.sp, 20.sp, FontWeight.SemiBold)

    /** 13/20 Medium Mono：地址、最近写入摘要、HEX 帧（= 旧 [Mono]，补行高 20）。 */
    val monoBody = mono(13.sp, 20.sp, FontWeight.Medium)

    /** 12/16 Medium Mono：HEX 列、日志时间/字节、会话条副行（原 11.5、12.5）。 */
    val monoSmall = mono(12.sp, 16.sp, FontWeight.Medium)

    /** 11/16 Medium Mono：面板 meta、参考号小字、坐标轴、计数（原 10、10.5）。 */
    val monoCaption = mono(11.sp, 16.sp, FontWeight.Medium)
}

/**
 * 旧写法 `style = Mono`（§3.4 映射表第 2 行）：= [ModbusType.monoBody]。
 * 必须是 getter：写成初始化式时，若先访问 ModbusType，它的初始化会触发本文件的静态初始化，
 * 此时 ModbusType 还没初始化完，Mono 会拿到 null（云端单测 3321b3f50f3648b4 抓到）。
 */
val Mono: TextStyle get() = ModbusType.monoBody
