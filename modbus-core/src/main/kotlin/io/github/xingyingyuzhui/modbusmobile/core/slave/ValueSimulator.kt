package io.github.xingyingyuzhui.modbusmobile.core.slave

import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.random.Random

/**
 * 从站模拟规则的波形（spec §5.3）。所有波形都按“工程上的数值”算，最后才换成寄存器原始值：
 * 寄存器区四舍五入并夹到 0..65535（以及规则的上下限），线圈 / 离散输入非 0 即 ON。
 */
sealed interface Waveform {
    /** 固定值。 */
    data class Constant(val value: Double) : Waveform

    /** 每次刷新加 [step]；越过 [max] 时 [wrap] 为 true 回到 [min]，否则停在端点。[step] 可为负。 */
    data class Ramp(val start: Double, val step: Double, val min: Double, val max: Double, val wrap: Boolean = true) : Waveform {
        init {
            require(max >= min) { "上限必须不小于下限" }
        }
    }

    /** 正弦：center + amplitude × sin(2π·t / period + phase)。 */
    data class Sine(val center: Double, val amplitude: Double, val periodMs: Long, val phaseDeg: Double = 0.0) : Waveform {
        init {
            require(periodMs > 0) { "周期必须大于 0" }
        }
    }

    /** 方波：每个周期前 [duty] 比例为 [high]，其余为 [low]。线圈用它做周期翻转。 */
    data class Square(val low: Double, val high: Double, val periodMs: Long, val duty: Double = 0.5) : Waveform {
        init {
            require(periodMs > 0) { "周期必须大于 0" }
            require(duty in 0.0..1.0) { "占空比必须在 0..1" }
        }
    }

    /** 每次刷新在 [min]..[max] 内均匀随机取一个值。 */
    data class RandomUniform(val min: Double, val max: Double) : Waveform {
        init {
            require(max >= min) { "上限必须不小于下限" }
        }
    }

    /** 按顺序循环输出，每次刷新取下一个。 */
    data class Sequence(val values: List<Double>) : Waveform {
        init {
            require(values.isNotEmpty()) { "序列不能为空" }
        }
    }
}

/** 规则作用的一段连续地址；同一段里每个地址写同一个值。 */
data class SimulationTarget(val unitId: Int, val area: RegisterArea, val address: Int, val count: Int = 1) {
    init {
        require(unitId in 1..247) { "Unit ID 必须在 1..247" }
        require(count in 1..125) { "地址个数必须在 1..125" }
        require(address in 0..0xFFFF && address.toLong() + count - 1 <= 0xFFFFL) { "地址超出 0..65535" }
    }

    val isBit: Boolean get() = area == RegisterArea.COIL || area == RegisterArea.DISCRETE_INPUT
}

/** 外部主站写了规则管着的地址时怎么办。默认暂停，避免刚写进去就被模拟值覆盖（spec §5.3）。 */
enum class ExternalWritePolicy { PAUSE, OVERRIDE }

data class SimulationRule(
    val id: String,
    val target: SimulationTarget,
    val waveform: Waveform,
    val refreshMs: Long = 1_000,
    /** 每次在结果上叠加 ±[noise] 的均匀随机量；0 表示不叠加。 */
    val noise: Double = 0.0,
    val clampMin: Double = 0.0,
    val clampMax: Double = 65_535.0,
    val onExternalWrite: ExternalWritePolicy = ExternalWritePolicy.PAUSE,
    val enabled: Boolean = true,
) {
    init {
        require(refreshMs >= MIN_REFRESH_MS) { "刷新间隔不能小于 $MIN_REFRESH_MS ms" }
        require(noise >= 0.0) { "叠加随机量不能为负" }
        require(clampMax >= clampMin) { "上限必须不小于下限" }
    }

    companion object {
        const val MIN_REFRESH_MS = 50L
    }
}

object Waveforms {
    /**
     * 第 [step] 次刷新（从 0 起）、距规则开始 [elapsedMs] 毫秒时的数值，不含叠加随机和限幅。
     * Ramp / Sequence / Random 按刷新次数走，Sine / Square 按时间走。
     */
    fun value(waveform: Waveform, elapsedMs: Long, step: Long, random: Random): Double = when (waveform) {
        is Waveform.Constant -> waveform.value
        is Waveform.Ramp -> ramp(waveform, step)
        is Waveform.Sine -> {
            val phase = 2.0 * PI * (elapsedMs % waveform.periodMs) / waveform.periodMs + waveform.phaseDeg * PI / 180.0
            waveform.center + waveform.amplitude * sin(phase)
        }
        is Waveform.Square -> {
            val position = (elapsedMs % waveform.periodMs).toDouble() / waveform.periodMs
            if (position < waveform.duty) waveform.high else waveform.low
        }
        is Waveform.RandomUniform ->
            if (waveform.max == waveform.min) waveform.min else random.nextDouble(waveform.min, waveform.max)
        is Waveform.Sequence -> waveform.values[(step % waveform.values.size).toInt()]
    }

    private fun ramp(w: Waveform.Ramp, step: Long): Double {
        val raw = w.start + w.step * step
        if (!w.wrap) return raw.coerceIn(w.min, w.max)
        val span = w.max - w.min
        if (span <= 0.0) return w.min
        // 回绕区间是 [min, max]，长度按整数步看是 span + 1（例如 0..65535 回绕后 65535 的下一个是 0）。
        val period = span + 1.0
        val offset = raw - w.min
        return w.min + (offset - floor(offset / period) * period)
    }

    /** 加叠加随机、限幅后的寄存器原始值：寄存器 0..65535，位 0 / 1。 */
    fun raw(rule: SimulationRule, value: Double, random: Random): Int {
        val noisy = if (rule.noise > 0.0) value + random.nextDouble(-rule.noise, rule.noise) else value
        if (rule.target.isBit) return if (noisy != 0.0) 1 else 0
        val clamped = noisy.coerceIn(maxOf(rule.clampMin, 0.0), minOf(rule.clampMax, 65_535.0))
        return clamped.roundToLong().toInt()
    }
}
