package io.github.xingyingyuzhui.modbusmobile.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import io.github.xingyingyuzhui.modbusmobile.application.AccentColor
import io.github.xingyingyuzhui.modbusmobile.application.DensityMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * T-20261018-and-06（CL-01）：ModbusColors / ModbusSpacing / 圆角 / ModbusType 与 ux 的 tokens_v1.json 逐项比对。
 * 纯 JVM 测试（不走 Robolectric），不受 ARM64 Conscrypt 环境问题（B-3）影响。
 * json 是 /workspace/handoff/ux/outbox/T-20261018-and-03/T-20261018-and-03_tokens_v1.json 的原样拷贝。
 */
class DesignTokensTest {
    @Suppress("UNCHECKED_CAST")
    private val tokens: Map<String, Any?> by lazy {
        val text = javaClass.classLoader!!.getResourceAsStream("design/T-20261018-and-03_tokens_v1.json")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        MiniJson(text).parse() as Map<String, Any?>
    }

    @Suppress("UNCHECKED_CAST")
    private fun obj(vararg path: String): Map<String, Any?> =
        path.fold(tokens) { acc, key -> acc[key] as Map<String, Any?> }

    private fun hex(c: Color): String = "#%06X".format(c.toArgb() and 0xFFFFFF)

    private fun parseHex(s: String): Int = s.removePrefix("#").toLong(16).toInt()

    private fun pick(name: String, f: (ModbusColors) -> Color): Pair<String, (ModbusColors) -> Color> = name to f

    private val colorFields: Map<String, (ModbusColors) -> Color> = mapOf(
        pick("bg") { it.bg },
        pick("surface1") { it.surface1 },
        pick("surface2") { it.surface2 },
        pick("surface3") { it.surface3 },
        pick("line") { it.line },
        pick("rowLine") { it.rowLine },
        pick("outlineControl") { it.outlineControl },
        pick("text") { it.text },
        pick("muted") { it.muted },
        pick("dim") { it.dim },
        pick("tx") { it.tx },
        pick("txText") { it.txText },
        pick("rxLocal") { it.rxLocal },
        pick("error") { it.error },
        pick("foreign") { it.foreign },
        pick("echo") { it.echo },
        pick("ambiguous") { it.ambiguous },
        pick("dropped") { it.dropped },
        pick("warning") { it.warning },
        pick("success") { it.success },
        pick("dangerFill") { it.dangerFill },
        pick("onDanger") { it.onDanger },
        pick("island") { it.island },
        pick("scrim") { it.scrim },
    )

    @Test
    fun bothThemesMatchTokensJsonColorByColor() {
        for (theme in listOf("light", "dark")) {
            val colors = modbusColors(theme == "dark", AccentColor.AMBER)
            val table = obj("color", theme)
            assertEquals("$theme：json 里每个颜色 token 都有 Kotlin 字段", table.keys, colorFields.keys)
            for ((name, field) in colorFields) {
                val spec = table[name] as Map<*, *>
                val actual = field(colors)
                assertEquals("$theme.$name", (spec["value"] as String).uppercase(), hex(actual))
                val alpha = (spec["alpha"] as Number?)?.toFloat() ?: 1f
                assertEquals("$theme.$name alpha", alpha, actual.alpha, 0.005f)
            }
        }
    }

    @Test
    fun washAlphaIsOneValuePerTheme() {
        val wash = obj("color", "washAlpha")
        val dark = modbusColors(true, AccentColor.AMBER)
        val light = modbusColors(false, AccentColor.AMBER)
        assertEquals((wash["dark"] as Number).toFloat(), dark.washAlpha, 0.0001f)
        assertEquals((wash["light"] as Number).toFloat(), light.washAlpha, 0.0001f)
        // 所有语义色同一档
        for (c in listOf(dark.tx, dark.rxLocal, dark.error, dark.dropped, dark.accent)) {
            assertEquals(0.10f, dark.wash(c).alpha, 0.0001f)
        }
        for (c in listOf(light.tx, light.rxLocal, light.error, light.dropped, light.accent)) {
            assertEquals(0.07f, light.wash(c).alpha, 0.0001f)
        }
    }

