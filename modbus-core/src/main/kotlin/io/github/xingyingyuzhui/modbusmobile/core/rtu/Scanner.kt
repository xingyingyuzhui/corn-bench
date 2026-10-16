package io.github.xingyingyuzhui.modbusmobile.core.rtu

import io.github.xingyingyuzhui.modbusmobile.core.model.FunctionCode
import io.github.xingyingyuzhui.modbusmobile.core.transport.ByteChunk

sealed interface ScanEvent {
    data class Request(val raw: ByteArray) : ScanEvent
    data class Response(val raw: ByteArray) : ScanEvent
    data class Echo(val raw: ByteArray) : ScanEvent
    data class Discarded(val reason: String) : ScanEvent
}

interface RtuFrameScanner {
    fun onClockTick(nowMonotonicNs: Long)
    fun reset(epoch: Long)
    fun armEcho(frame: ByteArray)
    fun accept(chunk: ByteChunk): List<ScanEvent>
}

enum class ScanMode {
    REQUEST,
    RESPONSE,
}

/**
 * 点对点扫描：只认当前方向上的完整帧，并抑制与刚发送帧完全一致的回显。
 * 回显可以跨多个 [ByteChunk] 拼齐；方向模糊和多站抢占不在这里做。
 */
class PointToPointScanner : RtuFrameScanner {
    private val buffer = ArrayDeque<Byte>()
    private var mode = ScanMode.REQUEST
    private var expectedFunction: FunctionCode? = null
    private var echo: ByteArray? = null
    private var epoch: Long = 0

    fun expectRequests() {
        mode = ScanMode.REQUEST
        expectedFunction = null
    }

    fun expectResponse(function: FunctionCode) {
        mode = ScanMode.RESPONSE
        expectedFunction = function
    }

    override fun onClockTick(nowMonotonicNs: Long) = Unit

    override fun reset(epoch: Long) {
        this.epoch = epoch
        buffer.clear()
        echo = null
    }

    override fun armEcho(frame: ByteArray) {
        echo = frame.copyOf()
    }

    override fun accept(chunk: ByteChunk): List<ScanEvent> {
        if (epoch != 0L && chunk.connectionEpoch != epoch) {
            return listOf(ScanEvent.Discarded("epoch"))
        }
        chunk.bytes.forEach { buffer.addLast(it) }
        val events = mutableListOf<ScanEvent>()
        while (buffer.size > MAX_BUFFER) {
            buffer.removeFirst()
            events += ScanEvent.Discarded("buffer")
        }
        while (true) {
            if (!consumeEcho(events)) break
            when (val step = extractOne()) {
                Extract.NeedMore -> break
                is Extract.Event -> events += step.event
            }
        }
        return events
    }

    private fun consumeEcho(events: MutableList<ScanEvent>): Boolean {
        val expected = echo ?: return true
        if (buffer.size < expected.size) {
            if (isPrefixOf(expected)) return false
            echo = null
            return true
        }
        if (startsWith(expected)) {
            events += ScanEvent.Echo(take(expected.size))
            echo = null
            return true
        }
        echo = null
        return true
    }

    private fun extractOne(): Extract {
        if (buffer.size < 2) return Extract.NeedMore
        return if (mode == ScanMode.RESPONSE) extractResponse() else extractRequest()
    }

    private fun extractRequest(): Extract {
        val function = buffer[1].toInt() and 0xFF
        if ((function and 0x80) != 0) {
            if (buffer.size < 5) return Extract.NeedMore
            val frame = peek(5)
            return if (Crc16Modbus.matches(frame)) {
                Extract.Event(ScanEvent.Discarded("exception-shaped")).also { take(5) }
            } else {
                dropOne("crc")
            }
        }
        val length = requestLength(function) ?: return Extract.NeedMore
        if (length < 0) return dropOne("unknown-function")
        val frame = peek(length)
        if (!Crc16Modbus.matches(frame)) return dropOne("crc")
        take(length)
        return Extract.Event(ScanEvent.Request(frame))
    }

