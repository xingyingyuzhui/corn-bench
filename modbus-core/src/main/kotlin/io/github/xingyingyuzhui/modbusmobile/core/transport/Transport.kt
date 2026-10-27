package io.github.xingyingyuzhui.modbusmobile.core.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface MonotonicClock {
    fun nowNs(): Long
}

object SystemMonotonicClock : MonotonicClock {
    override fun nowNs(): Long = System.nanoTime()
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR,
}

data class ByteChunk(
    val bytes: ByteArray,
    val receivedAtMonotonicNs: Long,
    val connectionEpoch: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ByteChunk) return false
        return receivedAtMonotonicNs == other.receivedAtMonotonicNs &&
            connectionEpoch == other.connectionEpoch &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + receivedAtMonotonicNs.hashCode()
        result = 31 * result + connectionEpoch.hashCode()
        return result
    }
}

sealed interface SendResult {
    data object AcceptedByTransport : SendResult
    data object WriteReturned : SendResult
    data object ConfirmedOnWire : SendResult
    data class Failed(val reason: String) : SendResult
}

/**
 * 串口字节流。实现不得假设 USB 批次间隔等于 Modbus 字符间隔。
 */
interface ByteTransport {
    suspend fun send(frame: ByteArray): SendResult
    val received: Flow<ByteChunk>
    val connectionState: StateFlow<ConnectionState>
    val connectionEpoch: Long
}