    @Test
    fun everyAccentMatchesTokensJsonPerTheme() {
        val table = obj("color", "accent")
        assertEquals(table.keys, AccentColor.entries.map { it.name.lowercase() }.toSet())
        for (accent in AccentColor.entries) {
            for (theme in listOf("light", "dark")) {
                val spec = obj("color", "accent", accent.name.lowercase(), theme)
                val c = modbusColors(theme == "dark", accent)
                val label = "${accent.name}.$theme"
                assertEquals("$label.fill", (spec["fill"] as String).uppercase(), hex(c.accent))
                assertEquals("$label.on", (spec["on"] as String).uppercase(), hex(c.onAccent))
                assertEquals("$label.text", (spec["text"] as String).uppercase(), hex(c.accentText))
                assertEquals("$label.border", (spec["border"] as String).uppercase(), hex(c.accentBorder))
                assertEquals(parseHex(spec["fill"] as String), c.accent.toArgb() and 0xFFFFFF)
            }
        }
    }

    @Test
    fun defaultAccentIsAmberPendingD1() {
        assertEquals(AccentColor.AMBER, AccentColor.DEFAULT)
        assertEquals(AccentColor.AMBER, UiSettings().accent)
        assertEquals(5, AccentColor.entries.size) // D2：默认保留 5 个
    }

    @Test
    fun fontScaleStillMultipliesPendingD3() {
        assertNull(FONT_SCALE_CAP)
        assertEquals(1.69f, totalFontScale(1.3f, 1.3f), 0.0001f)
    }

    @Test
    fun spacingScaleMatchesTokensJson() {
        val sp = obj("spacing")
        fun dp(key: String) = ((sp[key] as Map<*, *>)["dp"] as Number).toFloat()
        val s = ModbusSpacing.Comfortable
        assertEquals(dp("2xs"), s.xxs.value, 0f)
        assertEquals(dp("xs"), s.xs.value, 0f)
        assertEquals(dp("s"), s.s.value, 0f)
        assertEquals(dp("m"), s.gapS.value, 0f)
        assertEquals(dp("l"), s.gapM.value, 0f)
        assertEquals(dp("xl"), s.card.value, 0f)
        assertEquals(dp("page"), s.page.value, 0f)
        assertEquals(dp("2xl"), s.section.value, 0f)
        assertEquals(dp("3xl"), s.xxl.value, 0f)
    }

    @Test
    fun compactDensityIsALookupTable12_6_8() {
        val sp = obj("spacing")
        fun dp(key: String) = ((sp[key] as Map<*, *>)["dp"] as Number).toFloat()
        val c = ModbusSpacing.of(DensityMode.COMPACT)
        assertEquals(12f, c.page.value, 0f)
        assertEquals(6f, c.gapS.value, 0f)
        assertEquals(8f, c.gapM.value, 0f)
        assertEquals(dp("compact.page"), c.page.value, 0f)
        assertEquals(dp("compact.gapS"), c.gapS.value, 0f)
        assertEquals(dp("compact.gapM"), c.gapM.value, 0f)
        // 卡片内边距等不随密度变
        assertEquals(12f, c.card.value, 0f)
        assertEquals(ModbusSpacing.Comfortable, ModbusSpacing.of(DensityMode.COMFORTABLE))
    }

    @Test
    fun radiiMatchTokensJson() {
        val r = obj("radius")
        fun dp(key: String) = ((r[key] as Map<*, *>)["dp"] as Number).toFloat()
        assertEquals(dp("badge"), BadgeRadius.value, 0f)
        assertEquals(dp("iconBlock"), IconBlockRadius.value, 0f)
        assertEquals(dp("field"), FieldRadius.value, 0f)
        assertEquals(dp("row"), RowRadius.value, 0f)
        assertEquals(dp("button"), ButtonRadius.value, 0f)
        assertEquals(dp("card"), CardRadius.value, 0f)
        assertEquals(dp("chip"), ChipRadius.value, 0f)
        assertEquals(dp("sheet"), SheetRadius.value, 0f)
        assertEquals(dp("island"), IslandRadius.value, 0f)
    }

