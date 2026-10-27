package io.github.xingyingyuzhui.modbusmobile.io

import io.github.xingyingyuzhui.modbusmobile.core.transport.MonotonicClock
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SerialIoWorkerTest {
    @Test
    fun readErrorDoesNotSkipPortClose() {
        val closes = AtomicInteger()
        val failed = CountDownLatch(1)
        val port = object : RawSerialPort {
            override fun read(dest: ByteArray, timeoutMs: Int): Int = throw IOException("unplugged")
            override fun write(src: ByteArray, timeoutMs: Int) = Unit
            override fun close() {
                closes.incrementAndGet()
            }
        }
        val worker = SerialIoWorker(port, clock, onChunk = {}, onError = { failed.countDown() })
        val epochBefore = worker.connectionEpoch
        worker.start()
        assertTrue(failed.await(3, TimeUnit.SECONDS))
        worker.close()
        assertEquals(1, closes.get())
        assertTrue(worker.connectionEpoch > epochBefore)
        worker.close()
        assertEquals(1, closes.get())
    }

    @Test
    fun closeFromReadThreadStillReleasesThePortOnce() {
        val closes = AtomicInteger()
        val closed = CountDownLatch(1)
        val port = object : RawSerialPort {
            override fun read(dest: ByteArray, timeoutMs: Int): Int = throw IOException("unplugged")
            override fun write(src: ByteArray, timeoutMs: Int) = Unit
            override fun close() {
                closes.incrementAndGet()
                closed.countDown()
            }
        }
        lateinit var worker: SerialIoWorker
        worker = SerialIoWorker(port, clock, onChunk = {}, onError = { worker.close() })
        worker.start()
        assertTrue(closed.await(3, TimeUnit.SECONDS))
        worker.close()
        assertEquals(1, closes.get())
    }

    private val clock = object : MonotonicClock {
        override fun nowNs(): Long = 0L
    }
}
