package io.github.xingyingyuzhui.modbusmobile.io

import android.hardware.usb.UsbDeviceConnection
import com.hoho.android.usbserial.driver.UsbSerialPort
import io.github.xingyingyuzhui.modbusmobile.core.transport.ByteChunk
import io.github.xingyingyuzhui.modbusmobile.core.transport.MonotonicClock
import io.github.xingyingyuzhui.modbusmobile.core.transport.SendResult
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 唯一的读线程。关闭顺序：停止标记 → epoch++ → 关闭端口打断阻塞读 → 有界 join。
 * 读线程自己发现错误时只回调，不在这条线程里 join 自己。
 */
class SerialIoWorker(
    private val port: UsbSerialPort,
    private val connection: UsbDeviceConnection,
    private val clock: MonotonicClock,
    private val onChunk: (ByteChunk) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val stop = AtomicBoolean(false)
    private val epoch = AtomicLong(1)
    private val writeLock = Any()
    private val thread = Thread(::readLoop, "modbus-serial-io")

    val connectionEpoch: Long get() = epoch.get()

    fun start() {
        thread.start()
    }

    fun write(bytes: ByteArray, timeoutMs: Int): SendResult {
        if (stop.get()) return SendResult.Failed("closed")
        return try {
            synchronized(writeLock) {
                port.write(bytes, timeoutMs)
            }
            SendResult.WriteReturned
        } catch (error: Exception) {
            SendResult.Failed(error.message ?: "write failed")
        }
    }

    fun close() {
        if (!stop.compareAndSet(false, true)) return
        epoch.incrementAndGet()
        runCatching { port.close() }
        runCatching { connection.close() }
        runCatching { thread.join(2_000) }
        if (thread.isAlive) onError("串口读线程未在 2 秒内退出")
    }

    private fun readLoop() {
        val buffer = ByteArray(512)
        while (!stop.get()) {
            try {
                val count = port.read(buffer, READ_TIMEOUT_MS)
                if (count > 0 && !stop.get()) {
                    onChunk(ByteChunk(buffer.copyOf(count), clock.nowNs(), epoch.get()))
                }
            } catch (error: Exception) {
                if (stop.get()) break
                stop.set(true)
                onError(error.message ?: "read failed")
                break
            }
        }
    }

    private companion object {
        const val READ_TIMEOUT_MS = 1_000
    }
}
