package io.github.xingyingyuzhui.modbusmobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.ui.components.PrimaryButton
import io.github.xingyingyuzhui.modbusmobile.ui.components.SecondaryButton
import io.github.xingyingyuzhui.modbusmobile.ui.components.SegItemTag
import io.github.xingyingyuzhui.modbusmobile.ui.components.SegmentedTextControl
import io.github.xingyingyuzhui.modbusmobile.ui.components.SelectChip
import io.github.xingyingyuzhui.modbusmobile.ui.components.SmallButton
import io.github.xingyingyuzhui.modbusmobile.ui.theme.modbusColors
import io.github.xingyingyuzhui.modbusmobile.application.AccentColor
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** G#1/2/10：全局默认值按稿面尺寸；触控热区保持 ≥ 48dp（真机触控未验证）。浅底透明度按规范_v1 §4.3 统一。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class DesignDefaultsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show() {
        compose.setContent {
            ModbusTheme(UiSettings(theme = ThemeMode.DARK)) {
                Column(Modifier.padding(16.dp)) {
                    SegmentedTextControl(listOf("A", "B", "C"), "A", {}) { it }
                    SecondaryButton("次按钮", {}, Modifier.testTag("secondary"))
                    PrimaryButton("主按钮", {}, Modifier.testTag("primary"))
                    SelectChip("9600", true, onClick = {})
                    SmallButton("小", {}, Modifier.testTag("small"))
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun segmentItemsAreDesignHeightByDefault() {
        show()
        val h = compose.onAllNodesWithTag(SegItemTag)[0].getBoundsInRoot().height.value
        assertEquals("分段项 36dp（稿面）", 36f, h, 1.2f)
    }

    @Test
    fun defaultButtonsAreDesignHeight() {
        show()
        assertEquals("次按钮默认 52（同主按钮）", 52f, compose.onNodeWithTag("secondary").getBoundsInRoot().height.value, 0.6f)
        assertEquals(52f, compose.onNodeWithTag("primary").getBoundsInRoot().height.value, 0.6f)
    }

    @Test
    fun smallVisualControlsStillGet48dpTouchHeight() {
        show()
        val node = compose.onNodeWithTag("small").fetchSemanticsNode()
        assertEquals("可见高度 32（稿面 xs）", 32f, node.boundsInRoot.height, 0.6f)
        assertTrue("热区（touchBounds）高度 ≥48：${node.touchBoundsInRoot.height}", node.touchBoundsInRoot.height >= 47.9f)
    }

    /** T-20261018-and-06：规范_v1 §4.3 把 7 档浅底统一为一档（深 .10 / 浅 .07），替换原来的按颜色取值。 */
    @Test
    fun washAlphaIsOneValuePerTheme() {
        val dark = modbusColors(true, AccentColor.AMBER)
        val light = modbusColors(false, AccentColor.AMBER)
        for (c in listOf(dark.tx, dark.rxLocal, dark.error, dark.foreign, dark.echo, dark.dropped)) {
            assertEquals(0.10f, dark.wash(c).alpha, 0.006f)
        }
        for (c in listOf(light.tx, light.rxLocal, light.error, light.foreign, light.ambiguous, light.dropped)) {
            assertEquals(0.07f, light.wash(c).alpha, 0.006f)
        }
    }
}
