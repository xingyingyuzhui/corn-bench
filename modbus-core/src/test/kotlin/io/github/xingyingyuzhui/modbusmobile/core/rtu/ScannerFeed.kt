package io.github.xingyingyuzhui.modbusmobile.core.rtu

import io.github.xingyingyuzhui.modbusmobile.core.model.toHex

fun f(hex: String): ByteArray = Crc16Modbus.append(
    hex.split(" ").filter { it.isNotBlank() }.map { it.toInt(16).toByte() }.toByteArray(),
)

class Feed(val scanner: RtuFrameScanner, val epoch: Long = 1) {
    fun at(ms: Long, vararg chunks: ByteArray): List<ScanEvent> =
        chunks.flatMap { chunk ->
            scanner.accept(
                io.github.xingyingyuzhui.modbusmobile.core.transport.ByteChunk(
                    chunk,
                    ms * 1_000_000,
                    epoch,
                ),
            )
        }

    fun tick(ms: Long): List<ScanEvent> = scanner.onClockTick(ms * 1_000_000)
}

fun serialConfig(timeoutMs: Long = 1_000): ScannerConfig = ScannerConfig.forSerial(
    baud = 9600,
    bitsPerChar = 10,
    localUnits = setOf(1),
    responseTimeoutMs = timeoutMs,
)

fun openScanner(timeoutMs: Long = 1_000, echo: EchoTracker? = null): MultidropScanner =
    MultidropScanner(serialConfig(timeoutMs), echo).also { it.reset(1) }

fun frameEvents(events: List<ScanEvent>): List<ScanEvent> = events.filter {
    it is ScanEvent.LocalRequest || it is ScanEvent.ForeignRequest ||
        it is ScanEvent.ForeignResponse || it is ScanEvent.Response ||
        it is ScanEvent.Echo || it is ScanEvent.Ambiguous
}

fun ScanEvent.rawHex(): String = when (this) {
    is ScanEvent.LocalRequest -> raw.toHex()
    is ScanEvent.ForeignRequest -> raw.toHex()
    is ScanEvent.ForeignResponse -> raw.toHex()
    is ScanEvent.Response -> raw.toHex()
    is ScanEvent.Echo -> raw.toHex()
    is ScanEvent.Ambiguous -> raw.toHex()
    else -> ""
}
