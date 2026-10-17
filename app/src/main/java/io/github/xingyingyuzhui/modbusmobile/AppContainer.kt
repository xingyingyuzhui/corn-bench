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
import io.github.xingyingyuzhui.modbusmobile.usb.withLocalSerialLab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val clock = SystemMonotonicClock
    val bridge = UsbSessionBridge()
    val repository = UsbDeviceRepository(app).also { it.start() }
    private val permissions = UsbPermissionRequester(app, repository)
    private val usb = withLocalSerialLab(AndroidUsbStack(app, repository, permissions, bridge), scope, clock)
    val sessionManager: SessionManager = DefaultSessionManager(
        scope,
        clock,
        usb,
        slaveSetupStore = io.github.xingyingyuzhui.modbusmobile.application.slave.FileSlaveSetupStore(java.io.File(app.filesDir, "projects")),
    )
    val trafficRecorder = TrafficRecorder(sessionManager, scope)
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