    @Test
    fun typeScaleMatchesTokensJson() {
        val styles: Map<String, TextStyle> = mapOf(
            "headline" to ModbusType.headline, "titleL" to ModbusType.titleL, "titleM" to ModbusType.titleM,
            "titleS" to ModbusType.titleS, "body" to ModbusType.body, "bodyS" to ModbusType.bodyS,
            "label" to ModbusType.label, "caption" to ModbusType.caption, "button" to ModbusType.button,
            "buttonCompact" to ModbusType.buttonCompact, "mono.display" to ModbusType.monoDisplay,
            "mono.valueL" to ModbusType.monoValueL, "mono.valueM" to ModbusType.monoValueM,
            "mono.body" to ModbusType.monoBody, "mono.small" to ModbusType.monoSmall,
            "mono.caption" to ModbusType.monoCaption,
        )
        val t = obj("type")
        assertEquals(t.keys, styles.keys)
        for ((key, style) in styles) {
            val spec = t[key] as Map<*, *>
            assertEquals("$key size", (spec["sizeSp"] as Number).toFloat(), style.fontSize.value, 0f)
            assertEquals("$key lineHeight", (spec["lineHeightSp"] as Number).toFloat(), style.lineHeight.value, 0f)
            // 第一个字重（"Bold 700 / ..."）
            val weight = Regex("(\\d{3})").find(spec["weight"] as String)!!.value.toInt()
            assertEquals("$key weight", FontWeight(weight), style.fontWeight)
            val mono = (spec["family"] as String).contains("Mono")
            assertEquals("$key family", if (mono) JetBrainsMono else null, style.fontFamily)
            assertNotNull("$key lineHeightStyle", style.lineHeightStyle)
            assert(style.fontSize.value >= 11f) { "$key < 11sp" }
        }
    }
}

/** 测试用的最小 JSON 解析器（只要对象 / 数组 / 字符串 / 数字 / true/false/null）。不引入依赖。 */
private class MiniJson(private val s: String) {
    private var i = 0

    fun parse(): Any? {
        val v = value()
        ws()
        check(i == s.length) { "trailing data at $i" }
        return v
    }

    private fun ws() {
        while (i < s.length && s[i].isWhitespace()) i++
    }

    private fun value(): Any? {
        ws()
        return when (val c = s[i]) {
            '{' -> obj()
            '[' -> arr()
            '"' -> str()
            't' -> lit("true", true)
            'f' -> lit("false", false)
            'n' -> lit("null", null)
            else -> if (c == '-' || c.isDigit()) num() else error("bad char '$c' at $i")
        }
    }

    private fun lit(word: String, v: Any?): Any? {
        check(s.startsWith(word, i)) { "expected $word at $i" }
        i += word.length
        return v
    }

    private fun obj(): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        i++
        ws()
        if (s[i] == '}') { i++; return out }
        while (true) {
            ws()
            val k = str()
            ws(); check(s[i] == ':'); i++
            out[k] = value()
            ws()
            when (s[i++]) {
                ',' -> continue
                '}' -> return out
                else -> error("bad object at $i")
            }
        }
    }

    private fun arr(): List<Any?> {
        val out = ArrayList<Any?>()
        i++
        ws()
        if (s[i] == ']') { i++; return out }
        while (true) {
            out += value()
            ws()
            when (s[i++]) {
                ',' -> continue
                ']' -> return out
                else -> error("bad array at $i")
            }
        }
    }

    private fun str(): String {
        check(s[i] == '"')
        i++
        val sb = StringBuilder()
        while (true) {
            val c = s[i++]
            when (c) {
                '"' -> return sb.toString()
                '\\' -> {
                    when (val e = s[i++]) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> sb.append('\r')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                        else -> sb.append(e)
                    }
                }
                else -> sb.append(c)
            }
        }
    }

    private fun num(): Number {
        val start = i
        while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
        val t = s.substring(start, i)
        return if (t.any { it in ".eE" }) t.toDouble() else t.toLong()
    }
}
