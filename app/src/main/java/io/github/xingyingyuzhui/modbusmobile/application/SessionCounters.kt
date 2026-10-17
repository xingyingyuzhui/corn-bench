package io.github.xingyingyuzhui.modbusmobile.application

import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficDirection
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 本次连接会话的收发计数（会话条右侧、从站统计格用）。application 层从已经发布的报文和主站结果里数，不改 modbus-core。
 *
 * 每次 [SessionManager.connect] 都从 0 开始；断开后归零。各项含义：
 * - [tx] / [rx]：本机发出 / 收到的帧数。回显是自己发的字节绕回来，单独记在 [echoes]，不算 [rx]。
 * - [errors]：主站这一边是没拿到正常响应的事务（超时、异常响应、响应不合法、结果未知、断开）；
 *   从站这一边是本机回出去的异常响应，加上方向不明的帧。
 * - [localRequests] / [responses] / [exceptions] / [foreignFrames] / [ambiguous]：从站统计格（稿面 b1-5）。
 *   [responses] 包含异常响应，[exceptions] 只数异常响应。
 *
 * 扫描器内部丢弃的字节（CRC 错、残帧）引擎不发布成报文，这里数不到；不显示，也不编一个数出来。
 */
data class SessionCounters(
    val tx: Long = 0,
    val rx: Long = 0,
    val errors: Long = 0,
    val localRequests: Long = 0,
    val responses: Long = 0,
    val exceptions: Long = 0,
    val foreignFrames: Long = 0,
    val ambiguous: Long = 0,
    val echoes: Long = 0,
) {
    /** 记一帧已经发布的报文。 */
    fun count(event: TrafficEvent): SessionCounters {
        var next = this
        when (event.kind) {
            TrafficKind.ECHO -> return copy(echoes = echoes + 1)
            TrafficKind.RX_LOCAL_REQUEST -> next = next.copy(localRequests = localRequests + 1)
            TrafficKind.TX_RESPONSE -> {
                val exception = event.isExceptionFrame()
                next = next.copy(
                    responses = responses + 1,
                    exceptions = if (exception) exceptions + 1 else exceptions,
                    errors = if (exception) errors + 1 else errors,
                )
            }
            TrafficKind.RX_FOREIGN_REQUEST, TrafficKind.RX_FOREIGN_RESPONSE ->
                next = next.copy(foreignFrames = foreignFrames + 1)
            TrafficKind.AMBIGUOUS -> next = next.copy(ambiguous = ambiguous + 1, errors = errors + 1)
            TrafficKind.TX_REQUEST, TrafficKind.RX_RESPONSE, TrafficKind.DROPPED -> Unit
        }
        return when (event.direction) {
            TrafficDirection.TX -> next.copy(tx = next.tx + 1)
            TrafficDirection.RX -> next.copy(rx = next.rx + 1)
        }
    }

    /** 记一次主站事务的结果：没拿到正常响应就算一次错误。 */
    fun countMasterResult(result: ModbusResult): SessionCounters =
        if (result is ModbusResult.Success) this else copy(errors = errors + 1)

    companion object {
        val Empty = SessionCounters()
    }
}

/** 异常响应：功能码最高位为 1（`Unit, FC|0x80, 异常码, CRC`）。 */
private fun TrafficEvent.isExceptionFrame(): Boolean =
    bytes.size >= 2 && (bytes[1].toInt() and 0x80) != 0

/** 给没有会话的实现（测试替身等）用的常量流。 */
internal val NoCounters: StateFlow<SessionCounters> = MutableStateFlow(SessionCounters.Empty).asStateFlow()
