package io.github.xingyingyuzhui.modbusmobile.usb

import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.SerialSettings
import io.github.xingyingyuzhui.modbusmobile.application.UsbDeviceSummary
import io.github.xingyingyuzhui.modbusmobile.application.UsbStack
import io.github.xingyingyuzhui.modbusmobile.core.transport.ByteTransport
import io.github.xingyingyuzhui.modbusmobile.core.transport.MonotonicClock
import io.github.xingyingyuzhui.modbusmobile.core.transport.SendResult
import io.github.xingyingyuzhui.modbusmobile.io.RawSerialPort
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LocalSerialLabStackTest {
    @Test fun virtualDeviceUsesSerialWorkerAndDoesNotStopPhysicalUsb() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val physical = PhysicalStack()
        val port = Port()
        var listeners = 0
        val stack = LocalSerialLabStack(physical, scope, clock, {
            listeners++
            object : LabListener {
                override fun accept() = port
                override fun close() = Unit
            }
        })
        try {
            assertEquals(0, listeners)
            assertTrue(stack.devices.value.any { it.deviceId == LOCAL_LAB_DEVICE_ID })
            assertTrue(stack.requestPermission(LOCAL_LAB_DEVICE_ID))
            assertEquals(0, physical.permissions)
            val transport = stack.open(ConnectionTarget.Usb(LOCAL_LAB_DEVICE_ID, 0), SerialSettings())
            assertEquals(SendResult.WriteReturned, transport.send(byteArrayOf(1, 3, 0, 0)))
            assertArrayEquals(byteArrayOf(1, 3, 0, 0), port.lastWrite)
            physical.handler("unrelated USB error")
            assertFalse(port.closed.get())
            stack.stop()
            assertTrue(port.closed.get())
            assertEquals(0, physical.stops)
        } finally { stack.stop(); scope.cancel() }
    }

    @Test fun acceptTimeoutClosesListener() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val listener = WaitingListener()
        val stack = LocalSerialLabStack(PhysicalStack(), scope, clock, { listener }, acceptTimeoutMs = 50)
        try {
            try {
                stack.open(ConnectionTarget.Usb(LOCAL_LAB_DEVICE_ID, 0), SerialSettings())
                fail("Expected timeout")
            } catch (error: IOException) {
                assertTrue(error.message.orEmpty().contains("超时"))
            }
            assertTrue(listener.closed.await(1, TimeUnit.SECONDS))
        } finally { stack.stop(); scope.cancel() }
    }

    @Test fun cancellationClosesPendingListener() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val listener = WaitingListener()
        val stack = LocalSerialLabStack(PhysicalStack(), scope, clock, { listener })
        try {
            val opening = async(Dispatchers.Default) {
                stack.open(ConnectionTarget.Usb(LOCAL_LAB_DEVICE_ID, 0), SerialSettings())
            }
            assertTrue(listener.started.await(1, TimeUnit.SECONDS))
            opening.cancel()
            opening.join()
            assertTrue(listener.closed.await(1, TimeUnit.SECONDS))
        } finally { stack.stop(); scope.cancel() }
    }

    private class WaitingListener : LabListener {
        val started = CountDownLatch(1)
        val closed = CountDownLatch(1)
        override fun accept(): RawSerialPort {
            started.countDown()
            closed.await()
            throw IOException("listener closed")
        }
        override fun close() { closed.countDown() }
    }

    private class Port : RawSerialPort {
        val closed = AtomicBoolean(false)
        private val closeSignal = CountDownLatch(1)
        var lastWrite: ByteArray? = null
        override fun read(dest: ByteArray, timeoutMs: Int): Int {
            closeSignal.await(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            return 0
        }
        override fun write(src: ByteArray, timeoutMs: Int) { lastWrite = src.copyOf() }
        override fun close() { closed.set(true); closeSignal.countDown() }
    }

    private class PhysicalStack : UsbStack {
        override val devices = MutableStateFlow(listOf(UsbDeviceSummary(12, 0, "USB", 1, 2)))
        override val detached = MutableSharedFlow<Int>()
        var stops = 0
        var permissions = 0
        var handler: (String) -> Unit = {}
        override suspend fun requestPermission(deviceId: Int): Boolean { permissions++; return true }
        override suspend fun open(target: ConnectionTarget.Usb, serial: SerialSettings): ByteTransport = error("unexpected physical open")
        override fun stop() { stops++ }
        override fun setIoErrorHandler(handler: (String) -> Unit) { this.handler = handler }
    }

    private val clock = object : MonotonicClock { override fun nowNs() = System.nanoTime() }
}
