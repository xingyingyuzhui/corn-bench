package io.github.xingyingyuzhui.modbusmobile.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.corn.ui.mode.LocalModeSwitcher
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 评审 E P2-9：上位机页头在没有外壳（玉米标降级为装饰）时也有可读的 label —— 页面标题是 heading；
 * 有外壳时另有且只有一个“切换模式”按钮。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PageHeaderA11yTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val switchButton = hasContentDescription("切换模式") and
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    @Test
    fun withoutShellTheTitleIsTheHeading() {
        compose.setContent { ModbusTheme(UiSettings()) { PageHeader("连接") } }
        compose.onAllNodes(isHeading() and hasText("连接")).assertCountEquals(1)
        compose.onAllNodes(switchButton).assertCountEquals(0)
    }

    @Test
    fun withShellThereIsExactlyOneSwitchButton() {
        compose.setContent {
            ModbusTheme(UiSettings()) {
                CompositionLocalProvider(LocalModeSwitcher provides {}) { PageHeader("连接") }
            }
        }
        compose.onAllNodes(isHeading() and hasText("连接")).assertCountEquals(1)
        compose.onAllNodes(switchButton).assertCountEquals(1)
    }
}
