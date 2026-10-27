package io.github.xingyingyuzhui.modbusmobile.application

import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.rtu.RtuCodec

/**
 * 请求将要发出的整帧字节（Unit + PDU + CRC），和主站引擎发送时是同一个编码器。
 * 界面层要显示“将发送的字节”时一律经这里（或 [SessionManager.previewFrame]），不直接引用 core 的 RTU 编码。
 */
fun encodeRequestFrame(request: ModbusRequest): ByteArray = RtuCodec.encodeRequest(request)
