package io.github.xingyingyuzhui.modbusmobile.application.panel

import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * 添加向导里的“实时预览”：单点位、单独的 [PointStore] 和 [PollScheduler]（不碰面板自己的轮询）。
 * 只读：和面板轮询一样只发读请求，经同一个会话的互斥锁与手动请求交错。向导关闭时 [close]。
 */
class PointPreview(session: SessionManager, scope: CoroutineScope, nowMs: () -> Long) {
    private val store = PointStore()

    /** 预览用的样本历史（曲线向导实时画图用；独立于面板的历史，关闭向导即丢）。 */
    val history = SampleHistory()
    private val scheduler = PollScheduler(session, scope, store, nowMs, history = history)

    val snapshots: StateFlow<Map<String, PointSnapshot>> = store.snapshots

    fun setPoint(point: PointDefinition?) = setPoints(listOfNotNull(point))

    /** 曲线向导：多个系列同时预览，样本留在 [history]（只留 10 分钟 / 5000 点）。 */
    fun setPoints(points: List<PointDefinition>) {
        history.track(points.associate { it.id to SampleHistory.Tracked(it, PREVIEW_RETENTION_MS, PREVIEW_MAX_POINTS) })
        scheduler.setPoints(points)
    }

    fun setActive(value: Boolean) = scheduler.setActive(value)

    fun close() {
        history.track(emptyMap())
        scheduler.setPoints(emptyList())
        scheduler.setActive(false)
    }

    private companion object {
        const val PREVIEW_RETENTION_MS = 10 * 60_000L
        const val PREVIEW_MAX_POINTS = 5_000
    }
}
