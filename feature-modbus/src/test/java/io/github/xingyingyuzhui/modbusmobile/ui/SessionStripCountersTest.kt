package io.github.xingyingyuzhui.modbusmobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.xingyingyuzhui.modbusmobile.application.SessionCounters
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.ui.components.SessionStrip
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 会话条右侧的收发计数（稿面 b1-2：TX / RX 一行，错误一行）；只在主站运行中显示。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class SessionStripCountersTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private class CountingSession(role: Role?) : PanelFakeSession(role) {
        override val counters = MutableStateFlow(SessionCounters(tx = 128, rx = 126, errors = 2))
    }

    private fun show(session: CountingSession) {
        compose.setContent {
            CompositionLocalProvider(LocalSession provides session, LocalOpenConnection provides {}) {
                ModbusTheme(UiSettings(theme = ThemeMode.DARK)) { SessionStrip() }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun masterStripShowsTxRxAndErrors() {
        val session = CountingSession(Role.MASTER)
        show(session)
        compose.onNodeWithText("TX 128", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("RX 126", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("错误 2", useUnmergedTree = true).assertIsDisplayed()
        session.counters.value = SessionCounters(tx = 1_284, rx = 1_280, errors = 0)
        compose.waitForIdle()
        compose.onNodeWithText("TX 1,284", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("错误 0", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun slaveAndIdleStripsHaveNoCounters() {
        val slave = CountingSession(Role.SLAVE)
        show(slave)
        compose.onNodeWithText("TX", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun idleStripHasNoCounters() {
        val idle = CountingSession(null)
        idle.state.value = SessionState.Idle
        show(idle)
        compose.onNodeWithText("TX", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }
}
