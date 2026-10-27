package io.github.xingyingyuzhui.modbusmobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.ui.LocalHistoryCatalog
import io.github.xingyingyuzhui.modbusmobile.ui.LocalMasterPoller
import io.github.xingyingyuzhui.modbusmobile.ui.LocalPanelRuntime
import io.github.xingyingyuzhui.modbusmobile.ui.LocalProjectRepository
import io.github.xingyingyuzhui.modbusmobile.ui.LocalSession
import io.github.xingyingyuzhui.modbusmobile.ui.LocalSettingsRepository
import io.github.xingyingyuzhui.modbusmobile.ui.LocalSlaveSetupStore
import io.github.xingyingyuzhui.modbusmobile.ui.LocalTrafficRecorder
import io.github.xingyingyuzhui.modbusmobile.ui.ModbusApp
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import io.github.xingyingyuzhui.modbusmobile.ui.theme.SystemBarsFollowTheme

/**
 * 上位机模式的入口 Composable（原 Corn Modbus `MainActivity` 的 setContent 内容）。
 *
 * 从宿主的 Application / Context 懒加载 [ModbusContainer]，把会话、设置、记录器等挂到页面用的
 * CompositionLocal 上。系统栏样式由外壳 Activity 统一设置，这里不重复设置。
 *
 * ViewModel 挂在调用方的 ViewModelStoreOwner 上：外壳把上位机子图出栈时它们随之清掉。
 */
@Composable
fun ModbusEntry() {
    val context = LocalContext.current
    val container = androidx.compose.runtime.remember(context) { ModbusFeature.container(context) }
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
    val sessionState by container.sessionManager.state.collectAsStateWithLifecycle()
    KeepScreenOn(settings.keepScreenOn && sessionState is SessionState.Running)
    CompositionLocalProvider(
        LocalSession provides container.sessionManager,
        LocalTrafficRecorder provides container.trafficRecorder,
        LocalSettingsRepository provides container.settingsRepository,
        LocalProjectRepository provides container.projectRepository,
        LocalPanelRuntime provides container.panelRuntime,
        LocalHistoryCatalog provides container.historyCatalog,
        LocalSlaveSetupStore provides container.slaveSetupStore,
        LocalMasterPoller provides container.masterPoller,
    ) {
        ModbusTheme(settings) {
            SystemBarsFollowTheme()
            ModbusApp()
        }
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}
