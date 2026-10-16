package io.github.xingyingyuzhui.modbusmobile.core.rtu

import io.github.xingyingyuzhui.modbusmobile.core.transport.ByteChunk

/**
 * 多站总线扫描。本机请求只有在 [trusted] 为 true 时才会变成 [ScanEvent.LocalRequest]。
 * 从不可信起点恢复出来的本机请求记为 [ScanEvent.Ambiguous]，调用方不得回复、不得写寄存器。
 */
class MultidropScanner(
    private val config: ScannerConfig,
    private val echo: EchoTracker? = null,
) : RtuFrameScanner {
    private val buf = RxBuffer(config.maxBufferBytes)
    private var epoch = 0L
    private var trusted = true
    private var observed: Observed? = null
    private var partialSinceNs = -1L
    private var lastByteNs = -1L
    val stats = ScannerStats()
    val pendingBytes: Int get() = buf.size

    override fun reset(epoch: Long) {
        this.epoch = epoch
        buf.clear()
        trusted = true
        observed = null
        partialSinceNs = -1L
        lastByteNs = -1L
        echo?.clear()
    }

    override fun armEcho(frame: ByteArray) {
        val now = if (lastByteNs >= 0) lastByteNs else 0L
        echo?.arm(frame, now)
    }

    override fun accept(chunk: ByteChunk): List<ScanEvent> {
        if (chunk.connectionEpoch != epoch) {
            return listOf(record(ScanEvent.Dropped(DropReason.OLD_EPOCH, chunk.bytes.size)))
        }
        val out = mutableListOf<ScanEvent>()
        val now = chunk.receivedAtMonotonicNs
        if (lastByteNs >= 0 && now - lastByteNs >= config.idleGapNs) {
            if (buf.size > 0) out += record(dropAll(DropReason.STALE_PARTIAL_FRAME))
            trusted = true
        }
        if (buf.size == 0) partialSinceNs = now
        val overflow = buf.size + chunk.bytes.size - config.maxBufferBytes
        if (overflow > 0) {
            buf.drop(overflow)
            trusted = false
            if (buf.size == 0) partialSinceNs = now
            out += record(ScanEvent.Dropped(DropReason.BUFFER_OVERFLOW, overflow))
        }
        val incoming = if (chunk.bytes.size > config.maxBufferBytes) {
            chunk.bytes.copyOfRange(chunk.bytes.size - config.maxBufferBytes, chunk.bytes.size)
        } else {
            chunk.bytes
        }
        buf.append(incoming)
        lastByteNs = now
        drain(now, out)
        return out
    }

    override fun onClockTick(nowMonotonicNs: Long): List<ScanEvent> {
        val out = mutableListOf<ScanEvent>()
        observed?.takeIf { nowMonotonicNs >= it.deadlineNs }?.let { pending ->
            observed = null
            out += record(ScanEvent.TransactionClosed(pending.unit, pending.function, CloseReason.RESPONSE_TIMEOUT))
        }
        echo?.expire(nowMonotonicNs)
        if (buf.size > 0 && partialSinceNs >= 0 && nowMonotonicNs - partialSinceNs >= config.frameAssemblyBudgetNs) {
            if (lastByteNs >= 0 && nowMonotonicNs - lastByteNs >= config.idleGapNs) {
                out += record(dropAll(DropReason.STALE_PARTIAL_FRAME))
                trusted = true
            } else {
                buf.drop(1)
                trusted = false
                partialSinceNs = nowMonotonicNs
                out += record(ScanEvent.Dropped(DropReason.STALE_PARTIAL_FRAME, 1))
            }
        }
        drain(nowMonotonicNs, out)
        return out
    }

    private fun drain(now: Long, out: MutableList<ScanEvent>) {
        repeat(config.maxStepsPerCall) {
            if (buf.size == 0) return
            when (val step = classifyHead(now)) {
                Step.Wait -> return
                is Step.Emit -> {
                    if (step.length <= 0 || step.length > buf.size) {
                        buf.drop(1)
                        trusted = false
                        out += record(ScanEvent.Dropped(DropReason.MALFORMED, 1))
                    } else {
                        buf.drop(step.length)
                        step.events.forEach { out += record(it) }
                        trusted = true
                        partialSinceNs = now
                    }
                }
                is Step.DropByte -> {
                    buf.drop(1)
                    trusted = false
                    out += record(ScanEvent.Dropped(step.reason, 1))
                }
            }
        }
    }

    private fun classifyHead(now: Long): Step {
        echo?.let { tracker ->
            when (val match = tracker.match(buf)) {
                is EchoMatch.Full -> {
                    val raw = buf.copy(0, match.length)
                    tracker.consume()
                    return Step.Emit(match.length, listOf(ScanEvent.Echo(raw)))
                }
                EchoMatch.Partial -> return Step.Wait
                EchoMatch.Mismatch, EchoMatch.Idle -> Unit
            }
        }
        if (buf.size < 2) return Step.Wait
        val unit = buf[0]
        val current = observed
        val candidates = buildList {
            if (current != null && unit == current.unit) {
                add(Candidate.ObservedResponse(FrameShapes.response(current.function, buf)))
            }
            add(Candidate.Request(FrameShapes.request(buf)))
            if (unit !in config.localUnits && unit != 0 && (current == null || unit != current.unit)) {
                add(Candidate.UnsolicitedResponse(FrameShapes.response(buf[1] and 0x7F, buf)))
            }
        }
        val valid = candidates.firstOrNull { candidate ->
            val shape = candidate.shape
            shape is Shape.Length &&
                shape.total <= buf.size &&
                Crc16Modbus.matches(buf, 0, shape.total)
        }
        if (valid != null) return emit(valid, now)
        if (candidates.any { candidate ->
                val shape = candidate.shape
                shape is Shape.NeedMore || (shape is Shape.Length && shape.total > buf.size)
            }
        ) {
            return Step.Wait
        }
        val reason = if (candidates.all { it.shape is Shape.Unmodeled }) {
            DropReason.UNKNOWN_UNFRAMED_FC
        } else {
            DropReason.CRC
        }
        return Step.DropByte(reason)
    }

    private fun emit(candidate: Candidate, now: Long): Step {
        val length = (candidate.shape as Shape.Length).total
        val raw = buf.copy(0, length)
        val unit = raw[0].toInt() and 0xFF
        val events = mutableListOf<ScanEvent>()
        when (candidate) {
            is Candidate.ObservedResponse -> {
                val pending = observed
                if (pending != null) {
                    events += ScanEvent.TransactionClosed(pending.unit, pending.function, CloseReason.RESPONDED)
                }
                observed = null
                events += ScanEvent.ForeignResponse(raw, solicited = true)
            }
            is Candidate.UnsolicitedResponse -> events += ScanEvent.ForeignResponse(raw, solicited = false)
            is Candidate.Request -> {
                val isLocal = unit == 0 || unit in config.localUnits
                if (isLocal && !trusted) {
                    events += ScanEvent.Ambiguous(raw, "untrusted-start")
                } else {
                    observed?.let { pending ->
                        events += ScanEvent.TransactionClosed(
                            pending.unit,
                            pending.function,
                            CloseReason.PREEMPTED_BY_NEW_REQUEST,
                        )
                    }
                    observed = if (!isLocal) {
                        Observed(unit, raw[1].toInt() and 0xFF, now + config.responseTimeoutNs)
                    } else {
                        null
                    }
                    events += if (isLocal) ScanEvent.LocalRequest(raw) else ScanEvent.ForeignRequest(raw)
                }
            }
        }
        return Step.Emit(length, events)
    }

    private fun dropAll(reason: DropReason): ScanEvent.Dropped {
        val bytes = buf.size
        buf.clear()
        partialSinceNs = -1L
        return ScanEvent.Dropped(reason, bytes)
    }

    private fun record(event: ScanEvent): ScanEvent {
        stats.on(event)
        return event
    }

    private data class Observed(val unit: Int, val function: Int, val deadlineNs: Long)

    private sealed interface Candidate {
        val shape: Shape

        class ObservedResponse(override val shape: Shape) : Candidate
        class Request(override val shape: Shape) : Candidate
        class UnsolicitedResponse(override val shape: Shape) : Candidate
    }

    private sealed interface Step {
        data object Wait : Step
        class Emit(val length: Int, val events: List<ScanEvent>) : Step
        class DropByte(val reason: DropReason) : Step
    }
}
