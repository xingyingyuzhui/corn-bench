@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.xingyingyuzhui.modbusmobile.ui.panel

import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.SerialSettings
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.application.UsbDeviceSummary
import io.github.xingyingyuzhui.modbusmobile.application.panel.Conversion
import io.github.xingyingyuzhui.modbusmobile.application.panel.DataType
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointDefinition
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointSnapshot
import io.github.xingyingyuzhui.modbusmobile.application.panel.Quality
import io.github.xingyingyuzhui.modbusmobile.application.panel.RawValue
import io.github.xingyingyuzhui.modbusmobile.application.panel.Widget
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetKinds
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResponse
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import io.github.xingyingyuzhui.modbusmobile.core.slave.VirtualRegisterBank
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 寄存器表的写入（P8b2）：✎ 只开输入框 / 生成计划，确认前不发、取消不发；换算反算；线圈行翻转；
 * 编辑模式 / 从站 / 没连接 / 表没开“允许修改” / 输入类区域一律拒绝；一次一个写入流程。
 */
class PanelRegisterTableControllerTest {
    private class Session(role: Role? = Role.MASTER) : SessionManager {
        val requests = mutableListOf<ModbusRequest>()
        var writeResult: ModbusResult? = null
        override val state = MutableStateFlow<SessionState>(
            if (role == null) SessionState.Idle else SessionState.Running(role, ConnectionTarget.Demo, epoch = 1),
        )
        override val traffic = MutableSharedFlow<TrafficEvent>()
        override val slaveBank = MutableStateFlow<VirtualRegisterBank?>(null)
        override val usbDevices = MutableStateFlow<List<UsbDeviceSummary>>(emptyList())
        override val echoDetected = MutableStateFlow<Boolean?>(null)
        override val serial = MutableStateFlow<SerialSettings?>(null)
        override suspend fun connect(target: ConnectionTarget, serial: SerialSettings, role: Role) = Unit
        override suspend fun disconnect() = Unit
        override suspend fun execute(request: ModbusRequest): ModbusResult {
            requests += request
            return writeResult ?: when (request) {
                is ModbusRequest.WriteSingleRegister ->
                    ModbusResult.Success(ModbusResponse.WriteSingleRegister(request.unitId, request.function, request.address, request.value))
                is ModbusRequest.WriteMultipleRegisters ->
                    ModbusResult.Success(ModbusResponse.WriteMultipleRegisters(request.unitId, request.function, request.address, request.quantity))
                is ModbusRequest.WriteSingleCoil ->
                    ModbusResult.Success(ModbusResponse.WriteSingleCoil(request.unitId, request.function, request.address, request.value))
                else -> ModbusResult.Disconnected
            }
        }
    }

    private fun table(
        area: RegisterArea = RegisterArea.HOLDING_REGISTER,
        type: DataType = DataType.U16,
        conversion: Conversion = Conversion(),
        rows: Int = 4,
        writable: Boolean = true,
        unit: Int = 3,
    ) = Widget(
        "t1", WidgetKinds.REGISTER_TABLE, title = "水泵参数",
        point = PointDefinition("p-t", "水泵", unit, area, 100, type, conversion = conversion, unitLabel = "℃"),
        spanColumns = 2, tableRows = rows, tableWritable = writable,
    )

    private fun TestScope.controller(session: Session, editing: () -> Boolean = { false }, snapshots: Map<String, PointSnapshot> = emptyMap()) =
        PanelWriteController(session, this, editing, { snapshots[it] }, wallClock = { 100_000L })

    private fun TestScope.after(block: () -> Unit) {
        block()
        advanceUntilIdle()
    }

    private fun snap(id: String, raw: RawValue, eng: Double, text: String, quality: Quality = Quality.OK) =
        PointSnapshot(id, quality, raw, eng, text, updatedAtMs = 1, updatedWallMs = 97_000L)

    // ---- 寄存器行 ----

    @Test fun pencilOnlyOpensTheEntryBoxAndNothingIsSent() = runTest {
        val session = Session()
        val c = controller(session)
        after { c.requestRow(table(), 2) }
        assertEquals(2, c.entry!!.index)
        assertNull("还没输入，不能有计划", c.pending)
        assertTrue(session.requests.isEmpty())
        after { c.cancelEntry() }
        assertNull(c.entry)
        assertTrue("取消输入框什么都不发", session.requests.isEmpty())
    }

