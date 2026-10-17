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

    /** 当前这帧能不能用来判断“适配器有没有回显”。见 [arm]。 */
    private var votes = true
    private var deadlineNs = 0L
    private var seen = 0
    private var missed = 0

    var detected: Boolean? = when (mode) {
        EchoMode.ON -> true
        EchoMode.OFF -> false
        EchoMode.AUTO -> null
    }
        private set

    /**
     * FC05 / FC06 的请求和正常应答字节完全相同：收到一模一样的字节，既可能是回显，也可能是对方的应答。
     * 这种帧照样武装（宁可报“结果未知”也不把回显当成写入成功），但**不参与 AUTO 判定**，免得没有回显的总线被判成“有回显”。
     */
    fun arm(frame: ByteArray, nowNs: Long) {
        if (detected == false) return
        tx = frame.copyOf()
        votes = !frame.isSingleWrite()
        deadlineNs = nowNs + wireNs(frame.size) + slackNs
    }

    /** 写调用返回后再起算截止时间：USB 写本身可能耗时，回显不能还没到就过期。 */
    fun restartDeadline(nowNs: Long) {
        val frame = tx ?: return
        deadlineNs = maxOf(deadlineNs, nowNs + wireNs(frame.size) + slackNs)
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
        if (!votes) return
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
        if (!votes) return
        if (++missed >= 2 && mode == EchoMode.AUTO && detected == null) detected = false
    }
}

/** 正常（非异常）的 FC05 / FC06 帧：请求和应答同形。 */
private fun ByteArray.isSingleWrite(): Boolean =
    size >= 2 && (this[1].toInt() and 0xFF).let { it == 0x05 || it == 0x06 }
