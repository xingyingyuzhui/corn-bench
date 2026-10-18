package io.github.xingyingyuzhui.modbusmobile.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.xingyingyuzhui.modbusmobile.application.AccentColor
import io.github.xingyingyuzhui.modbusmobile.ui.theme.modbusColors
import org.junit.Assert.assertTrue
import org.junit.Test

/** T-20261018-and-07 A4：开关关闭态的边界（outlineControl 描边 / 滑块）对 surface1 ≥3:1，深浅色都看得见（纯 JVM）。 */
class SwitchContrastTest {
    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return maxOf(la, lb) / minOf(la, lb).toDouble()
    }

    @Test
    fun offTrackOutlineIsVisibleOnSurface1InBothThemes() {
        for (dark in listOf(false, true)) {
            val c = modbusColors(dark, AccentColor.DEFAULT)
            val ratio = contrast(c.outlineControl, c.surface1)
            assertTrue("dark=$dark outlineControl/surface1 = $ratio", ratio >= 3.0)
        }
    }
}
