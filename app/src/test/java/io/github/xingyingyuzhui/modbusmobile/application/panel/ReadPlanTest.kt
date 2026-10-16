package io.github.xingyingyuzhui.modbusmobile.application.panel

import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadPlanTest {
    private fun p(
        id: String,
        address: Int,
        type: DataType = DataType.U16,
        unit: Int = 1,
        area: RegisterArea = RegisterArea.HOLDING_REGISTER,
        pollMs: Int = 1000,
        ascii: Int = 1,
    ) = PointDefinition(id, id, unit, area, address, type, asciiRegisters = ascii, pollMs = pollMs)

    @Test fun contiguousPointsMergeIntoOneRead() {
        val groups = ReadPlan.build(listOf(p("a", 10), p("b", 11, DataType.F32), p("c", 13)))
        assertEquals(1, groups.size)
        val g = groups.single()
        assertEquals(10, g.start)
        assertEquals(4, g.quantity)
        assertEquals(listOf("a" to 0, "b" to 1, "c" to 3), g.members.map { it.point.id to it.offset })
        assertEquals(ModbusRequest.ReadHoldingRegisters(1, 10, 4), g.request())
    }

    @Test fun gapsDoNotMergeByDefaultButCanBeAllowed() {
        val points = listOf(p("a", 10), p("b", 12))
        assertEquals(2, ReadPlan.build(points).size)
        val merged = ReadPlan.build(points, maxGap = 1).single()
        assertEquals(3, merged.quantity)
        assertEquals(2, merged.members[1].offset)
    }

    @Test fun overlappingPointsShareRegisters() {
        val g = ReadPlan.build(listOf(p("a", 10, DataType.U32), p("b", 11))).single()
        assertEquals(2, g.quantity)
        assertEquals(listOf(0, 1), g.members.map { it.offset })
    }

    @Test fun unitAreaAndPeriodSeparateGroups() {
        val groups = ReadPlan.build(
            listOf(
                p("a", 0), p("b", 1, unit = 2), p("c", 1, area = RegisterArea.INPUT_REGISTER), p("d", 1, pollMs = 500),
            ),
        )
        assertEquals(4, groups.size)
    }

    @Test fun readsAreSplitAtProtocolLimit() {
        val points = (0 until 130).map { p("r$it", it) }
        val groups = ReadPlan.build(points)
        assertEquals(listOf(125, 5), groups.map { it.quantity })
        assertTrue(groups.all { it.request() is ModbusRequest.ReadHoldingRegisters })
    }

    @Test fun coilsUseBitReadsWithLargerLimit() {
        val points = (0 until 10).map { p("c$it", it, DataType.BOOL, area = RegisterArea.COIL) }
        val g = ReadPlan.build(points).single()
        assertTrue(g.isBit)
        assertEquals(ModbusRequest.ReadCoils(1, 0, 10), g.request())
    }

    @Test fun duplicateIdsAndInvalidPointsAreSkipped() {
        val groups = ReadPlan.build(listOf(p("a", 5), p("a", 99), p("bad", 70000), p("zero", 1, unit = 0)))
        assertEquals(1, groups.size)
        assertEquals(5, groups.single().start)
    }

    @Test fun emptyInputGivesEmptyPlan() {
        assertTrue(ReadPlan.build(emptyList()).isEmpty())
    }
}
