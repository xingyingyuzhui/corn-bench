package dev.corn.flashing.ui

import androidx.compose.ui.graphics.Color
import dev.corn.ui.theme.BenchAccent
import dev.corn.ui.theme.benchColors
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * “视觉零变化”的锁（第一期建立，第二期继续有效）。
 *
 * `CornColors` 第二期起是随主题解析的 `@Composable` getter（浅色 / 强调色都会变），
 * 所以这里分两层锁定：
 * 1. 纯 JVM：`benchColors(dark = true)`（玉米）必须逐项等于 Corn Flashing 0.1.8 的字面值；
 * 2. 源码扫描：`CornColors` 的每个字段必须映射到对应的语义 token 字段，不能悄悄换源。
 *
 * 要改烧录模式的颜色，先改设计规范，再有意更新这里。
 */
class CornColorsMatchTokensTest {
    private val legacy = mapOf(
        "Background" to (Color(0xFF121212) to "bg"),
        "Surface1" to (Color(0xFF1B1B1B) to "surface1"),
        "Surface2" to (Color(0xFF232323) to "surface2"),
        "Surface3" to (Color(0xFF2C2C2C) to "surface3"),
        "Line" to (Color(0xFF2F2F2F) to "line"),
        "Text" to (Color(0xFFF3EFE4) to "text"),
        "Dim" to (Color(0xFF9B968A) to "muted"),
        "Mute" to (Color(0xFF6D6961) to "dim"),
        "Corn" to (Color(0xFFF7C948) to "accent"),
        "CornDeep" to (Color(0xFFE9A93A) to "accentDeep"),
        "CornOn" to (Color(0xFF1A1606) to "onAccent"),
        "CornContainer" to (Color(0x1FF7C948) to "accentContainer"),
        "Leaf" to (Color(0xFF86A96F) to "success"),
        "LeafContainer" to (Color(0x2486A96F) to "successContainer"),
        "Red" to (Color(0xFFEF6B5B) to "danger"),
        "RedContainer" to (Color(0x21EF6B5B) to "dangerContainer"),
        "Blue" to (Color(0xFF7FB0E8) to "info"),
        "BlueContainer" to (Color(0x1F7FB0E8) to "infoContainer"),
        "NavBackground" to (Color(0xFF161616) to "navBackground"),
    )

    @Test
    fun darkCornTokensKeepTheLegacyValues() {
        val t = benchColors(dark = true, accent = BenchAccent.CORN)
        legacy.forEach { (name, spec) ->
            val (expected, token) = spec
            val actual = when (token) {
                "bg" -> t.bg; "surface1" -> t.surface1; "surface2" -> t.surface2; "surface3" -> t.surface3
                "line" -> t.line; "text" -> t.text; "muted" -> t.muted; "dim" -> t.dim
                "accent" -> t.accent; "accentDeep" -> t.accentDeep; "onAccent" -> t.onAccent
                "accentContainer" -> t.accentContainer
                "success" -> t.success; "successContainer" -> t.successContainer
                "danger" -> t.danger; "dangerContainer" -> t.dangerContainer
                "info" -> t.info; "infoContainer" -> t.infoContainer
                "navBackground" -> t.navBackground
                else -> error("未知 token: $token")
            }
            assertEquals("CornColors.$name ← token.$token", expected, actual)
        }
    }

    /** 每个 CornColors 字段都指向对应的 token 字段（第二期改成 getter 后仍然成立）。 */
    @Test
    fun everyCornColorsFieldMapsToItsTokenField() {
        val source = File("src/main/java/dev/corn/flashing/ui/Theme.kt").readText()
        legacy.forEach { (name, spec) ->
            val token = spec.second
            assertTrue(
                "CornColors.$name 应映射到 LocalBenchColors.current.$token",
                source.contains("val $name: Color") && source.contains("get() = LocalBenchColors.current.$token"),
            )
        }
    }

    /** 强调色换了之后，品牌槽位必须跟着变（否则第二期等于没开）。 */
    @Test
    fun accentSwapChangesOnlyTheAccentSlots() {
        val corn = benchColors(dark = true, accent = BenchAccent.CORN)
        val blue = benchColors(dark = true, accent = BenchAccent.BLUE)
        assertEquals(corn.surface1, blue.surface1)
        assertEquals(corn.text, blue.text)
        assertEquals(corn.danger, blue.danger)
        assertEquals(corn.success, blue.success)
        assertTrue("强调色应变化", corn.accent != blue.accent)
        assertTrue("强调色文字应变化", corn.accentText != blue.accentText)
        assertTrue("onAccent 应变化", corn.onAccent != blue.onAccent)
    }
}
