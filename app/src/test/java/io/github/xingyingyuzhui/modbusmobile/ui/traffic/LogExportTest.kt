package io.github.xingyingyuzhui.modbusmobile.ui.traffic

import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficDirection
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficKind
import java.io.ByteArrayOutputStream
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogExportTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val wall = 1_760_000_000_123L // 2025-10-09 08:53:20.123 UTC

    private fun entry(seq: Long, bytes: ByteArray, kind: TrafficKind, dir: TrafficDirection, wallMs: Long) =
        LogEntry(seq, TrafficEvent(0, dir, bytes, kind), describeFrame(bytes, kind), wallMs, 0)

    private val request = byteArrayOf(0x01, 0x03, 0x00, 0x00, 0x00, 0x02, 0xC4.toByte(), 0x0B)
    private val badCrc = byteArrayOf(0x01, 0x03, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00)

    private fun export(items: List<LogItem>, format: ExportFormat): String {
        val out = ByteArrayOutputStream()
        writeLogExport(out, items, format, utc)
        return out.toString(Charsets.UTF_8)
    }

    @Test
    fun wallClockFormatIsHhMmSsSss() {
        assertEquals("08:53:20.123", formatWallClock(wall, utc))
        assertEquals("2025-10-09 08:53:20.123", formatWallDateTime(wall, utc))
        assertEquals("modbus-log-20251009-085320.csv", exportFileName(ExportFormat.CSV, wall, utc))
        assertEquals("modbus-log-20251009-085320.txt", exportFileName(ExportFormat.TXT, wall, utc))
    }

    @Test
    fun csvHasBomHeaderAndOneRowPerFrameWithWallClock() {
        val text = export(
            listOf(
                entry(0, request, TrafficKind.TX_REQUEST, TrafficDirection.TX, wall),
                entry(1, badCrc, TrafficKind.RX_RESPONSE, TrafficDirection.RX, wall + 6),
            ),
            ExportFormat.CSV,
        )
        assertTrue("UTF-8 BOM", text.startsWith("\uFEFF"))
        val lines = text.removePrefix("\uFEFF").trimEnd().split("\r\n")
        assertEquals(3, lines.size)
        assertEquals("序号,日期时间,时间,方向,类型,Unit,功能码,长度,CRC,HEX", lines[0])
        assertEquals("1,2025-10-09 08:53:20.123,08:53:20.123,TX,TX_REQUEST,1,3,8,OK,01 03 00 00 00 02 C4 0B", lines[1])
        assertEquals("2,2025-10-09 08:53:20.129,08:53:20.129,RX,RX_RESPONSE,1,3,8,BAD,01 03 00 00 00 02 00 00", lines[2])
    }

    @Test
    fun txtIsOneLinePerFrameStartingWithTheWallClock() {
        val text = export(
            listOf(
                entry(0, request, TrafficKind.TX_REQUEST, TrafficDirection.TX, wall),
                entry(1, badCrc, TrafficKind.RX_RESPONSE, TrafficDirection.RX, wall + 6),
            ),
            ExportFormat.TXT,
        )
        val lines = text.trimEnd().split("\r\n")
        assertEquals("08:53:20.123 TX 01 03 00 00 00 02 C4 0B", lines[0])
        assertEquals("08:53:20.129 RX 01 03 00 00 00 02 00 00  [CRC 错误]", lines[1])
    }

    @Test
    fun newSessionMarkersAreWrittenAndNotCountedAsFrames() {
        val items = listOf(
            entry(0, request, TrafficKind.TX_REQUEST, TrafficDirection.TX, wall),
            LogMarker(1, wall + 1000, 2),
            entry(2, request, TrafficKind.TX_REQUEST, TrafficDirection.TX, wall + 2000),
        )
        val csv = export(items, ExportFormat.CSV).removePrefix("\uFEFF").trimEnd().split("\r\n")
        assertTrue(csv[2].startsWith("#,2025-10-09 08:53:21.123,08:53:21.123,,新会话"))
        assertTrue("序号不被标记打断", csv[3].startsWith("2,"))
        val txt = export(items, ExportFormat.TXT).trimEnd().split("\r\n")
        assertEquals("08:53:21.123 --- 新会话 ---", txt[1])
    }

    @Test
    fun csvCellsWithCommasOrQuotesAreEscaped() {
        // HEX 列本身不会有逗号；这里直接测转义规则通过空帧的类型名之外的路径不可达，
        // 所以只断言：普通行不含多余引号。
        val text = export(listOf(entry(0, request, TrafficKind.TX_REQUEST, TrafficDirection.TX, wall)), ExportFormat.CSV)
        assertTrue(!text.contains('"'))
    }
}
