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
    // Corn Bench 里烧录和上位机共用一个 applicationId：两边的授权广播 action 都必须带模块名，
    // 否则一边收到另一边的授权结果（烧录侧是 "<包名>.flashing.USB_PERMISSION"）。
    private val action = permissionAction(context.packageName)

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

    companion object {
        /** USB 授权结果广播的 action；与烧录模式的 `FlashingFeature` 侧保持不同。 */
        fun permissionAction(packageName: String) = "$packageName.modbus.USB_PERMISSION"
    }
}
