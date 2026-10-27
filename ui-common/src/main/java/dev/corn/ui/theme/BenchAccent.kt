package dev.corn.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 品牌强调色（第二期开放）。两套值分别对应深色 / 浅色表面；
 * 语义色里的 `accent*` 由它派生，界面代码仍然只读 `BenchColors`。
 *
 * 为什么不在 feature 里定义：外观是**外壳级**设置（两个模式共用一套主题），
 * 强调色必须和深浅色一样从 :ui-common 出发，否则上位机换了强调色、切到烧录又变回玉米色。
 */
enum class BenchAccent(val label: String) {
    /** 玉米黄：品牌默认，也是第一期的唯一取值。 */
    CORN("玉米"),

    BLUE("蓝"),
    GREEN("绿"),
    TEAL("青"),
    PURPLE("紫"),
    ;

    fun tones(dark: Boolean): AccentTones = if (dark) darkTones else lightTones

    private val lightTones: AccentTones
        get() = when (this) {
            CORN -> AccentTones(0xFFF7C948, on = 0xFF1A1606, text = 0xFF8A6200, border = 0xFF8A6200, deep = 0xFFE9A93A)
            BLUE -> AccentTones(0xFF1F6FEB, on = 0xFFFFFFFF, text = 0xFF0A60C9, border = 0xFF1F6FEB, deep = 0xFF1557C0)
            GREEN -> AccentTones(0xFF1A7F37, on = 0xFFFFFFFF, text = 0xFF177334, border = 0xFF1A7F37, deep = 0xFF156A2D)
            TEAL -> AccentTones(0xFF0B7373, on = 0xFFFFFFFF, text = 0xFF0B7373, border = 0xFF0B7373, deep = 0xFF095E5E)
            PURPLE -> AccentTones(0xFF8250DF, on = 0xFFFFFFFF, text = 0xFF6E40C9, border = 0xFF8250DF, deep = 0xFF6E40C9)
        }

    private val darkTones: AccentTones
        get() = when (this) {
            CORN -> AccentTones(0xFFF7C948, on = 0xFF1A1606, text = 0xFFF7C948, border = 0xFFF7C948, deep = 0xFFE9A93A)
            BLUE -> AccentTones(0xFF4493F8, on = 0xFF0D1117, text = 0xFF4493F8, border = 0xFF4493F8, deep = 0xFF2F7FE0)
            GREEN -> AccentTones(0xFF2DA44E, on = 0xFF0D1117, text = 0xFF2DA44E, border = 0xFF2DA44E, deep = 0xFF248A41)
            TEAL -> AccentTones(0xFF14A3A3, on = 0xFF0D1117, text = 0xFF14A3A3, border = 0xFF14A3A3, deep = 0xFF108C8C)
            PURPLE -> AccentTones(0xFFA371F7, on = 0xFF0D1117, text = 0xFFA371F7, border = 0xFFA371F7, deep = 0xFF8B5CF6)
        }

    companion object {
        /** 第一期只有玉米；第二期默认仍是玉米（品牌色）。 */
        val DEFAULT: BenchAccent = CORN
    }
}

/**
 * 一个主题下的强调色取值。
 *
 * @param fill 主按钮、开关开、选中填充
 * @param on fill 上的文字
 * @param text surface 上的选中文字 / 链接（浅色主题需要更深才够对比度）
 * @param border 焦点环 / 选中描边
 * @param deep 渐变 / 按下态用的深一档
 */
data class AccentTones(
    val fill: Long,
    val on: Long,
    val text: Long,
    val border: Long,
    val deep: Long,
)

fun colorOf(argb: Long): Color = Color(argb.toInt())
