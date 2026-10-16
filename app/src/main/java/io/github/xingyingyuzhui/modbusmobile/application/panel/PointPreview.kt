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
    private val scheduler = PollScheduler(session, scope, store, nowMs)

    val snapshots: StateFlow<Map<String, PointSnapshot>> = store.snapshots

    fun setPoint(point: PointDefinition?) = scheduler.setPoints(listOfNotNull(point))

    fun setActive(value: Boolean) = scheduler.setActive(value)

    fun close() {
        scheduler.setPoints(emptyList())
        scheduler.setActive(false)
    }
}
