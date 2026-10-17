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

val Mono = TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
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
 * 字号层级（设计规范_v1 §3.1，16 档）。中文用系统字体；数字、地址、HEX 用 JetBrains Mono。
 * 最小字号 11sp；字重只用 400 / 500（仅等宽）/ 600 / 700。
 * 现有各屏字号先不替换（CL-14 机械替换），本对象只是落点；映射见 §3.4。
 */
object ModbusType {
    /** 20/26 Bold：PageHeader / SubPageHeader / 编辑页标题（原 20）。 */
    val headline = sans(20.sp, 26.sp, FontWeight.Bold)

    /** 17/24 Bold：弹层与对话框标题（原 ConfirmSheet 18/24、AlertDialog 18）。 */
    val titleL = sans(17.sp, 24.sp, FontWeight.Bold)

    /** 16/22 Bold：横屏页标题、帧详情标题、曲线全屏标题（原 16）。 */
    val titleM = sans(16.sp, 22.sp, FontWeight.Bold)

    /** 15/20 SemiBold：提示卡标题、开关/灯状态文字（原 15）。 */
    val titleS = sans(15.sp, 20.sp, FontWeight.SemiBold)

    /** 14/20 Regular：正文、设置行、设备名（原 14）。强调用 [bodyStrong]。 */
    val body = sans(14.sp, 20.sp, FontWeight.Normal)

    /** 14/20 SemiBold：body 的 600 字重（同一档）。 */
    val bodyStrong = body.copy(fontWeight = FontWeight.SemiBold)

    /** 13/18 Regular：说明段落、空态正文（原 13/19、13.5）。 */
    val bodyS = sans(13.sp, 18.sp, FontWeight.Normal)

    /** 12/17 Regular：SectionLabel、chip、SmallButton、输入错误、提示（原 12.5、11.5、12）。 */
    val label = sans(12.sp, 17.sp, FontWeight.Normal)

    /** 11/15 Regular：输入格标签、表头、Dock 标签；全 App 最小字号（原 10、10.5、9.5、11）。badge 用 [captionStrong]。 */
    val caption = sans(11.sp, 15.sp, FontWeight.Normal)

    /** 11/15 SemiBold：badge。 */
    val captionStrong = caption.copy(fontWeight = FontWeight.SemiBold)

    /** 16/22 Bold：52dp 主/次按钮。 */
    val button = sans(16.sp, 22.sp, FontWeight.Bold)

    /** 14/20 Bold：44dp 紧凑按钮。 */
    val buttonCompact = sans(14.sp, 20.sp, FontWeight.Bold)

    /** 26/32 SemiBold Mono：面板数值卡主值。 */
    val monoDisplay = mono(26.sp, 32.sp, FontWeight.SemiBold)

    /** 22/28 SemiBold Mono：写入确认 当前→新值、曲线全屏当前值、仪表中心值。 */
    val monoValueL = mono(22.sp, 28.sp, FontWeight.SemiBold)

    /** 17/22 SemiBold Mono：输入格值、FieldTile、结果表值、统计格。 */
    val monoValueM = mono(17.sp, 22.sp, FontWeight.SemiBold)

    /** 13/18 Medium Mono：地址、最近写入摘要、HEX 帧（原 Mono 13 无行高）。 */
    val monoBody = mono(13.sp, 18.sp, FontWeight.Medium)

    /** 12/16 Medium Mono：HEX 列、日志时间/字节、会话条副行（原 11.5、12.5）。 */
    val monoSmall = mono(12.sp, 16.sp, FontWeight.Medium)

    /** 11/15 Medium Mono：面板 meta、参考号小字、坐标轴、计数（原 10、10.5）。 */
    val monoCaption = mono(11.sp, 15.sp, FontWeight.Medium)
}
