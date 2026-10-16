@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.xingyingyuzhui.modbusmobile.ui.workbench

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.SerialSettings
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.application.UsbDeviceSummary
import io.github.xingyingyuzhui.modbusmobile.core.model.ExceptionCode
import io.github.xingyingyuzhui.modbusmobile.core.model.FunctionCode
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResponse
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import io.github.xingyingyuzhui.modbusmobile.core.slave.VirtualRegisterBank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkbenchWriteTest {
    @Test
    fun writeNeverGoesOutBeforeTheUserConfirms() = withModel { model, session ->
        model.setWordText(0, "123")
        model.requestWrite()
        advanceUntilIdle()
        assertNotNull(model.pending)
        assertTrue("点“预览并写入”不能直接发送", session.requests.isEmpty())

        model.dismissPending()
        advanceUntilIdle()
        assertNull(model.pending)
        assertTrue("取消后不能发送", session.requests.isEmpty())
        assertTrue(model.recentWrites.isEmpty())

        model.requestWrite()
        model.confirmWrite()
        advanceUntilIdle()
        assertEquals(listOf<ModbusRequest>(ModbusRequest.WriteSingleRegister(1, 0, 123)), session.requests)
        assertNull(model.pending)
        assertEquals(WriteOutcome.OK, model.recentWrites.single().outcome)
        assertEquals(0x06, model.recentWrites.single().functionCode)
    }

    @Test
    fun invalidInputShowsAnErrorAndDoesNotOpenTheSheet() = withModel { model, session ->
        model.setWordText(0, "abc")
        model.requestWrite()
        advanceUntilIdle()
        assertNull(model.pending)
        assertNotNull(model.formError)
        assertTrue(session.requests.isEmpty())
    }

    @Test
    fun severalValuesGoOutAsFc16() = withModel { model, session ->
        model.addValue()
        model.addValue()
        model.setWordText(0, "100")
        model.setWordText(1, "300")
        model.setWordText(2, "500")
        model.address = "100"
        model.requestWrite()
        assertEquals(0x10, model.pending!!.plan.functionCode)
        model.confirmWrite()
        advanceUntilIdle()
        assertEquals(listOf<ModbusRequest>(ModbusRequest.WriteMultipleRegisters(1, 100, listOf(100, 300, 500))), session.requests)
        assertEquals("FC10", fcLabel(model.recentWrites.single().functionCode))
    }

    @Test
    fun forcingFc16SendsOneValueAsAMultipleWrite() = withModel { model, session ->
        model.toggleForceMultiple()
        model.setWordText(0, "7")
        model.requestWrite()
        model.confirmWrite()
        advanceUntilIdle()
        assertEquals(listOf<ModbusRequest>(ModbusRequest.WriteMultipleRegisters(1, 0, listOf(7))), session.requests)
    }

    @Test
    fun coilConfirmationCanFlipTheNewValueAndKnowsTheCurrentOne() = withModel { model, session ->
        // 先读线圈，确认弹窗里的“当前”才有来源。
        session.next += ModbusResult.Success(ModbusResponse.ReadBits(1, FunctionCode.READ_COILS, List(10) { false }))
        model.area = RegisterArea.COIL
        model.address = "2"
        model.read()
        advanceUntilIdle()
        model.selectMode(WorkbenchMode.WRITE)
        model.selectWriteArea(RegisterArea.COIL)
        model.requestWrite()
        val pending = model.pending!!
        assertEquals(listOf<Int?>(0), pending.current)
        assertEquals(0x05, pending.plan.functionCode)

        model.setPendingCoil(true)
        assertEquals(true, model.pending!!.plan.bits.single())
        // 01 05 00 02 FF 00 + CRC：与稿面 b1-4 一致
        assertEquals(0xFF, model.pending!!.plan.frame[4].toInt() and 0xFF)
        model.confirmWrite()
        advanceUntilIdle()
        assertEquals(ModbusRequest.WriteSingleCoil(1, 2, true), session.requests.last())
    }

    @Test
    fun unknownCurrentValueIsNotInvented() = withModel { model, _ ->
        model.requestWrite()
        assertEquals(listOf<Int?>(null), model.pending!!.current)
    }

    @Test
    fun failedWritesAreRecordedWithTheirOutcome() = withModel { model, session ->
        session.next += ModbusResult.Timeout
        session.next += ModbusResult.UnknownOutcome
        session.next += ModbusResult.Exception(ExceptionCode.ILLEGAL_DATA_ADDRESS)
        repeat(3) {
            model.requestWrite()
            model.confirmWrite()
            advanceUntilIdle()
        }
        assertEquals(
            listOf(WriteOutcome.EXCEPTION, WriteOutcome.UNKNOWN, WriteOutcome.TIMEOUT),
            model.recentWrites.map { it.outcome },
        )
        assertEquals(2, model.recentWrites.first().exceptionCode)
    }

    @Test
    fun recentWritesKeepTheLastFive() = withModel { model, session ->
        repeat(7) { index ->
            model.setWordText(0, index.toString())
            model.requestWrite()
            model.confirmWrite()
            advanceUntilIdle()
        }
        assertEquals(5, model.recentWrites.size)
        assertEquals(6, model.recentWrites.first().words.single())
        assertEquals(7, session.requests.size)
        model.clearRecentWrites()
        assertTrue(model.recentWrites.isEmpty())
    }

    @Test
    fun clickingARecentWriteRefillsTheFormWithoutSending() = withModel { model, session ->
        model.address = "5"
        model.setWordText(0, "123")
        model.requestWrite()
        model.confirmWrite()
        advanceUntilIdle()
        model.address = "0"
        model.setWordText(0, "0")
        model.reuse(model.recentWrites.single())
        assertEquals("5", model.address)
        assertEquals(listOf("123"), model.writeWords)
        assertEquals(WorkbenchMode.WRITE, model.mode)
        assertEquals(1, session.requests.size)
    }

    @Test
    fun readMarksOnlyTheRegistersThatChanged() = withModel { model, session ->
        var now = 10_000L
        val clocked = WorkbenchViewModel(session, clock = { now })
        session.next += readOf(listOf(1, 2, 3))
        session.next += readOf(listOf(1, 9, 3))
        clocked.read()
        advanceUntilIdle()
        assertTrue((clocked.readState as ReadState.Ok).result.changedAt.isEmpty())
        now = 10_500L
        clocked.read()
        advanceUntilIdle()
        val result = (clocked.readState as ReadState.Ok).result
        assertEquals(mapOf(1 to 10_500L), result.changedAt)
        assertEquals(listOf(1, 9, 3), result.values)
        clocked.onClearedForTest()
    }

    @Test
    fun editingARowOpensTheWriteFormAtThatAddress() = withModel { model, session ->
        session.next += readOf(listOf(0, 10, 20))
        model.address = "40"
        model.read()
        advanceUntilIdle()
        val result = (model.readState as ReadState.Ok).result
        model.editRow(result, 41)
        assertEquals(WorkbenchMode.WRITE, model.mode)
        assertEquals("41", model.address)
        assertEquals(listOf("10"), model.writeWords)
        assertTrue(session.requests.size == 1)
    }

    @Test
    fun readFailuresBecomeAStateNotACrash() = withModel { model, session ->
        session.next += ModbusResult.Timeout
        model.read()
        advanceUntilIdle()
        assertTrue(model.readState is ReadState.Failed)
        model.address = "x"
        model.read()
        advanceUntilIdle()
        assertNotNull(model.formError)
        assertEquals(1, session.requests.size)
    }

    @Test
    fun pollingStopsWhenTheConnectionIsGone() = withModel { model, session ->
        session.next += ModbusResult.Disconnected
        model.changePolling(true)
        advanceUntilIdle()
        assertFalse(model.polling)
        assertEquals(1, session.requests.size)
    }

    @Test
    fun pollingYieldsToTheWriteMode() = withModel { model, session ->
        session.next += readOf(listOf(1))
        model.selectMode(WorkbenchMode.WRITE)
        model.changePolling(true)
        testScheduler.advanceTimeBy(1_000)
        testScheduler.runCurrent()
        assertTrue("写入模式下轮询不发读请求", session.requests.isEmpty())
        model.changePolling(false)
        advanceUntilIdle()
    }

    private fun readOf(values: List<Int>) =
        ModbusResult.Success(ModbusResponse.ReadRegisters(1, FunctionCode.READ_HOLDING_REGISTERS, values))

    private fun withModel(block: suspend TestScope.(WorkbenchViewModel, ScriptedSession) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val session = ScriptedSession()
            val model = ViewModelProvider(store, session.factory())[WorkbenchViewModel::class.java]
            advanceUntilIdle()
            block(model, session)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}

private fun WorkbenchViewModel.onClearedForTest() = changePolling(false)

/** 按顺序吐出预设结果；没有预设时：写请求回显成功，读请求断开。 */
private class ScriptedSession : SessionManager {
    val requests = mutableListOf<ModbusRequest>()
    val next = ArrayDeque<ModbusResult>()
    override val state = MutableStateFlow<SessionState>(SessionState.Running(Role.MASTER, ConnectionTarget.Demo, 1))
    override val traffic = MutableSharedFlow<TrafficEvent>()
    override val slaveBank = MutableStateFlow<VirtualRegisterBank?>(null)
    override val usbDevices = MutableStateFlow<List<UsbDeviceSummary>>(emptyList())
    override val echoDetected = MutableStateFlow<Boolean?>(null)
    override val serial = MutableStateFlow<SerialSettings?>(null)

    override suspend fun connect(target: ConnectionTarget, serial: SerialSettings, role: Role) = Unit

    override suspend fun disconnect() = Unit

    override suspend fun execute(request: ModbusRequest): ModbusResult {
        requests += request
        next.removeFirstOrNull()?.let { return it }
        return when (request) {
            is ModbusRequest.WriteSingleRegister ->
                ModbusResult.Success(ModbusResponse.WriteSingleRegister(request.unitId, request.function, request.address, request.value))
            is ModbusRequest.WriteSingleCoil ->
                ModbusResult.Success(ModbusResponse.WriteSingleCoil(request.unitId, request.function, request.address, request.value))
            is ModbusRequest.WriteMultipleRegisters ->
                ModbusResult.Success(ModbusResponse.WriteMultipleRegisters(request.unitId, request.function, request.address, request.quantity))
            is ModbusRequest.WriteMultipleCoils ->
                ModbusResult.Success(ModbusResponse.WriteMultipleCoils(request.unitId, request.function, request.address, request.quantity))
            else -> ModbusResult.Disconnected
        }
    }

    fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return WorkbenchViewModel(this@ScriptedSession) as T
        }
    }
}
