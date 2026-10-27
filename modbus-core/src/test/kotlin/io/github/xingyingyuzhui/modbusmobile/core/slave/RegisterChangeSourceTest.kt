package io.github.xingyingyuzhui.modbusmobile.core.slave

import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.transport.MonotonicClock
import kotlin.test.Test
import kotlin.test.assertEquals

class RegisterChangeSourceTest {
    private val clock = object : MonotonicClock {
        var ns = 0L
        override fun nowNs() = ns
    }

    @Test
    fun changesCarrySourceOldNewAndTime() {
        val bank = InMemoryRegisterBank(clock)
        bank.putWord(1, RegisterArea.HOLDING_REGISTER, 0, 10)
        assertEquals(RegisterChange(1, RegisterArea.HOLDING_REGISTER, 0, ChangeSource.LOCAL_EDIT, null, 10, 0), bank.lastChange(1, RegisterArea.HOLDING_REGISTER, 0))
        clock.ns = 42
        bank.writeWords(1, RegisterArea.HOLDING_REGISTER, 0, listOf(20))
        assertEquals(RegisterChange(1, RegisterArea.HOLDING_REGISTER, 0, ChangeSource.EXTERNAL_WRITE, 10, 20, 42), bank.lastChange(1, RegisterArea.HOLDING_REGISTER, 0))
        bank.putBit(1, RegisterArea.COIL, 3, true)
        bank.writeBits(1, RegisterArea.COIL, 3, listOf(false), ChangeSource.BROADCAST)
        val bit = bank.lastChange(1, RegisterArea.COIL, 3)!!
        assertEquals(ChangeSource.BROADCAST, bit.source)
        assertEquals(1, bit.oldValue)
        assertEquals(0, bit.newValue)
    }

    @Test
    fun rejectedWriteLeavesNoChange() {
        val bank = InMemoryRegisterBank(clock)
        bank.putWord(1, RegisterArea.HOLDING_REGISTER, 0, 1)
        // 跨到未映射的地址 1：整段拒绝
        bank.writeWords(1, RegisterArea.HOLDING_REGISTER, 0, listOf(5, 6))
        assertEquals(ChangeSource.LOCAL_EDIT, bank.lastChange(1, RegisterArea.HOLDING_REGISTER, 0)!!.source)
    }
}
