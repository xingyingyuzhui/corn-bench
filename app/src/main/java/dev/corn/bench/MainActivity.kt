package dev.corn.bench

import android.Manifest
import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Corn Bench 唯一的 Activity。两个模式都是这里的 Compose 子图，切换不重启 Activity。
 *
 * 启动时按 `bench_ui` 里的 `last_mode` 直接进上次的模式；第一次启动显示全屏模式选择页。
 * 系统栏保持透明，图标深浅由各模式的主题（`SystemBarsFollowTheme`）设置。
 */
class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    /** 每收到一次 USB 插入 +1；只用于外壳的“不会自动切换模式”提示。 */
    private var usbAttachTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) usbAttachTick = 1
        setContent {
            BenchRoot(usbAttachTick = usbAttachTick)
        }
    }

    /** 已在前台时再插 USB：只提示，不换模式（`singleTop` 复用本实例）。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) usbAttachTick += 1
    }
}