    @Test fun submitBuildsAPlanForTheRowAddressAndOnlyConfirmSends() = runTest {
        val session = Session()
        val c = controller(session)
        after { c.requestRow(table(), 2) }
        after { assertNull(c.submitRow("500")) }
        assertNull(c.entry)
        val pending = c.pending!!
        assertTrue("下一步只是生成计划", session.requests.isEmpty())
        assertEquals("第 3 行 = 起始 100 + 2", ModbusRequest.WriteSingleRegister(3, 102, 500), pending.write.plan.request)
        assertEquals(2, pending.row!!.index)
        assertEquals("500", pending.row!!.shownValue)
        after { c.dismiss() }
        assertNull(c.pending)
        assertTrue("取消确认弹窗不发", session.requests.isEmpty())

        after { c.requestRow(table(), 2) }
        after { c.submitRow("500") }
        after { c.confirm() }
        assertEquals(listOf<ModbusRequest>(ModbusRequest.WriteSingleRegister(3, 102, 500)), session.requests)
        assertEquals(WriteNoticeKind.OK, c.notice!!.kind)
        assertEquals("U3 HR102 = 500", c.notice!!.summary)
        assertNull(c.pending)
        assertNull(c.busyWidgetId)
    }

    @Test fun engineeringValueIsInvertedByScaleAndShownAfterRounding() = runTest {
        val session = Session()
        val c = controller(session)
        val w = table(conversion = Conversion(0.1, 0.0, 1))
        after { c.requestRow(w, 0) }
        after { c.submitRow("23.46") }
        assertEquals(listOf(235), c.pending!!.write.plan.words)
        assertEquals("确认弹窗里显示的是真正会存下的值", "23.5", c.pending!!.row!!.shownValue)
    }

    @Test fun thirtyTwoBitRowsUseTwoWordsAndTheRowStride() = runTest {
        val session = Session()
        val c = controller(session)
        val w = table(type = DataType.U32)
        after { c.requestRow(w, 1) }
        after { c.submitRow("65536") }
        val plan = c.pending!!.write.plan
        assertEquals(ModbusRequest.WriteMultipleRegisters(3, 102, listOf(1, 0)), plan.request)
        after { c.confirm() }
        assertEquals(ModbusRequest.WriteMultipleRegisters(3, 102, listOf(1, 0)), session.requests.single())
    }

    @Test fun knownCurrentWordsAreShownOnlyWhenTheReadingIsFresh() = runTest {
        val row = table().rowPoint(1).id
        val ok = snap(row, RawValue.Integer(77, false), 77.0, "77")
        val c = controller(Session(), snapshots = mapOf(row to ok))
        after { c.requestRow(table(), 1) }
        after { c.submitRow("5") }
        assertEquals(listOf<Int?>(77), c.pending!!.write.current)
        assertEquals(3_000L, c.pending!!.write.currentAgeMs)
        val c2 = controller(Session(), snapshots = mapOf(row to ok.copy(quality = Quality.STALE)))
        after { c2.requestRow(table(), 1) }
        after { c2.submitRow("5") }
        assertEquals("过期的值不当“当前值”", listOf<Int?>(null), c2.pending!!.write.current)
    }

    @Test fun badInputKeepsTheEntryOpenAndSendsNothing() = runTest {
        val session = Session()
        val c = controller(session)
        after { c.requestRow(table(), 0) }
        assertEquals(RowEntryError.EMPTY, c.submitRow("  "))
        assertEquals(RowEntryError.NOT_A_NUMBER, c.submitRow("abc"))
        assertEquals(RowEntryError.NOT_A_NUMBER, c.submitRow("NaN"))
        assertEquals(RowEntryError.OUT_OF_RANGE, c.submitRow("70000"))
        assertEquals(RowEntryError.OUT_OF_RANGE, c.submitRow("-1"))
        assertNotNull("输入不合法时输入框还在", c.entry)
        assertNull(c.pending)
        assertTrue(session.requests.isEmpty())
        val zero = controller(session)
        after { zero.requestRow(table(conversion = Conversion(0.0)), 0) }
        // scale = 0 的点位本身就非法（isUsable 为 false），不会走到这里
        assertNull(zero.entry)
    }

    // ---- 线圈行 ----

    @Test fun coilRowFlipsTheKnownStateAndTheSheetCanChangeIt() = runTest {
        val session = Session()
        val w = table(RegisterArea.COIL, DataType.BOOL, rows = 3)
        val row = w.rowPoint(1)
        val on = snap(row.id, RawValue.Flag(true), 1.0, "")
        val c = controller(session, snapshots = mapOf(row.id to on))
        after { c.requestRow(w, 1) }
        assertNull("线圈行不开输入框，直接出确认", c.entry)
        assertEquals("当前是 ON → 目标 OFF", ModbusRequest.WriteSingleCoil(3, 101, false), c.pending!!.write.plan.request)
        assertEquals("OFF", c.pending!!.row!!.shownValue)
        assertTrue(session.requests.isEmpty())
        after { c.setPendingCoil(true) }
        after { c.confirm() }
        assertEquals(listOf<ModbusRequest>(ModbusRequest.WriteSingleCoil(3, 101, true)), session.requests)
    }

