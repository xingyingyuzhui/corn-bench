package dev.corn.bench

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import dev.corn.ui.theme.LocalBenchColors

/** 与 androidx.activity 的 enableEdgeToEdge 默认三键导航栏遮罩一致（库里是 internal）。 */
private val NavigationLightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val NavigationDarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

/**
 * 外壳级系统栏样式：图标深浅跟随当前模式的 token，而不是系统深浅色。
 *
 * 两个模式原来各自在自己的 Activity 里设置（烧录固定浅色图标、上位机跟随主题）。合并成一个 Activity 后
 * 这一步必须由外壳统一做：`MainActivity` 里的 `enableEdgeToEdge()` 只看系统 uiMode，
 * 系统浅色时会给炭黑底压一层深色图标，看不见。放在 `CornTheme` 里面调用（要读 `LocalBenchColors`）。
 */
@Composable
fun BenchSystemBars() {
    val dark = LocalBenchColors.current.isDark
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
