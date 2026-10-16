package io.github.xingyingyuzhui.modbusmobile.core.slave

import io.github.xingyingyuzhui.modbusmobile.core.model.ExceptionCode
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class BitCell(val address: Int, val value: Boolean)

data class WordCell(val address: Int, val value: Int)

data class RegisterChange(
    val unitId: Int,
    val area: RegisterArea,
    val address: Int,
)

sealed interface BankAccess<out T> {
    data class Ok<T>(val value: T) : BankAccess<T>
    data class Fail(val code: ExceptionCode) : BankAccess<Nothing>
}

interface VirtualRegisterBank {
    val changes: SharedFlow<RegisterChange>
    fun readBits(unitId: Int, area: RegisterArea, address: Int, quantity: Int): BankAccess<List<Boolean>>
    fun readWords(unitId: Int, area: RegisterArea, address: Int, quantity: Int): BankAccess<List<Int>>
    fun writeBits(unitId: Int, area: RegisterArea, address: Int, values: List<Boolean>): ExceptionCode?
    fun writeWords(unitId: Int, area: RegisterArea, address: Int, values: List<Int>): ExceptionCode?
    fun putBit(unitId: Int, area: RegisterArea, address: Int, value: Boolean)
    fun putWord(unitId: Int, area: RegisterArea, address: Int, value: Int)
    fun bits(unitId: Int, area: RegisterArea): List<BitCell>
    fun words(unitId: Int, area: RegisterArea): List<WordCell>
}

class InMemoryRegisterBank : VirtualRegisterBank {
    private val lock = Any()
    private val bits = mutableMapOf<Key, Boolean>()
    private val words = mutableMapOf<Key, Int>()
    private val updates = MutableSharedFlow<RegisterChange>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val changes: SharedFlow<RegisterChange> = updates.asSharedFlow()

    override fun readBits(unitId: Int, area: RegisterArea, address: Int, quantity: Int): BankAccess<List<Boolean>> {
        if (!area.isBit) return BankAccess.Fail(ExceptionCode.ILLEGAL_DATA_ADDRESS)
        return synchronized(lock) {
            val values = (0 until quantity).map { offset ->
                bits[Key(unitId, area, address + offset)] ?: return BankAccess.Fail(ExceptionCode.ILLEGAL_DATA_ADDRESS)
            }
            BankAccess.Ok(values)
        }
    }

    override fun readWords(unitId: Int, area: RegisterArea, address: Int, quantity: Int): BankAccess<List<Int>> {
        if (area.isBit) return BankAccess.Fail(ExceptionCode.ILLEGAL_DATA_ADDRESS)
        return synchronized(lock) {
            val values = (0 until quantity).map { offset ->
                words[Key(unitId, area, address + offset)] ?: return BankAccess.Fail(ExceptionCode.ILLEGAL_DATA_ADDRESS)
            }
            BankAccess.Ok(values)
        }
    }

    override fun writeBits(unitId: Int, area: RegisterArea, address: Int, values: List<Boolean>): ExceptionCode? =
        synchronized(lock) {
            if (!area.isBit) return ExceptionCode.ILLEGAL_DATA_ADDRESS
            val keys = values.indices.map { Key(unitId, area, address + it) }
            if (keys.any { it !in bits }) return ExceptionCode.ILLEGAL_DATA_ADDRESS
            keys.zip(values).forEach { (key, value) -> bits[key] = value }
            keys.forEach { publish(it) }
            null
        }

    override fun writeWords(unitId: Int, area: RegisterArea, address: Int, values: List<Int>): ExceptionCode? =
        synchronized(lock) {
            if (area.isBit) return ExceptionCode.ILLEGAL_DATA_ADDRESS
            if (values.any { it !in 0..0xFFFF }) return ExceptionCode.ILLEGAL_DATA_VALUE
            val keys = values.indices.map { Key(unitId, area, address + it) }
            if (keys.any { it !in words }) return ExceptionCode.ILLEGAL_DATA_ADDRESS
            keys.zip(values).forEach { (key, value) -> words[key] = value }
            keys.forEach { publish(it) }
            null
        }

    override fun putBit(unitId: Int, area: RegisterArea, address: Int, value: Boolean) {
        require(area.isBit)
        synchronized(lock) {
            val key = Key(unitId, area, address)
            bits[key] = value
            publish(key)
        }
    }

    override fun putWord(unitId: Int, area: RegisterArea, address: Int, value: Int) {
        require(!area.isBit)
        require(value in 0..0xFFFF)
        synchronized(lock) {
            val key = Key(unitId, area, address)
            words[key] = value
            publish(key)
        }
    }

    override fun bits(unitId: Int, area: RegisterArea): List<BitCell> = synchronized(lock) {
        bits.filterKeys { it.unitId == unitId && it.area == area }
            .map { BitCell(it.key.address, it.value) }
            .sortedBy { it.address }
    }

    override fun words(unitId: Int, area: RegisterArea): List<WordCell> = synchronized(lock) {
        words.filterKeys { it.unitId == unitId && it.area == area }
            .map { WordCell(it.key.address, it.value) }
            .sortedBy { it.address }
    }

    private fun publish(key: Key) {
        updates.tryEmit(RegisterChange(key.unitId, key.area, key.address))
    }

    private data class Key(val unitId: Int, val area: RegisterArea, val address: Int)
}

private val RegisterArea.isBit: Boolean
    get() = this == RegisterArea.COIL || this == RegisterArea.DISCRETE_INPUT
