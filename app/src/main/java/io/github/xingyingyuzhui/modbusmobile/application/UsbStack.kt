package io.github.xingyingyuzhui.modbusmobile.application

import io.github.xingyingyuzhui.modbusmobile.core.transport.ByteTransport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface UsbStack {
    val devices: StateFlow<List<UsbDeviceSummary>>
    val detached: Flow<Int>

    suspend fun requestPermission(deviceId: Int): Boolean
    suspend fun open(target: ConnectionTarget.Usb, serial: SerialSettings): ByteTransport
    fun stop()
    fun setIoErrorHandler(handler: (String) -> Unit)
}
