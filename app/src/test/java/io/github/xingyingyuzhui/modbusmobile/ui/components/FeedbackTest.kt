package io.github.xingyingyuzhui.modbusmobile.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.ui.shell.DockInsets
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalDockInsets
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusMotion
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** T-20261018-and-08（CL-03）A2/A3/A4：结果条自动收起规则、UndoBar 5s 消失与替换、未知态有回读（虚拟时钟）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class FeedbackTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun showBanner(kind: ResultKind, onDismiss: () -> Unit = {}) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ModbusTheme(UiSettings()) {
                ResultBanner(ResultNotice(kind, "结果", 1), onReadBack = {}, onRetry = {}, onDismiss = onDismiss)
            }
        }
        compose.mainClock.advanceTimeBy(ModbusMotion.STANDARD.toLong() + 50)
    }

    @Test
    fun successCollapsesAfterThreeSeconds() {
        var dismissed = 0
        showBanner(ResultKind.SUCCESS) { dismissed++ }
        compose.onNodeWithTag(ResultBannerTag).assertExists()
        compose.mainClock.advanceTimeBy(ModbusMotion.SUCCESS_DISMISS - 500)
        compose.onNodeWithTag(ResultBannerTag).assertExists()
        compose.mainClock.advanceTimeBy(500 + ModbusMotion.STANDARD.toLong() + 100)
        compose.onNodeWithTag(ResultBannerTag).assertDoesNotExist()
        assertEquals(1, dismissed)
    }

    @Test
    fun failureStays() {
        showBanner(ResultKind.FAILURE)
        compose.mainClock.advanceTimeBy(ModbusMotion.SUCCESS_DISMISS * 3)
        compose.onNodeWithTag(ResultBannerTag).assertExists()
        compose.onNodeWithText("重试").assertExists()
    }

    @Test
    fun unknownStaysAndOffersReadBack() {
        var readBacks = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ModbusTheme(UiSettings()) {
                ResultBanner(ResultNotice(ResultKind.UNKNOWN, "结果未知", 7), onReadBack = { readBacks++ })
            }
        }
        compose.mainClock.advanceTimeBy(ModbusMotion.SUCCESS_DISMISS * 3)
        compose.onNodeWithTag(ResultBannerTag).assertExists()
        compose.onNodeWithText("回读").assertExists()
        compose.onNodeWithTag(ResultBannerReadBackTag).performClick()
        assertEquals(1, readBacks)
    }

    @Test
    fun compactBannerIs36dpHigh() {
        compose.setContent {
            ModbusTheme(UiSettings()) {
                ResultBanner(ResultNotice(ResultKind.UNKNOWN, "结果未知", 1), compact = true, onReadBack = {})
            }
        }
        compose.waitForIdle()
        val h = compose.onNodeWithTag(ResultBannerTag).getBoundsInRoot().let { it.bottom - it.top }
        assertEquals(36f, h.value, 0.6f)
    }

    @Test
    fun undoBarDisappearsAfterFiveSecondsAndNewReplacesOld() {
        val state = UndoBarState()
        var undone = ""
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ModbusTheme(UiSettings()) {
                CompositionLocalProvider(LocalDockInsets provides DockInsets(bottom = 64.dp)) {
                    Box(Modifier.fillMaxSize()) { UndoBarHost(state) }
                }
            }
        }
        compose.runOnIdle { state.show("已删除 A", "撤销") { undone += "A" } }
        compose.mainClock.advanceTimeBy(ModbusMotion.UNDO - 2000)
        compose.onNodeWithText("已删除 A").assertExists()
        // 新的替换旧的，重新计时
        compose.runOnIdle { state.show("已删除 B", "撤销") { undone += "B" } }
        compose.mainClock.advanceTimeBy(ModbusMotion.STANDARD.toLong() + 50)
        compose.onNodeWithText("已删除 A").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(ModbusMotion.UNDO - 1000)
        compose.onNodeWithText("已删除 B").assertExists()
        compose.onNodeWithTag(UndoBarActionTag).performClick()
        assertEquals("B", undone)
        compose.mainClock.advanceTimeBy(ModbusMotion.STANDARD.toLong() + 50)
        compose.onNodeWithText("已删除 B").assertDoesNotExist()
    }

    @Test
    fun undoBarSitsEightDpAboveTheDock() {
        val state = UndoBarState()
        compose.setContent {
            ModbusTheme(UiSettings()) {
                CompositionLocalProvider(LocalDockInsets provides DockInsets(bottom = 64.dp)) {
                    Box(Modifier.fillMaxSize()) { UndoBarHost(state) }
                }
            }
        }
        compose.runOnIdle { state.show("已复制", null) }
        compose.waitForIdle()
        val root = compose.onRoot().getBoundsInRoot()
        val bar = compose.onNodeWithTag(UndoBarTag).getBoundsInRoot()
        assertEquals(64f + 8f, (root.bottom - bar.bottom).value, 0.6f)
    }
}