    @Test fun coilRowWithUnknownStateDefaultsToOnAndNeverGuessesCurrent() = runTest {
        val c = controller(Session())
        val w = table(RegisterArea.COIL, DataType.BOOL)
        after { c.requestRow(w, 0) }
        assertEquals(ModbusRequest.WriteSingleCoil(3, 100, true), c.pending!!.write.plan.request)
        assertEquals(listOf<Int?>(null), c.pending!!.write.current)
    }

    // ---- 守卫 ----

    @Test fun editingModeRefusesEvenToOpenTheEntryAndSendsNothing() = runTest {
        val session = Session()
        var editing = true
        val c = controller(session, editing = { editing })
        after { c.requestRow(table(), 0) }
        assertNull(c.entry)
        assertNull(c.pending)
        // 输入框开着时进了编辑模式：下一步也不会生成计划
        editing = false
        after { c.requestRow(table(), 0) }
        assertNotNull(c.entry)
        editing = true
        after { assertNull(c.submitRow("5")) }
        assertNull(c.entry)
        assertNull(c.pending)
        assertTrue(session.requests.isEmpty())
    }

    @Test fun startingAnEditDismissesAnOpenEntryAndSheet() = runTest {
        val c = controller(Session())
        after { c.requestRow(table(), 0) }
        after { c.dismiss() }
        assertNull(c.entry)
        after { c.requestRow(table(), 0) }
        after { c.submitRow("5") }
        assertNotNull(c.pending)
        after { c.dismiss() }
        assertNull(c.pending)
    }

    @Test fun slaveIdleAndConnectionLossRefuse() = runTest {
        for (role in listOf(Role.SLAVE, null)) {
            val session = Session(role)
            val c = controller(session)
            after { c.requestRow(table(), 0) }
            assertNull(c.entry)
            assertNull(c.pending)
            assertEquals(WriteNoticeKind.NOT_MASTER, c.notice!!.kind)
            assertTrue(session.requests.isEmpty())
        }
        // 输入框开着，会话变成从站：下一步不生成计划
        val session = Session()
        val c = controller(session)
        after { c.requestRow(table(), 0) }
        session.state.value = SessionState.Running(Role.SLAVE, ConnectionTarget.Demo, epoch = 2)
        after { c.submitRow("5") }
        assertNull(c.pending)
        assertTrue(session.requests.isEmpty())
    }

    @Test fun confirmWhenSessionTurnedSlaveSendsNothing() = runTest {
        val session = Session()
        val c = controller(session)
        after { c.requestRow(table(), 0) }
        after { c.submitRow("5") }
        session.state.value = SessionState.Running(Role.SLAVE, ConnectionTarget.Demo, epoch = 2)
        after { c.confirm() }
        assertNull(c.pending)
        assertTrue(session.requests.isEmpty())
    }

    @Test fun notWritableTablesAndReadOnlyAreasAreIgnored() = runTest {
        val session = Session()
        val c = controller(session)
        after { c.requestRow(table(writable = false), 0) }
        assertNull(c.entry)
        assertNull("没勾“允许修改”：连提示都不弹，根本没有 ✎", c.notice)
        after { c.requestRow(table(RegisterArea.INPUT_REGISTER, writable = false), 0) }
        assertNull(c.entry)
        // 即使文件被改成“输入寄存器 + 可写”，Widget 本身不可用，控制器也拒绝
        after { c.requestRow(table(RegisterArea.INPUT_REGISTER, writable = true), 0) }
        assertNull(c.entry)
        assertNull(c.pending)
        // 行号越界
        after { c.requestRow(table(rows = 4), 4) }
        after { c.requestRow(table(rows = 4), -1) }
        assertNull(c.entry)
        // 别的组件类型
        after { c.requestRow(table().copy(kind = WidgetKinds.NUMBER), 0) }
        assertNull(c.entry)
        assertTrue(session.requests.isEmpty())
    }

    @Test fun onlyOneFlowAtATime() = runTest {
        val session = Session()
        val c = controller(session)
        after { c.requestRow(table(), 0) }
        after { c.requestRow(table(), 1) } // 输入框开着，新的请求忽略
        assertEquals(0, c.entry!!.index)
        after { c.submitRow("5") }
        after { c.requestRow(table(), 1) } // 弹窗开着，忽略
        assertEquals(0, c.pending!!.row!!.index)
        assertNull(c.entry)
    }

    // ---- 结果 ----

    @Test fun deviceExceptionAndTimeoutAreReportedAndNothingIsRetried() = runTest {
        val session = Session()
        session.writeResult = ModbusResult.Timeout
        val c = controller(session)
        after { c.requestRow(table(), 0) }
        after { c.submitRow("5") }
        after { c.confirm() }
        assertEquals(WriteNoticeKind.TIMEOUT, c.notice!!.kind)
        assertEquals("超时不自动重发", 1, session.requests.size)
    }
}
