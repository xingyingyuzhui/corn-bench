package io.github.xingyingyuzhui.modbusmobile.usb

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.SerialSettings
import io.github.xingyingyuzhui.modbusmobile.application.UsbDeviceSummary
import io.github.xingyingyuzhui.modbusmobile.application.UsbStack
import io.github.xingyingyuzhui.modbusmobile.core.transport.ByteTransport
import io.github.xingyingyuzhui.modbusmobile.core.transport.MonotonicClock
import io.github.xingyingyuzhui.modbusmobile.io.RawSerialPort
import io.github.xingyingyuzhui.modbusmobile.io.SerialIoWorker
import java.io.EOFException
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

internal const val LOCAL_LAB_DEVICE_ID = Int.MIN_VALUE
internal const val LOCAL_LAB_SOCKET_NAME = "modbus-mobile-rtu-lab"
private val labDevice = UsbDeviceSummary(
    LOCAL_LAB_DEVICE_ID, 0, "本地虚拟串口 · ADB（调试）", 0, 0,
)

/** Debug source set only: ADB forwards raw RTU bytes to this abstract Unix socket. */
internal fun withLocalSerialLab(stack: UsbStack, scope: CoroutineScope, clock: MonotonicClock): UsbStack =
    LocalSerialLabStack(stack, scope, clock)

internal interface LabListener : AutoCloseable {
    fun accept(): RawSerialPort
}

internal class LocalSerialLabStack(
    private val physical: UsbStack,
    scope: CoroutineScope,
    private val clock: MonotonicClock,
    private val createListener: () -> LabListener = { AndroidLabListener() },
    private val acceptTimeoutMs: Long = 30_000,
) : UsbStack {
    override val devices = physical.devices.map { it + labDevice }
        .stateIn(scope, SharingStarted.Eagerly, physical.devices.value + labDevice)
    override val detached = physical.detached
    private val lock = Any()
    private var physicalActive = false
    private var pending: LabListener? = null
    private var transport: UsbSerialTransport? = null
    private var generation = 0L
    @Volatile private var onError: (String) -> Unit = {}

    init {
        physical.setIoErrorHandler { reason ->
            if (synchronized(lock) { physicalActive }) onError(reason)
        }
    }

    override fun refreshDevices() = physical.refreshDevices()

    override suspend fun requestPermission(deviceId: Int): Boolean =
        deviceId == LOCAL_LAB_DEVICE_ID || physical.requestPermission(deviceId)

    override suspend fun open(target: ConnectionTarget.Usb, serial: SerialSettings): ByteTransport {
        stop()
        if (target.deviceId != LOCAL_LAB_DEVICE_ID) {
            synchronized(lock) { physicalActive = true }
            return try {
                physical.open(target, serial)
            } catch (error: Exception) {
                stop()
                throw error
            }
        }
        require(target.portIndex == 0) { "本地虚拟串口仅支持端口 0" }
        val (listener, openingGeneration) = synchronized(lock) {
            createListener().also { pending = it } to generation
        }
        val port = try {
            withTimeoutOrNull(acceptTimeoutMs) { acceptPort(listener) }
                ?: throw IOException("等待本地 ADB 串口桥超时，请启动主机串口服务后重连")
        } finally {
            synchronized(lock) { if (pending === listener) pending = null }
            runCatching { listener.close() }
        }
        lateinit var next: UsbSerialTransport
        val worker = SerialIoWorker(port, clock, { next.emit(it) }, { reason ->
            val active = synchronized(lock) { transport === next }
            if (active) {
                next.fail()
                next.close()
                onError("本地虚拟串口：$reason")
            }
        })
        next = UsbSerialTransport(worker)
        synchronized(lock) {
            if (generation != openingGeneration) {
                next.close()
                throw IOException("本地虚拟串口连接已取消")
            }
            transport = next
            worker.start()
        }
        return next
    }

    override fun stop() {
        val resources = synchronized(lock) {
            generation += 1
            Triple(pending, transport, physicalActive).also {
                pending = null
                transport = null
                physicalActive = false
            }
        }
        runCatching { resources.first?.close() }
        resources.second?.close()
        if (resources.third) physical.stop()
    }

    override fun setIoErrorHandler(handler: (String) -> Unit) { onError = handler }

    private suspend fun acceptPort(listener: LabListener): RawSerialPort = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { runCatching { listener.close() } }
        Thread({
            try {
                val accepted = listener.accept()
                continuation.resume(accepted) { _, abandoned, _ -> abandoned.close() }
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(error))
            }
        }, "modbus-lab-accept").apply { isDaemon = true; start() }
    }
}

private class AndroidLabListener : LabListener {
    private val server = LocalServerSocket(LOCAL_LAB_SOCKET_NAME)
    override fun accept(): RawSerialPort = LocalSocketSerialPort(server.accept())
    override fun close() = server.close()
}

/** Poll supplies a finite idle wait without LocalSocket's platform-dependent EAGAIN timeout errors. */
internal fun readLabBytes(
    dest: ByteArray,
    timeoutMs: Int,
    awaitReadable: (Int) -> Boolean,
    read: (ByteArray) -> Int,
): Int {
    if (!awaitReadable(timeoutMs.coerceAtLeast(1))) return 0
    return read(dest).also { if (it < 0) throw EOFException("主机串口桥已关闭") }
}

/** Write deadlines close the socket to abort blocked output; SO_TIMEOUT is deliberately unused. */
internal class LocalSocketSerialPort(private val socket: LocalSocket) : RawSerialPort {
    private val closed = AtomicBoolean(false)
    private val writer = Executors.newSingleThreadExecutor { task ->
        Thread(task, "modbus-lab-write").apply { isDaemon = true }
    }

    override fun read(dest: ByteArray, timeoutMs: Int): Int = readLabBytes(
        dest,
        timeoutMs,
        awaitReadable = { timeout ->
            val descriptor = StructPollfd().apply {
                fd = socket.fileDescriptor
                events = OsConstants.POLLIN.toShort()
            }
            val ready = Os.poll(arrayOf(descriptor), timeout)
            if (descriptor.revents.toInt() and OsConstants.POLLNVAL != 0) {
                throw IOException("本地虚拟串口已关闭")
            }
            ready > 0
        },
        read = { socket.inputStream.read(it) },
    )

    override fun write(src: ByteArray, timeoutMs: Int) {
        check(!closed.get()) { "本地虚拟串口已关闭" }
        val task = writer.submit { socket.outputStream.write(src); socket.outputStream.flush() }
        try {
            task.get(timeoutMs.coerceAtLeast(1).toLong(), TimeUnit.MILLISECONDS)
        } catch (error: Exception) {
            close()
            task.cancel(true)
            if (error is TimeoutException) throw IOException("本地虚拟串口写入超时", error)
            throw IOException("本地虚拟串口写入失败", error)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { socket.close() }
        writer.shutdownNow()
    }
}
