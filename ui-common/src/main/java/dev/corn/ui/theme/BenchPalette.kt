package dev.corn.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 第 1 层：色板（原始色值，不带语义）。界面代码不直接用这里的值，只用 [BenchColors] 的语义字段。
 *
 * 深色一档取自 Corn Flashing 已定稿的炭黑 + 玉米观感（原 `CornColors`，值逐个对齐，Flashing 第一期视觉零变化）；
 * 报文语义色（tx / foreign / echo / ambiguous / dropped）是 Corn Bench 新增，挑选时保证与玉米黄、叶绿、红互相可分。
 * 浅色一档是第二期设计规范出来之前的暂定值：第一期只开放深色，浅色只要求“能承载、对比度达标”。
 */
object BenchPalette {
    // ---- 炭黑（深色表面） ----
    val Charcoal950 = Color(0xFF121212) // 页面底
    val Charcoal925 = Color(0xFF161616) // 底部导航底（Flashing NavBackground）
    val Charcoal900 = Color(0xFF1B1B1B) // surface1：卡片
    val Charcoal850 = Color(0xFF232323) // surface2：输入格、按钮、dock
    val Charcoal825 = Color(0xFF242424) // 行分隔线（比卡片略亮，比描边暗）
    val Charcoal800 = Color(0xFF2C2C2C) // surface3：进度条底轨
    val Charcoal750 = Color(0xFF2F2F2F) // 描边
    val Ash500 = Color(0xFF77726A) // 控件边界（对 Charcoal900 ≥ 3:1）

    // ---- 暖白文字 ----
    val Cream50 = Color(0xFFF3EFE4) // 正文
    val Stone400 = Color(0xFF9B968A) // 次要文字（Flashing Dim）
    val Stone600 = Color(0xFF6D6961) // 弱化 / 禁用（Flashing Mute）

    // ---- 玉米（品牌强调色） ----
    val Corn400 = Color(0xFFF7C948)
    val Corn500 = Color(0xFFE9A93A)
    val Corn950 = Color(0xFF1A1606) // 玉米底上的文字
    val Corn700 = Color(0xFF8A6200) // 浅色主题里玉米色文字（对白底 ≥ 4.5:1）

    // ---- 功能色（深色） ----
    val Leaf400 = Color(0xFF86A96F)
    val Red400 = Color(0xFFEF6B5B)
    val Red600 = Color(0xFFCF3F2E)
    val Sky400 = Color(0xFF7FB0E8)

    /**
     * 报文 TX：青绿。原 Modbus 的 TX 琥珀 #F5A524 与玉米 #F7C948 色差只有 ΔE≈19，几乎分不清；
     * 青绿与玉米 ΔE≈83、与 RX 叶绿 ΔE≈36、与外来帧天蓝 ΔE≈47，在 #1B1B1B 上对比度 9.2:1。
     */
    val Teal400 = Color(0xFF4FD1C5)
    val Violet400 = Color(0xFFB08CF0)
    val Pink400 = Color(0xFFE07AB5)
    val Taupe400 = Color(0xFF8E8A80)

    // ---- 浅色（暂定，第二期替换） ----
    val Paper50 = Color(0xFFF6F4EE)
    val Paper0 = Color(0xFFFFFFFF)
    val Paper100 = Color(0xFFEFECE4)
    val Paper200 = Color(0xFFE4E0D6)
    val Paper300 = Color(0xFFDCD7CB)
    val Paper150 = Color(0xFFECE9E1)
    val Ink900 = Color(0xFF1C1A15)
    val Ink600 = Color(0xFF5F5A4F)
    val Ink500 = Color(0xFF7D776B)
    val Ink400 = Color(0xFF8A8578)
    val Leaf700 = Color(0xFF3F6B2A)
    val Red700 = Color(0xFFC4372A)
    val Sky700 = Color(0xFF2F6DB5)
    val Teal700 = Color(0xFF0B6E68)
    val Violet700 = Color(0xFF7A48D6)
    val Pink700 = Color(0xFFB3347F)
    val Taupe700 = Color(0xFF6F6A60)
}
