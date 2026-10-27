package io.github.xingyingyuzhui.modbusmobile.ui.theme

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/** 与 androidx.activity 的 enableEdgeToEdge 默认三键导航栏遮罩一致（库里是 internal）。 */
private val NavigationLightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val NavigationDarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

/**
 * 系统栏图标深浅跟随 App 主题，而不是系统深浅色。
 * 默认的 enableEdgeToEdge() 只看系统 uiMode：系统浅色 + App 手动深色时，状态栏会是深色图标压在深色背景上，看不见。
 * 必须放在 [ModbusTheme] 里面（读 [LocalModbusColors] 的 isDark）。
 */
@Composable
fun SystemBarsFollowTheme() {
    val dark = LocalModbusColors.current.isDark
    val activity = LocalContext.current.findComponentActivity() ?: return
    DisposableEffect(activity, dark) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(NavigationLightScrim, NavigationDarkScrim) { dark },
        )
        onDispose { }
    }
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}
