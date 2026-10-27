package io.github.xingyingyuzhui.modbusmobile.ui.theme

import androidx.compose.ui.graphics.Color
import dev.corn.ui.theme.benchColors
import io.github.xingyingyuzhui.modbusmobile.application.AccentColor
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 第一期换皮锁：`ModbusColors` 的每个字段必须严格等于 :ui-common 语义 token 的对应值。
 *
 * 这样“换皮”就不会悄悄漏掉某个字段，也不会有人把旧色值写回来。纯 JVM，不跑 Robolectric。
 */
class ModbusColorsMatchTokensTest {
    @Test
    fun darkFieldsMatchUiCommonTokens() = assertMapping(dark = true)

    @Test
    fun lightFieldsMatchUiCommonTokens() = assertMapping(dark = false)

    @Test
    fun accentParameterNoLongerChangesColors() {
        for (accent in AccentColor.entries) {
            assertEquals(modbusColors(true, AccentColor.AMBER), modbusColors(true, accent))
            assertEquals(modbusColors(false, AccentColor.AMBER), modbusColors(false, accent))
        }
    }

    private fun assertMapping(dark: Boolean) {
        val t = benchColors(dark)
        val c = modbusColors(dark)
        val pairs = listOf(
            "isDark" to (t.isDark to c.isDark),
            "bg" to (t.bg to c.bg),
            "surface1" to (t.surface1 to c.surface1),
            "surface2" to (t.surface2 to c.surface2),
            "surface3" to (t.surface3 to c.surface3),
            "line" to (t.line to c.line),
            "rowLine" to (t.rowLine to c.rowLine),
            "outlineControl" to (t.outlineControl to c.outlineControl),
            "island" to (t.island to c.island),
            "scrim" to (t.scrim to c.scrim),
            "text" to (t.text to c.text),
            "muted" to (t.muted to c.muted),
            "dim" to (t.dim to c.dim),
            "tx" to (t.tx to c.tx),
            "txText" to (t.txText to c.txText),
            "rxLocal" to (t.rx to c.rxLocal),
            "error" to (t.danger to c.error),
            "foreign" to (t.foreign to c.foreign),
            "echo" to (t.echo to c.echo),
            "ambiguous" to (t.ambiguous to c.ambiguous),
            "dropped" to (t.dropped to c.dropped),
            "warning" to (t.warning to c.warning),
            "success" to (t.success to c.success),
            "dangerFill" to (t.dangerFill to c.dangerFill),
            "onDanger" to (t.onDanger to c.onDanger),
            "accent" to (t.accent to c.accent),
            "onAccent" to (t.onAccent to c.onAccent),
            "accentText" to (t.accentText to c.accentText),
            "accentBorder" to (t.accentBorder to c.accentBorder),
        )
        for ((name, value) in pairs) {
            val (expected, actual) = value
            assertEquals(if (dark) "dark.$name" else "light.$name", expected, actual)
        }
        assertEquals("washAlpha", t.washAlpha, c.washAlpha, 0f)
        assertEquals("accent 固定玉米色", t.accent, c.accent)
    }

    /** 旧 Modbus 琥珀 #F5A524 与玉米 #F7C948 太近；TX 必须是新的青绿 #4FD1C5（board 决策）。 */
    @Test
    fun txIsTheAgreedTealNotTheOldAmber() {
        val c = modbusColors(true)
        assertEquals(Color(0xFF4FD1C5), c.tx)
        assertEquals(Color(0xFF4FD1C5), c.txText)
    }
}
