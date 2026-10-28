package dev.corn.flashing.ui

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
import dev.corn.ui.theme.CornTheme as BenchCornTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 评审 E P2-9：烧录页头在两种情况下都有可读的 label。
 * 无外壳（降级为纯图标）时图标是装饰，页头靠标题 heading；有外壳时多一个且只有一个“切换模式”按钮。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TopBarA11yTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val switchButton = hasContentDescription("切换模式") and
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    @Test
    fun withoutShellTheTitleIsTheHeading() {
        compose.setContent { BenchCornTheme { TopBar("烧录", "离线烧录 · 自动识别目标") } }
        compose.onAllNodes(isHeading() and hasText("烧录") and hasText("离线烧录 · 自动识别目标")).assertCountEquals(1)
        compose.onAllNodes(switchButton).assertCountEquals(0)
    }

    @Test
    fun withShellThereIsExactlyOneSwitchButton() {
        compose.setContent {
            BenchCornTheme {
                CompositionLocalProvider(LocalModeSwitcher provides {}) { TopBar("烧录", "批量 · 工位 1") }
            }
        }
        compose.onAllNodes(isHeading() and hasText("烧录")).assertCountEquals(1)
        compose.onAllNodes(switchButton).assertCountEquals(1)
    }
}