    private fun extractResponse(): Extract {
        val expected = expectedFunction ?: return dropOne("no-expectation")
        val function = buffer[1].toInt() and 0xFF
        if (function == (expected.code or 0x80)) {
            if (buffer.size < 5) return Extract.NeedMore
            val frame = peek(5)
            return if (Crc16Modbus.matches(frame)) {
                take(5)
                Extract.Event(ScanEvent.Response(frame))
            } else {
                dropOne("crc")
            }
        }
        val length = responseLength(expected) ?: return Extract.NeedMore
        if (length < 0) return dropOne("length")
        val frame = peek(length)
        if (!Crc16Modbus.matches(frame) || frame[1].toInt().and(0xFF) != expected.code) {
            return dropOne("crc")
        }
        take(length)
        return Extract.Event(ScanEvent.Response(frame))
    }

    private fun requestLength(function: Int): Int? = when (function) {
        0x01, 0x02, 0x03, 0x04, 0x05, 0x06 -> if (buffer.size < 8) null else 8
        0x0F, 0x10 -> countedLength(header = 7, byteCountAt = 6, extra = 9)
        else -> -1
    }

    private fun responseLength(expected: FunctionCode): Int? = when (expected) {
        FunctionCode.READ_COILS,
        FunctionCode.READ_DISCRETE_INPUTS,
        FunctionCode.READ_HOLDING_REGISTERS,
        FunctionCode.READ_INPUT_REGISTERS,
        -> countedLength(header = 3, byteCountAt = 2, extra = 5)
        FunctionCode.WRITE_SINGLE_COIL,
        FunctionCode.WRITE_SINGLE_REGISTER,
        FunctionCode.WRITE_MULTIPLE_COILS,
        FunctionCode.WRITE_MULTIPLE_REGISTERS,
        -> if (buffer.size < 8) null else 8
    }

    private fun countedLength(header: Int, byteCountAt: Int, extra: Int): Int? {
        if (buffer.size < header) return null
        val byteCount = buffer[byteCountAt].toInt() and 0xFF
        if (byteCount > 250) return -1
        val total = extra + byteCount
        return if (buffer.size < total) null else total
    }

    private fun dropOne(reason: String): Extract {
        buffer.removeFirst()
        return Extract.Event(ScanEvent.Discarded(reason))
    }

    private fun isPrefixOf(frame: ByteArray): Boolean {
        if (buffer.size > frame.size) return false
        buffer.forEachIndexed { index, byte -> if (byte != frame[index]) return false }
        return true
    }

    private fun startsWith(frame: ByteArray): Boolean {
        if (buffer.size < frame.size) return false
        for (index in frame.indices) {
            if (buffer.elementAt(index) != frame[index]) return false
        }
        return true
    }

    private fun peek(length: Int): ByteArray = ByteArray(length) { buffer.elementAt(it) }

    private fun take(length: Int): ByteArray {
        val frame = peek(length)
        repeat(length) { buffer.removeFirst() }
        return frame
    }

    private sealed interface Extract {
        data object NeedMore : Extract
        data class Event(val event: ScanEvent) : Extract
    }

    private companion object {
        const val MAX_BUFFER = 512
    }
}

/**
 * TODO: 多站总线扫描见 spec §3.2 / §3.2.2a，留给下一个 PR。
 * 本期不实现外站抢占、残帧时钟和模糊方向。
 */
class MultidropScanner : RtuFrameScanner {
    override fun onClockTick(nowMonotonicNs: Long) = Unit
    override fun reset(epoch: Long) = Unit
    override fun armEcho(frame: ByteArray) = Unit
    override fun accept(chunk: ByteChunk): List<ScanEvent> {
        error("MultidropScanner 未实现，见 spec §3.2/§3.2.2a")
    }
}
