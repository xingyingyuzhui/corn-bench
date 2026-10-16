package io.github.xingyingyuzhui.modbusmobile.core.rtu

enum class EchoMode {
    OFF,
    ON,
    AUTO,
}

sealed interface EchoMatch {
    data object Idle : EchoMatch
    data object Partial : EchoMatch
    data object Mismatch : EchoMatch
    data class Full(val length: Int) : EchoMatch
}

/**
 * 只跟踪最后一次发送的整帧。匹配上或对不上都解除武装，不会按历史 TX 永久过滤。
 * [arm] 必须在 [io.github.xingyingyuzhui.modbusmobile.core.transport.ByteTransport.send] 之前调用。
 */
class EchoTracker(
    private val mode: EchoMode,
    private val wireNs: (Int) -> Long,
    private val slackNs: Long,
) {
    private var tx: ByteArray? = null
    private var deadlineNs = 0L
    private var seen = 0
    private var missed = 0

    var detected: Boolean? = when (mode) {
        EchoMode.ON -> true
        EchoMode.OFF -> false
        EchoMode.AUTO -> null
    }
        private set

    fun arm(frame: ByteArray, nowNs: Long) {
        if (detected == false) return
        tx = frame.copyOf()
        deadlineNs = nowNs + wireNs(frame.size) + slackNs
    }

    fun match(buf: RxWindow): EchoMatch {
        val expected = tx ?: return EchoMatch.Idle
        val comparable = minOf(buf.size, expected.size)
        for (index in 0 until comparable) {
            if (buf[index] != (expected[index].toInt() and 0xFF)) {
                tx = null
                miss()
                return EchoMatch.Mismatch
            }
        }
        return if (buf.size >= expected.size) EchoMatch.Full(expected.size) else EchoMatch.Partial
    }

    fun consume() {
        tx = null
        if (++seen >= 2 && mode == EchoMode.AUTO) detected = true
    }

    fun expire(nowNs: Long) {
        if (tx != null && nowNs >= deadlineNs) {
            tx = null
            miss()
        }
    }

    /** 上一笔已经结束，回显仍未匹配。记一次未出现，避免被下一笔请求清掉后失去计数。 */
    fun abandon() {
        if (tx != null) {
            tx = null
            miss()
        }
    }

    fun clear() {
        tx = null
    }

    private fun miss() {
        if (++missed >= 2 && mode == EchoMode.AUTO && detected == null) detected = false
    }
}
