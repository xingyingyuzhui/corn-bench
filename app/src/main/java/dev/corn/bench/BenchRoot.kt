package dev.corn.bench

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.corn.flashing.FlashingEntry
import dev.corn.flashing.FlashingFeature
import dev.corn.ui.mode.BenchMode
import dev.corn.ui.mode.LocalModeSwitcher
import dev.corn.ui.mode.ModeConfirmDialog
import dev.corn.ui.mode.ModeOption
import dev.corn.ui.mode.ModePickerScreen
import dev.corn.ui.mode.ModeSwitchSheet
import dev.corn.ui.theme.AppearanceController
import dev.corn.ui.theme.BenchAccent
import dev.corn.ui.theme.BenchAppearance
import dev.corn.ui.theme.BenchThemeMode
import dev.corn.ui.theme.BenchType
import dev.corn.ui.theme.CornTheme
import dev.corn.ui.theme.LocalAppearanceController
import dev.corn.ui.theme.LocalBenchColors
import io.github.xingyingyuzhui.modbusmobile.ModbusEntry
import io.github.xingyingyuzhui.modbusmobile.ModbusFeature
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 模式切换的守卫：能否离开某个模式、离开前要释放什么。
 *
 * 单独抽成接口，是为了让外壳的模式切换逻辑能在纯 Compose 测试里替换成假实现，
 * 不必真的创建 Room / USB / DataStore。
 */
interface ModeGuard {
    /** 不能离开这个模式的原因；null = 可以离开。 */
    fun blockedReason(mode: BenchMode): String?

    /** 离开这个模式前释放资源（USB、轮询、前台服务）。true = 已释放，可以切换。 */
    suspend fun release(mode: BenchMode): Boolean
}

/** 默认守卫：把请求转给两个功能模块各自的懒加载入口。 */
object DefaultModeGuard : ModeGuard {
    override fun blockedReason(mode: BenchMode): String? = when (mode) {
        BenchMode.MODBUS -> ModbusFeature.leaveBlockedReason()
        BenchMode.FLASHING -> FlashingFeature.leaveBlockedReason()
    }

    override suspend fun release(mode: BenchMode): Boolean = when (mode) {
        BenchMode.MODBUS -> ModbusFeature.releaseUsb()
        BenchMode.FLASHING -> FlashingFeature.releaseUsb()
    }
}

/** 每个模式一份 ViewModelStore：切走时清掉，切回来是新的 ViewModel（旧数据不会串台）。 */
private class ModeViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    fun clear() {
        viewModelStore.clear()
    }
}

const val BenchLoadingTag = "bench-loading"
const val BenchUsbHintTag = "bench-usb-hint"

/**
 * Corn Bench 的外壳：决定先显示模式选择页还是上次的模式，并在模式之间切换。
 *
 * [modeStore] 默认用 DataStore（`bench_ui` / `last_mode`）；测试传 [MemoryModeStore]。
 * [modbusContent] / [flashingContent] 默认是两个模块的入口 Composable，测试可替换成占位内容。
 */
@Composable
fun BenchRoot(
    modeStore: ModeStore? = null,
    guard: ModeGuard = DefaultModeGuard,
    /** 每插一次 USB +1；0 = 本次启动不是 USB 唤起。只提示，不自动切模式。 */
    usbAttachTick: Int = 0,
    /** 外观（深浅色 / 强调色）。默认用 `bench_ui`；测试可注入内存实现。 */
    appearanceController: AppearanceController? = null,
    modbusContent: @Composable () -> Unit = { ModbusEntry() },
    flashingContent: @Composable () -> Unit = { FlashingEntry() },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = modeStore ?: remember(context) { DataStoreModeStore(context.applicationContext, scope) }
    val stored by store.state.collectAsStateWithLifecycle()

    // 本次会话显示的模式。null + stored 已读且为 Unset = 首次启动，显示选择页。
    // 读盘期间（Loading）显示空底，避免已经选过模式却先闪一下选择页。
    var chosen by remember { mutableStateOf<BenchMode?>(null) }
    LaunchedEffect(stored) {
        if (chosen == null && stored is ModeState.Selected) chosen = (stored as ModeState.Selected).mode
    }

    // 外观由外壳统一持有：两个模式共用同一套深浅色与强调色，改完切换模式不会变。
    // 先建 store 再读它的 state（不能反过来：那时 provider 还没生效，读到的永远是默认值）。
    val appearanceStore = appearanceController
        ?: remember(context) { BenchAppearanceStore(context.applicationContext, scope) }
    val appearance by appearanceStore.state
    // 外观还没读完盘时不画任何内容（只露出跟随系统的窗口底色）：用户选了和系统相反的主题时，
    // 先按默认值画一帧再跳色会闪一下。读 bench_ui 一般只要几毫秒。
    val appearanceLoaded = (appearanceStore as? BenchAppearanceStore)?.loaded?.value ?: true
    if (!appearanceLoaded) {
        Box(Modifier.fillMaxSize().testTag(BenchLoadingTag))
        return
    }
    val dark = appearance.isDark()
    CornTheme(
        appearance = BenchAppearance(
            dark = dark,
            accent = appearance.accent,
        ),
    ) {
        CompositionLocalProvider(LocalAppearanceController provides appearanceStore) {
            // 系统栏图标深浅跟当前模式的主题走（系统浅色时炭黑底也需要浅色图标）。
            BenchSystemBars()
            Box(Modifier.fillMaxSize().background(LocalBenchColors.current.bg)) {
                when {
                    chosen != null -> ModeShell(
                        mode = chosen!!,
                        guard = guard,
                        usbAttachTick = usbAttachTick,
                        onModeChange = { next ->
                            chosen = next
                            scope.launch { store.setMode(next) }
                        },
                        modbusContent = modbusContent,
                        flashingContent = flashingContent,
                    )
                    stored == ModeState.Loading -> Box(
                        Modifier.fillMaxSize().testTag(BenchLoadingTag),
                    )
                    else -> ModePickerScreen(onPick = { picked ->
                        chosen = picked
                        scope.launch { store.setMode(picked) }
                    })
                }
            }
        }
    }
}

