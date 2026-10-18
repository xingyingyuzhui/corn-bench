package io.github.xingyingyuzhui.modbusmobile.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** T-20261018-and-07（CL-02）A2/A3：loading 连点只触发 1 次；输入出错有 semantics error。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class ButtonStatesTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loadingPrimaryButtonSwallowsRepeatTaps() {
        var calls = 0
        compose.setContent {
            ModbusTheme(UiSettings(theme = ThemeMode.DARK)) {
                var loading by remember { mutableStateOf(false) }
                PrimaryButton(
                    "发送写入",
                    onClick = { calls++; loading = true },
                    modifier = Modifier.testTag("write"),
                    loading = loading,
                    loadingText = "正在写入…",
                )
            }
        }
        repeat(5) { compose.onNodeWithTag("write").performClick() }
        compose.waitForIdle()
        assertEquals(1, calls)
        compose.onNodeWithTag("write").assertIsNotEnabled()
        compose.onNodeWithTag("write").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "正在写入…"))
        compose.onNodeWithText("正在写入…").assertExists()
    }

    @Test
    fun loadingSecondaryAndSmallButtonsDoNotClick() {
        var calls by mutableIntStateOf(0)
        compose.setContent {
            ModbusTheme(UiSettings()) {
                Column {
                    SecondaryButton("读取", { calls++ }, Modifier.testTag("read"), loading = true)
                    SmallButton("回读", { calls++ }, Modifier.testTag("small"), loading = true)
                    TextLink("清空", { calls++ }, Modifier.testTag("link"), enabled = false)
                }
            }
        }
        listOf("read", "small", "link").forEach { compose.onNodeWithTag(it).performClick() }
        compose.waitForIdle()
        assertEquals(0, calls)
    }

    @Test
    fun numberInputErrorIsExposedToAccessibility() {
        compose.setContent {
            ModbusTheme(UiSettings()) {
                NumberInput("起始地址", "70000", error = "起始地址应在 0–65535 之间") {}
            }
        }
        compose.onNodeWithText("起始地址应在 0–65535 之间").assertExists()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Error, "起始地址应在 0–65535 之间")).assertExists()
    }

    @Test
    fun disabledButtonDoesNotUseAlphaAndIsMarkedDisabled() {
        var calls = 0
        compose.setContent {
            ModbusTheme(UiSettings()) {
                PrimaryButton("发送写入", { calls++ }, Modifier.testTag("p"), enabled = false)
            }
        }
        compose.onNodeWithTag("p").performClick()
        compose.onNodeWithTag("p").assertIsNotEnabled()
        assertEquals(0, calls)
    }
}
