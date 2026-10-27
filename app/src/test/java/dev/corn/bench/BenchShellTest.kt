package dev.corn.bench

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.takahirom.roborazzi.captureRoboImage
import dev.corn.ui.mode.BenchMode
import dev.corn.ui.mode.BrandMarkOrSwitcher
import dev.corn.ui.mode.BrandSwitcherTag
import dev.corn.ui.mode.ModeConfirmTag
import dev.corn.ui.mode.ModePickerTag
import dev.corn.ui.mode.ModeSheetTag
import dev.corn.ui.mode.modeCardTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@Composable
private fun ModbusPlaceholder() {
    // 代替真正的上位机入口：只要一个模式切换器，证明 CompositionLocal 已挂到当前模式的内容上。
    Text("MODBUS", Modifier.testTag("content-modbus"))
    BrandMarkOrSwitcher()
}

@Composable
private fun FlashingPlaceholder() {
    Text("FLASHING", Modifier.testTag("content-flashing"))
    BrandMarkOrSwitcher()
}

/** 记录调用顺序的假守卫。 */
private class RecordingGuard(
    var blocked: String? = null,
    var released: Boolean = true,
) : ModeGuard {
    val releasedModes = mutableListOf<BenchMode>()
    val blockedQueries = mutableListOf<BenchMode>()

    override fun blockedReason(mode: BenchMode): String? {
        blockedQueries += mode
        return blocked
    }

    override suspend fun release(mode: BenchMode): Boolean {
        releasedModes += mode
        return released
    }
}

/** 用来验证“切走模式后 ViewModel 被清掉”。 */
class ProbeViewModel : ViewModel() {
    var cleared = false

