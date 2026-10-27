package io.github.xingyingyuzhui.modbusmobile.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class UsbPermissionRequester(
    private val context: Context,
    private val repository: UsbDeviceRepository,
) {
    private val action = "${context.packageName}.USB_PERMISSION"

    suspend fun request(deviceId: Int): Boolean {
        val device = repository.find(deviceId) ?: return false
        if (repository.hasPermission(device)) return true
        return suspendCancellableCoroutine { continuation ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (intent?.action != action) return
                    unregister(this)
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (continuation.isActive) continuation.resume(granted)
                }
            }
            val filter = IntentFilter(action)
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            continuation.invokeOnCancellation { unregister(receiver) }
            val explicit = Intent(action).setPackage(context.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(context, 0, explicit, flags)
            try {
                repository.requestPermission(device, pending)
            } catch (_: Exception) {
                unregister(receiver)
                if (continuation.isActive) continuation.resume(false)
            }
        }
    }

    private fun unregister(receiver: BroadcastReceiver) {
        runCatching { context.unregisterReceiver(receiver) }
    }
}
