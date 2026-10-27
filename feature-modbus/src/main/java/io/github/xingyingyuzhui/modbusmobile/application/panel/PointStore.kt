package io.github.xingyingyuzhui.modbusmobile.application.panel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 数据质量。
 * - [NO_DATA]：还没读到过值（刚开始 / 刚换连接）。
 * - [OK]：最近一次读取成功。
 * - [STALE]：最近一次读取失败，但还保留着上一次的值（界面应显示成“旧值”）。
 * - [TIMEOUT]：连续失败达到 [PollScheduler.TIMEOUT_STREAK] 次，且最近一次是超时。
 * - [ERROR]：设备返回异常码 / 响应无效 / 值本身无效（BCD 非法、F32 为 NaN）。
 */
enum class Quality { NO_DATA, OK, STALE, TIMEOUT, ERROR }

enum class ErrorKind { TIMEOUT, EXCEPTION, INVALID_RESPONSE, UNKNOWN_OUTCOME, DISCONNECTED, BAD_VALUE }

data class PointError(val kind: ErrorKind, val code: Int? = null, val detail: String? = null)

/** 一个点位的最新状态。失败时保留上一次成功的 [raw] / [engineering] / [text]，由 [quality] 说明可信度。 */
data class PointSnapshot(
    val pointId: String,
    val quality: Quality = Quality.NO_DATA,
    val raw: RawValue? = null,
    val engineering: Double? = null,
    /** 已按小数位格式化的文本；ASCII 为字符串；布尔点位和 NaN 为 null。 */
    val text: String? = null,
    val level: ThresholdLevel = ThresholdLevel.NORMAL,
    /** 最近一次成功读取的时间（调度器的单调毫秒时钟）。 */
    val updatedAtMs: Long? = null,
    /** 同一时刻的墙钟（毫秒），界面显示“10:21:07”用。 */
    val updatedWallMs: Long? = null,
    /** 连续失败次数（成功后清零）。 */
    val failures: Int = 0,
    val error: PointError? = null,
)

/** 点位最新值的内存存储（P7 的曲线缓冲会另建，不放在这里）。线程安全：只通过原子更新改 StateFlow。 */
class PointStore {
    private val _snapshots = MutableStateFlow<Map<String, PointSnapshot>>(emptyMap())
    val snapshots: StateFlow<Map<String, PointSnapshot>> = _snapshots.asStateFlow()

    /** 换工程 / 换连接：全部回到“无数据”。上一次连接读到的值不再可信。 */
    fun reset(points: List<PointDefinition>) {
        _snapshots.value = points.associate { it.id to PointSnapshot(it.id) }
    }

    fun get(id: String): PointSnapshot? = _snapshots.value[id]

    /** 一次性写入一批（同一次读取的所有点位），观察者只会看到整批更新后的状态。 */
    fun apply(updates: Map<String, PointSnapshot>) {
        if (updates.isEmpty()) return
        _snapshots.update { it + updates }
    }
}
