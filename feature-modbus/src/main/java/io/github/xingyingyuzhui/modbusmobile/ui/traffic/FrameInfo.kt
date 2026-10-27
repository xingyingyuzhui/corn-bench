package io.github.xingyingyuzhui.modbusmobile.ui.traffic

import io.github.xingyingyuzhui.modbusmobile.application.FrameInfo
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficKind

/** 日志过滤：全部 / 本机 / 外站 / 回显 / 异常 / 不明。 */
enum class LogFilter { ALL, LOCAL, FOREIGN, ECHO, ERROR, UNKNOWN }

fun LogFilter.accepts(kind: TrafficKind, info: FrameInfo): Boolean = when (this) {
    LogFilter.ALL -> true
    LogFilter.LOCAL -> when (kind) {
        TrafficKind.TX_REQUEST, TrafficKind.TX_RESPONSE, TrafficKind.RX_LOCAL_REQUEST, TrafficKind.RX_RESPONSE -> true
        else -> false
    }
    LogFilter.FOREIGN -> kind == TrafficKind.RX_FOREIGN_REQUEST || kind == TrafficKind.RX_FOREIGN_RESPONSE
    LogFilter.ECHO -> kind == TrafficKind.ECHO
    LogFilter.ERROR -> info.isException || info.isBadCrc
    LogFilter.UNKNOWN -> kind == TrafficKind.AMBIGUOUS || kind == TrafficKind.DROPPED
}

/** 相对会话起点的时间：mm:ss.mmm，超过一小时前面加小时。 */
fun formatOffset(ms: Long): String {
    val total = ms.coerceAtLeast(0)
    val millis = (total % 1000).toInt()
    val seconds = (total / 1000 % 60).toInt()
    val minutes = (total / 60_000 % 60).toInt()
    val hours = (total / 3_600_000).toInt()
    return if (hours > 0) "%d:%02d:%02d.%03d".format(hours, minutes, seconds, millis)
    else "%02d:%02d.%03d".format(minutes, seconds, millis)
}
