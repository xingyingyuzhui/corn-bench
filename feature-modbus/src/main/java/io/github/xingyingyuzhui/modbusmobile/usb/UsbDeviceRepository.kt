package io.github.xingyingyuzhui.modbusmobile.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.hoho.android.usbserial.driver.UsbSerialProber
import io.github.xingyingyuzhui.modbusmobile.application.UsbDeviceSummary
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class UsbDeviceRepository(private val context: Context) {
    private val usbManager = context.getSystemService(UsbManager::class.java)
    private val devicesState = MutableStateFlow(snapshot())
    private val detachEvents = MutableSharedFlow<Int>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val devices: StateFlow<List<UsbDeviceSummary>> = devicesState.asStateFlow()
    val detached: SharedFlow<Int> = detachEvents.asSharedFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                deviceFrom(intent)?.let { detachEvents.tryEmit(it.deviceId) }
            }
            devicesState.value = snapshot()
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    fun refresh() {
        devicesState.value = snapshot()
    }

    fun snapshot(): List<UsbDeviceSummary> {
        val manager = usbManager ?: return emptyList()
        val portsByDevice = runCatching {
            UsbSerialProber.getDefaultProber().findAllDrivers(manager).associate { driver ->
                driver.device.deviceId to driver.ports.size.coerceAtLeast(1)
            }
        }.getOrDefault(emptyMap())
        return manager.deviceList.values.flatMap { device ->
            val ports = portsByDevice[device.deviceId] ?: 1
            val name = runCatching { device.productName }.getOrNull() ?: "USB 设备"
            (0 until ports).map { index ->
                UsbDeviceSummary(
                    deviceId = device.deviceId,
                    portIndex = index,
                    productName = name,
                    vendorId = device.vendorId,
                    productId = device.productId,
                )
            }
        }
    }

    fun find(deviceId: Int): UsbDevice? =
        usbManager?.deviceList?.values?.firstOrNull { it.deviceId == deviceId }

    fun hasPermission(device: UsbDevice): Boolean = usbManager?.hasPermission(device) == true

    fun requestPermission(device: UsbDevice, pendingIntent: PendingIntent) {
        usbManager?.requestPermission(device, pendingIntent)
    }

    private fun deviceFrom(intent: Intent): UsbDevice? = if (Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
    }
}
