package io.github.xingyingyuzhui.modbusmobile.core.rtu

import io.github.xingyingyuzhui.modbusmobile.core.model.FunctionCode
import io.github.xingyingyuzhui.modbusmobile.core.transport.ByteChunk

enum class ScanMode {
    REQUEST,
    RESPONSE,
}

/**
 * 点对点扫描。长度只从 [FrameShapes] 取。
 * 默认回显跟踪用很长的时限，主站不打 tick 时仍能吞掉刚发出的整帧。
 */
class PointToPointScanner(
    private val echo: EchoTracker = EchoTracker(
        mode = EchoMode.ON,
        wireNs = { 0L },
        slackNs = Long.MAX_VALUE / 4,
    ),
) : RtuFrameScanner {
    private val buf = RxBuffer(4 * FrameShapes.MAX_ADU)
    private var mode = ScanMode.REQUEST
    private var expectedFunction: FunctionCode? = null
    private var epoch: Long = 0

    fun expectRequests() {
        mode = ScanMode.REQUEST
        expectedFunction = null
    }

    fun expectResponse(function: FunctionCode) {
        mode = ScanMode.RESPONSE
        expectedFunction = function
    }

    override fun onClockTick(nowMonotonicNs: Long): List<ScanEvent> {
        echo.expire(nowMonotonicNs)
        return emptyList()
    }

    override fun reset(epoch: Long) {
        this.epoch = epoch
        buf.clear()
        echo.clear()
    }

    override fun armEcho(frame: ByteArray) {
        echo.arm(frame, nowNs = 0L)
    }

    override fun accept(chunk: ByteChunk): List<ScanEvent> {
        if (epoch != 0L && chunk.connectionEpoch != epoch) {
            return listOf(ScanEvent.Discarded("epoch"))
        }
        val out = mutableListOf<ScanEvent>()
        push(chunk.bytes, out)
        drain(out)
        return out
    }

    private fun push(bytes: ByteArray, out: MutableList<ScanEvent>) {
        if (bytes.isEmpty()) return
        val capacity = buf.capacity
        val incoming = if (bytes.size > capacity) {
            out += ScanEvent.Discarded("buffer")
            bytes.copyOfRange(bytes.size - capacity, bytes.size)
        } else {
            bytes
        }
        val overflow = buf.size + incoming.size - capacity
        if (overflow > 0) {
            buf.drop(overflow)
            out += ScanEvent.Discarded("buffer")
        }
        buf.append(incoming)
    }

    private fun drain(out: MutableList<ScanEvent>) {
        repeat(MAX_STEPS) {
            if (buf.size == 0) return
            when (val echoMatch = echo.match(buf)) {
                is EchoMatch.Full -> {
                    out += ScanEvent.Echo(buf.copy(0, echoMatch.length))
                    buf.drop(echoMatch.length)
                    echo.consume()
                    return@repeat
                }
                EchoMatch.Partial -> return
                EchoMatch.Mismatch, EchoMatch.Idle -> Unit
            }
            when (val step = extract()) {
                Step.Wait -> return
                is Step.Emit -> {
                    out += step.event
                    buf.drop(step.length)
                }
                is Step.Drop -> {
                    buf.drop(1)
                    out += ScanEvent.Discarded(step.reason)
                }
            }
        }
    }

    private fun extract(): Step {
        if (buf.size < 2) return Step.Wait
        return if (mode == ScanMode.RESPONSE) extractResponse() else extractRequest()
    }

    private fun extractRequest(): Step {
        if ((buf[1] and 0x80) != 0) {
            if (buf.size < 5) return Step.Wait
            return if (Crc16Modbus.matches(buf, 0, 5)) {
                Step.Emit(5, ScanEvent.Discarded("exception-shaped"))
            } else {
                Step.Drop("crc")
            }
        }
        return when (val shape = FrameShapes.request(buf)) {
            is Shape.NeedMore -> Step.Wait
            is Shape.Length -> when {
                shape.total > buf.size -> Step.Wait
                Crc16Modbus.matches(buf, 0, shape.total) -> {
                    Step.Emit(shape.total, ScanEvent.LocalRequest(buf.copy(0, shape.total)))
                }
                else -> Step.Drop("crc")
            }
            Shape.Malformed -> Step.Drop("length")
            Shape.Unmodeled -> Step.Drop("unknown-function")
        }
    }

    private fun extractResponse(): Step {
        val expected = expectedFunction ?: return Step.Drop("no-expectation")
        return when (val shape = FrameShapes.response(expected.code, buf)) {
            is Shape.NeedMore -> Step.Wait
            is Shape.Length -> when {
                shape.total > buf.size -> Step.Wait
                Crc16Modbus.matches(buf, 0, shape.total) -> {
                    Step.Emit(shape.total, ScanEvent.Response(buf.copy(0, shape.total)))
                }
                else -> Step.Drop("crc")
            }
            Shape.Malformed -> Step.Drop("crc")
            Shape.Unmodeled -> Step.Drop("length")
        }
    }

    private sealed interface Step {
        data object Wait : Step
        class Emit(val length: Int, val event: ScanEvent) : Step
        class Drop(val reason: String) : Step
    }

    private companion object {
        const val MAX_STEPS = 1024
    }
}
