package io.github.xingyingyuzhui.modbusmobile.core.slave

import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.transport.MonotonicClock
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SimulationEngineTest {
    private class ManualClock(var ms: Long = 0) : MonotonicClock {
        override fun nowNs(): Long = ms * 1_000_000L
    }

    private val clock = ManualClock()
    private val bank = InMemoryRegisterBank(clock).apply {
        repeat(4) { putWord(1, RegisterArea.HOLDING_REGISTER, it, 0) }
        repeat(2) { putBit(1, RegisterArea.COIL, it, false) }
    }

    private fun hr(address: Int) = bank.words(1, RegisterArea.HOLDING_REGISTER).first { it.address == address }.value

    private fun engine(vararg rules: SimulationRule) = SimulationEngine(bank, clock, Random(7)).also { it.setRules(rules.toList()) }

    private fun target(address: Int, area: RegisterArea = RegisterArea.HOLDING_REGISTER, count: Int = 1) =
        SimulationTarget(1, area, address, count)

    @Test
    fun sineFollowsTimeAndWritesWithSimulationSource() {
        val sim = engine(SimulationRule("s", target(0), Waveform.Sine(center = 236.0, amplitude = 40.0, periodMs = 10_000), refreshMs = 500))
        sim.tick()
        assertEquals(236, hr(0), "t=0：中心值")
        clock.ms = 2_500 // 1/4 周期
        sim.tick()
        assertEquals(276, hr(0), "1/4 周期：中心 + 幅度")
        assertEquals(ChangeSource.SIMULATION, bank.lastChange(1, RegisterArea.HOLDING_REGISTER, 0)?.source)
        assertEquals(276, sim.states.value.single().lastRaw)
    }

    @Test
    fun rampWrapsAndRespectsRefreshInterval() {
        val sim = engine(SimulationRule("r", target(1), Waveform.Ramp(start = 65_534.0, step = 1.0, min = 0.0, max = 65_535.0), refreshMs = 1_000))
        sim.tick(); assertEquals(65_534, hr(1))
        clock.ms = 500; sim.tick(); assertEquals(65_534, hr(1), "没到刷新间隔不写")
        clock.ms = 1_000; sim.tick(); assertEquals(65_535, hr(1))
        clock.ms = 2_000; sim.tick(); assertEquals(0, hr(1), "回绕到下限")
    }

    @Test
    fun squareTogglesACoil() {
        val sim = engine(SimulationRule("q", target(0, RegisterArea.COIL), Waveform.Square(0.0, 1.0, periodMs = 2_000), refreshMs = 1_000))
        sim.tick(); assertEquals(true, bank.bits(1, RegisterArea.COIL)[0].value)
        clock.ms = 1_000; sim.tick(); assertEquals(false, bank.bits(1, RegisterArea.COIL)[0].value)
        clock.ms = 2_000; sim.tick(); assertEquals(true, bank.bits(1, RegisterArea.COIL)[0].value)
    }

    @Test
    fun externalWritePausesTheRuleAndResumeContinues() {
        val sim = engine(SimulationRule("c", target(2), Waveform.Ramp(start = 0.0, step = 10.0, min = 0.0, max = 1_000.0), refreshMs = 1_000))
        sim.tick(); assertEquals(0, hr(2))
        clock.ms = 300
        // 外部主站写 HR2 = 500（单播）
        bank.writeWords(1, RegisterArea.HOLDING_REGISTER, 2, listOf(500))
        clock.ms = 1_000; sim.tick()
        assertEquals(500, hr(2), "外部写入后不被模拟值覆盖")
        assertEquals(RuleStatus.PAUSED_BY_EXTERNAL_WRITE, sim.states.value.single().status)
        clock.ms = 5_000; sim.tick()
        assertEquals(500, hr(2), "暂停期间一直不写")
        sim.resume("c")
        sim.tick()
        assertEquals(10, hr(2), "恢复后接着上次的步数")
        assertEquals(RuleStatus.RUNNING, sim.states.value.single().status)
    }

    @Test
    fun broadcastAlsoPausesButOverridePolicyKeepsWriting() {
        val pausing = SimulationRule("p", target(0), Waveform.Constant(7.0), refreshMs = 1_000)
        val overriding = SimulationRule("o", target(1), Waveform.Constant(9.0), refreshMs = 1_000, onExternalWrite = ExternalWritePolicy.OVERRIDE)
        val sim = engine(pausing, overriding)
        sim.tick()
        clock.ms = 100
        bank.writeWords(1, RegisterArea.HOLDING_REGISTER, 0, listOf(1, 2), ChangeSource.BROADCAST)
        clock.ms = 1_000; sim.tick()
        assertEquals(1, hr(0))
        assertEquals(9, hr(1), "OVERRIDE 规则照写")
        assertEquals(RuleStatus.PAUSED_BY_EXTERNAL_WRITE, sim.states.value.first { it.rule.id == "p" }.status)
    }

    @Test
    fun externalWriteBeforeTheRuleStartedDoesNotPauseIt() {
        bank.writeWords(1, RegisterArea.HOLDING_REGISTER, 3, listOf(99))
        clock.ms = 10
        val sim = engine(SimulationRule("k", target(3), Waveform.Constant(5.0)))
        sim.tick()
        assertEquals(5, hr(3))
    }

    @Test
    fun unmappedTargetIsNotCreated() {
        val sim = engine(SimulationRule("u", target(100), Waveform.Constant(1.0)))
        sim.tick()
        assertEquals(RuleStatus.UNMAPPED, sim.states.value.single().status)
        assertNull(bank.lastChange(1, RegisterArea.HOLDING_REGISTER, 100))
        assertEquals(4, bank.words(1, RegisterArea.HOLDING_REGISTER).size, "没有新建寄存器")
    }

    @Test
    fun noiseAndClampStayInRange() {
        val sim = engine(SimulationRule("n", target(0, count = 4), Waveform.Constant(10.0), noise = 50.0, clampMin = 0.0, clampMax = 20.0))
        repeat(20) { i ->
            clock.ms = i * 1_000L
            sim.tick()
            (0 until 4).forEach { assertEquals(true, hr(it) in 0..20, "HR$it=${hr(it)}") }
        }
    }

    @Test
    fun pauseAllStopsEveryRule() {
        val sim = engine(SimulationRule("a", target(0), Waveform.Constant(3.0)))
        sim.pauseAll()
        sim.tick()
        assertEquals(0, hr(0))
        assertEquals(RuleStatus.PAUSED, sim.states.value.single().status)
    }

    @Test
    fun rulesValidateTheirParameters() {
        assertFailsWith<IllegalArgumentException> { SimulationRule("x", target(0), Waveform.Constant(1.0), refreshMs = 10) }
        assertFailsWith<IllegalArgumentException> { Waveform.Sine(0.0, 1.0, periodMs = 0) }
        assertFailsWith<IllegalArgumentException> { SimulationTarget(0, RegisterArea.HOLDING_REGISTER, 0) }
        assertFailsWith<IllegalArgumentException> { engine().setRules(listOf(SimulationRule("d", target(0), Waveform.Constant(1.0)), SimulationRule("d", target(1), Waveform.Constant(1.0)))) }
    }
}
