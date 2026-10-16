package io.github.xingyingyuzhui.modbusmobile.core.master

import io.github.xingyingyuzhui.modbusmobile.core.model.ExceptionCode
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResponse
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficDirection
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import io.github.xingyingyuzhui.modbusmobile.core.rtu.DecodedResponse
import io.github.xingyingyuzhui.modbusmobile.core.rtu.PointToPointScanner
import io.github.xingyingyuzhui.modbusmobile.core.rtu.RtuCodec
import io.github.xingyingyuzhui.modbusmobile.core.rtu.ScanEvent
import io.github.xingyingyuzhui.modbusmobile.core.transport.ByteTransport
import io.github.xingyingyuzhui.modbusmobile.core.transport.ConnectionState
import io.github.xingyingyuzhui.modbusmobile.core.transport.MonotonicClock
import io.github.xingyingyuzhui.modbusmobile.core.transport.SendResult
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield

interface MasterEngine {
    val traffic: SharedFlow<TrafficEvent>
    suspend fun execute(request: ModbusRequest): ModbusResult
    fun close()
}

class DefaultMasterEngine(
    private val transport: ByteTransport,
    private val clock: MonotonicClock,
    private val scope: CoroutineScope,
    private val responseTimeout: Duration = 1_000.milliseconds,
    private val turnaround: Duration = 150.milliseconds,
    private val suppressEcho: Boolean = false,
    private val onTraffic: (TrafficEvent) -> Unit = {},
) : MasterEngine {
    private val mutex = Mutex()
    private val inbox = Channel<io.github.xingyingyuzhui.modbusmobile.core.transport.ByteChunk>(capacity = 1024)
    private val scanner = PointToPointScanner()
    private val events = MutableSharedFlow<TrafficEvent>(replay = 64, extraBufferCapacity = 64)
    private val collector = scope.launch {
        transport.received.collect { chunk -> inbox.send(chunk) }
    }

    override val traffic: SharedFlow<TrafficEvent> = events.asSharedFlow()

    override suspend fun execute(request: ModbusRequest): ModbusResult = mutex.withLock {
        if (transport.connectionState.value != ConnectionState.CONNECTED) {
            return ModbusResult.Disconnected
        }
        val epoch = transport.connectionEpoch
        yield()
        while (inbox.tryReceive().getOrNull() != null) {
            // 丢掉上一笔迟到的字节，避免配到新请求上。
        }
        scanner.reset(epoch)
        scanner.expectResponse(request.function)
        val frame = RtuCodec.encodeRequest(request)
        if (suppressEcho) scanner.armEcho(frame)
        publish(TrafficDirection.TX, frame)
        var sent = false
        when (val send = transport.send(frame)) {
            is SendResult.Failed -> {
                return if (request.isWrite) ModbusResult.UnknownOutcome else ModbusResult.Disconnected
            }
            SendResult.AcceptedByTransport, SendResult.WriteReturned, SendResult.ConfirmedOnWire -> sent = true
        }
        if (transport.connectionEpoch != epoch || transport.connectionState.value != ConnectionState.CONNECTED) {
            return if (request.isWrite && sent) ModbusResult.UnknownOutcome else ModbusResult.Disconnected
        }
        if (request.isBroadcast) {
            if (turnaround > Duration.ZERO) delay(turnaround)
            return ModbusResult.Success(syntheticBroadcast(request))
        }
        val deadline = clock.nowNs() + responseTimeout.inWholeNanoseconds
        try {
            while (clock.nowNs() < deadline) {
                if (transport.connectionEpoch != epoch || transport.connectionState.value != ConnectionState.CONNECTED) {
                    return if (request.isWrite) ModbusResult.UnknownOutcome else ModbusResult.Disconnected
                }
                val remainingMs = ((deadline - clock.nowNs()) / 1_000_000L).coerceAtLeast(0L)
                val chunk = withTimeoutOrNull(remainingMs) { inbox.receive() } ?: break
                if (chunk.connectionEpoch != epoch) {
                    return if (request.isWrite) ModbusResult.UnknownOutcome else ModbusResult.Disconnected
                }
                for (event in scanner.accept(chunk)) {
                    when (event) {
                        is ScanEvent.Response -> {
                            publish(TrafficDirection.RX, event.raw)
                            return match(request, event.raw)
                        }
                        is ScanEvent.Echo, is ScanEvent.Discarded, is ScanEvent.Request -> Unit
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return if (request.isWrite && sent) ModbusResult.UnknownOutcome else ModbusResult.Disconnected
        }
        return if (request.isWrite) ModbusResult.UnknownOutcome else ModbusResult.Timeout
    }

    override fun close() {
        collector.cancel()
        inbox.close()
    }

    private fun publish(direction: TrafficDirection, bytes: ByteArray) {
        val event = TrafficEvent(clock.nowNs(), direction, bytes.copyOf())
        events.tryEmit(event)
        onTraffic(event)
    }

    private fun match(request: ModbusRequest, raw: ByteArray): ModbusResult {
        val decoded = when (val result = RtuCodec.decodeResponse(raw)) {
            is DecodedResponse.Ok -> result.response
            DecodedResponse.BadCrc, DecodedResponse.Invalid -> {
                return ModbusResult.InvalidResponse("响应无法解码")
            }
        }
        if (decoded.unitId != request.unitId) return ModbusResult.InvalidResponse("Unit ID 不匹配")
        if (decoded is ModbusResponse.ExceptionFrame) {
            if (decoded.function != request.function) return ModbusResult.InvalidResponse("异常功能码不匹配")
            return ModbusResult.Exception(decoded.code)
        }
        if (decoded.function != request.function) return ModbusResult.InvalidResponse("功能码不匹配")
        return when (request) {
            is ModbusRequest.ReadCoils, is ModbusRequest.ReadDiscreteInputs -> {
                val response = decoded as? ModbusResponse.ReadBits
                    ?: return ModbusResult.InvalidResponse("响应类型不匹配")
                val quantity = request.readQuantity
                val expectedBytes = (quantity + 7) / 8
                if (response.values.size != expectedBytes * 8) {
                    return ModbusResult.InvalidResponse("字节数不匹配")
                }
                ModbusResult.Success(response.copy(values = response.values.take(quantity)))
            }
            is ModbusRequest.ReadHoldingRegisters, is ModbusRequest.ReadInputRegisters -> {
                val response = decoded as? ModbusResponse.ReadRegisters
                    ?: return ModbusResult.InvalidResponse("响应类型不匹配")
                if (response.values.size != request.readQuantity) {
                    return ModbusResult.InvalidResponse("数量不匹配")
                }
                ModbusResult.Success(response)
            }
            is ModbusRequest.WriteSingleCoil -> {
                val response = decoded as? ModbusResponse.WriteSingleCoil
                    ?: return ModbusResult.InvalidResponse("响应类型不匹配")
                if (response.address != request.address || response.value != request.value) {
                    return ModbusResult.InvalidResponse("写入回显不匹配")
                }
                ModbusResult.Success(response)
            }
            is ModbusRequest.WriteSingleRegister -> {
                val response = decoded as? ModbusResponse.WriteSingleRegister
                    ?: return ModbusResult.InvalidResponse("响应类型不匹配")
                if (response.address != request.address || response.value != request.value) {
                    return ModbusResult.InvalidResponse("写入回显不匹配")
                }
                ModbusResult.Success(response)
            }
            is ModbusRequest.WriteMultipleCoils -> {
                val response = decoded as? ModbusResponse.WriteMultipleCoils
                    ?: return ModbusResult.InvalidResponse("响应类型不匹配")
                if (response.address != request.address || response.quantity != request.quantity) {
                    return ModbusResult.InvalidResponse("写入回显不匹配")
                }
                ModbusResult.Success(response)
            }
            is ModbusRequest.WriteMultipleRegisters -> {
                val response = decoded as? ModbusResponse.WriteMultipleRegisters
                    ?: return ModbusResult.InvalidResponse("响应类型不匹配")
                if (response.address != request.address || response.quantity != request.quantity) {
                    return ModbusResult.InvalidResponse("写入回显不匹配")
                }
                ModbusResult.Success(response)
            }
        }
    }

    private fun syntheticBroadcast(request: ModbusRequest): ModbusResponse = when (request) {
        is ModbusRequest.WriteSingleCoil -> ModbusResponse.WriteSingleCoil(0, request.function, request.address, request.value)
        is ModbusRequest.WriteSingleRegister -> ModbusResponse.WriteSingleRegister(0, request.function, request.address, request.value)
        is ModbusRequest.WriteMultipleCoils -> ModbusResponse.WriteMultipleCoils(0, request.function, request.address, request.quantity)
        is ModbusRequest.WriteMultipleRegisters -> {
            ModbusResponse.WriteMultipleRegisters(0, request.function, request.address, request.quantity)
        }
        else -> ModbusResponse.ExceptionFrame(0, request.function, ExceptionCode.ILLEGAL_FUNCTION)
    }
}

private val ModbusRequest.readQuantity: Int
    get() = when (this) {
        is ModbusRequest.ReadCoils -> quantity
        is ModbusRequest.ReadDiscreteInputs -> quantity
        is ModbusRequest.ReadHoldingRegisters -> quantity
        is ModbusRequest.ReadInputRegisters -> quantity
        else -> error("not a read")
    }
