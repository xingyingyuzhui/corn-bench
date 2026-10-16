package io.github.xingyingyuzhui.modbusmobile.usb

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.SerialSettings
import io.github.xingyingyuzhui.modbusmobile.application.UsbStack
import io.github.xingyingyuzhui.modbusmobile.core.transport.ByteTransport
import io.github.xingyingyuzhui.modbusmobile.service.SessionService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class AndroidUsbStack(
    private val context: Context,
    private val repository: UsbDeviceRepository,
    private val permissions: UsbPermissionRequester,
    private val bridge: UsbSessionBridge,
) : UsbStack {
    override val devices: StateFlow<List<io.github.xingyingyuzhui.modbusmobile.application.UsbDeviceSummary>> =
        repository.devices
    override val detached: Flow<Int> = repository.detached

    override suspend fun requestPermission(deviceId: Int): Boolean = permissions.request(deviceId)

    override suspend fun open(target: ConnectionTarget.Usb, serial: SerialSettings): ByteTransport {
        bridge.prepare()
        val intent = Intent(context, SessionService::class.java).apply {
            action = SessionService.ACTION_OPEN
            putExtra(SessionService.EXTRA_DEVICE_ID, target.deviceId)
            putExtra(SessionService.EXTRA_PORT_INDEX, target.portIndex)
            putExtra(SessionService.EXTRA_BAUD, serial.baudRate)
            putExtra(SessionService.EXTRA_DATA_BITS, serial.dataBits)
            putExtra(SessionService.EXTRA_STOP_BITS, serial.stopBits)
            putExtra(SessionService.EXTRA_PARITY, serial.parity.name)
        }
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (error: Exception) {
            bridge.fail(error)
            throw error
        }
        return try {
            bridge.await()
        } catch (error: Exception) {
            stop()
            throw error
        }
    }

    override fun stop() {
        context.stopService(Intent(context, SessionService::class.java))
    }

    override fun setIoErrorHandler(handler: (String) -> Unit) {
        bridge.onIoError = handler
    }
}
