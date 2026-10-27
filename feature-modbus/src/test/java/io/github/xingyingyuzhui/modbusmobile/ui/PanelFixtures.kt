package io.github.xingyingyuzhui.modbusmobile.ui

import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.SerialSettings
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.application.UsbDeviceSummary
import io.github.xingyingyuzhui.modbusmobile.application.panel.Conversion
import io.github.xingyingyuzhui.modbusmobile.application.panel.DataType
import io.github.xingyingyuzhui.modbusmobile.application.panel.ErrorKind
import io.github.xingyingyuzhui.modbusmobile.application.panel.Panel
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointDefinition
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointError
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointSnapshot
import io.github.xingyingyuzhui.modbusmobile.application.panel.Project
import io.github.xingyingyuzhui.modbusmobile.application.panel.Quality
import io.github.xingyingyuzhui.modbusmobile.application.panel.RawValue
import io.github.xingyingyuzhui.modbusmobile.application.panel.StateMapping
import io.github.xingyingyuzhui.modbusmobile.application.panel.StateTone
import io.github.xingyingyuzhui.modbusmobile.application.panel.ThresholdBand
import io.github.xingyingyuzhui.modbusmobile.application.panel.ThresholdLevel
import io.github.xingyingyuzhui.modbusmobile.application.panel.Widget
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetKinds
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import io.github.xingyingyuzhui.modbusmobile.core.slave.VirtualRegisterBank
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** 面板测试共用：假会话 + 稿面 b2-1 那组组件（温度 / 压力 / 压缩机 / 运行模式 / 阀门开度 / 压缩机电流）。 */
internal open class PanelFakeSession(role: Role? = Role.MASTER) : SessionManager {
    override val state = MutableStateFlow<SessionState>(
        if (role == null) SessionState.Idle else SessionState.Running(role, ConnectionTarget.Demo, epoch = 1),
    )
    override val traffic = MutableSharedFlow<TrafficEvent>(replay = 1)
    override val slaveBank = MutableStateFlow<VirtualRegisterBank?>(null)
    override val usbDevices = MutableStateFlow<List<UsbDeviceSummary>>(emptyList())
    override val echoDetected = MutableStateFlow<Boolean?>(null)
    override val serial = MutableStateFlow<SerialSettings?>(SerialSettings())
    override suspend fun connect(target: ConnectionTarget, serial: SerialSettings, role: Role) = Unit
    override suspend fun disconnect() = Unit
    override suspend fun execute(request: ModbusRequest): ModbusResult = ModbusResult.Disconnected
}

internal object PanelFixtures {
    const val WALL_MS = 1_792_203_667_000L // 2026-10-17 10:21:07 +08:00

    private fun point(
        id: String,
        name: String,
        address: Int,
        type: DataType = DataType.S16,
        area: RegisterArea = RegisterArea.HOLDING_REGISTER,
        conversion: Conversion = Conversion(),
        unit: String = "",
        thresholds: List<ThresholdBand> = emptyList(),
        min: Double? = null,
        max: Double? = null,
    ) = PointDefinition(
        id, name, 1, area, address, type, conversion = conversion, unitLabel = unit, thresholds = thresholds,
        rangeMin = min, rangeMax = max, pollMs = 100,
    )

    val temp = point("temp", "供水温度", 100, conversion = Conversion(0.1, 0.0, 1), unit = "℃")
    val pressure = point(
        "press", "供水压力", 101, DataType.U16, conversion = Conversion(0.01, 0.0, 2), unit = "MPa",
        thresholds = listOf(ThresholdBand(ThresholdLevel.WARN, min = 0.8)),
    )
    val compressor = point("comp", "压缩机", 0, DataType.BOOL, RegisterArea.COIL)
    val mode = point("mode", "运行模式", 120, DataType.U16)
    val valve = point("valve", "阀门开度", 130, DataType.U16, unit = "%", min = 0.0, max = 100.0)
    val current = point("cur", "压缩机电流", 140, DataType.U16, conversion = Conversion(0.1, 0.0, 1), unit = "A")

    val widgets = listOf(
        Widget("w-temp", WidgetKinds.NUMBER, point = temp),
        Widget("w-press", WidgetKinds.NUMBER, point = pressure),
        Widget("w-comp", WidgetKinds.LAMP, point = compressor, onLabel = "运行", offLabel = "停止"),
        Widget(
            "w-mode", WidgetKinds.STATUS_TEXT, point = mode,
            states = listOf(
                StateMapping(0, "停止", StateTone.GRAY), StateMapping(1, "运行", StateTone.GREEN),
                StateMapping(2, "制冷", StateTone.BLUE), StateMapping(4, "故障", StateTone.RED),
            ),
        ),
        Widget("w-valve", WidgetKinds.PROGRESS, point = valve),
        Widget("w-cur", WidgetKinds.NUMBER, point = current),
    )

    fun project(vararg extraPanels: Panel) = Project(
        "default", "x",
        listOf(Panel("p1", "机房 A", widgets = widgets)) + extraPanels,
    )

    private fun ok(p: PointDefinition, raw: RawValue, eng: Double, text: String?, level: ThresholdLevel = ThresholdLevel.NORMAL) =
        PointSnapshot(p.id, Quality.OK, raw, eng, text, level, 1_000, WALL_MS)

    /** 稿面数值：23.4 ℃ / 0.82 MPa（≥0.8 预警）/ 运行 / 制冷 / 62 % / 12.6 A（连续超时 3 次，值是上一次的）。 */
    val snapshots: Map<String, PointSnapshot> = mapOf(
        "temp" to ok(temp, RawValue.Integer(234, false), 23.4, "23.4"),
        "press" to ok(pressure, RawValue.Integer(82, false), 0.82, "0.82", ThresholdLevel.WARN),
        "comp" to ok(compressor, RawValue.Flag(true), 1.0, null),
        "mode" to ok(mode, RawValue.Integer(2, false), 2.0, "2"),
        "valve" to ok(valve, RawValue.Integer(62, false), 62.0, "62"),
        "cur" to PointSnapshot(
            "cur", Quality.TIMEOUT, RawValue.Integer(126, false), 12.6, "12.6", ThresholdLevel.NORMAL, 1_000, WALL_MS,
            failures = 3, error = PointError(ErrorKind.TIMEOUT),
        ),
    )
}
