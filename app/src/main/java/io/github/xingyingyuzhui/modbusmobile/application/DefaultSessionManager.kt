package io.github.xingyingyuzhui.modbusmobile.application

import io.github.xingyingyuzhui.modbusmobile.core.master.DefaultMasterEngine
import io.github.xingyingyuzhui.modbusmobile.core.master.MasterEngine
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import io.github.xingyingyuzhui.modbusmobile.core.rtu.EchoTracker
import io.github.xingyingyuzhui.modbusmobile.core.rtu.ScannerConfig
import io.github.xingyingyuzhui.modbusmobile.core.slave.DefaultSlaveEngine
import io.github.xingyingyuzhui.modbusmobile.core.slave.InMemoryRegisterBank
import io.github.xingyingyuzhui.modbusmobile.core.slave.SlaveConfig
import io.github.xingyingyuzhui.modbusmobile.core.slave.SlaveEngine
import io.github.xingyingyuzhui.modbusmobile.core.slave.VirtualRegisterBank
import io.github.xingyingyuzhui.modbusmobile.core.transport.ByteTransport
import io.github.xingyingyuzhui.modbusmobile.core.transport.MonotonicClock
import io.github.xingyingyuzhui.modbusmobile.core.transport.memory.InMemoryBus
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DefaultSessionManager(
    private val scope: CoroutineScope,
    private val clock: MonotonicClock,
    usb: UsbStack? = null,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : SessionManager {
    private val usbStack = usb
    private val gate = Mutex()
    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    private val _traffic = MutableSharedFlow<TrafficEvent>(
        replay = 1_000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val _slaveBank = MutableStateFlow<VirtualRegisterBank?>(null)
    private val _usbDevices = MutableStateFlow<List<UsbDeviceSummary>>(emptyList())
    private val _echoDetected = MutableStateFlow<Boolean?>(null)
    private val _serial = MutableStateFlow<SerialSettings?>(null)

    private var epochCounter = 0L
    private var masterEngine: MasterEngine? = null
    private var slaveEngine: SlaveEngine? = null
    private var demoJob: Job? = null
    private var closeTransport: (suspend () -> Unit)? = null
    private var usbDetachJob: Job? = null

    override val state: StateFlow<SessionState> = _state.asStateFlow()
    override val traffic: SharedFlow<TrafficEvent> = _traffic.asSharedFlow()
    override val slaveBank: StateFlow<VirtualRegisterBank?> = _slaveBank.asStateFlow()
    override val usbDevices: StateFlow<List<UsbDeviceSummary>> = _usbDevices.asStateFlow()
    override val echoDetected: StateFlow<Boolean?> = _echoDetected.asStateFlow()
    override val serial: StateFlow<SerialSettings?> = _serial.asStateFlow()

    override fun refreshDevices() {
        usbStack?.refreshDevices()
    }

    init {
        val stack = usbStack
        if (stack != null) {
            scope.launch { stack.devices.collect { _usbDevices.value = it } }
            stack.setIoErrorHandler { reason ->
                scope.launch { disconnect(reason) }
            }
            usbDetachJob = scope.launch {
                stack.detached.collect { deviceId ->
                    val running = _state.value as? SessionState.Running ?: return@collect
                    val target = running.target as? ConnectionTarget.Usb ?: return@collect
                    if (target.deviceId == deviceId) disconnect("USB 已断开")
                }
            }
        }
    }

    override suspend fun connect(target: ConnectionTarget, serial: SerialSettings, role: Role) {
        gate.withLock {
            teardown()
            _state.value = when (target) {
                ConnectionTarget.Demo -> SessionState.Opening
                is ConnectionTarget.Usb -> SessionState.RequestingPermission
            }
            try {
                when (target) {
                    ConnectionTarget.Demo -> connectDemo(serial, role)
                    is ConnectionTarget.Usb -> connectUsb(target, serial, role)
                }
            } catch (cancelled: CancellationException) {
                teardown()
                _state.value = SessionState.Idle
                throw cancelled
            } catch (error: Exception) {
                teardown()
                _state.value = SessionState.Error(error.message ?: "连接失败")
            }
        }
    }

    override suspend fun disconnect() {
        disconnect(null)
    }

    suspend fun disconnect(reason: String?) {
        gate.withLock {
            _state.value = SessionState.Stopping
            teardown()
            _state.value = if (reason == null) SessionState.Idle else SessionState.Error(reason)
        }
    }

    override suspend fun execute(request: ModbusRequest): ModbusResult {
        val running = _state.value as? SessionState.Running
            ?: return ModbusResult.InvalidResponse("当前没有主站会话")
        if (running.role != Role.MASTER) return ModbusResult.InvalidResponse("当前不是主站")
        val master = masterEngine ?: return ModbusResult.InvalidResponse("当前不是主站")
        return master.execute(request)
    }

    private suspend fun connectDemo(serial: SerialSettings, role: Role) {
        val bus = InMemoryBus(clock)
        val bank = InMemoryRegisterBank()
        preset(bank, role)
        if (role == Role.MASTER) {
            val peer = DefaultSlaveEngine(
                transport = bus.right,
                bank = bank,
                clock = clock,
                enabledUnits = setOf(SLAVE_UNIT),
            )
            peer.start(scope)
            slaveEngine = peer
            masterEngine = newMaster(bus.left, serial, publishTraffic = true, reportEcho = true)
            closeTransport = { bus.left.close(); bus.right.close() }
        } else {
            val localSlave = phoneSlave(bus.left, bank, serial)
            localSlave.start(scope)
            slaveEngine = localSlave
            val peerMaster = newMaster(bus.right, serial, publishTraffic = false, reportEcho = false)
            masterEngine = null
            demoJob = scope.launch { runDemoMaster(peerMaster) }
            _slaveBank.value = bank
            closeTransport = {
                peerMaster.close()
                bus.left.close()
                bus.right.close()
            }
        }
        publishRunning(role, ConnectionTarget.Demo, serial)
    }

    private suspend fun connectUsb(target: ConnectionTarget.Usb, serial: SerialSettings, role: Role) {
        val stack = usbStack ?: error("USB 不可用")
        val granted = stack.requestPermission(target.deviceId)
        if (!granted) {
            _state.value = SessionState.Error("USB 权限被拒绝")
            return
        }
        _state.value = SessionState.Opening
        val transport = stack.open(target, serial)
        closeTransport = {
            (transport as? CloseableTransport)?.close()
            stack.stop()
        }
        val bank = InMemoryRegisterBank().also { preset(it, Role.SLAVE) }
        if (role == Role.MASTER) {
            masterEngine = newMaster(transport, serial, publishTraffic = true, reportEcho = true)
        } else {
            val slave = phoneSlave(transport, bank, serial)
            slave.start(scope)
            slaveEngine = slave
            _slaveBank.value = bank
        }
        publishRunning(role, target, serial)
    }

    private fun publishRunning(role: Role, target: ConnectionTarget, serial: SerialSettings) {
        epochCounter += 1
        _serial.value = serial
        _state.value = SessionState.Running(role, target, epochCounter)
    }

    private suspend fun runDemoMaster(master: MasterEngine) {
        var ticks = 0
        var value = 0
        while (scope.isActive) {
            delay(1_000)
            ticks += 1
            try {
                master.execute(ModbusRequest.ReadHoldingRegisters(SLAVE_UNIT, 0, 10))
                if (ticks % 5 == 0) {
                    value = (value + 1) and 0xFFFF
                    master.execute(ModbusRequest.WriteSingleRegister(SLAVE_UNIT, 0, value))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // 下一拍继续。演示从站不应因为一次解码失败停掉。
            }
        }
    }

    private suspend fun teardown() {
        demoJob?.cancel()
        demoJob?.join()
        demoJob = null
        masterEngine?.close()
        masterEngine = null
        slaveEngine?.close()
        slaveEngine = null
        val closing = closeTransport
        closeTransport = null
        if (closing != null) withContext(io) { closing() }
        _slaveBank.value = null
        _echoDetected.value = null
        _serial.value = null
    }

    private fun newMaster(
        transport: ByteTransport,
        serial: SerialSettings,
        publishTraffic: Boolean,
        reportEcho: Boolean,
    ): DefaultMasterEngine {
        val bits = serial.bitsPerChar().coerceAtLeast(1)
        val baud = serial.baudRate.coerceAtLeast(1)
        val echo = EchoTracker(
            mode = serial.echoMode,
            wireNs = { bytes -> bytes * bits * 1_000_000_000L / baud },
            slackNs = 100_000_000L,
        )
        if (reportEcho) _echoDetected.value = echo.detected
        return DefaultMasterEngine(
            transport = transport,
            clock = clock,
            scope = scope,
            responseTimeout = serial.responseTimeoutMs.milliseconds,
            turnaround = serial.turnaroundMs.milliseconds,
            interRequestDelay = serial.interRequestDelayMs.milliseconds,
            echo = echo,
            onTraffic = if (publishTraffic) ::publish else ({}),
            onEchoDetected = if (reportEcho) {
                { detected -> _echoDetected.value = detected }
            } else {
                {}
            },
        )
    }

    private fun phoneSlave(transport: ByteTransport, bank: InMemoryRegisterBank, serial: SerialSettings): DefaultSlaveEngine {
        val units = setOf(SLAVE_UNIT)
        val bits = serial.bitsPerChar()
        return DefaultSlaveEngine(
            transport = transport,
            bank = bank,
            clock = clock,
            enabledUnits = units,
            onTraffic = ::publish,
            config = SlaveConfig(
                topology = serial.topology,
                echoMode = serial.echoMode,
                localUnits = units,
                baud = serial.baudRate,
                bitsPerChar = bits,
                scanner = ScannerConfig.forSerial(
                    baud = serial.baudRate,
                    bitsPerChar = bits,
                    localUnits = units,
                    responseTimeoutMs = serial.responseTimeoutMs,
                ),
            ),
            onEchoDetected = { _echoDetected.value = it },
        )
    }

    private fun publish(event: TrafficEvent) {
        _traffic.tryEmit(event)
    }

    private fun preset(bank: InMemoryRegisterBank, role: Role) {
        if (role == Role.MASTER) {
            repeat(10) { bank.putWord(SLAVE_UNIT, RegisterArea.HOLDING_REGISTER, it, it * 10) }
        } else {
            repeat(10) { bank.putWord(SLAVE_UNIT, RegisterArea.HOLDING_REGISTER, it, 0) }
        }
        repeat(8) { bank.putBit(SLAVE_UNIT, RegisterArea.COIL, it, false) }
    }

    private companion object {
        const val SLAVE_UNIT = 1
    }
}

interface CloseableTransport : ByteTransport {
    fun close()
}
