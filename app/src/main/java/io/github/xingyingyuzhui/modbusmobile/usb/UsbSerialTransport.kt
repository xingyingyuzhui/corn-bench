package io.github.xingyingyuzhui.modbusmobile.usb

import io.github.xingyingyuzhui.modbusmobile.application.CloseableTransport
import io.github.xingyingyuzhui.modbusmobile.core.transport.ByteChunk
import io.github.xingyingyuzhui.modbusmobile.core.transport.ConnectionState
import io.github.xingyingyuzhui.modbusmobile.core.transport.SendResult
import io.github.xingyingyuzhui.modbusmobile.io.SerialIoWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext

class UsbSerialTransport(
    private val worker: SerialIoWorker,
) : CloseableTransport {
    private val incoming = Channel<ByteChunk>(capacity = 1024)
    private val state = MutableStateFlow(ConnectionState.CONNECTED)
    private val closed = java.util.concurrent.atomic.AtomicBoolean(false)

    override val received = incoming.receiveAsFlow()
    override val connectionState: StateFlow<ConnectionState> = state.asStateFlow()
    override val connectionEpoch: Long get() = worker.connectionEpoch

    fun emit(chunk: ByteChunk) {
        incoming.trySend(chunk)
    }

    fun fail() {
        state.value = ConnectionState.ERROR
        incoming.close()
    }

    override suspend fun send(frame: ByteArray): SendResult =
        withContext(Dispatchers.IO) { worker.write(frame, WRITE_TIMEOUT_MS) }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        state.value = ConnectionState.DISCONNECTED
        worker.close()
        incoming.close()
    }

    private companion object {
        const val WRITE_TIMEOUT_MS = 500
    }
}
