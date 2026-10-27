package io.github.xingyingyuzhui.modbusmobile.core.transport.memory

import io.github.xingyingyuzhui.modbusmobile.core.transport.ByteChunk
import io.github.xingyingyuzhui.modbusmobile.core.transport.ByteTransport
import io.github.xingyingyuzhui.modbusmobile.core.transport.ConnectionState
import io.github.xingyingyuzhui.modbusmobile.core.transport.MonotonicClock
import io.github.xingyingyuzhui.modbusmobile.core.transport.SendResult
import java.util.Collections
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * 一对互连的 [ByteTransport]。可配置分块、投递延迟和本机回显。
 * 回显写回发送端的接收队列，不计入对端发送。
 */
class InMemoryBus(
    private val clock: MonotonicClock,
    private val chunkSize: Int = 256,
    private val deliveryDelay: Duration = Duration.ZERO,
    private val echo: Boolean = false,
) {
    val left: Endpoint = Endpoint(clock)
    val right: Endpoint = Endpoint(clock)

    init {
        left.bind(right)
        right.bind(left)
        left.chunkSize = chunkSize.coerceAtLeast(1)
        right.chunkSize = chunkSize.coerceAtLeast(1)
        left.deliveryDelay = deliveryDelay
        right.deliveryDelay = deliveryDelay
        left.echo = echo
        right.echo = echo
    }

    class Endpoint(private val clock: MonotonicClock) : ByteTransport {
        private val incoming = Channel<ByteChunk>(capacity = 4096)
        private val state = MutableStateFlow(ConnectionState.CONNECTED)
        private val epoch = java.util.concurrent.atomic.AtomicLong(1)
        private val sent = Collections.synchronizedList(mutableListOf<ByteArray>())

        private lateinit var peer: Endpoint
        internal var chunkSize: Int = 256
        internal var deliveryDelay: Duration = 0.milliseconds
        internal var echo: Boolean = false

        override val received = incoming.receiveAsFlow()
        override val connectionState: StateFlow<ConnectionState> = state.asStateFlow()
        override val connectionEpoch: Long get() = epoch.get()
        val sentFrames: List<ByteArray> get() = sent.toList()

        internal fun bind(peer: Endpoint) {
            this.peer = peer
        }

        override suspend fun send(frame: ByteArray): SendResult {
            if (state.value != ConnectionState.CONNECTED) {
                return SendResult.Failed("disconnected")
            }
            sent += frame.copyOf()
            val pieces = frame.asList().chunked(chunkSize)
            for (piece in pieces) {
                if (deliveryDelay > Duration.ZERO) delay(deliveryDelay)
                val bytes = piece.toByteArray()
                val now = clock.nowNs()
                if (echo) {
                    incoming.send(ByteChunk(bytes.copyOf(), now, connectionEpoch))
                }
                peer.incoming.send(ByteChunk(bytes, now, peer.connectionEpoch))
            }
            return SendResult.WriteReturned
        }

        fun close() {
            if (state.value == ConnectionState.DISCONNECTED) return
            epoch.incrementAndGet()
            state.value = ConnectionState.DISCONNECTED
            incoming.close()
        }
    }
}
