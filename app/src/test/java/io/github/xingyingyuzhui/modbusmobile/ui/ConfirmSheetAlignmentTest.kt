package io.github.xingyingyuzhui.modbusmobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.xingyingyuzhui.modbusmobile.application.FontScale
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.ui.components.ConfirmSheetBody
import io.github.xingyingyuzhui.modbusmobile.ui.components.ConfirmValueRow
import io.github.xingyingyuzhui.modbusmobile.ui.components.SegmentedTextControl
import io.github.xingyingyuzhui.modbusmobile.ui.components.SemanticKind
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 写入确认弹层的对齐断言（技术总工 2026-10-17 实机反馈：“很多元素都没对齐”）。
 * 断言的是布局位置（getBoundsInRoot），不是截图；字形在行框内是否居中靠 Roborazzi 图目测。
 * 场景：标准 / 字体 1.3 + 窄屏 / 带“来自 N 秒前”小字 / 线圈。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class ConfirmSheetAlignmentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun register_with_note_dark() = register(ThemeMode.DARK, FontScale.STANDARD, "register_with_note_dark_360")

    @Test fun register_with_note_light() = register(ThemeMode.LIGHT, FontScale.STANDARD, "register_with_note_light_360")

    @Test @Config(qualifiers = "w320dp-h640dp")
    fun register_font13_narrow_dark() = register(ThemeMode.DARK, FontScale.EXTRA, "register_font13_narrow_dark_320")

    @Test @Config(qualifiers = "w320dp-h640dp")
    fun register_font13_narrow_light() = register(ThemeMode.LIGHT, FontScale.EXTRA, "register_font13_narrow_light_320")

    @Test
    fun valueColumnsKeepTheirAlignmentWithoutTheNote() {
        // 没有小字时，数值行的位置和有小字时（上面的测试）规则一致：两列同顶同基线。
        show(ThemeMode.DARK, FontScale.STANDARD) { Row(note = null, hint = null) }
        assertValueRowAligned()
    }

    @Test
    fun coilRowCentersTheSegmentedControlOnTheSameMidline() {
        show(ThemeMode.LIGHT, FontScale.STANDARD) {
            ConfirmValueRow(
                leftLabel = "读取值",
                leftValue = "OFF",
                leftNote = "来自 74 秒前的读取",
                rightControl = {
                    SegmentedTextControl(listOf(false, true), true, {}, Modifier) { if (it) "ON" else "OFF" }
                },
            )
        }
        val value = bounds("confirm-left-value")
        val arrow = bounds("confirm-arrow")
        assertClose("箭头与“当前值”同一条水平中线", arrow.centerY(), value.centerY())
        compose.onRoot().captureRoboImage("src/test/screenshots/confirm-align/coil_row.png")
    }

    private fun register(theme: ThemeMode, scale: FontScale, name: String) {
        show(theme, scale, pad = false) {
            ConfirmSheetBody(
                title = "写单个寄存器",
                confirmLabel = "发送写入",
                dismissLabel = "取消",
                onConfirm = {},
                onDismiss = {},
                badge = "Unit 1 · 地址 0 · 40001",
                badgeKind = SemanticKind.DROPPED,
                frame = byteArrayOf(0x01, 0x06, 0x00, 0x00, 0x00, 0x7B, 0xC9.toByte(), 0xE9.toByte()),
                frameHighlight = 4..5,
                frameCaption = "将发送 8 字节 · FC06",
                warning = "写入超时不会自动重试，结果标记为「未知」。",
            ) {
                Row(note = "来自 74 秒前的写入", hint = "新值与上次写入的值相同。", label = "上次写入")
            }
        }
        assertValueRowAligned()

        // 标题与右上角徽标：行框垂直居中（同一条中线）。
        val title = bounds("confirm-title")
        val badge = bounds("confirm-badge")
        assertClose("标题与 Unit 徽标垂直居中", title.centerY(), badge.centerY())

        // 各区块左边距一致：标题、“上次写入”标签都从同一个 x 开始。
        assertClose("标题与标签左边距", title.left, bounds("confirm-left-label").left)

        // 两个按钮等高（主按钮 52）。
        val cancel = compose.onNodeWithText("取消").getBoundsInRoot()
        val send = compose.onNodeWithText("发送写入").getBoundsInRoot()
        assertClose("取消键与发送键等高", cancel.height, send.height)
        assertClose("按钮高度 52", send.height, 52.dp)
        assertClose("取消键与发送键同一水平线", cancel.centerY(), send.centerY())

        // 小字在整行下面，不在任何一列里。
        val value = bounds("confirm-left-value")
        assertTrue("小字在数值行下方", bounds("confirm-note").top >= value.bottom)
        assertTrue("提示在小字下方", bounds("confirm-hint").top >= bounds("confirm-note").bottom)
        compose.onRoot().captureRoboImage("src/test/screenshots/confirm-align/$name.png")
    }

    @Composable
    private fun Row(note: String?, hint: String?, label: String = "上次写入") {
        ConfirmValueRow(
            leftLabel = label,
            leftValue = "123",
            leftNote = note,
            rightLabel = "新值",
            rightValue = "123",
            hint = hint,
        )
    }

    private fun assertValueRowAligned() {
        val left = bounds("confirm-left-value")
        val right = bounds("confirm-right-value")
        val arrow = bounds("confirm-arrow")
        assertClose("两个数值顶部一致", left.top, right.top)
        assertClose("两个数值底部（基线所在行框）一致", left.bottom, right.bottom)
        assertClose("两个数值字号（行框高度）一致", left.height, right.height)
        assertClose("两个标签顶部一致", bounds("confirm-left-label").top, bounds("confirm-right-label").top)
        assertClose("箭头与数值同一条水平中线", arrow.centerY(), left.centerY())
        assertClose("箭头与右侧数值同一条水平中线", arrow.centerY(), right.centerY())
        assertTrue("新值在箭头右边", right.left > arrow.right)
    }

    private fun bounds(tag: String): DpRect {
        val b = compose.onNodeWithTag(tag).getBoundsInRoot()
        return DpRect(b.left, b.top, b.right, b.bottom)
    }

    private fun DpRect.centerY(): Dp = (top + bottom) / 2

    private fun assertClose(message: String, actual: Dp, expected: Dp, tolerance: Dp = 0.6.dp) {
        assertEquals(message, expected.value, actual.value, tolerance.value)
    }

    private fun show(theme: ThemeMode, scale: FontScale, pad: Boolean = true, content: @Composable () -> Unit) {
        val settings = UiSettings(theme = theme, fontScale = scale)
        compose.setContent {
            ModbusTheme(settings) {
                Box(Modifier.fillMaxSize().background(LocalModbusColors.current.surface1), contentAlignment = Alignment.BottomCenter) {
                    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.Bottom) {
                        // ConfirmSheetBody 自带 16dp 左右边距；单独测 ConfirmValueRow 时补上同样的边距。
                        Box(Modifier.padding(horizontal = if (pad) 16.dp else 0.dp)) { content() }
                    }
                }
            }
        }
        compose.waitForIdle()
    }
}
