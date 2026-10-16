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
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import io.github.xingyingyuzhui.modbusmobile.core.slave.InMemoryRegisterBank
import io.github.xingyingyuzhui.modbusmobile.core.slave.VirtualRegisterBank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkbenchViewModelTest {
    @Test
    fun slaveWorkbenchFollowsTheNewBankAfterReconnect() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val session = FakeSession()
            val model = ViewModelProvider(store, session.factory())[WorkbenchViewModel::class.java]
            val first = InMemoryRegisterBank().also { it.putWord(1, RegisterArea.HOLDING_REGISTER, 0, 11) }
            val second = InMemoryRegisterBank().also { it.putWord(1, RegisterArea.HOLDING_REGISTER, 0, 22) }

            advanceUntilIdle()
            session.banks.value = first
            advanceUntilIdle()
            assertEquals("11", model.slaveRows.holding.single().draft)

            session.banks.value = null
            advanceUntilIdle()
            assertTrue(model.slaveRows.holding.isEmpty())

            session.banks.value = second
            advanceUntilIdle()
            assertEquals("22", model.slaveRows.holding.single().draft)

            model.editHolding(0, "33")
            advanceUntilIdle()
            assertEquals(33, second.words(1, RegisterArea.HOLDING_REGISTER).single().value)
            assertEquals(11, first.words(1, RegisterArea.HOLDING_REGISTER).single().value)
            assertEquals("33", model.slaveRows.holding.single().draft)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}

private class FakeSession : SessionManager {
    val banks = MutableStateFlow<VirtualRegisterBank?>(null)
    override val state = MutableStateFlow<SessionState>(SessionState.Idle)
    override val traffic = MutableSharedFlow<TrafficEvent>()
    override val slaveBank = banks
    override val usbDevices = MutableStateFlow<List<UsbDeviceSummary>>(emptyList())
    override val echoDetected = MutableStateFlow<Boolean?>(null)
    override val serial = MutableStateFlow<SerialSettings?>(null)

    override suspend fun connect(target: ConnectionTarget, serial: SerialSettings, role: Role) = Unit

    override suspend fun disconnect() = Unit

    override suspend fun execute(request: ModbusRequest): ModbusResult = ModbusResult.Disconnected

    fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return WorkbenchViewModel(this@FakeSession) as T
        }
    }
}