/**
 * 一个模式的全屏内容 + 模式切换面板。
 *
 * 离开当前模式时先调 [ModeGuard.release]：返回 false（超时 / 有任务在跑）就停在原模式并提示；
 * 成功后由 [BenchRoot] 改 `chosen`，旧模式的 ViewModelStore 在 [DisposableEffect] 里清掉。
 */
@Composable
fun ModeShell(
    mode: BenchMode,
    guard: ModeGuard = DefaultModeGuard,
    usbAttachTick: Int = 0,
    onModeChange: (BenchMode) -> Unit,
    modbusContent: @Composable () -> Unit = { ModbusEntry() },
    flashingContent: @Composable () -> Unit = { FlashingEntry() },
) {
    val scope = rememberCoroutineScope()
    val colors = LocalBenchColors.current
    val owner = remember(mode) { ModeViewModelStoreOwner() }
    DisposableEffect(mode) {
        onDispose { owner.clear() }
    }

    var sheetOpen by remember { mutableStateOf(false) }
    var blockedMessage by remember { mutableStateOf<String?>(null) }
    var switching by remember { mutableStateOf(false) }
    var usbHint by remember { mutableStateOf(false) }

    // 每次插入 USB 只提示 5 秒；不自动切换模式（第一期已拍板）。
    LaunchedEffect(usbAttachTick) {
        // tick 从 0 变 1（或每次 +1）时提示；模式切换导致的重组不会重复触发（key 没变）。
        if (usbAttachTick > 0) {
            usbHint = true
            delay(5_000)
            usbHint = false
        }
    }

    // 卡片置灰只看“能不能离开当前模式”：切换前必须释放的是当前模式的 USB / 任务。
    val leaveReason = guard.blockedReason(mode)
    val options = BenchMode.entries.map { candidate ->
        ModeOption(
            mode = candidate,
            current = candidate == mode,
            blockedReason = if (candidate == mode) null else leaveReason,
        )
    }

    fun requestSwitch(target: BenchMode) {
        if (target == mode || switching) return
        val reason = guard.blockedReason(mode)
        if (reason != null) {
            blockedMessage = reason
            return
        }
        switching = true
        scope.launch {
            val released = guard.release(mode)
            switching = false
            if (!released) {
                blockedMessage = "当前模式仍在收尾（USB 可能还在识别）。稍后再试一次。"
                return@launch
            }
            sheetOpen = false
            onModeChange(target)
        }
    }

    CompositionLocalProvider(
        LocalModeSwitcher provides { sheetOpen = true },
        LocalViewModelStoreOwner provides owner,
    ) {
        Box(Modifier.fillMaxSize()) {
            when (mode) {
                BenchMode.MODBUS -> modbusContent()
                BenchMode.FLASHING -> flashingContent()
            }
            if (usbHint) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .background(colors.surface2)
                        .testTag(BenchUsbHintTag)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        "检测到 USB 设备；不会自动切换模式",
                        color = colors.text,
                        style = BenchType.caption,
                    )
                }
            }
        }
    }

    if (sheetOpen) {
        ModeSwitchSheet(
            options = options,
            onPick = ::requestSwitch,
            onDismiss = { if (!switching) sheetOpen = false },
        )
    }
    blockedMessage?.let { message ->
        ModeConfirmDialog(
            title = "现在不能切换",
            message = message,
            confirmLabel = "知道了",
            dismissLabel = "关闭",
            onConfirm = { blockedMessage = null },
            onDismiss = { blockedMessage = null },
        )
    }
    // 释放过程中不让返回键把面板关掉，避免用户以为没切。
    BackHandler(enabled = switching) { }
}
