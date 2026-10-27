package io.github.xingyingyuzhui.modbusmobile.core.rtu

import io.github.xingyingyuzhui.modbusmobile.core.transport.ByteChunk

sealed interface ScanEvent {
    class LocalRequest(val raw: ByteArray) : ScanEvent
    class ForeignRequest(val raw: ByteArray) : ScanEvent
    class ForeignResponse(val raw: ByteArray, val solicited: Boolean) : ScanEvent
    class Response(val raw: ByteArray) : ScanEvent
    class Echo(val raw: ByteArray) : ScanEvent
    class Ambiguous(val raw: ByteArray, val reason: String) : ScanEvent
    data class Dropped(val reason: DropReason, val bytes: Int) : ScanEvent
    data class TransactionClosed(val unit: Int, val function: Int, val reason: CloseReason) : ScanEvent

    /** 点对点扫描器内部丢弃，不带原始字节。 */
    data class Discarded(val reason: String) : ScanEvent
}

enum class DropReason {
    CRC,
    UNKNOWN_UNFRAMED_FC,
    MALFORMED,
    STALE_PARTIAL_FRAME,
    BUFFER_OVERFLOW,
    OLD_EPOCH,
}

enum class CloseReason {
    RESPONDED,
    PREEMPTED_BY_NEW_REQUEST,
    RESPONSE_TIMEOUT,
}

enum class BusTopology {
    POINT_TO_POINT,
    MULTIDROP,
}

class ScannerStats {
    private val drops = mutableMapOf<DropReason, Int>()
    private val closes = mutableMapOf<CloseReason, Int>()
    private val events = mutableMapOf<String, Int>()

    fun on(event: ScanEvent) {
        when (event) {
            is ScanEvent.Dropped -> drops[event.reason] = dropped(event.reason) + 1
            is ScanEvent.TransactionClosed -> closes[event.reason] = closed(event.reason) + 1
            else -> {
                val name = event::class.simpleName ?: "event"
                events[name] = (events[name] ?: 0) + 1
            }
        }
    }

    fun dropped(reason: DropReason): Int = drops[reason] ?: 0

    fun closed(reason: CloseReason): Int = closes[reason] ?: 0

    fun events(name: String): Int = events[name] ?: 0
}

data class ScannerConfig(
    val localUnits: Set<Int>,
    val responseTimeoutNs: Long,
    val frameAssemblyBudgetNs: Long,
    val idleGapNs: Long,
    val maxBufferBytes: Int = 4 * FrameShapes.MAX_ADU,
    val maxStepsPerCall: Int = 512,
) {
    companion object {
        /** bitsPerChar = 1 起始位 + 数据位 + 校验位(0 或 1) + 停止位。 */
        fun forSerial(
            baud: Int,
            bitsPerChar: Int,
            localUnits: Set<Int>,
            responseTimeoutMs: Long,
            usbSlackMs: Long = 40,
            schedSlackMs: Long = 20,
        ): ScannerConfig {
            require(baud > 0 && bitsPerChar > 0)
            fun wireNs(bytes: Int) = bytes * bitsPerChar * 1_000_000_000L / baud
            return ScannerConfig(
                localUnits = localUnits,
                responseTimeoutNs = responseTimeoutMs * 1_000_000,
                frameAssemblyBudgetNs = wireNs(FrameShapes.MAX_ADU) + (2 * usbSlackMs + schedSlackMs) * 1_000_000,
                idleGapNs = maxOf(wireNs(4), (usbSlackMs + schedSlackMs) * 1_000_000),
            )
        }
    }
}

interface RtuFrameScanner {
    fun onClockTick(nowMonotonicNs: Long): List<ScanEvent>
    fun reset(epoch: Long)
    fun armEcho(frame: ByteArray)
    fun accept(chunk: ByteChunk): List<ScanEvent>
}
