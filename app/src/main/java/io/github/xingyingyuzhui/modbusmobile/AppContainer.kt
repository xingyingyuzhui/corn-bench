package io.github.xingyingyuzhui.modbusmobile

import android.app.Application
import io.github.xingyingyuzhui.modbusmobile.application.DefaultSessionManager
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
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
    val sessionManager: SessionManager = DefaultSessionManager(scope, clock, usb)
}
