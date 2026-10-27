package io.github.xingyingyuzhui.modbusmobile.application.panel

import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea

/** 一次读请求覆盖的范围，以及其中每个点位在响应里的偏移。 */
data class ReadGroup(
    val unitId: Int,
    val area: RegisterArea,
    val start: Int,
    val quantity: Int,
    val pollMs: Int,
    val members: List<Member>,
) {
    data class Member(val point: PointDefinition, val offset: Int)

    val isBit: Boolean get() = area == RegisterArea.COIL || area == RegisterArea.DISCRETE_INPUT

    fun request(): ModbusRequest = when (area) {
        RegisterArea.COIL -> ModbusRequest.ReadCoils(unitId, start, quantity)
        RegisterArea.DISCRETE_INPUT -> ModbusRequest.ReadDiscreteInputs(unitId, start, quantity)
        RegisterArea.INPUT_REGISTER -> ModbusRequest.ReadInputRegisters(unitId, start, quantity)
        RegisterArea.HOLDING_REGISTER -> ModbusRequest.ReadHoldingRegisters(unitId, start, quantity)
    }
}

object ReadPlan {
    /**
     * 把点位合成尽量少的读请求：同一从站、同一区域、同一轮询周期，范围相连或重叠（允许相隔不超过 [maxGap] 个单位）才合并；
     * 一次读不超过协议上限（寄存器 125、线圈 2000）。id 重复的点位只取第一个；校验不通过的点位不进计划。
     * 默认 [maxGap] = 0：不多读没有被点位用到的地址，避免读到设备上不存在的地址而整组失败。
     */
    fun build(points: List<PointDefinition>, maxGap: Int = 0): List<ReadGroup> {
        val unique = LinkedHashMap<String, PointDefinition>()
        for (p in points) if (p.validate().isEmpty() && p.id !in unique) unique[p.id] = p
        val groups = mutableListOf<ReadGroup>()
        val buckets = unique.values.groupBy { Triple(it.unitId, it.area, it.pollMs) }
        for ((key, list) in buckets) {
            val limit = if (key.second == RegisterArea.COIL || key.second == RegisterArea.DISCRETE_INPUT) {
                ModbusRequest.MAX_READ_BITS
            } else {
                ModbusRequest.MAX_READ_REGISTERS
            }
            val sorted = list.sortedWith(compareBy({ it.address }, { it.id }))
            var current = mutableListOf<PointDefinition>()
            var start = 0
            var end = 0 // 不含
            fun flush() {
                if (current.isEmpty()) return
                groups += ReadGroup(
                    key.first, key.second, start, end - start, key.third,
                    current.map { ReadGroup.Member(it, it.address - start) },
                )
                current = mutableListOf()
            }
            for (p in sorted) {
                val pEnd = p.address + p.quantity
                if (current.isNotEmpty() && p.address <= end + maxGap && maxOf(end, pEnd) - start <= limit) {
                    end = maxOf(end, pEnd)
                } else {
                    flush()
                    start = p.address
                    end = pEnd
                }
                current += p
            }
            flush()
        }
        return groups
    }
}
