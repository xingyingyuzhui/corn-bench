package io.github.xingyingyuzhui.modbusmobile.ui.shell

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.xingyingyuzhui.modbusmobile.R

enum class Destination(
    val route: String,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
) {
    Connection("connection", R.string.nav_connection, R.drawable.ic_nav_connection),
    Workbench("workbench", R.string.nav_workbench, R.drawable.ic_nav_workbench),
    Panel("panel", R.string.nav_panel, R.drawable.ic_nav_panel),
    Log("log", R.string.nav_log, R.drawable.ic_nav_log),
    Mine("mine", R.string.nav_mine, R.drawable.ic_nav_mine),
}

/** 曲线全屏路由：`curve?id=<曲线组件 id>`；不带 id 也能进（显示“没找到这个曲线”），所以 id 是可选参数。 */
const val CurveRoute = "curve"
const val CurveRoutePattern = "curve?id={id}"

fun curveRoute(widgetId: String): String = "curve?id=" + android.net.Uri.encode(widgetId)

fun isCurveRoute(route: String?): Boolean = route != null && (route == CurveRoute || route.startsWith("curve?"))
