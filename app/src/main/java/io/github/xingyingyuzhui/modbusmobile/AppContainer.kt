package io.github.xingyingyuzhui.modbusmobile

import android.app.Application
import io.github.xingyingyuzhui.modbusmobile.application.DataStoreSettingsRepository
import io.github.xingyingyuzhui.modbusmobile.application.DefaultSessionManager
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import io.github.xingyingyuzhui.modbusmobile.application.SettingsRepository
import io.github.xingyingyuzhui.modbusmobile.application.TrafficRecorder
import io.github.xingyingyuzhui.modbusmobile.core.transport.SystemMonotonicClock
import io.github.xingyingyuzhui.modbusmobile.usb.AndroidUsbStack
import io.github.xingyingyuzhui.modbusmobile.usb.UsbDeviceRepository
import io.github.xingyingyuzhui.modbusmobile.usb.UsbPermissionRequester
import io.github.xingyingyuzhui.modbusmobile.usb.UsbSessionBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val clock = SystemMonotonicClock
    val bridge = UsbSessionBridge()
    val repository = UsbDeviceRepository(app).also { it.start() }
    private val permissions = UsbPermissionRequester(app, repository)
    private val usb = AndroidUsbStack(app, repository, permissions, bridge)
    val sessionManager: SessionManager = DefaultSessionManager(
        scope,
        clock,
        usb,
        slaveSetupStore = io.github.xingyingyuzhui.modbusmobile.application.slave.FileSlaveSetupStore(java.io.File(app.filesDir, "projects")),
    )
    val trafficRecorder = TrafficRecorder(sessionManager, scope)

    /**
     * 主站工作台的「每 N s」轮询（T-20261018-qa-02）：应用级作用域，离开工作台 / 退到后台都不停，
     * 只在用户点停止、断开连接或 close() 时停。跑在主线程（和原来 viewModelScope 一样），结果直接交给工作台显示。
     */
    val masterPoller = io.github.xingyingyuzhui.modbusmobile.application.MasterPoller(
        sessionManager,
        CoroutineScope(scope.coroutineContext + Dispatchers.Main.immediate),
    )
    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(app, scope)
    val pointStore = io.github.xingyingyuzhui.modbusmobile.application.panel.PointStore()
    val sampleHistory = io.github.xingyingyuzhui.modbusmobile.application.panel.SampleHistory()
    val pollScheduler = io.github.xingyingyuzhui.modbusmobile.application.panel.PollScheduler(
        sessionManager, scope, pointStore, nowMs = { System.nanoTime() / 1_000_000L }, history = sampleHistory,
    )
    val panelRuntime = io.github.xingyingyuzhui.modbusmobile.application.panel.PanelRuntime(pointStore, pollScheduler, sampleHistory) { previewScope ->
        io.github.xingyingyuzhui.modbusmobile.application.panel.PointPreview(sessionManager, previewScope) { System.nanoTime() / 1_000_000L }
    }
    val projectRepository = io.github.xingyingyuzhui.modbusmobile.application.panel.ProjectRepository(java.io.File(app.filesDir, "projects"))
}
