package io.github.xingyingyuzhui.modbusmobile.usb

import io.github.xingyingyuzhui.modbusmobile.core.transport.ByteTransport
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

class UsbSessionBridge {
    @Volatile
    private var pending: CompletableDeferred<Result<ByteTransport>>? = null

    @Volatile
    var onIoError: ((String) -> Unit)? = null

    fun prepare() {
        pending = CompletableDeferred()
    }

    suspend fun await(): ByteTransport {
        val deferred = pending ?: error("USB 会话尚未准备")
        return withTimeout(8.seconds) { deferred.await().getOrThrow() }
    }

    fun succeed(transport: ByteTransport) {
        pending?.complete(Result.success(transport))
    }

    fun fail(error: Throwable) {
        pending?.complete(Result.failure(error))
    }
}