    override fun onCleared() {
        cleared = true
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class BenchShellTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(
        store: ModeStore,
        guard: ModeGuard = RecordingGuard(),
        usbTick: Int = 0,
        content: @Composable (BenchMode) -> Unit = { mode ->
            when (mode) {
                BenchMode.MODBUS -> ModbusPlaceholder()
                BenchMode.FLASHING -> FlashingPlaceholder()
            }
        },
    ) {
        compose.setContent {
            BenchRoot(
                modeStore = store,
                guard = guard,
                usbAttachTick = usbTick,
                modbusContent = { content(BenchMode.MODBUS) },
                flashingContent = { content(BenchMode.FLASHING) },
            )
        }
        compose.waitForIdle()
    }

    @Test
    fun firstLaunchShowsTheModePicker() {
        show(MemoryModeStore(null))
        compose.onNodeWithTag(ModePickerTag).assertIsDisplayed()
        compose.onNodeWithText("先选一个模式").assertIsDisplayed()
        compose.onNodeWithTag(modeCardTag(BenchMode.MODBUS)).assertIsDisplayed()
        compose.onNodeWithTag(modeCardTag(BenchMode.FLASHING)).assertIsDisplayed()
    }

    @Test
    fun pickingOnFirstLaunchEntersTheModeAndPersistsIt() {
        val store = MemoryModeStore(null)
        show(store)
        compose.onNodeWithTag(modeCardTag(BenchMode.FLASHING)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("content-flashing").assertIsDisplayed()
        compose.onNodeWithTag(ModePickerTag).assertDoesNotExist()
        assertEquals(ModeState.Selected(BenchMode.FLASHING), store.state.value)
    }

    @Test
    fun lastModeIsOpenedDirectlyWithoutThePicker() {
        show(MemoryModeStore(BenchMode.MODBUS))
        compose.onNodeWithTag("content-modbus").assertIsDisplayed()
        compose.onNodeWithTag(ModePickerTag).assertDoesNotExist()
    }

    @Test
    fun brandingSwitcherOpensTheSheetAndSwitchingReleasesTheOldMode() {
        val guard = RecordingGuard()
        val store = MemoryModeStore(BenchMode.MODBUS)
        show(store, guard)
        compose.onNodeWithTag(BrandSwitcherTag).performClick()
        compose.onNodeWithTag(ModeSheetTag).assertIsDisplayed()
        compose.onNodeWithTag(modeCardTag(BenchMode.FLASHING)).performClick()
        compose.waitForIdle()
        assertEquals(listOf(BenchMode.MODBUS), guard.releasedModes)
        compose.onNodeWithTag("content-flashing").assertIsDisplayed()
        assertEquals(ModeState.Selected(BenchMode.FLASHING), store.state.value)
    }

    @Test
    fun blockedModeShowsTheReasonAndDoesNotSwitch() {
        val guard = RecordingGuard(blocked = "正在烧录：等这一片完成后再切换")
        val store = MemoryModeStore(BenchMode.FLASHING)
        show(store, guard)
        compose.onNodeWithTag(BrandSwitcherTag).performClick()
        compose.onNodeWithTag(ModeSheetTag).assertIsDisplayed()
        compose.onNodeWithText("正在烧录：等这一片完成后再切换").assertIsDisplayed()
        // 被拦截的目标卡是 disabled（置灰 + 写原因），点了也不会走释放 / 切换。
        compose.onNodeWithTag(modeCardTag(BenchMode.MODBUS)).assertIsNotEnabled()
        compose.onNodeWithTag(modeCardTag(BenchMode.MODBUS)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("content-flashing").assertIsDisplayed()
        assertEquals(ModeState.Selected(BenchMode.FLASHING), store.state.value)
        assertTrue("拦截时不应调用 release", guard.releasedModes.isEmpty())
    }

    @Test
    fun releaseTimeoutKeepsTheCurrentMode() {
        val guard = RecordingGuard(released = false)
        val store = MemoryModeStore(BenchMode.MODBUS)
        show(store, guard)
        compose.onNodeWithTag(BrandSwitcherTag).performClick()
        compose.onNodeWithTag(modeCardTag(BenchMode.FLASHING)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(ModeConfirmTag).assertIsDisplayed()
        // 真的写了原因，而不是留一个永远不存在的字符串断言。
        compose.onNodeWithText("当前模式仍在收尾", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("content-modbus").assertIsDisplayed()
        assertEquals(ModeState.Selected(BenchMode.MODBUS), store.state.value)
    }

    @Test
    fun currentModeCardIsNotClickable() {
        val guard = RecordingGuard()
        val store = MemoryModeStore(BenchMode.MODBUS)
        show(store, guard)
        compose.onNodeWithTag(BrandSwitcherTag).performClick()
        compose.onNodeWithTag(modeCardTag(BenchMode.MODBUS)).performClick()
        compose.waitForIdle()
        // 面板还开着，模式没变，也没有走释放。
        compose.onNodeWithTag(ModeSheetTag).assertIsDisplayed()
        assertTrue(guard.releasedModes.isEmpty())
    }

    @Test
    fun switchingModesClearsTheOldViewModelStore() {
        var oldVm: ProbeViewModel? = null
        val store = MemoryModeStore(BenchMode.MODBUS)
        show(store) { mode ->
            val vm: ProbeViewModel = viewModel()
            if (mode == BenchMode.MODBUS) oldVm = vm
            Text(mode.key, Modifier.testTag("content-${mode.key}"))
            BrandMarkOrSwitcher()
        }
        compose.onNodeWithTag(BrandSwitcherTag).performClick()
        compose.onNodeWithTag(modeCardTag(BenchMode.FLASHING)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("content-flashing").assertIsDisplayed()
        assertTrue("切走后旧模式的 ViewModel 应已 onCleared", oldVm?.cleared == true)
    }

    @Test
    fun usbAttachOnlyHintsAndDoesNotSwitch() {
        val store = MemoryModeStore(BenchMode.MODBUS)
        show(store, usbTick = 1)
        compose.onNodeWithTag(BenchUsbHintTag).assertIsDisplayed()
        compose.onNodeWithText("不会自动切换模式", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("content-modbus").assertIsDisplayed()
        // 5 秒后提示自己消失，模式仍然不变。
        compose.mainClock.advanceTimeBy(5_100)
        compose.waitForIdle()
        compose.onNodeWithTag(BenchUsbHintTag).assertDoesNotExist()
        assertEquals(ModeState.Selected(BenchMode.MODBUS), store.state.value)
    }

    @Test
    fun loadingDoesNotFlashThePicker() {
        val loading = object : ModeStore {
            override val state = kotlinx.coroutines.flow.MutableStateFlow<ModeState>(ModeState.Loading)
            override suspend fun setMode(mode: BenchMode) = Unit
        }
        show(loading)
        compose.onNodeWithTag(BenchLoadingTag).assertIsDisplayed()
        compose.onNodeWithTag(ModePickerTag).assertDoesNotExist()
    }

    // ---- Roborazzi 截图（渲染，不是实机）----

    @Test
    fun screenshotModePicker() {
        show(MemoryModeStore(null))
        compose.onNodeWithTag(ModePickerTag).captureRoboImage("bench_mode_picker.png")
    }

    @Test
    fun screenshotModeSheet() {
        show(MemoryModeStore(BenchMode.MODBUS))
        compose.onNodeWithTag(BrandSwitcherTag).performClick()
        compose.onNodeWithTag(ModeSheetTag).captureRoboImage("bench_mode_sheet.png")
    }

    @Test
    fun screenshotBlockedSheet() {
        val guard = RecordingGuard(blocked = "正在烧录：等这一片完成后再切换")
        show(MemoryModeStore(BenchMode.FLASHING), guard)
        compose.onNodeWithTag(BrandSwitcherTag).performClick()
        compose.onNodeWithTag(ModeSheetTag).captureRoboImage("bench_mode_sheet_blocked.png")
    }
}
