package dev.corn.bench

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.corn.ui.mode.BenchMode
import dev.corn.ui.mode.BrandMarkOrSwitcher
import dev.corn.ui.mode.BrandSwitcherTag
import dev.corn.ui.mode.modeCardTag
import dev.corn.ui.theme.BenchAccent
import dev.corn.ui.theme.BenchThemeMode
import dev.corn.ui.theme.LocalBenchColors
import dev.corn.ui.theme.benchColors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 记录模式内容最后一次拿到的 token。 */
private class Seen {
    var dark: Boolean? = null
    var accent: Color? = null
    var background: Color? = null
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class BenchAppearanceTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val seen = Seen()
    private val appearance = FakeAppearance()
    private val noGuard = object : ModeGuard {
        override fun blockedReason(mode: BenchMode): String? = null
        override suspend fun release(mode: BenchMode) = true
    }

    private fun show(
        store: ModeStore = MemoryModeStore(BenchMode.MODBUS),
        themeMode: BenchThemeMode = BenchThemeMode.DARK,
    ) {
        appearance.setThemeMode(themeMode)
        compose.setContent {
            BenchRoot(
                modeStore = store,
                guard = noGuard,
                appearanceController = appearance,
                modbusContent = { Probe(BenchMode.MODBUS) },
                flashingContent = { Probe(BenchMode.FLASHING) },
            )
        }
        compose.waitForIdle()
    }

    @Composable
    private fun Probe(mode: BenchMode) {
        val colors = LocalBenchColors.current
        seen.dark = colors.isDark
        seen.accent = colors.accent
        seen.background = colors.bg
        Text(mode.key, Modifier.testTag("content-${mode.key}"))
        // 真入口的页头里有这个切换器；测试内容也放一个，才能验证“切模式”这条路径。
        BrandMarkOrSwitcher()
    }

    @Test
    fun switchingModesKeepsTheSameShellAppearance() {
        show()
        compose.onNodeWithTag("content-modbus").assertExists()
        val dark = seen.dark
        val accent = seen.accent

        compose.onNodeWithTag(BrandSwitcherTag).performClick()
        compose.onNodeWithTag(modeCardTag(BenchMode.FLASHING)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("content-flashing").assertExists()

        // 切模式不换主题：两个模式拿到的是同一套 token。
        assertEquals(dark, seen.dark)
        assertEquals(accent, seen.accent)
    }

    @Test
    fun changingAccentReachesTheModeContent() {
        show()
        val corn = seen.accent
        compose.runOnIdle { appearance.setAccent(BenchAccent.BLUE) }
        compose.waitForIdle()
        assertNotEquals("强调色应传到模式内容", corn, seen.accent)
        assertEquals(benchColors(dark = true, accent = BenchAccent.BLUE).accent, seen.accent)
    }

    @Test
    fun switchingToLightThemeReachesTheModeContent() {
        show()
        assertEquals(true, seen.dark)
        val darkBg = seen.background

        compose.runOnIdle { appearance.setThemeMode(BenchThemeMode.LIGHT) }
        compose.waitForIdle()
        assertEquals("浅色主题应传到模式内容", false, seen.dark)
        assertNotEquals(darkBg, seen.background)
        assertTrue("浅色背景应比深色背景亮", seen.background!!.luminance() > darkBg!!.luminance())
    }

    /**
     * 跟随系统时按系统深浅走（不是恒为深色 / 恒为浅色）。
     * 用同一份断言覆盖两种 uiMode：`night` 下应当是深色，`notnight` 下应当是浅色。
     */
    @Test
    fun systemModeFollowsTheSystemConfiguration() {
        val night = compose.activity.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        show(themeMode = BenchThemeMode.SYSTEM)
        assertEquals("uiMode night=$night", night, seen.dark)
    }

    @get:Rule
    val tmp = TemporaryFolder()

    /**
     * 首帧等读盘：外观读完之前外壳只放一个空的加载占位（露出窗口底色），不画模式内容；
     * 读完后第一帧就是盘上的主题（这里是浅色），不会先按默认值画一帧。
     */
    @Test
    fun shellWaitsForStoredAppearanceBeforeDrawing() {
        val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val disk = PreferenceDataStoreFactory.create(scope = io) { tmp.root.resolve("bench_ui.preferences_pb") }
            val gate = CompletableDeferred<Pair<String?, String?>?>()
            val stored = BenchAppearanceStore(compose.activity, io, disk) { gate.await() }
            compose.setContent {
                BenchRoot(
                    modeStore = MemoryModeStore(BenchMode.MODBUS),
                    guard = noGuard,
                    appearanceController = stored,
                    modbusContent = { Probe(BenchMode.MODBUS) },
                    flashingContent = { Probe(BenchMode.FLASHING) },
                )
            }
            compose.waitForIdle()
            compose.onNodeWithTag(BenchLoadingTag).assertExists()
            compose.onNodeWithTag("content-modbus").assertDoesNotExist()
            assertEquals("读盘前不应画过模式内容", null, seen.dark)

            gate.complete("LIGHT" to null)
            compose.waitUntil(10_000) { stored.loaded.value }
            compose.waitForIdle()
            compose.onNodeWithTag("content-modbus").assertIsDisplayed()
            assertEquals(false, seen.dark)
        } finally {
            io.cancel()
        }
    }
}
