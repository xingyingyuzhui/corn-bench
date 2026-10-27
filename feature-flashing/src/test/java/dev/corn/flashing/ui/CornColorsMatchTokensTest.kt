package dev.corn.flashing.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 第一期“视觉零变化”的锁：CornColors 改为从 :ui-common token 取值后，每个字段必须与 Corn Flashing 0.1.8 的字面值相同。
 * 要改烧录模式的颜色，先改设计规范，再有意更新这里。
 */
class CornColorsMatchTokensTest {
    private val legacy = mapOf(
        "Background" to Color(0xFF121212),
        "Surface1" to Color(0xFF1B1B1B),
        "Surface2" to Color(0xFF232323),
        "Surface3" to Color(0xFF2C2C2C),
        "Line" to Color(0xFF2F2F2F),
        "Text" to Color(0xFFF3EFE4),
        "Dim" to Color(0xFF9B968A),
        "Mute" to Color(0xFF6D6961),
        "Corn" to Color(0xFFF7C948),
        "CornDeep" to Color(0xFFE9A93A),
        "CornOn" to Color(0xFF1A1606),
        "CornContainer" to Color(0x1FF7C948),
        "Leaf" to Color(0xFF86A96F),
        "LeafContainer" to Color(0x2486A96F),
        "Red" to Color(0xFFEF6B5B),
        "RedContainer" to Color(0x21EF6B5B),
        "Blue" to Color(0xFF7FB0E8),
        "BlueContainer" to Color(0x1F7FB0E8),
        "NavBackground" to Color(0xFF161616),
    )

    private val current = with(CornColors) {
        mapOf(
            "Background" to Background, "Surface1" to Surface1, "Surface2" to Surface2, "Surface3" to Surface3,
            "Line" to Line, "Text" to Text, "Dim" to Dim, "Mute" to Mute, "Corn" to Corn, "CornDeep" to CornDeep,
            "CornOn" to CornOn, "CornContainer" to CornContainer, "Leaf" to Leaf, "LeafContainer" to LeafContainer,
            "Red" to Red, "RedContainer" to RedContainer, "Blue" to Blue, "BlueContainer" to BlueContainer,
            "NavBackground" to NavBackground,
        )
    }

    @Test
    fun everyFieldKeepsItsLegacyValue() {
        assertEquals(legacy.keys, current.keys)
        legacy.forEach { (name, expected) -> assertEquals(name, expected, current.getValue(name)) }
    }
}
