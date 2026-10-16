package io.github.xingyingyuzhui.modbusmobile.ui.traffic

import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficDirection
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** 保存格式。CSV 带 UTF-8 BOM（Excel 才会正确显示中文），TXT 是一帧一行。 */
enum class ExportFormat(val extension: String, val mime: String) {
    CSV("csv", "text/csv"),
    TXT("txt", "text/plain"),
}

/** 日志里的墙钟时间：`HH:mm:ss.SSS`（本地时区；测试里可传固定时区）。 */
fun formatWallClock(wallMs: Long, zone: TimeZone = TimeZone.getDefault()): String =
    SimpleDateFormat("HH:mm:ss.SSS", Locale.US).apply { timeZone = zone }.format(Date(wallMs))

fun formatWallDateTime(wallMs: Long, zone: TimeZone = TimeZone.getDefault()): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).apply { timeZone = zone }.format(Date(wallMs))

fun exportFileName(format: ExportFormat, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): String =
    "modbus-log-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).apply { timeZone = zone }.format(Date(nowMs)) +
        "." + format.extension

private fun TrafficDirection.token() = if (this == TrafficDirection.TX) "TX" else "RX"

private fun LogEntry.crcToken(): String = when (info.crcOk) {
    true -> "OK"
    false -> "BAD"
    null -> ""
}

private fun csvCell(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

/**
 * 把已记录的日志写成文件内容（逐帧流式写，不在内存里拼大字符串）。
 *
 * CSV 列：序号, 日期时间(yyyy-MM-dd HH:mm:ss.SSS), 时间(HH:mm:ss.SSS), 方向, 类型, Unit, 功能码, 长度, CRC, HEX；
 * TXT：`HH:mm:ss.SSS TX 01 03 00 00 00 02 C4 0B`，CRC 错 / 异常帧行尾带标记。
 * 摘要文字（“读保持 U1 …”）是界面语言，随界面翻译，不写进文件，文件只放可复算的字段。
 * 新会话分隔：CSV 里一行 `#,时间,,,新会话…`，TXT 里一行 `--- 新会话 ---`。
 */
fun writeLogExport(out: OutputStream, items: List<LogItem>, format: ExportFormat, zone: TimeZone = TimeZone.getDefault()) {
    val writer = out.bufferedWriter(Charsets.UTF_8)
    if (format == ExportFormat.CSV) {
        writer.write("\uFEFF")
        writer.write("序号,日期时间,时间,方向,类型,Unit,功能码,长度,CRC,HEX\r\n")
    }
    var n = 0
    items.forEach { item ->
        when (item) {
            is LogMarker -> when (format) {
                ExportFormat.CSV -> writer.write("#,${formatWallDateTime(item.wallMs, zone)},${formatWallClock(item.wallMs, zone)},,新会话,,,,,\r\n")
                ExportFormat.TXT -> writer.write("${formatWallClock(item.wallMs, zone)} --- 新会话 ---\r\n")
            }
            is LogEntry -> {
                n++
                val fc = item.info.function?.let { "%d".format(it) } ?: ""
                val unit = item.info.unit?.toString() ?: ""
                when (format) {
                    ExportFormat.CSV -> writer.write(
                        listOf(
                            n.toString(),
                            formatWallDateTime(item.wallMs, zone),
                            formatWallClock(item.wallMs, zone),
                            item.event.direction.token(),
                            item.event.kind.name,
                            unit,
                            fc,
                            item.info.size.toString(),
                            item.crcToken(),
                            item.event.hex,
                        ).joinToString(",") { csvCell(it) } + "\r\n",
                    )
                    ExportFormat.TXT -> {
                        val tail = when {
                            item.info.isBadCrc -> "  [CRC 错误]"
                            item.info.isException -> "  [异常 %02X]".format(item.info.exceptionCode ?: 0)
                            else -> ""
                        }
                        writer.write("${formatWallClock(item.wallMs, zone)} ${item.event.direction.token()} ${item.event.hex}$tail\r\n")
                    }
                }
            }
        }
    }
    writer.flush()
}
